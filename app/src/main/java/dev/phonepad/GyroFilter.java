package dev.phonepad;

/** Device-coordinate angular rates, never integrated orientation. */
public final class GyroFilter {
    private final float[] bias=new float[3],filtered=new float[3],sum=new float[3];
    private long previous,started;
    private int samples;
    private boolean calibrating;
    public void calibrate(){calibrating=true;reset();}
    public boolean calibrating(){return calibrating;}
    public void cancelCalibration(){calibrating=false;reset();}
    public void reset(){java.util.Arrays.fill(filtered,0);previous=0;restartCalibration();}
    private void restartCalibration(){started=0;samples=0;java.util.Arrays.fill(sum,0);}
    public float[] bias(){return bias.clone();}
    public float[] filtered(){return filtered.clone();}
    public float[] sample(float rawX,float rawY,long nanos,float smoothing,float deadzone){
        float[] out=sample(rawX,rawY,0,nanos,smoothing,deadzone);return new float[]{out[0],out[1]};
    }
    public float[] sample(float rawX,float rawY,float rawZ,long nanos,float smoothing,float deadzone){
        if(!Float.isFinite(rawX)||!Float.isFinite(rawY)||!Float.isFinite(rawZ)||nanos<=previous)return filtered();
        if(previous!=0&&nanos-previous>=100_000_000L){java.util.Arrays.fill(filtered,0);restartCalibration();previous=0;}
        float[] raw={rawX,rawY,rawZ};
        if(calibrating){
            if(Math.sqrt(rawX*rawX+rawY*rawY+rawZ*rawZ)>.12)restartCalibration();
            else{
                if(started==0)started=nanos;samples++;
                for(int i=0;i<3;i++)sum[i]+=raw[i];
                if(nanos-started>=1_000_000_000L&&samples>=30){for(int i=0;i<3;i++)bias[i]=sum[i]/samples;calibrating=false;}
            }
            previous=nanos;return new float[3];
        }
        float dt=previous==0?16:(nanos-previous)/1_000_000f;previous=nanos;
        float keep=(float)Math.pow(PadState.clamp(smoothing,0,.95f),dt/16);
        for(int i=0;i<3;i++){
            float rate=raw[i]-bias[i];if(Math.abs(rate)<=deadzone)rate=0;
            filtered[i]=filtered[i]*keep+rate*(1-keep);
            if(Math.abs(filtered[i])<=.000001f)filtered[i]=0;
        }
        return filtered();
    }
    /** Surface rotation values 0..3. Camera Y follows the controller's down-positive convention. */
    public static float[] camera(float x,float y,int rotation){
        switch(rotation){case 1:return new float[]{-x,y};case 2:return new float[]{y,x};case 3:return new float[]{x,-y};default:return new float[]{-y,-x};}
    }
    public static float[] camera(float[] rates,int rotation,ControllerConfig c){
        float[] out=camera(rates[0],rates[1],rotation);
        out[0]*=c.gyroGainX()*(c.gyroInvertX?-1:1);out[1]*=c.gyroGainY()*(c.gyroInvertY?-1:1);return out;
    }
}
