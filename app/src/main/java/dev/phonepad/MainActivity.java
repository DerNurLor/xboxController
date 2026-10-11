package dev.phonepad;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.JSONObject;
import java.security.MessageDigest;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int QR_SCAN_REQUEST=4713;
    private android.hardware.input.InputManager inputManager;
    private final android.hardware.input.InputManager.InputDeviceListener deviceListener=new android.hardware.input.InputManager.InputDeviceListener(){public void onInputDeviceAdded(int id){}public void onInputDeviceChanged(int id){if(pad!=null)pad.release();}public void onInputDeviceRemoved(int id){if(pad!=null)pad.release();}};
    private PadView pad;
    private BluetoothPad bluetooth;
    private WifiPad wifi;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean resumed, wifiMode, suggestedCompanion, autoReconnectAttempted;
    public PhoneHaptics haptics;
    private String btStatus="Bluetooth · not connected",wifiStatus="Wi-Fi · not connected";
    private long lastSend,lastReconnect;
    private final Runnable tick=new Runnable(){public void run(){
        if(!resumed)return;
        long now=SystemClock.elapsedRealtime();
        boolean isConnected=wifiMode?wifi.connected():bluetooth.connected();
        if(isConnected&&!wifiMode&&!suggestedCompanion){suggestedCompanion=true;Toast.makeText(MainActivity.this,"Bluetooth ready. For game vibration, open Connection → Find my computer.",Toast.LENGTH_LONG).show();}
        if(pad.connected&&!isConnected)pad.release();
        pad.gyro.sync(resumed);if(pad.connected!=isConnected)pad.invalidate();pad.connected=isConnected;pad.refresh(SystemClock.uptimeMillis());
        if(wifiMode){wifi.sendSource(pad.input.sourceState.report());wifi.send(pad.state.report());wifi.sendDesktop(pad.input.automation.desktop());}
        if(!wifiMode && permitted()) {
            if(now-lastSend>=16){bluetooth.sendSource(pad.input.sourceState.report());bluetooth.send(pad.state.report());bluetooth.sendDesktop(pad.input.automation.desktop());lastSend=now;}
            if(now-lastReconnect>2000){bluetooth.start();bluetooth.reconnect();lastReconnect=now;}
        }
        boolean connected=wifiMode?wifi.connected():bluetooth.connected();if(pad.connected!=connected)pad.invalidate();pad.connected=connected;
        handler.removeCallbacks(this);handler.postDelayed(this,pad.nextFrameDelay());
    }};
    @Override public void onCreate(Bundle saved) {
        AppAppearance.apply(this);super.onCreate(saved);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WindowManager.LayoutParams windowParams=getWindow().getAttributes();
        windowParams.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        getWindow().setAttributes(windowParams);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_LAYOUT_STABLE|
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        if(Build.VERSION.SDK_INT>=30) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller=getWindow().getInsetsController();
            if(controller!=null) {
                controller.hide(WindowInsets.Type.systemBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }
        haptics=new PhoneHaptics(this);
        bluetooth=new BluetoothPad(this,new BluetoothPad.Listener(){
            public void disconnected(){if(pad!=null&&!wifiMode)pad.release();}
            public void stopped(){if(!wifiMode)haptics.stopHost();}
            public void status(String s){btStatus=s;showStatus();android.util.Log.i("PhonePad",s);}
            public void rumble(int low,int high,int ms){if(!wifiMode)haptics.host(low,high,ms,"Bluetooth report 2");}
        });
        wifi=new WifiPad(new WifiPad.Listener(){
            public void status(String s){handler.post(()->{wifiStatus=s;showStatus();android.util.Log.i("PhonePad",s);});}
            public void stopped(long session){handler.post(()->{if(wifiMode&&wifi.sessionGeneration()==session){haptics.stopHost();if(pad!=null){pad.input.automation.clearGameState();pad.release();}}});}
            public void gameState(String json,long session){long received=SystemClock.uptimeMillis();handler.post(()->{if(!wifiMode||wifi.sessionGeneration()!=session||pad==null||SystemClock.uptimeMillis()-received>250)return;try{JSONObject object=new JSONObject(json);Map<String,String> values=new HashMap<>();Iterator<String> names=object.keys();while(names.hasNext()){String name=names.next();values.put(name,object.getString(name));}if(pad.input.automation.gameState(values,SystemClock.uptimeMillis())){if(!pad.settingsOpen&&!pad.editing)pad.hardwareChanged();pad.invalidate();}}catch(Exception ignored){}});}
            public void rumble(int low,int high,int ms){}
            public void rumble(int low,int high,int ms,long session){long received=SystemClock.uptimeMillis();handler.post(()->{long age=SystemClock.uptimeMillis()-received;if(wifiMode&&wifi.sessionGeneration()==session&&age<250)haptics.host(low,high,(int)Math.max(0,ms-age),"Wi-Fi PP01 authenticated ACK");});}
        });
        pad=new PadView(this,new PadView.Actions(){
            public void connect(){connectionDialog();}
            public void settings(){settingsDialog();}
            public void feedback(int strength){haptics.local(strength);}
            public void emergency(){emergencyStop();}
            public void pause(){gameMenu();}
            public void released(){haptics.stop();if(wifiMode)wifi.neutral();else if(bluetooth!=null&&permitted())bluetooth.neutral();}
            public void transitioned(){if(wifiMode)wifi.replace(pad.state.report());else changed();}
            public void changed(){
                if(pad==null)return;
                if(resumed){handler.removeCallbacks(tick);handler.postDelayed(tick,pad.nextFrameDelay());}
                if(wifiMode){wifi.sendSource(pad.input.sourceState.report());wifi.send(pad.state.report());wifi.sendDesktop(pad.input.automation.desktop());}
                else if(bluetooth!=null && permitted()){bluetooth.sendSource(pad.input.sourceState.report());bluetooth.send(pad.state.report());bluetooth.sendDesktop(pad.input.automation.desktop());}
            }
        });
        pad.gyro=new GyroInput(this,pad);setContentView(pad);inputManager=(android.hardware.input.InputManager)getSystemService(INPUT_SERVICE);inputManager.registerInputDeviceListener(deviceListener,handler);
        if(!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},1);
    }
    private boolean permitted(){return Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
    void scanCompanionQr(){startActivityForResult(new Intent(this,QrScannerActivity.class),QR_SCAN_REQUEST);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==QR_SCAN_REQUEST&&result==RESULT_FIRST_USER&&data!=null&&data.getBooleanExtra(QrScannerActivity.EXTRA_MANUAL,false)){
            new ControllerSettings(this,pad,this::connectionDialog).wifiPage();
            return;
        }
        if(request!=QR_SCAN_REQUEST||result!=RESULT_OK||data==null)return;
        try{
            String payload=data.getStringExtra(QrScannerActivity.EXTRA_PAYLOAD);
            if(payload==null||!payload.startsWith("PPQR1:"))throw new IllegalArgumentException("This is not a PhonePad connection code.");
            JSONObject config=new JSONObject(payload.substring(6));
            if(config.getInt("v")!=1)throw new IllegalArgumentException("This PhonePad code version is not supported.");
            String host=config.getString("host"),id=config.getString("id"),name=config.optString("name","Computer"),publicKey=config.getString("public"),token=config.getString("token");
            int port=config.getInt("port");byte[] encoded=CompanionLink.hex(publicKey,91);String expected=CompanionLink.hex(MessageDigest.getInstance("SHA-256").digest(encoded));
            CompanionLink.hex(token,16);
            if(host.trim().isEmpty()||port<1||port>65535||!id.equals(expected))throw new IllegalArgumentException("The connection code has invalid network details.");
            CompanionLink.Computer computer=new CompanionLink.Computer(name,host,port,id,publicKey,false);
            new ControllerSettings(this,pad,this::connectionDialog).pairScanned(computer,token);
        }catch(Exception error){Toast.makeText(this,error.getMessage()==null?"Could not read the PhonePad connection code.":error.getMessage(),Toast.LENGTH_LONG).show();}
    }
    @Override public void onRequestPermissionsResult(int r,String[] permissions,int[] results){super.onRequestPermissionsResult(r,permissions,results);if(permitted())bluetooth.start();else{btStatus="Nearby devices permission required · tap Connect to retry";showStatus();}}
    private void showStatus(){if(pad!=null){pad.status=wifiMode?wifiStatus:btStatus;pad.transport=wifiMode?"WI-FI · 60 HZ":"BLUETOOTH HID";pad.invalidate();}}
    @Override protected void onResume(){super.onResume();if(pad!=null)pad.loadSettings();resumed=true;haptics.active(true);if(!autoReconnectAttempted){autoReconnectAttempted=true;reconnectLastComputer();}if(permitted()&&!wifiMode)bluetooth.start();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){pad.gyro.sync(false);pad.release();if(permitted())bluetooth.neutral();wifi.neutral();resumed=false;haptics.active(false);handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onStop(){if(permitted())bluetooth.suspend();super.onStop();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.hide(WindowInsets.Type.systemBars());}if(haptics!=null)haptics.active(focus&&resumed&&pad!=null&&!pad.settingsOpen);if(!focus&&pad!=null){pad.gyro.sync(false);pad.release();wifi.neutral();if(permitted())bluetooth.neutral();}}
    @Override protected void onDestroy(){if(inputManager!=null)inputManager.unregisterInputDeviceListener(deviceListener);wifi.close();if(permitted())bluetooth.close();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private void emergencyStop(){pad.release();haptics.stop();wifi.close();wifiMode=false;if(permitted())bluetooth.disconnect();getSharedPreferences("connection",0).edit().remove("lastTransport").remove("lastWifiKey").remove("lastComputer").apply();showStatus();Toast.makeText(this,"All inputs released and transports disconnected",Toast.LENGTH_LONG).show();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){return HardwareInput.key(pad,event)||super.dispatchKeyEvent(event);}
    @Override public boolean dispatchGenericMotionEvent(MotionEvent event){return HardwareInput.motion(pad,event)||super.dispatchGenericMotionEvent(event);}
    @Override public boolean dispatchTouchEvent(MotionEvent event){if(event.isFromSource(InputDevice.SOURCE_MOUSE)&&HardwareInput.motion(pad,event))return true;return super.dispatchTouchEvent(event);}
    @Override public void onBackPressed(){if(pad.editing&&!pad.settingsOpen){pad.finishEditing();new ControllerSettings(this,pad,this::connectionDialog).setup();return;}if(pad.settingsOpen){super.onBackPressed();return;}gameMenu();}
    private void gameMenu(){
        if(pad.gameplayLock.locked){pad.gameHandleLearned();pad.unlock();}
        else new ControllerSettings(this,pad,this::connectionDialog).setup();
    }
    private void connectionDialog(){new ControllerSettings(this,pad,this::connectionDialog).connectionPage();}
    void connectionAction(int i,ControllerSettings settings){
        pad.release();
        switch(i){
            case 0:chooseBluetooth(settings,false);break;
            case 1:if(ensureBluetooth()){switchBluetooth();Intent in=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);in.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,180);startActivity(in);}break;
            case 2:startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));break;
            case 3:settings.wifiPage();break;
            case 4:emergencyStop();break;
            case 5:settings.connectionStatus(btStatus+"\n"+wifiStatus+"\nWi-Fi keyboard/mouse output: "+(wifi.desktopConnected()?"acknowledged":"not acknowledged · updated PhonePad Companion required")+"\nLast connected Bluetooth address: "+getSharedPreferences("connection",0).getString("lastComputer","None")+"\nBluetooth reports: "+bluetooth.reports+" / failures: "+bluetooth.failures+"\nWi-Fi desktop generated / serialized / attempted / acknowledged: "+wifi.desktopGenerated+" / "+wifi.desktopSerialized+" / "+wifi.desktopSendAttempts+" / "+wifi.desktopAcknowledged+"\nWi-Fi reports: "+wifi.reports+"\nInvalid feedback: "+wifi.invalidFeedback+" · stale feedback: "+wifi.staleFeedback);break;
        }
    }
    boolean bluetoothConnected(){return !wifiMode&&bluetooth.connected();}
    private boolean ensureBluetooth(){
        if(!permitted()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},1);return false;}
        if(!bluetooth.enabled()){try{startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));}catch(ActivityNotFoundException e){Toast.makeText(this,"Bluetooth is unavailable",Toast.LENGTH_LONG).show();}return false;}return true;
    }
    private void switchBluetooth(){pad.input.automation.clearGameState();wifi.close();wifiMode=false;bluetooth.start();showStatus();}
    void knownComputers(LinearLayout body,ControllerSettings settings){
        if(!permitted())return;String previous=getSharedPreferences("connection",0).getString("lastComputer","");
        for(BluetoothDevice device:bluetooth.paired()){
            boolean known=device.getAddress().equals(previous),computer=device.getBluetoothClass()!=null&&device.getBluetoothClass().getMajorDeviceClass()==BluetoothClass.Device.Major.COMPUTER;if(!known&&!computer)continue;
            String name=device.getName()==null?"Unnamed computer":device.getName();settings.computerRow(body,name,known?"Previously connected":"Paired",()->{if(ensureBluetooth()){switchBluetooth();bluetooth.connect(device);settings.connectionPage();}});
        }
    }
    private void chooseBluetooth(ControllerSettings settings,boolean all){
        if(!ensureBluetooth())return;List<BluetoothDevice> devices=new ArrayList<>(bluetooth.paired());String previous=getSharedPreferences("connection",0).getString("lastComputer","");
        devices.removeIf(d->!all&&!d.getAddress().equals(previous)&&(d.getBluetoothClass()==null||d.getBluetoothClass().getMajorDeviceClass()!=BluetoothClass.Device.Major.COMPUTER));
        devices.sort((a,b)->Boolean.compare(!a.getAddress().equals(previous),!b.getAddress().equals(previous)));
        String[] names=new String[devices.size()];Runnable[] actions=new Runnable[devices.size()];for(int i=0;i<devices.size();i++){BluetoothDevice device=devices.get(i);String name=device.getName()==null?"Unnamed device":device.getName();boolean computer=device.getBluetoothClass()!=null&&device.getBluetoothClass().getMajorDeviceClass()==BluetoothClass.Device.Major.COMPUTER;names[i]=name+"\n"+(computer?"Computer":"Bluetooth device")+(device.getAddress().equals(previous)?" · Previously connected":" · Paired");actions[i]=()->{switchBluetooth();bluetooth.connect(device);settings.connectionPage();};}
        settings.devicePicker(names,actions,all,()->chooseBluetooth(settings,!all),()->connectionAction(1,settings));
    }
    void connectWifi(String host,int port,String key){
        if(host.isEmpty())throw new IllegalArgumentException("Enter your computer's address");WifiPad.hex(key);if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
        haptics.stop();if(permitted())bluetooth.disconnect();wifiMode=true;wifi.connect(host,port,key);showStatus();getSharedPreferences("connection",0).edit().putString("host",host).putString("port",Integer.toString(port)).putString("lastWifiKey",key.trim()).putString("lastTransport","wifi").apply();
    }
    private void reconnectLastComputer(){
        SharedPreferences prefs=getSharedPreferences("connection",0);
        if(!"wifi".equals(prefs.getString("lastTransport","")))return;
        String host=prefs.getString("host","");
        String key=prefs.getString("lastWifiKey","");
        int port;try{port=Integer.parseInt(prefs.getString("port","39570"));}catch(Exception ignored){port=39570;}
        if(host.isEmpty()||key.isEmpty())return;
        try{connectWifi(host,port,key);}catch(IllegalArgumentException invalid){prefs.edit().remove("lastTransport").remove("lastWifiKey").apply();}
    }
    private void settingsDialog(){new ControllerSettings(this,pad,this::connectionDialog).show();}
}
