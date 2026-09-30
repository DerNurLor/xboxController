package dev.phonepad;

/** Transport-neutral state. Y increases downward. HID button usages are 1-based. */
public final class PadState {
    public static final int A=0, B=1, X=3, Y=4, LB=6, RB=7, VIEW=10, MENU=11,
            GUIDE=12, L3=13, R3=14, SHARE=15;
    public int buttons, hat=8;
    public float lx, ly, rx, ry, lt, rt;
    public void clear() { buttons=0; hat=8; lx=ly=rx=ry=lt=rt=0; }
    public byte[] report() {
        byte[] r=new byte[13];
        r[0]=(byte)buttons; r[1]=(byte)(buttons>>8); r[2]=(byte)hat;
        axis(r,3,lx); axis(r,5,ly); axis(r,7,rx); axis(r,9,ry);
        r[11]=(byte)Math.round(clamp(lt,0,1)*255); r[12]=(byte)Math.round(clamp(rt,0,1)*255);
        return r;
    }
    private static void axis(byte[] b,int i,float v) {
        int n=Math.round(clamp(v,-1,1)*32767); b[i]=(byte)n; b[i+1]=(byte)(n>>8);
    }
    public static float clamp(float v,float lo,float hi) { return Float.isFinite(v)?Math.max(lo,Math.min(hi,v)):0; }
    public static float[] stick(float x,float y,float dead,float sensitivity) {
        double length=Math.hypot(x,y);
        if(length<=dead) return new float[]{0,0};
        float amount=clamp((float)((Math.min(1,length)-dead)/(1-dead))*sensitivity,0,1);
        return new float[]{(float)(x/length*amount),(float)(y/length*amount)};
    }
}
