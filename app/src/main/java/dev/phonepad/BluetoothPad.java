package dev.phonepad;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** All calls and callbacks are serialized on the activity's main thread. */
public final class BluetoothPad implements AutoCloseable {
    public interface Listener { void status(String text); void rumble(int low,int high,int millis); default void disconnected() {} default void stopped(){rumble(0,0,0);} }
    private final Context context;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothHidDevice hid;
    private BluetoothDevice host, desired;
    private boolean requested, registered, registering, closed;
    private byte[] latest=new PadState().report(),latestKeyboard=new byte[29],latestMouse=new byte[7];
    public long reports, failures,desktopReports,desktopFailures;
    private long retryAt;
    private boolean lastSpaceState;
    private long lastFailureLog;
    public BluetoothPad(Context c,Listener l) {
        context=c; listener=l;
        BluetoothManager manager=c.getSystemService(BluetoothManager.class);
        adapter=manager==null?null:manager.getAdapter();
    }
    public boolean enabled() { return adapter!=null && adapter.isEnabled(); }
    public boolean connected() { return host!=null && registered; }
    public List<BluetoothDevice> paired() {
        if(!enabled()) return Collections.emptyList();
        List<BluetoothDevice> d=new ArrayList<>(adapter.getBondedDevices());
        d.sort(Comparator.comparing(x->x.getName()==null?x.getAddress():x.getName())); return d;
    }
    public void start() {
        if(closed) return;
        if(!enabled()) { listener.status("Turn on Bluetooth to connect"); return; }
        restoreLastComputer();
        if(hid!=null) { register(); return; }
        if(requested) return;
        requested=adapter.getProfileProxy(context,service,BluetoothProfile.HID_DEVICE);
        listener.status(requested?"Opening Bluetooth gamepad service…":"Bluetooth HID unavailable on this phone");
    }
    private final BluetoothProfile.ServiceListener service=new BluetoothProfile.ServiceListener() {
        public void onServiceConnected(int profile,BluetoothProfile proxy) {
            if(closed) { adapter.closeProfileProxy(profile,proxy); return; }
            hid=(BluetoothHidDevice)proxy; register();
        }
        public void onServiceDisconnected(int profile) {
            hid=null; host=null; registered=false; registering=false; requested=false;
            latest=new PadState().report();listener.disconnected();listener.status("Bluetooth service disconnected");
        }
    };
    private void register() {
        if(registered || registering || hid==null || closed) return;
        registering=hid.registerApp(new BluetoothHidDeviceAppSdpSettings("PhonePad",
            "PhonePad Bluetooth Gamepad Keyboard and Mouse","PhonePad",
            (byte)(BluetoothHidDevice.SUBCLASS1_COMBO|BluetoothHidDevice.SUBCLASS2_GAMEPAD),HidDescriptor.BYTES),null,null,
            context.getMainExecutor(),callback);
        if(!registering) listener.status("HID registration rejected; close other controller apps");
    }
    public void connect(BluetoothDevice device) {
        if(host!=null && !host.equals(device)) { neutral(); hid.disconnect(host); }
        desired=device; retryAt=0;
        context.getSharedPreferences("connection",0).edit().putString("lastComputer",device.getAddress()).putString("lastTransport","bluetooth").apply();
        start(); reconnect();
    }
    private void restoreLastComputer() {
        if(desired!=null || adapter==null) return;
        android.content.SharedPreferences prefs=context.getSharedPreferences("connection",0);
        String transport=prefs.getString("lastTransport","");
        if("wifi".equals(transport)) return;
        String address=prefs.getString("lastComputer","");
        if(address.isEmpty()) return;
        try { desired=adapter.getRemoteDevice(address); }
        catch(IllegalArgumentException ignored) { listener.status("Previously connected Bluetooth computer is unavailable"); }
    }
    public void reconnect() {
        if(registered && host==null && desired!=null && android.os.SystemClock.elapsedRealtime()>=retryAt) {
            retryAt=android.os.SystemClock.elapsedRealtime()+6000;
            listener.status("Connecting to "+name(desired)+"…");
            if(!hid.connect(desired)) listener.status("Connection refused; check host Bluetooth settings");
        }
    }
    public void disconnect() {
        desired=null; neutral(); if(hid!=null && host!=null) hid.disconnect(host); host=null;
        listener.disconnected();listener.status("Disconnected");
    }
    private byte[] latestSource=new PadState().report();
    public void sendSource(byte[] report){latestSource=report.clone();if(connected())hid.sendReport(host,5,latestSource);}
    public void send(byte[] report) {
        latest=report.clone();
        if(connected()) { if(hid.sendReport(host,1,latest)) reports++; else failures++; }
    }
    public void sendDesktop(byte[] state) {
        latestKeyboard=HidDesktopReports.keyboard(state);latestMouse=HidDesktopReports.mouse(state);
        if(connected()) {
            boolean keyboardSent=hid.sendReport(host,HidDesktopReports.KEYBOARD_REPORT_ID,latestKeyboard);
            boolean mouseSent=hid.sendReport(host,HidDesktopReports.MOUSE_REPORT_ID,latestMouse);
            if(keyboardSent&&mouseSent)desktopReports++;else desktopFailures++;
            int space=Automation.KEYS.get("KEY_SPACE");
            boolean spaceDown=(latestKeyboard[space/8]&(1<<(space%8)))!=0;
            if(spaceDown!=lastSpaceState){
                android.util.Log.i("PhonePadHID","Space "+(spaceDown?"pressed":"released")+"; keyboard report accepted="+keyboardSent);
                lastSpaceState=spaceDown;
            }
            long now=android.os.SystemClock.elapsedRealtime();
            if((!keyboardSent||!mouseSent)&&now-lastFailureLog>=1000){
                lastFailureLog=now;
                android.util.Log.w("PhonePadHID","Desktop HID report rejected; keyboard="+keyboardSent+", mouse="+mouseSent+", totals="+desktopReports+"/"+desktopFailures);
            }
        }
    }
    public void neutral() { sendSource(new PadState().report());send(new PadState().report());sendDesktop(new byte[Automation.DESKTOP_SIZE]);listener.stopped(); }
    public void suspend() {
        neutral();
        if(hid!=null && (registered || registering)) hid.unregisterApp();
        registered=false;registering=false;host=null;
    }
    private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice plugged,boolean ok) {
            registered=ok; registering=false;
            if(!ok) { host=null;latest=new PadState().report();listener.disconnected(); listener.stopped(); }
            listener.status(ok?"Bluetooth ready · choose a paired host":"Bluetooth paused · return to controller to resume");
            if(ok) reconnect();
        }
        @Override public void onConnectionStateChanged(BluetoothDevice d,int state) {
            if(state==BluetoothProfile.STATE_CONNECTED) {
                if(desired!=null && !desired.equals(d)) { hid.disconnect(d); return; }
                host=d; desired=d; context.getSharedPreferences("connection",0).edit().putString("lastComputer",d.getAddress()).apply(); neutral(); listener.status("Bluetooth · "+name(d));
            } else if(state==BluetoothProfile.STATE_DISCONNECTED && (host==null || host.equals(d))) {
                host=null;latest=new PadState().report();latestKeyboard=new byte[29];latestMouse=new byte[7];listener.disconnected(); listener.stopped(); listener.status("Host disconnected · reconnecting while app is open");
            }
        }
        @Override public void onGetReport(BluetoothDevice d,byte type,byte id,int size) {
            byte[] report=id==5?latestSource:id==1?latest:id==HidDesktopReports.KEYBOARD_REPORT_ID?latestKeyboard:id==HidDesktopReports.MOUSE_REPORT_ID?latestMouse:null;
            if(type==BluetoothHidDevice.REPORT_TYPE_INPUT && report!=null && (size==0 || size>=report.length))
                hid.replyReport(d,type,id,report);
            else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM);
        }
        @Override public void onSetReport(BluetoothDevice d,byte type,byte id,byte[] data) {
            if(type==BluetoothHidDevice.REPORT_TYPE_OUTPUT && id==2 && host!=null && host.equals(d) && data!=null && data.length==3) {
                output(data); hid.reportError(d,BluetoothHidDevice.ERROR_RSP_SUCCESS);
            } else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM);
        }
        @Override public void onInterruptData(BluetoothDevice d,byte id,byte[] data) {
            if(id==2 && host!=null && host.equals(d) && data!=null && data.length==3) output(data);
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice d) { desired=null; host=null; neutral();listener.disconnected(); }
    };
    private void output(byte[] data) { listener.rumble(data[0]&255,data[1]&255,(data[2]&255)*10); }
    private String name(BluetoothDevice d) { return d.getName()==null?"Unnamed computer":d.getName(); }
    @Override public void close() {
        closed=true; desired=null; neutral();
        if(hid!=null) { hid.unregisterApp(); adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid); }
        hid=null; host=null; registered=false; main.removeCallbacksAndMessages(null);
    }
}
