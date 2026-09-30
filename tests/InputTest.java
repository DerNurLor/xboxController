package dev.phonepad;

import java.util.*;

public final class InputTest {
    static int checks;static long time=1000;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    static void near(float a,float b,String m){check(Math.abs(a-b)<.002,m+" expected "+b+" got "+a);}
    static ControllerInput engine(String preset){ControllerInput e=new ControllerInput(ControllerConfig.preset(preset));e.resize(1100,480);return e;}
    static void down(ControllerInput e,int id,String name){ControllerConfig.Control c=e.config.get(name);check(e.down(id,c.x*e.width,c.y*e.height,time+=20),"capture "+name);}
    static void moveStick(ControllerInput e,int id,String name,float x,float y){ControllerConfig.Control c=e.config.get(name);ControllerInput.Pointer p=e.owner(c);e.move(id,p.cx+x*e.radius(c),p.cy+y*e.radius(c),time+=20);e.update(time);}
    static void neutral(ControllerInput e){check(Arrays.equals(e.state.report(),new PadState().report()),"neutral HID report");}
    static boolean pressed(ControllerInput e,int bit){return (e.state.buttons&(1<<bit))!=0;}
    public static void main(String[] args){
        ControllerInput e=engine("Default");
        for(String name:new String[]{"A","B","X","Y","LB","RB","VIEW","MENU","HOME"}){down(e,10,name);check(pressed(e,e.config.get(name).bit),name+" press");e.move(10,-100,-100,time+=20);e.update(time);check(pressed(e,e.config.get(name).bit),name+" keeps capture outside");e.up(10,time+=20);neutral(e);}
        ControllerConfig.Control d=e.config.get("D-PAD");for(int hat=0;hat<8;hat++){down(e,1,"D-PAD");double a=hat*Math.PI/4-Math.PI/2;e.move(1,d.x*e.width+(float)Math.cos(a)*40,d.y*e.height+(float)Math.sin(a)*40,time+=20);e.update(time);check(e.state.hat==hat,"Dpad "+hat);e.up(1,time+=20);}
        for(String stick:new String[]{"LS","RS"}){down(e,1,stick);moveStick(e,1,stick,1,0);near(stick.equals("LS")?e.state.lx:e.state.rx,1,stick+" positive X");moveStick(e,1,stick,-1,0);near(stick.equals("LS")?e.state.lx:e.state.rx,-1,stick+" negative X");moveStick(e,1,stick,0,-1);near(stick.equals("LS")?e.state.ly:e.state.ry,-1,stick+" negative Y");moveStick(e,1,stick,0,1);near(stick.equals("LS")?e.state.ly:e.state.ry,1,stick+" positive Y");e.up(1,time+=20);neutral(e);}
        for(String mode:new String[]{"EDGE","VERTICAL","DIGITAL"})for(boolean reverse:new boolean[]{false,true}){
            e.config.triggerMode=mode;e.config.get("LT").reverse=e.config.get("RT").reverse=reverse;
            down(e,1,"LT");down(e,2,"RT");
            for(int step=0;step<=10;step++){float value=step/10f;for(int id=1;id<=2;id++){ControllerConfig.Control c=e.config.get(id==1?"LT":"RT");float sign=reverse?-1:1;float dx=mode.equals("EDGE")?sign*(id==1?1:-1)*e.config.travel*e.width*value:0;float dy=mode.equals("VERTICAL")?sign*e.config.travel*e.height*value:0;e.move(id,c.x*e.width+dx,c.y*e.height+dy,time+=20);}e.update(time);near(e.state.lt,mode.equals("DIGITAL")?1:value,"LT range "+mode);near(e.state.rt,mode.equals("DIGITAL")?1:value,"RT range "+mode);check(Math.abs((e.state.report()[12]&255)-Math.round(e.state.rt*255))==0,"HID RT byte");}
            e.up(1,time+=20);near(e.state.lt,0,"LT release only");near(e.state.rt,1,"RT unaffected");e.up(2,time+=20);neutral(e);
        }
        e=engine("Default");e.config.triggerMode="DIGITAL";
        down(e,0,"LS");moveStick(e,0,"LS",0,-1);down(e,4,"RS");moveStick(e,4,"RS",1,0);down(e,9,"RT");down(e,12,"A");down(e,15,"RB");
        near(e.state.ly,-1,"five pointers LS");near(e.state.rx,1,"five pointers RS");near(e.state.rt,1,"five pointers RT");check(pressed(e,PadState.A)&&pressed(e,PadState.RB),"five pointers buttons");
        e.up(9,time+=20);near(e.state.rt,0,"RT lifted");near(e.state.ly,-1,"LS stays held");check(pressed(e,PadState.A),"A stays held");e.cancel(12,time+=20);check(!pressed(e,PadState.A)&&pressed(e,PadState.RB),"cancel only pointer");e.release();neutral(e);
        for(String s:new String[]{"LS","RS"}){e=engine("Default");down(e,1,s);e.up(1,time+=20);down(e,2,s);int bit=s.equals("LS")?PadState.L3:PadState.R3;check(pressed(e,bit),"double tap click");e.up(2,time+=20);neutral(e);e.config.clickGesture="LONG_PRESS";down(e,1,s);e.update(time+=500);check(pressed(e,bit),"long press click");e.release();neutral(e);}
        e=engine("FPS");down(e,1,"RS");ControllerConfig.Control rs=e.config.get("RS");e.move(1,rs.x*1100+20,rs.y*480,time+=20);e.update(time);check(e.state.rx>0,"camera delta");e.move(1,rs.x*1100+10,rs.y*480,time+=20);e.update(time);check(e.state.rx<0,"camera reverses without crossing start");e.update(time+=100);near(e.state.rx,0,"camera idle neutral");e.up(1,time+=10);neutral(e);
        for(boolean preserve:new boolean[]{false,true}){
            e=engine("Racing");e.config.preserveY=preserve;down(e,1,"LS");near(e.state.rt,0,"center");
            for(float[] xy:new float[][]{{1,0},{-1,0},{0,-1},{0,1},{0,-.5f},{.7f,-.7f},{-.7f,.7f},{1,-.04f}}){moveStick(e,1,"LS",xy[0],xy[1]);float expected=ControllerInput.integratedTrigger(xy[1],e.config.triggerDeadzone,1,"LINEAR");near(e.state.rt,xy[1]<0?expected:0,"racing RT");near(e.state.lt,xy[1]>0?expected:0,"racing LT");if(!preserve)near(e.state.ly,0,"racing Y suppressed");else if(xy[1]!=0)check(Math.signum(e.state.ly)==Math.signum(xy[1]),"Y preserved");if(xy[0]!=0)check(Math.signum(e.state.lx)==Math.signum(xy[0]),"diagonal steering");check((e.state.report()[12]&255)==Math.round(e.state.rt*255),"racing HID RT");check((e.state.report()[11]&255)==Math.round(e.state.lt*255),"racing HID LT");}
            moveStick(e,1,"LS",.7f,-.7f);down(e,2,"RS");moveStick(e,2,"RS",1,0);down(e,3,"A");down(e,4,"RB");check(e.state.lx>0&&e.state.rt>0&&e.state.rx>0&&pressed(e,PadState.A)&&pressed(e,PadState.RB),"integrated four pointer combination");e.up(1,time+=20);near(e.state.rt,0,"racing release gas");near(e.state.lt,0,"racing release brake");near(e.state.lx,0,"racing release steering");check(pressed(e,PadState.A),"racing other fingers survive");e.release();neutral(e);
            ControllerConfig.Control rt=e.config.get("RT");check(!e.hit(rt,rt.x*1100,rt.y*480),"hidden trigger no hitbox");e.configure(ControllerConfig.preset("Default"));neutral(e);check(e.config.shown(e.config.get("RT")),"Default restores independent triggers");
        }
        for(float width:new float[]{960,1100,2400}){e=engine("Racing");e.resize(width,1080);down(e,1,"LS");moveStick(e,1,"LS",0,-1);near(e.state.rt,1,"aspect-independent range");e.release();}
        ControllerConfig fitted=ControllerConfig.preset("Default");float widthBefore=fitted.get("LS").w,heightBefore=fitted.get("LS").h;fitted.fitHeight();near(fitted.get("LS").w,widthBefore,"fit preserves width");near(fitted.get("LS").h,heightBefore,"fit preserves height");float bottom=0;for(ControllerConfig.Control c:fitted.controls)if(fitted.shown(c)){check(c.y-c.h/2>=.084f&&c.y+c.h/2<=.986f,"fit inside usable height");bottom=Math.max(bottom,c.y+c.h/2);}near(bottom,.985f,"fit reaches bottom");float fittedY=fitted.get("LS").y;fitted.fitHeight();near(fitted.get("LS").y,fittedY,"fit is stable when repeated");
        // Long press becomes a captured click, including later movement.
        e=engine("Default");e.config.clickGesture="LONG_PRESS";down(e,1,"LS");e.update(time+=500);moveStick(e,1,"LS",1,0);check(pressed(e,PadState.L3),"long press click survives drag");e.up(1,time+=10);neutral(e);
        // Two contacts on one mapped button must each own their press.
        down(e,1,"A");down(e,2,"A");e.up(1,time+=10);check(pressed(e,PadState.A),"second button owner survives");e.up(2,time+=10);neutral(e);
        // Combined and dedicated click sources release independently.
        e.config.get("L3+R3").visible=true;e.config.get("L3").visible=true;
        down(e,1,"L3+R3");down(e,2,"L3");check(pressed(e,PadState.L3)&&pressed(e,PadState.R3),"combined clicks");e.up(1,time+=10);check(pressed(e,PadState.L3)&&!pressed(e,PadState.R3),"combined release respects other owner");e.release();
        for(String orientation:new String[]{"VERTICAL","HORIZONTAL"})for(boolean invert:new boolean[]{false,true})for(boolean floating:new boolean[]{false,true}){
            e=engine("Default");e.config.get("LT/RT").visible=true;e.config.unifiedOrientation=orientation;e.config.unifiedInvert=invert;e.config.unifiedFloating=floating;
            down(e,1,"LT/RT");ControllerInput.Pointer p=e.owner(e.config.get("LT/RT"));float travel=e.config.unifiedTravel*(orientation.equals("VERTICAL")?e.height:e.width);
            for(float amount:new float[]{-1,-.5f,0,.5f,1}){float displacement=amount*travel;e.move(1,p.cx+(orientation.equals("HORIZONTAL")?displacement:0),p.cy+(orientation.equals("VERTICAL")?displacement:0),time+=20);e.update(time);float v=ControllerInput.integratedTrigger(amount,e.config.unifiedDeadzone,1,"LINEAR");boolean lt=(invert?-amount:amount)>0;near(e.state.lt,lt?v:0,"unified LT");near(e.state.rt,lt?0:v,"unified RT");check(e.state.lt==0||e.state.rt==0,"unified exclusivity");}
            down(e,2,"LS");moveStick(e,2,"LS",1,0);down(e,3,"RS");moveStick(e,3,"RS",0,-1);check(e.state.lx>0&&e.state.ry<0,"unified and two sticks");e.cancel(1,time+=10);near(e.state.lt,0,"unified cancellation");near(e.state.rt,0,"unified cancellation");e.release();neutral(e);
        }
        e=engine("FPS");e.config.cameraFire=true;down(e,1,"RS");down(e,2,"RS");moveStick(e,1,"RS",.3f,0);check(e.state.rx>0&&e.state.rt==1,"camera movement and secondary fire");e.up(2,time+=10);near(e.state.rt,0,"secondary fire release");check(e.owner(e.config.get("RS"))!=null,"camera still owned");down(e,3,"RS");e.up(1,time+=10);neutral(e);check(e.pointerCount()==0,"camera release invalidates dependent fire");
        e=engine("Default");e.config.clickGesture="SECOND_FINGER";down(e,1,"LS");down(e,2,"LS");check(pressed(e,PadState.L3),"second finger click");moveStick(e,1,"LS",1,0);check(e.state.lx>.999f&&pressed(e,PadState.L3),"click and movement independent");e.up(2,time+=10);check(!pressed(e,PadState.L3)&&e.state.lx>.999f,"secondary click release");e.release();
        e.config.get("LS HOLD").visible=true;down(e,1,"LS");moveStick(e,1,"LS",1,0);down(e,2,"LS HOLD");e.up(2,time+=10);e.up(1,time+=10);near(e.state.lx,1,"movement held after release");down(e,3,"LS HOLD");near(e.state.lx,0,"explicit movement hold cancellation");e.release();neutral(e);
        e.config.get("AIM").visible=true;e.config.aimMode="TOGGLE";down(e,1,"AIM");e.up(1,time+=10);near(e.state.lt,1,"aim toggle");e.release();neutral(e);
        // Per-stick settings affect output while racing uses physical vertical movement.
        e=engine("Racing");e.config.get("LS").invertY=true;down(e,1,"LS");moveStick(e,1,"LS",0,-1);near(e.state.rt,1,"racing uses physical Y despite inversion");e.release();
        ControllerConfig copy=e.config.copy();copy.cameraX=2;copy.get("LS").response=2;check(e.config.cameraX==1&&e.config.get("LS").response==1,"new settings deep-copy isolation");
        // Mapped gesture exclusivity and bounded pulse lifetimes.
        e=engine("Default");e.config.get("A").gesture="TAP";down(e,1,"A");check(!pressed(e,PadState.A),"tap waits for release");e.up(1,time+=10);check(pressed(e,PadState.A),"tap emits on release");e.update(time+=61);neutral(e);
        e.config.get("A").gesture="TOGGLE";down(e,1,"A");e.up(1,time+=10);check(pressed(e,PadState.A),"mapped toggle persists");down(e,1,"A");e.up(1,time+=10);neutral(e);
        e.config.get("A").gesture="LONG_PRESS";down(e,1,"A");check(!pressed(e,PadState.A),"long press pending");e.update(time+=500);check(pressed(e,PadState.A),"mapped long press");e.up(1,time+=10);neutral(e);
        e.config.get("A").gesture="SWIPE";down(e,1,"A");ControllerInput.Pointer a=e.owner(e.config.get("A"));e.move(1,a.x+70,a.y,time+=20);e.update(time);check(pressed(e,PadState.A),"swipe mapped press");e.release();
        e.config.get("A").gesture="HOLD";e.config.get("A").action="NEXT";down(e,1,"A");check("NEXT:".equals(e.takeAction()),"action dispatched once");e.update(time+=20);check(e.takeAction()==null,"held action does not repeat");neutral(e);e.release();
        e.config.get("A").action="BUTTONS";e.config.get("A").modifiedButtons=1<<PadState.B;e.config.get("MODIFIER").visible=true;down(e,1,"A");down(e,2,"MODIFIER");check(pressed(e,PadState.B)&&!pressed(e,PadState.A),"modifier substitutes mapping");e.up(2,time+=10);check(pressed(e,PadState.A)&&!pressed(e,PadState.B),"modifier release restores mapping");e.release();
        // Independent trigger mode overrides, legacy defaults and independent hit dimensions.
        e.config.get("LT").mode="DIGITAL";down(e,1,"LT");down(e,2,"RT");near(e.state.lt,1,"per-trigger digital");near(e.state.rt,0,"other trigger retains edge mode");e.release();
        ControllerConfig.Control stick=e.config.get("LS");stick.hitWidth=2;stick.hitHeight=.5f;check(e.hit(stick,stick.x*1100+e.radius(stick)*1.5f,stick.y*480),"wide activation region");check(!e.hit(stick,stick.x*1100,stick.y*480+e.radius(stick)),"short activation region");
        // Gyro uses an explicit additive clamp with stale-sample and interruption resets.
        e=engine("Default");e.config.gyroEnabled=true;e.config.gyroAimOnly=false;e.gyro(.5f,-.25f,time);e.update(time);near(e.state.rx,.5f,"gyro X");near(e.state.ry,-.25f,"gyro Y");down(e,1,"RS");moveStick(e,1,"RS",1,0);near(e.state.rx,1,"gyro plus touch clamps");e.release();neutral(e);e.gyro(.4f,0,time);e.update(time+=101);neutral(e);
        e.config.gyroAimOnly=true;e.gyro(.4f,0,time);e.update(time);neutral(e);e.config.triggerMode="DIGITAL";down(e,1,"LT");check(e.state.rx>0,"aim-only gyro activation");e.release();
        GyroFilter filter=new GyroFilter();filter.calibrate();for(int i=0;i<70;i++)filter.sample(.02f,-.03f,1_000_000_000L+i*20_000_000L,.3f,.005f);check(!filter.calibrating(),"still calibration completes");float[] filtered=filter.sample(.02f,-.03f,3_000_000_000L,0,.005f);near(filtered[0],0,"calibrated gyro rest X");near(filtered[1],0,"calibrated gyro rest Y");filter.calibrate();for(int i=0;i<70;i++)filter.sample(1,0,4_000_000_000L+i*20_000_000L,0,0);check(filter.calibrating(),"moving calibration rejected");
        e=engine("Default");e.config.get("A").gesture="SECOND_FINGER";down(e,1,"A");neutral(e);down(e,2,"A");check(pressed(e,PadState.A),"mapped second finger activates");e.up(1,time+=10);neutral(e);e.release();
        e.config.get("A").gesture="HOLD";e.config.get("A").modifiedButtons=1<<PadState.B;e.config.get("X").action="MODIFIER";e.config.get("X").gesture="LONG_PRESS";down(e,1,"A");down(e,2,"X");check(pressed(e,PadState.A)&&!pressed(e,PadState.X),"pending modifier does not emit button");e.update(time+=500);check(pressed(e,PadState.B)&&!pressed(e,PadState.A),"gesture modifier independent of insertion order");e.up(2,time+=10);check(pressed(e,PadState.A),"gesture modifier releases");e.release();
        e=engine("Default");e.config.switchButtons=(1<<PadState.LB)|(1<<PadState.RB);down(e,1,"LB");check(e.takeAction()==null,"partial chord no switch");down(e,2,"RB");check("NEXT:".equals(e.takeAction()),"complete chord switches");e.update(time+=20);check(e.takeAction()==null,"held chord does not repeat");e.release();
        e.config.get("FIRE").visible=true;down(e,1,"FIRE");near(e.state.rt,1,"floating fire output");e.up(1,time+=10);neutral(e);
        // Camera output is stable for the same speed at different event intervals.
        for(int dt:new int[]{2,8,16,32}){e=engine("FPS");down(e,1,"RS");ControllerInput.Pointer pointer=e.owner(e.config.get("RS"));e.move(1,pointer.x+dt*.5f,pointer.y,time+=dt);e.update(time);near(e.state.rx,.3f,"camera sample-rate independence");e.release();}
        System.out.println("Input tests passed: "+checks+" assertions (pointer ownership → final state → HID report)");
    }
}
