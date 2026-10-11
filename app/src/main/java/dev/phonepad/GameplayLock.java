package dev.phonepad;

/** Deliberate unlock ownership is separate from gameplay pointer ownership. */
public final class GameplayLock {
    public boolean locked;
    private int owner=-1;
    private float x,y;
    private long start;
    public void reset(boolean enabled){locked=enabled;cancel();}
    public void cancel(){owner=-1;}
    public void down(int id,float x,float y,long now){if(!locked)return;owner=id;this.x=x;this.y=y;start=now;}
    public void move(int id,float x,float y,float slop){if(id==owner&&Math.hypot(x-this.x,y-this.y)>slop)cancel();}
    public void up(int id){if(id==owner)cancel();}
    public boolean holding(){return owner!=-1;}
    public float progress(long now){return owner<0?0:Math.max(0,Math.min(1,(now-start)/1400f));}
    public boolean update(long now){if(progress(now)<1)return false;locked=false;cancel();return true;}
}
