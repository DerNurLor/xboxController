package dev.phonepad;

import android.bluetooth.*;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.util.*;

/** All calls and callbacks are serialized on the activity's main thread. */
public final class BluetoothPad implements AutoCloseable {
    public interface Listener { void status(String text); void rumble(int low,int high,int millis); default void disconnected() {} }
    private final Context context;
    private final Listener listener;
    private final BluetoothAdapter adapter;
    private final Handler main=new Handler(Looper.getMainLooper());
    private BluetoothHidDevice hid;
    private BluetoothDevice host, desired;
    private boolean requested, registered, registering, closed;
    private byte[] latest=new PadState().report();
    public long reports, failures;
    private long retryAt;
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
            "PhonePad Bluetooth Gamepad","PhonePad",(byte)0x08,HidDescriptor.BYTES),null,null,
            context.getMainExecutor(),callback);
        if(!registering) listener.status("HID registration rejected; close other controller apps");
    }
    public void connect(BluetoothDevice device) {
        if(host!=null && !host.equals(device)) { neutral(); hid.disconnect(host); }
        desired=device; retryAt=0; start(); reconnect();
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
    public void send(byte[] report) {
        latest=report.clone();
        if(connected()) { if(hid.sendReport(host,1,latest)) reports++; else failures++; }
    }
    public void neutral() { send(new PadState().report()); listener.rumble(0,0,0); }
    public void suspend() {
        neutral();
        if(hid!=null && (registered || registering)) hid.unregisterApp();
        registered=false;registering=false;host=null;
    }
    private final BluetoothHidDevice.Callback callback=new BluetoothHidDevice.Callback() {
        @Override public void onAppStatusChanged(BluetoothDevice plugged,boolean ok) {
            registered=ok; registering=false;
            if(!ok) { host=null;latest=new PadState().report();listener.disconnected(); listener.rumble(0,0,0); }
            listener.status(ok?"Bluetooth ready · choose a paired host":"Bluetooth paused · return to controller to resume");
            if(ok) reconnect();
        }
        @Override public void onConnectionStateChanged(BluetoothDevice d,int state) {
            if(state==BluetoothProfile.STATE_CONNECTED) {
                if(desired!=null && !desired.equals(d)) { hid.disconnect(d); return; }
                host=d; desired=d; neutral(); listener.status("Bluetooth · "+name(d));
            } else if(state==BluetoothProfile.STATE_DISCONNECTED && (host==null || host.equals(d))) {
                host=null;latest=new PadState().report();listener.disconnected(); listener.rumble(0,0,0); listener.status("Host disconnected · reconnecting while app is open");
            }
        }
        @Override public void onGetReport(BluetoothDevice d,byte type,byte id,int size) {
            if(type==BluetoothHidDevice.REPORT_TYPE_INPUT && id==1 && (size==0 || size>=latest.length))
                hid.replyReport(d,type,id,latest);
            else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM);
        }
        @Override public void onSetReport(BluetoothDevice d,byte type,byte id,byte[] data) {
            if(type==BluetoothHidDevice.REPORT_TYPE_OUTPUT && id==2 && data.length==3) {
                output(data); hid.reportError(d,BluetoothHidDevice.ERROR_RSP_SUCCESS);
            } else hid.reportError(d,BluetoothHidDevice.ERROR_RSP_INVALID_PARAM);
        }
        @Override public void onInterruptData(BluetoothDevice d,byte id,byte[] data) {
            if(id==2 && data.length==3) output(data);
        }
        @Override public void onVirtualCableUnplug(BluetoothDevice d) { desired=null; host=null; neutral();listener.disconnected(); }
    };
    private void output(byte[] data) { listener.rumble(data[0]&255,data[1]&255,(data[2]&255)*10); }
    private String name(BluetoothDevice d) { return d.getName()==null?d.getAddress():d.getName(); }
    @Override public void close() {
        closed=true; desired=null; neutral();
        if(hid!=null) { hid.unregisterApp(); adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE,hid); }
        hid=null; host=null; registered=false; main.removeCallbacksAndMessages(null);
    }
}
