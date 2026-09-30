package dev.phonepad;

/** Angular-rate input avoids integration drift. Calibration only accepts still samples. */
public final class GyroFilter {
    private float biasX,biasY,x,y,sumX,sumY;
    private long previous,started;
    private int samples;
    private boolean calibrating;
    public void calibrate(){calibrating=true;started=0;samples=0;sumX=sumY=0;reset();}
    public boolean calibrating(){return calibrating;}
    public void reset(){x=y=0;previous=0;}
    public float[] sample(float rawX,float rawY,long nanos,float smoothing,float deadzone){
        if(!Float.isFinite(rawX)||!Float.isFinite(rawY)||nanos<=previous)return new float[]{x,y};
        if(calibrating){
            if(Math.hypot(rawX,rawY)>.12){started=0;samples=0;sumX=sumY=0;}
            else{if(started==0)started=nanos;samples++;sumX+=rawX;sumY+=rawY;if(nanos-started>=1_000_000_000L&&samples>=30){biasX=sumX/samples;biasY=sumY/samples;calibrating=false;}}
            previous=nanos;return new float[]{0,0};
        }
        float dt=previous==0?16:Math.min(100,(nanos-previous)/1_000_000f);previous=nanos;
        float keep=(float)Math.pow(PadState.clamp(smoothing,0,.95f),dt/16);
        x=x*keep+(rawX-biasX)*(1-keep);y=y*keep+(rawY-biasY)*(1-keep);
        return new float[]{Math.abs(x)<deadzone?0:x,Math.abs(y)<deadzone?0:y};
    }
}
