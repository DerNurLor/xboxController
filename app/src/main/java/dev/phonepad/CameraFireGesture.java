package dev.phonepad;

/** A short stationary tap arms exactly one nearby second contact. No drag arms fire. */
public final class CameraFireGesture {
    public enum State { IDLE, CAMERA, ARMED, FIRING, CAMERA_ONLY }
    private State state=State.IDLE;
    private int owner=-1;
    private long down,armedAt;
    private float x,y,distance;
    private String control="";
    public State state(){return state;}
    public boolean firing(int id){return owner==id&&state==State.FIRING;}
    public void down(int id,String key,float px,float py,long now,float gap,float slop){
        boolean fire=state==State.ARMED&&key.equals(control)&&now>=armedAt&&now-armedAt<=gap&&Math.hypot(px-x,py-y)<=slop;
        owner=id;control=key;x=px;y=py;distance=0;down=now;state=fire?State.FIRING:State.CAMERA;
    }
    public void move(int id,float px,float py){if(id==owner)distance=Math.max(distance,(float)Math.hypot(px-x,py-y));}
    public void up(int id,long now,float tap,float slop){
        if(id!=owner)return;
        if(state==State.CAMERA&&now>=down&&now-down<=tap&&distance<=slop){state=State.ARMED;armedAt=now;}
        else state=State.IDLE;
        owner=-1;
    }
    public void expire(long now,float gap){if(state==State.ARMED&&(now<armedAt||now-armedAt>gap))state=State.IDLE;}
    public void stopFire(){if(state==State.FIRING)state=State.CAMERA_ONLY;}
    public void reset(){state=State.IDLE;owner=-1;control="";}
}
