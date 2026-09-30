package dev.phonepad;

import android.Manifest;
import android.app.*;
import android.bluetooth.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

public final class MainActivity extends Activity {
    private PadView pad;
    private BluetoothPad bluetooth;
    private WifiPad wifi;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean resumed, wifiMode;
    private Vibrator vibrator;
    private long vibrationEnd;
    private int amplitude;
    private String btStatus="Bluetooth · not connected",wifiStatus="Wi-Fi · not connected";
    private long lastSend,lastReconnect;
    private final Runnable tick=new Runnable(){public void run(){
        if(!resumed)return;
        long now=SystemClock.elapsedRealtime();
        boolean isConnected=wifiMode?wifi.connected():bluetooth.connected();
        if(pad.connected&&!isConnected)pad.release();
        pad.gyro.sync(resumed);pad.connected=isConnected;pad.refresh(SystemClock.uptimeMillis());
        if(wifiMode)wifi.send(pad.state.report());
        if(!wifiMode && permitted()) {
            if(now-lastSend>=16){bluetooth.send(pad.state.report());lastSend=now;}
            if(now-lastReconnect>2000){bluetooth.start();bluetooth.reconnect();lastReconnect=now;}
        }
        pad.connected=wifiMode?wifi.connected():bluetooth.connected();pad.invalidate();handler.postDelayed(this,16);
    }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
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
        vibrator=getSystemService(Vibrator.class);
        bluetooth=new BluetoothPad(this,new BluetoothPad.Listener(){
            public void disconnected(){if(pad!=null&&!wifiMode)pad.release();}
            public void status(String s){btStatus=s;showStatus();android.util.Log.i("PhonePad",s);}
            public void rumble(int low,int high,int ms){if(!wifiMode)vibrate(low,high,ms);}
        });
        wifi=new WifiPad(new WifiPad.Listener(){
            public void status(String s){handler.post(()->{wifiStatus=s;showStatus();android.util.Log.i("PhonePad",s);});}
            public void rumble(int low,int high,int ms){handler.post(()->{if(wifiMode)vibrate(low,high,ms);});}
        });
        pad=new PadView(this,new PadView.Actions(){
            public void connect(){connectionDialog();}
            public void settings(){settingsDialog();}
            public void released(){if(wifiMode)wifi.neutral();else if(bluetooth!=null&&permitted())bluetooth.neutral();}
            public void changed(){
                if(pad==null)return;
                if(wifiMode)wifi.send(pad.state.report());
                else if(bluetooth!=null && permitted())bluetooth.send(pad.state.report());
            }
        });
        pad.gyro=new GyroInput(this,pad);setContentView(pad);
        if(!permitted())requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},1);
    }
    private boolean permitted(){return Build.VERSION.SDK_INT<31 || (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.BLUETOOTH_ADVERTISE)==PackageManager.PERMISSION_GRANTED);}
    @Override public void onRequestPermissionsResult(int r,String[] permissions,int[] results){super.onRequestPermissionsResult(r,permissions,results);if(permitted())bluetooth.start();else{btStatus="Nearby devices permission required · tap Connect to retry";showStatus();}}
    private void showStatus(){if(pad!=null){pad.status=wifiMode?wifiStatus:btStatus;pad.transport=wifiMode?"WI-FI · 60 HZ":"BLUETOOTH HID";pad.invalidate();}}
    private void vibrate(int low,int high,int ms){
        if(vibrator==null)return;
        int a=Math.max(low,high);long now=SystemClock.elapsedRealtime();
        if(a==0 || ms==0 || !resumed){vibrator.cancel();amplitude=0;vibrationEnd=0;return;}
        if(a!=amplitude || vibrationEnd-now<80){
            int duration=Math.min(ms,2500);vibrator.vibrate(VibrationEffect.createOneShot(duration,a));vibrationEnd=now+duration;amplitude=a;
        }
    }
    @Override protected void onResume(){super.onResume();if(pad!=null)pad.loadSettings();resumed=true;if(permitted()&&!wifiMode)bluetooth.start();handler.removeCallbacks(tick);handler.post(tick);}
    @Override protected void onPause(){pad.gyro.sync(false);pad.release();if(permitted())bluetooth.neutral();wifi.neutral();resumed=false;vibrate(0,0,0);handler.removeCallbacks(tick);super.onPause();}
    @Override protected void onStop(){if(permitted())bluetooth.suspend();super.onStop();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&Build.VERSION.SDK_INT>=30){WindowInsetsController c=getWindow().getInsetsController();if(c!=null)c.hide(WindowInsets.Type.systemBars());}if(!focus&&pad!=null){pad.gyro.sync(false);pad.release();wifi.neutral();if(permitted())bluetooth.neutral();}}
    @Override protected void onDestroy(){wifi.close();if(permitted())bluetooth.close();handler.removeCallbacksAndMessages(null);super.onDestroy();}
    private void connectionDialog(){
        pad.release();new AlertDialog.Builder(this).setTitle("Connect PhonePad")
            .setItems(new String[]{"Bluetooth · choose paired host","Bluetooth · make phone discoverable","Open Bluetooth pairing settings","Wi-Fi · connect to companion","Disconnect","Connection diagnostics"},(d,i)->{
                switch(i){
                    case 0:chooseBluetooth();break;
                    case 1:if(ensureBluetooth()){switchBluetooth();Intent in=new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);in.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION,180);startActivity(in);}break;
                    case 2:startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));break;
                    case 3:wifiDialog();break;
                    case 4:wifi.close();if(permitted())bluetooth.disconnect();wifiStatus="Wi-Fi · disconnected";showStatus();break;
                    case 5:new AlertDialog.Builder(this).setTitle("Diagnostics").setMessage("Bluetooth: "+btStatus+"\nReports accepted by Android: "+bluetooth.reports+"\nSend failures: "+bluetooth.failures+"\n\nWi-Fi: "+wifiStatus+"\nReports sent: "+wifi.reports+"\n\nBluetooth is a standard HID gamepad. XInput-only Windows games need Steam Input or the companion. Bluetooth rumble needs a companion that understands PhonePad’s output report.\n\nKeep the app visible while playing. No console compatibility is claimed.").setPositiveButton("OK",null).show();break;
                }
            }).setNegativeButton("Close",null).show();
    }
    private boolean ensureBluetooth(){
        if(!permitted()){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_ADVERTISE},1);return false;}
        if(!bluetooth.enabled()){try{startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));}catch(ActivityNotFoundException e){Toast.makeText(this,"Bluetooth is unavailable",Toast.LENGTH_LONG).show();}return false;}return true;
    }
    private void switchBluetooth(){wifi.close();wifiMode=false;bluetooth.start();showStatus();}
    private void chooseBluetooth(){
        if(!ensureBluetooth())return;switchBluetooth();List<BluetoothDevice> devices=bluetooth.paired();
        String[] names=new String[devices.size()];for(int i=0;i<names.length;i++)names[i]=(devices.get(i).getName()==null?"Unnamed device":devices.get(i).getName())+"\n"+devices.get(i).getAddress();
        AlertDialog.Builder b=new AlertDialog.Builder(this).setTitle("Choose the computer / receiving device");
        if(names.length==0)b.setMessage("Pair this phone with your host in Bluetooth settings, then return here.");
        else b.setItems(names,(d,i)->bluetooth.connect(devices.get(i)));
        b.setNegativeButton("Cancel",null).show();
    }
    private LinearLayout column(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);int p=(int)(20*getResources().getDisplayMetrics().density);l.setPadding(p,8,p,8);return l;}
    private EditText field(LinearLayout box,String hint,String value){EditText e=new EditText(this);e.setSingleLine(true);e.setHint(hint);e.setText(value);box.addView(e);return e;}
    private void wifiDialog(){
        SharedPreferences pref=getSharedPreferences("connection",0);LinearLayout box=column();
        EditText host=field(box,"Computer IP address",pref.getString("host",""));
        EditText port=field(box,"Port",pref.getString("port","39570"));port.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        EditText token=field(box,"32-character pairing key from companion","");token.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        TextView info=new TextView(this);info.setText("Run the desktop companion first. Use its address and pairing key. The key is kept in memory only.");box.addView(info);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Wi-Fi companion").setView(box).setPositiveButton("Connect",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(d->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{
            try{
                String h=host.getText().toString().trim();if(h.isEmpty())throw new IllegalArgumentException("Enter your computer's address");
                int p=Integer.parseInt(port.getText().toString());String key=token.getText().toString();WifiPad.hex(key);
                if(p<1||p>65535)throw new IllegalArgumentException("Invalid port");
                if(permitted())bluetooth.disconnect();wifiMode=true;wifi.connect(h,p,key);showStatus();
                pref.edit().putString("host",h).putString("port",Integer.toString(p)).apply();dialog.dismiss();
            }catch(IllegalArgumentException e){token.setError(e.getMessage());}
        }));dialog.show();
    }
    private void settingsDialog(){new ControllerSettings(this,pad,this::connectionDialog).show();}
}
