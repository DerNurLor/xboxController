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
    static void nitroDoubleTap(){
        ControllerInput e=engine("Default");e.config.rtDoubleTapNitro=true;
        ControllerConfig copy=e.config.copy();check(copy.rtDoubleTapNitro,"nitro option copied with layout");
        down(e,1,"RT");e.up(1,time+=50);check(!pressed(e,PadState.A),"first RT tap does not trigger nitro");
        down(e,2,"RT");check(pressed(e,PadState.A),"second RT tap starts A nitro");check(e.needsFrame(),"nitro pulse keeps controller frames scheduled");
        e.update(time+=2999);check(pressed(e,PadState.A),"nitro remains held for three seconds");
        e.update(time+=2);check(!pressed(e,PadState.A),"nitro releases after three seconds");e.up(2,time+=20);neutral(e);
        e=engine("Default");e.config.rtDoubleTapNitro=true;down(e,1,"RT");e.up(1,time+=300);down(e,2,"RT");check(!pressed(e,PadState.A),"slow RT taps do not trigger nitro");e.release();neutral(e);
        e=engine("Default");e.config.rtDoubleTapNitro=true;down(e,1,"RT");e.up(1,time+=50);down(e,2,"RT");check(pressed(e,PadState.A),"nitro active before cancellation");e.release();neutral(e);
    }
    static void builtinMacros(){
        ControllerConfig c=ControllerConfig.preset("Default");c.rtDoubleTapNitro=true;c.ltSteering=true;c.ensureBuiltinMacros();
        check(!c.rtDoubleTapNitro&&!c.ltSteering,"legacy feature flags migrate to macro assignments");
        ControllerConfig.Macro nitro=c.macro("builtin:double_tap_nitro"),steer=c.macro("builtin:steering_lt");
        check(nitro!=null&&nitro.builtin.equals("double_tap_nitro")&&steer!=null&&steer.builtin.equals("steering_lt"),"built-in macro definitions are available");
        check(c.macroAssignments.stream().anyMatch(a->a.macroId.equals(nitro.id)&&a.controlId.equals("RT")&&a.trigger.equals("DOUBLE_TAP")),"old nitro switch becomes a double-tap RT assignment");
        check(c.macroAssignments.stream().anyMatch(a->a.macroId.equals(steer.id)&&a.controlId.equals("LT")&&a.trigger.equals("HOLD")),"old LT steering becomes a held macro assignment");ControllerConfig rightLegacy=ControllerConfig.preset("Default");rightLegacy.rtSteering=true;rightLegacy.ensureBuiltinMacros();check(rightLegacy.macroAssignments.stream().anyMatch(a->a.macroId.equals("builtin:steering_rt")&&a.controlId.equals("RT")),"legacy RT steering keeps its right-stick behavior");
        ControllerInput e=new ControllerInput(c);e.resize(1100,480);down(e,1,"RT");e.up(1,time+=50);check(!pressed(e,PadState.A),"macro nitro first tap");down(e,2,"RT");e.up(2,time+=50);check(pressed(e,PadState.A),"macro nitro fires on second tap");e.release();
        e=new ControllerInput(c);e.resize(1100,480);down(e,3,"LT");ControllerInput.Pointer lt=e.activePointers().get(3);e.move(3,lt.startX+e.width*e.config.travel*.5f,lt.startY-e.height*e.config.travel*.5f,time+=20);e.update(time);check(e.state.lx>.4f&&e.state.ly<-.4f,"steer macro maps LT touch vector to left stick");e.up(3,time+=20);near(e.state.lx,0,"steer macro releases stick X");near(e.state.ly,0,"steer macro releases stick Y");
        steer.program="steer RIGHT";e=new ControllerInput(c);e.resize(1100,480);down(e,4,"LT");lt=e.activePointers().get(4);e.move(4,lt.startX-e.width*e.config.travel*.5f,lt.startY,time+=20);e.update(time);check(e.state.rx<-.4f&&Math.abs(e.state.lx)<.01f,"editing steer macro changes target stick");
    }
    static void ltDragSteering(){
        ControllerInput e=engine("Default");
        check(!e.config.ltSteering&&!e.config.copy().ltSteering&&!e.config.rtSteering&&!e.config.copy().rtSteering,"trigger steering is opt-in and copied with layout");
        down(e,1,"LT");ControllerInput.Pointer lt=e.activePointers().get(1);
        e.move(1,lt.startX+e.width*e.config.travel*.5f,lt.startY,time+=20);e.update(time);
        near(e.state.lx,0,"LT drag steering disabled by default");e.release();
        e=engine("Default");e.config.ltSteering=true;down(e,1,"LT");lt=e.activePointers().get(1);
        e.move(1,lt.startX+e.width*e.config.travel*.5f,lt.startY,time+=20);e.update(time);
        check(e.state.lx>.4f,"LT right drag steers right");
        e.move(1,lt.startX-e.width*e.config.travel*.5f,lt.startY,time+=20);e.update(time);
        check(e.state.lx<-.4f,"LT left drag steers left");
        e.move(1,lt.startX,lt.startY+e.height*e.config.travel*.5f,time+=20);e.update(time);check(e.state.ly>.4f,"LT down drag steers down");
        e.move(1,lt.startX,lt.startY-e.height*e.config.travel*.5f,time+=20);e.update(time);check(e.state.ly<-.4f,"LT up drag steers up");
        e.move(1,lt.startX+e.width*e.config.travel*.5f,lt.startY-e.height*e.config.travel*.5f,time+=20);e.update(time);check(e.state.lx>.4f&&e.state.ly<-.4f,"LT diagonal drag combines axes");e.up(1,time+=20);near(e.state.lx,0,"LT release centers steering");near(e.state.ly,0,"LT release centers vertical steering");
        e=engine("Default");e.config.rtSteering=true;down(e,1,"RT");ControllerInput.Pointer rt=e.activePointers().get(1);e.move(1,rt.startX+e.width*e.config.travel*.5f,rt.startY-e.height*e.config.travel*.5f,time+=20);e.update(time);check(e.state.rx>.4f&&e.state.ry<-.4f,"RT drag steers right stick in four directions");near(e.state.lx,0,"RT drag leaves left stick centered");e.up(1,time+=20);near(e.state.rx,0,"RT release centers right-stick steering");
        e=engine("Default");e.config.ltSteering=true;down(e,1,"LT");down(e,2,"LS");lt=e.activePointers().get(1);ControllerInput.Pointer ls=e.owner(e.config.get("LS"));
        e.move(2,ls.cx-ls.control.w*e.width*.3f,ls.cy,time+=20);e.update(time);
        float manual=e.state.lx;e.move(1,lt.startX+e.width*e.config.travel*.5f,lt.startY,time+=20);e.update(time);
        check(e.state.lx<0&&Math.abs(e.state.lx-manual)<.02f,"physical left-stick touch takes precedence");e.release();neutral(e);
    }
    static void gyroPipeline(){
        GyroFilter f=new GyroFilter();
        float[] out=f.sample(0,0,0,1_000_000_000L,0,.015f);near(out[0],0,"zero gyro");
        f.calibrate();for(int n=0;n<60;n++)f.sample(.02f,-.03f,.01f,2_000_000_000L+n*20_000_000L,.35f,.005f);
        check(!f.calibrating(),"XYZ still calibration completes");
        out=f.sample(.02f,-.03f,.01f,3_200_000_000L,0,.005f);
        for(float rate:out)near(rate,0,"XYZ bias subtraction");
        for(int rotation=0;rotation<4;rotation++){
            float[] camera=GyroFilter.camera(out[0],out[1],rotation);near(camera[0],0,"rotation preserves calibrated rest X");near(camera[1],0,"rotation preserves calibrated rest Y");
        }
        float[][] expected={{-2,-1},{-1,2},{2,1},{1,-2}};
        for(int r=0;r<4;r++)for(int sign:new int[]{-1,1}){
            out=GyroFilter.camera(sign,2*sign,r);near(out[0],expected[r][0]*sign,"horizontal display mapping "+r);near(out[1],expected[r][1]*sign,"vertical display mapping "+r);
        }
        ControllerConfig c=ControllerConfig.preset("Default");c.gyroSensitivity=1.2f;
        near(c.gyroGainX(),1.2f,"legacy X gain");near(c.gyroGainY(),1.2f,"legacy Y gain");
        c.gyroSensitivityX=2;c.gyroSensitivityY=.5f;c.gyroInvertX=true;c.gyroInvertY=true;
        out=GyroFilter.camera(new float[]{.2f,.4f,0},1,c);near(out[0],.4f,"X sensitivity and invert");near(out[1],-.2f,"Y sensitivity and invert");near(c.copy().gyroGainY(),.5f,"axis gains copied");
        f=new GyroFilter();out=f.sample(.01f,-.01f,0,1_000_000_000L,0,.015f);near(out[0],0,"positive dead zone");near(out[1],0,"negative dead zone");
        out=f.sample(1,0,0,1_016_000_000L,.5f,0);near(out[0],.5f,"timestamp-based smoothing");
        out=f.sample(0,0,0,1_032_000_000L,.5f,0);near(out[0],.25f,"filter decay");
        out=f.sample(0,0,0,2_000_000_000L,.95f,0);near(out[0],0,"stale gap discards filter tail");
        f=new GyroFilter();for(int n=0;n<100;n++)out=f.sample(.02f,0,0,2_000_000_000L+n*16_000_000L,.95f,.015f);
        check(out[0]>.019f,"slow motion above dead zone survives smoothing");
        f.calibrate();for(int n=0;n<60;n++)f.sample(0,0,1,4_000_000_000L+n*20_000_000L,0,0);check(f.calibrating(),"Z motion rejects calibration");
        f=new GyroFilter();f.calibrate();for(int n=0;n<20;n++)f.sample(0,0,0,5_000_000_000L+n*20_000_000L,0,0);
        for(int n=0;n<20;n++)f.sample(0,0,0,7_000_000_000L+n*20_000_000L,0,0);check(f.calibrating(),"calibration cannot span a sensor outage");
        ControllerInput e=engine("Default");e.config.gyroEnabled=true;e.config.gyroAimOnly=false;e.gyro(.2f,-.1f,time);
        down(e,1,"RS");moveStick(e,1,"RS",.3f,.2f);float touchX=e.state.rx-.2f,touchY=e.state.ry+.1f;
        e.gyro(.4f,.1f,time);e.update(time);near(e.state.rx,touchX+.4f,"live touch plus gyro X");near(e.state.ry,touchY+.1f,"live touch plus gyro Y");
        e.up(1,time+=10);near(e.state.rx,.4f,"touch release preserves gyro");e.update(time+=100);neutral(e);
    }
    static void v5(){
        gyroPipeline();
        GameplayLock lock=new GameplayLock();lock.reset(true);lock.down(7,0,0,1000);check(!lock.update(2399),"unlock cannot finish early");check(lock.update(2400)&&!lock.locked,"deliberate unlock");lock.reset(true);lock.down(7,0,0,3000);lock.move(8,100,100,12);check(lock.holding(),"unrelated pointer cannot move unlock owner");lock.move(7,13,0,12);check(!lock.update(5000)&&lock.locked,"moving cancels unlock");lock.down(9,0,0,6000);lock.up(9);check(!lock.update(8000),"tap never unlocks");
        ControllerInput e=engine("Two-Thumb FPS / GTA On Foot");check(e.config.gameplayLock&&e.config.sameThumbFire,"new preset deliberately protected");check(e.config.copy().sameThumbFire&&e.config.copy().fireGapMs==e.config.fireGapMs,"copy retains gesture settings");
        down(e,11,"AIM");e.up(11,time+=20);check(e.state.lt==1,"aim latches before two-thumb stream");down(e,1,"LS");moveStick(e,1,"LS",1,0);down(e,2,"RS");ControllerConfig.Control rs=e.config.get("RS");float x=rs.x*e.width,y=rs.y*e.height;
        e.move(2,x+30,y,time+=20);e.update(time);check(e.state.rt==0&&e.state.rx>0,"ordinary camera drag never fires");e.up(2,time+=20);down(e,3,"RS");check(e.state.rt==0,"drag does not arm next touch");e.up(3,time+=20);down(e,4,"RS");check(e.state.rt==1&&e.state.lx>.99&&e.state.lt==1,"same thumb fire plus walking plus aim");check(!pressed(e,PadState.R3),"same thumb fire excludes R3");e.move(4,x+60,y+10,time+=20);e.update(time);check(e.state.rt==1&&e.state.rx>0&&e.state.lx>.99,"camera stays active while RT held");e.update(time+80);check(e.state.rx==0&&e.state.ry==0&&e.state.rt==1,"stationary camera neutral while fire holds");float visibleEdge=(rs.x+rs.w/2)*e.width+2;check(e.hit(rs,visibleEdge,y),"fire exit stays inside enlarged camera touch target");e.move(4,visibleEdge,y,time+=100);e.update(time);check(e.state.rt==0&&e.state.rx>0&&e.owner(rs)!=null,"exit ends fire while retaining camera ownership");e.move(4,x,y,time+=20);e.update(time);check(e.state.rt==0,"returning to camera cannot re-fire");e.up(4,time+=20);check(e.state.rt==0&&e.state.lx>.99,"fire release preserves walking");down(e,5,"RS");check(e.state.rt==0,"fire release cannot rearm fire");e.cancel(5,time+=20);check(e.state.rt==0&&e.state.lt==0,"cancel clears aim and fire");e.release();neutral(e);
        CameraFireGesture gesture=new CameraFireGesture();gesture.down(5,"RS",0,0,100,280,10);gesture.up(5,130,180,10);gesture.down(9,"RS",50,0,180,280,10);check(!gesture.firing(9),"distant tap rejected");gesture.up(9,220,180,10);gesture.down(10,"RS",50,0,700,280,10);check(!gesture.firing(10),"late second touch rejected");gesture.reset();gesture.down(1,"RS",0,0,1000,280,10);gesture.up(1,1010,180,10);gesture.down(2,"other",0,0,1020,280,10);check(!gesture.firing(2),"different camera cannot inherit arming");
        RumbleState h=RumbleState.parse(new byte[]{(byte)255,0,100},1000,"Bluetooth");check(h.heavy==1&&h.light==0&&h.expires==2000,"exact report channels and duration");near(h.mix(1100,1,1,1,false),(float)Math.sqrt(.5),"heavy-only RMS");near(new RumbleState(0,255,500,1000,"Wi-Fi").mix(1100,1,1,1,false),(float)Math.sqrt(.5),"light-only RMS");near(new RumbleState(255,255,500,1000,"Wi-Fi").mix(1100,1,1,1,false),1,"both motors bounded");near(h.mix(2000,1,1,1,false),0,"expiry stops");near(h.mix(1100,0,1,1,false),0,"channel weight zero");near(h.mix(1100,1,1,1,true),(float)Math.sqrt(.5)*.4f,"quiet scale");check(RumbleState.parse(new byte[]{1,2},0,"bad")==null,"malformed output rejected");check(!new RumbleState(255,255,0,0,"stop").active(0),"duration zero stops");check(new RumbleState(255,255,999999,0,"bounded").expires==2500,"effect duration bounded");
    }
    static void fullscreenCamera(){
        ControllerInput e=engine("FPS");ControllerConfig.Control camera=e.config.get("RS");float oldX=camera.x,oldW=camera.w;camera.fullscreen=true;
        check(e.target(550,40)==camera,"fullscreen camera catches empty background");
        check(e.target(550,40,false)!=camera,"editor keeps original camera bounds");
        for(String name:new String[]{"LS","A","LT","D-PAD"}){ControllerConfig.Control c=e.config.get(name);check(e.target(c.x*e.width,c.y*e.height)==c,"foreground wins over fullscreen camera "+name);}
        down(e,1,"LS");moveStick(e,1,"LS",1,0);down(e,2,"A");check(e.down(3,550,40,time+=20),"background captures third finger");e.move(3,600,60,time+=20);e.update(time);
        check(e.state.rx>0&&e.state.ry>0&&e.state.lx>0&&pressed(e,PadState.A),"camera walking and button work together");
        e.up(3,time+=20);check(e.state.rx==0&&e.state.ry==0&&e.state.lx>0&&pressed(e,PadState.A),"camera release preserves foreground inputs");e.release();neutral(e);
        check(e.config.copy().get("RS").fullscreen,"copy preserves fullscreen camera");camera.fullscreen=false;
        check(e.target(550,40)==null&&camera.x==oldX&&camera.w==oldW,"disabling restores original region and geometry");
        camera.fullscreen=true;camera.visible=false;check(e.target(550,40)==null,"hidden camera cannot capture background");camera.visible=true;camera.mode="FIXED";check(e.target(550,40)==null,"fullscreen only affects camera mode");
    }
    public static void main(String[] args){
        v5();fullscreenCamera();nitroDoubleTap();builtinMacros();ltDragSteering();
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
            for(float[] xy:new float[][]{{1,0},{-1,0},{0,-1},{0,1},{0,-.5f},{.7f,-.7f},{-.7f,.7f},{1,-.04f}}){moveStick(e,1,"LS",xy[0],xy[1]);float expected=ControllerInput.integratedTrigger((float)Math.hypot(xy[0],xy[1]),e.config.triggerDeadzone,1,"LINEAR");int pedal=e.activePointers().get(1).pedalDirection;near(e.state.rt,pedal<0?expected:0,"racing RT");near(e.state.lt,pedal>0?expected:0,"racing LT");if(!preserve)near(e.state.ly,0,"racing Y suppressed");else if(xy[1]!=0)check(Math.signum(e.state.ly)==Math.signum(xy[1]),"Y preserved");if(xy[0]!=0)check(Math.signum(e.state.lx)==Math.signum(xy[0]),"diagonal steering");check((e.state.report()[12]&255)==Math.round(e.state.rt*255),"racing HID RT");check((e.state.report()[11]&255)==Math.round(e.state.lt*255),"racing HID LT");}
            moveStick(e,1,"LS",0,-1);
            for(float angle:new float[]{0,.5f,1,1.57f}){moveStick(e,1,"LS",(float)Math.sin(angle),-(float)Math.cos(angle));near(e.state.rt,1,"gas maintained around steering arc");near(e.state.lt,0,"steering does not brake");}
            moveStick(e,1,"LS",-1,0);near(e.state.rt,1,"gas survives opposite full steering");
            moveStick(e,1,"LS",0,0);near(e.state.rt,0,"center releases pedal");
            moveStick(e,1,"LS",1,0);near(e.state.rt,0,"fresh horizontal steering coasts");near(e.state.lt,0,"fresh horizontal steering does not brake");
            moveStick(e,1,"LS",0,1);moveStick(e,1,"LS",-1,0);near(e.state.lt,1,"brake maintained while steering");near(e.state.rt,0,"brake clears gas");
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
        // V4.1: independent instances share output without sharing pointer ownership.
        e=engine("Default");ControllerConfig.Control extra=e.config.duplicateControl("A");extra.x=.5f;extra.y=.7f;
        String extraId=extra.id;check(!extraId.equals("A")&&e.config.copy().get(extraId)!=extra,"stable deep-copied control identity");
        down(e,1,"A");down(e,2,extraId);e.up(1,time+=10);check(pressed(e,PadState.A),"duplicate button retains independent owner");e.up(2,time+=10);neutral(e);
        e.config.get(extraId).visible=false;extra=e.config.duplicateControl("LS");extra.x=.5f;extra.y=.8f;down(e,1,"LS");moveStick(e,1,"LS",.7f,0);
        check(!e.down(2,extra.x*e.width,extra.y*e.height,time+=10),"second LS instance cannot overwrite active axis");e.up(1,time+=10);down(e,2,extra.id);e.up(2,time+=10);neutral(e);
        e.config.deleteControl("A");check(e.config.get("A")==null&&e.config.get(extraId)!=null,"deletion preserves other instances");
        // Opt-in transfer retains axes across geometry changes, releases every discrete input.
        e=engine("Default");e.config.transferSticks=true;e.config.clickGesture="LONG_PRESS";e.config.triggerMode="DIGITAL";
        down(e,1,"LS");moveStick(e,1,"LS",.7f,-.3f);down(e,2,"RS");moveStick(e,2,"RS",.3f,.4f);down(e,3,"RT");down(e,4,"A");
        float lx=e.state.lx,ly=e.state.ly,rx=e.state.rx,ry=e.state.ry;
        ControllerConfig next=e.config.copy();next.get("LS").x=.4f;next.get("LS").w*=.7f;next.get("LS").h*=.7f;
        e.transition(next,time+=10);near(e.state.lx,lx,"transfer LS X");near(e.state.ly,ly,"transfer LS Y");near(e.state.rx,rx,"transfer RS X");near(e.state.ry,ry,"transfer RS Y");
        check(e.pointerCount()==2&&e.state.buttons==0&&e.state.lt==0&&e.state.rt==0,"transfer drops buttons and triggers");
        e.update(time+=1000);check(e.state.buttons==0,"transferred sticks cannot synthesize long clicks");e.move(3,0,0,time);e.up(4,time);check(e.pointerCount()==2,"old non-stick events cannot create owners");
        e.up(1,time+=10);near(e.state.lx,0,"transferred LS release");check(e.state.rx!=0,"transferred RS remains held");e.cancel(2,time+=10);neutral(e);
        for(int incompatible=0;incompatible<7;incompatible++){
            e=engine("Default");e.config.transferSticks=true;down(e,1,"LS");moveStick(e,1,"LS",.6f,0);next=e.config.copy();
            switch(incompatible){case 0:next.transferSticks=false;break;case 1:next.integrated=true;break;case 2:next.get("LS").invertX=true;break;case 3:next.get("LS").visible=false;break;case 4:next.deleteControl("LS");break;case 5:next.get("LS").response=2;break;case 6:next.leftMode="FIXED";break;}
            e.transition(next,time+=10);neutral(e);check(e.pointerCount()==0,"incompatible transfer releases owner");
        }
        e=engine("Default");down(e,1,"LS");moveStick(e,1,"LS",1,0);next=e.config.copy();next.transferSticks=true;e.transition(next,time+=10);neutral(e);
        e=engine("FPS");e.config.transferSticks=true;e.config.cameraFire=true;down(e,1,"RS");moveStick(e,1,"RS",.3f,0);down(e,2,"RS");next=e.config.copy();e.transition(next,time+=1);check(e.state.rx>0&&e.state.rt==0&&e.pointerCount()==1,"camera transfer discards dependent fire");e.update(time+=61);near(e.state.rx,0,"transferred camera still expires");e.release();
        System.out.println("Input tests passed: "+checks+" assertions (pointer ownership → final state → HID report)");
    }
}
