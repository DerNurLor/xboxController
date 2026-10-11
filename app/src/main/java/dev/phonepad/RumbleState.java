package dev.phonepad;

/** Report 2 / PP01 ACK: unsigned heavy, light, duration (10ms). */
public final class RumbleState {
    public final float heavy,light;
    public final long received,expires;
    public final String source;
    public RumbleState(int heavy,int light,int millis,long now,String source){
        this.heavy=Math.max(0,Math.min(255,heavy))/255f;
        this.light=Math.max(0,Math.min(255,light))/255f;
        received=now;expires=now+Math.max(0,Math.min(2500,millis));this.source=source;
    }
    public static RumbleState parse(byte[] bytes,long now,String source){
        if(bytes==null||bytes.length!=3)return null;
        return new RumbleState(bytes[0]&255,bytes[1]&255,(bytes[2]&255)*10,now,source);
    }
    public boolean active(long now){return now>=received&&now<expires&&(heavy>0||light>0);}
    /** Weighted RMS, normalized for two full channels; local source is aggregated afterward. */
    public float mix(long now,float heavyWeight,float lightWeight,float strength,boolean quiet){
        if(!active(now))return 0;
        double h=heavy*Math.max(0,Math.min(1,heavyWeight)),l=light*Math.max(0,Math.min(1,lightWeight));
        return (float)Math.min(1,Math.sqrt((h*h+l*l)/2)*Math.max(0,Math.min(1,strength))*(quiet?.4:1));
    }
}
