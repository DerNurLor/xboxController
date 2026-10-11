package dev.phonepad;

import android.content.Context;
import android.hardware.*;
import android.os.SystemClock;
import android.view.Surface;

/** Sensor timestamps and bias stay in device coordinates across display rotation. */
public final class GyroInput implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor sensor;
    private final PadView pad;
    private final GyroFilter filter=new GyroFilter();
    private boolean registered;
    private long calibrationUntil;
    private float[] raw=new float[3],mapped=new float[2];
    private long timestamp,samples,dropped,lastLog;
    private float hz;
    private int rotation;
    public GyroInput(Context context,PadView pad){this.pad=pad;manager=context.getSystemService(SensorManager.class);sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);}
    public boolean available(){return sensor!=null;}
    public void sync(boolean active){
        if(!active&&calibrationUntil!=0){calibrationUntil=0;filter.cancelCalibration();}
        if(calibrationUntil!=0&&SystemClock.uptimeMillis()>calibrationUntil){calibrationUntil=0;filter.cancelCalibration();android.widget.Toast.makeText(pad.getContext(),"Calibration timed out. Keep the phone still and try again.",android.widget.Toast.LENGTH_SHORT).show();}
        boolean wanted=active&&!pad.editing&&(calibrationUntil!=0||pad.config.gyroEnabled&&!pad.settingsOpen&&pad.hasWindowFocus());
        if(wanted==registered)return;
        if(wanted&&sensor!=null){
            registered=manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME,0);

        }
        else{if(manager!=null)manager.unregisterListener(this);registered=false;filter.reset();hz=0;mapped=new float[2];pad.input.gyro(0,0,0);}
    }
    public void calibrate(){if(!available())return;filter.calibrate();calibrationUntil=SystemClock.uptimeMillis()+15000;pad.input.gyro(0,0,0);sync(true);android.widget.Toast.makeText(pad.getContext(),"Calibrating gyro: keep the phone still for one second",android.widget.Toast.LENGTH_LONG).show();}
    public boolean calibrating(){return filter.calibrating();}
    public String status(){return "Gyro available="+available()+" sampling="+registered+" calibrating="+calibrating()+
        "\nRaw XYZ rad/s "+java.util.Arrays.toString(raw)+"\nBias XYZ "+java.util.Arrays.toString(filter.bias())+
        "\nFiltered XYZ "+java.util.Arrays.toString(filter.filtered())+"\nCamera XY "+java.util.Arrays.toString(mapped)+
        " rotation="+(rotation*90)+" timestampNs="+timestamp+" Hz="+Math.round(hz)+" samples="+samples+" dropped="+dropped;}
    @Override public void onSensorChanged(SensorEvent event){
        if(!registered)return;
        long age=SystemClock.elapsedRealtimeNanos()-event.timestamp;
        if(age<0||age>=100_000_000L||event.timestamp<=timestamp||!Float.isFinite(event.values[0])||!Float.isFinite(event.values[1])||!Float.isFinite(event.values[2])){dropped++;return;}
        if(timestamp>0)hz=1_000_000_000f/(event.timestamp-timestamp);
        timestamp=event.timestamp;samples++;raw=event.values.clone();
        rotation=pad.getDisplay()==null?Surface.ROTATION_0:pad.getDisplay().getRotation();
        ControllerConfig c=pad.config;
        boolean wasCalibrating=filter.calibrating();
        mapped=GyroFilter.camera(filter.sample(raw[0],raw[1],raw[2],timestamp,c.gyroSmoothing,c.gyroDeadzone),rotation,c);
        if(wasCalibrating&&!filter.calibrating()){calibrationUntil=0;
            android.widget.Toast.makeText(pad.getContext(),"Gyro calibration complete",android.widget.Toast.LENGTH_SHORT).show();
            android.util.Log.i("PhonePadGyro","Calibration complete: bias="+java.util.Arrays.toString(filter.bias()));
        }
        // Delivery delays must not renew an old sample's 100ms gameplay lease.
        if(c.gyroEnabled&&!pad.settingsOpen&&!pad.editing&&pad.hasWindowFocus())pad.input.gyro(mapped[0],mapped[1],SystemClock.uptimeMillis()-age/1_000_000L);else pad.input.gyro(0,0,0);
        long now=SystemClock.uptimeMillis();if(now-lastLog>=1000){android.util.Log.d("PhonePadGyro",status());lastLog=now;}
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
