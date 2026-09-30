package dev.phonepad;

import android.content.Context;
import android.hardware.*;
import android.os.SystemClock;
import android.view.Surface;

/** Android sensor adapter. Display rotation is resolved before pure filtering. */
public final class GyroInput implements SensorEventListener {
    private final SensorManager manager;
    private final Sensor sensor;
    private final PadView pad;
    private final GyroFilter filter=new GyroFilter();
    private boolean registered;
    public GyroInput(Context context,PadView pad){this.pad=pad;manager=context.getSystemService(SensorManager.class);sensor=manager==null?null:manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);}
    public boolean available(){return sensor!=null;}
    public void sync(boolean active){boolean wanted=active&&pad.config.gyroEnabled&&!pad.editing&&pad.hasWindowFocus();if(wanted==registered)return;if(wanted&&sensor!=null)registered=manager.registerListener(this,sensor,SensorManager.SENSOR_DELAY_GAME);else{if(manager!=null)manager.unregisterListener(this);registered=false;filter.reset();pad.input.gyro(0,0,0);}}
    public void calibrate(){filter.calibrate();pad.input.gyro(0,0,0);}
    public boolean calibrating(){return filter.calibrating();}
    @Override public void onSensorChanged(SensorEvent event){
        if(!registered)return;float x=event.values[0],y=event.values[1];int rotation=pad.getDisplay()==null?Surface.ROTATION_90:pad.getDisplay().getRotation();
        float screenX=x,screenY=y;if(rotation==Surface.ROTATION_90){screenX=-y;screenY=x;}else if(rotation==Surface.ROTATION_270){screenX=y;screenY=-x;}else if(rotation==Surface.ROTATION_180){screenX=-x;screenY=-y;}
        ControllerConfig c=pad.config;float[] rates=filter.sample(-screenY,-screenX,event.timestamp,c.gyroSmoothing,c.gyroDeadzone);
        pad.input.gyro(rates[0]*c.gyroSensitivity*(c.gyroInvertX?-1:1),rates[1]*c.gyroSensitivity*(c.gyroInvertY?-1:1),SystemClock.uptimeMillis());
    }
    @Override public void onAccuracyChanged(Sensor sensor,int accuracy){}
}
