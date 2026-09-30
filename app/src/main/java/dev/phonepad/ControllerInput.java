package dev.phonepad;

import java.util.*;

/** Pure Java pointer interpreter. Sources aggregate into one transport-neutral state. */
public final class ControllerInput {
    public final PadState state=new PadState();
    public ControllerConfig config;
    private final Map<Integer,Pointer> pointers=new LinkedHashMap<>();
    private final Map<String,Long> taps=new HashMap<>();
    private final Set<ControllerConfig.Control> toggled=new HashSet<>();
    private final Map<ControllerConfig.Control,Long> pulses=new HashMap<>();
    private String pendingAction;
    private boolean modifier,chordHeld,movementToggleRequested;
    public float temporaryLT;
    public Map<Integer,Pointer> activePointers(){return Collections.unmodifiableMap(pointers);}
    public boolean modifierActive(){return modifier;}
    public String takeAction(){String action=pendingAction;pendingAction=null;return action;}
    private boolean aimLatched, movementHeld;
    private float heldX,heldY,gyroX,gyroY;
    private long gyroAt;
    public void gyro(float x,float y,long now){gyroX=PadState.clamp(x,-1,1);gyroY=PadState.clamp(y,-1,1);gyroAt=now;}
    public float width=1100,height=480;
    public static final class Pointer {
        public final ControllerConfig.Control control;
        public final float startX,startY;
        public float x,y,cx,cy,vx,vy,maxDistance;
        public final long down;
        public long moved;
        public boolean click,activated,actionSent;
        // Secondary contacts never write the primary stick's axes.
        public String role="PRIMARY";
        Pointer(ControllerConfig.Control c,float x,float y,long time){control=c;this.x=startX=x;this.y=startY=y;down=moved=time;}
    }
    public ControllerInput(ControllerConfig config){this.config=config;}
    public void configure(ControllerConfig c){release();config=c;}
    public void resize(float w,float h){release();width=Math.max(1,w);height=Math.max(1,h);}
    public int pointerCount(){return pointers.size();}
    public boolean active(ControllerConfig.Control c){return owner(c)!=null||toggled.contains(c)||pulses.containsKey(c)||c.kind.equals("aim")&&aimLatched||c.kind.equals("hold")&&movementHeld;}
    public boolean movementHeld(){return movementHeld;}
    public boolean aiming(){return aimLatched||state.lt>0;}
    public float radius(ControllerConfig.Control c){return Math.min(c.w*width,c.h*height)/2;}
    public Pointer owner(ControllerConfig.Control c){for(Pointer p:pointers.values())if(p.control==c&&p.role.equals("PRIMARY"))return p;return null;}
    public boolean circularHit(ControllerConfig.Control c){return (c.kind.equals("stick")&&!(c.bit==1&&config.mode(c).equals("TOUCHPAD")))||c.id.matches("[ABXY]");}
    public boolean hit(ControllerConfig.Control c,float x,float y){
        if(!config.shown(c))return false;
        float dx=x-c.x*width,dy=y-c.y*height;
        if(circularHit(c))return Math.hypot(dx/c.hitWidth,dy/c.hitHeight)<=radius(c)*c.hitbox;
        return Math.abs(dx)<=c.w*width*c.hitbox*c.hitWidth/2 && Math.abs(dy)<=c.h*height*c.hitbox*c.hitHeight/2;
    }
    /** Explicit controls win over stick regions; nearest target wins ties. */
    public ControllerConfig.Control target(float x,float y){
        ControllerConfig.Control best=null;float distance=Float.MAX_VALUE;int priority=9;
        for(ControllerConfig.Control c:config.controls){if(!hit(c,x,y))continue;int rank=c.kind.equals("stick")?1:0;float d=(float)Math.hypot((x-c.x*width)/Math.max(1,c.w*width),(y-c.y*height)/Math.max(1,c.h*height));if(rank<priority||rank==priority&&d<distance){priority=rank;distance=d;best=c;}}
        return best;
    }
    public boolean down(int id,float x,float y,long now){
        if(pointers.containsKey(id)||!Float.isFinite(x)||!Float.isFinite(y))return false;
        ControllerConfig.Control c=target(x,y);if(c==null)return false;
        Pointer existing=owner(c),p=new Pointer(c,x,y,now);p.cx=c.x*width;p.cy=c.y*height;
        if(c.kind.equals("stick")){
            if(existing!=null){
                if(c.bit==1&&config.mode(c).equals("TOUCHPAD")&&config.cameraFire)p.role="FIRE";
                else if(config.clickGesture.equals("SECOND_FINGER"))p.role="CLICK";
                else return false;
            }else{
                String mode=config.mode(c);
                if(mode.equals("FLOATING")){p.cx=x;p.cy=y;}
                Long last=taps.remove(c.id);p.click=config.clickGesture.equals("DOUBLE_TAP")&&last!=null&&now>=last&&now-last<350;
                if(c.bit==0)movementHeld=false;
            }
        }else if(c.kind.equals("fire")){p.cx=x;p.cy=y;}else if(c.kind.equals("unified")){
            if(existing!=null)return false;
            if(config.unifiedFloating){p.cx=x;p.cy=y;}
        }else if(c.kind.equals("trigger")&&existing!=null)return false;
        else if(c.kind.equals("hold")){
            if(existing!=null)return false;
            movementHeld=!movementHeld;heldX=state.lx;heldY=state.ly;
        }else if(c.kind.equals("aim")&&config.aimMode.equals("TOGGLE")){
            if(existing!=null)return false;
            aimLatched=!aimLatched;
        }
        if(c.kind.equals("button")||c.kind.equals("combo")){
            if(c.gesture.equals("DOUBLE_TAP")){Long last=taps.remove(c.id);p.activated=last!=null&&now>=last&&now-last<350;}
            if(c.gesture.equals("TOGGLE")){if(!toggled.remove(c))toggled.add(c);}
        }
        pointers.put(id,p);update(now);return true;
    }
    public void move(int id,float x,float y,long now){
        Pointer p=pointers.get(id);if(p==null||now<p.moved||!Float.isFinite(x)||!Float.isFinite(y))return;
        float dt=Math.max(1,now-p.moved);
        float sensitivity=p.control.sensitivity<0?config.sensitivity:p.control.sensitivity;
        float vx=PadState.clamp((x-p.x)/dt*.6f*sensitivity,-1,1),vy=PadState.clamp((y-p.y)/dt*.6f*sensitivity,-1,1);
        float keep=(float)Math.pow(config.cameraSmoothing,dt/16f);
        p.vx=p.vx*keep+vx*(1-keep);p.vy=p.vy*keep+vy*(1-keep);
        p.x=x;p.y=y;p.moved=now;p.maxDistance=Math.max(p.maxDistance,(float)Math.hypot(x-p.startX,y-p.startY));
    }
    public void up(int id,long now){
        Pointer p=pointers.remove(id);
        if(p!=null&&p.role.equals("PRIMARY")&&p.control.kind.equals("stick")){
            if(now>=p.down&&now-p.down<250&&p.maxDistance<radius(p.control)*.18f&&!p.click)taps.put(p.control.id,now);
            // Fire/click contacts depend on this camera/stick lifetime. Never transfer ownership.
            pointers.values().removeIf(other->other.control==p.control);
        }
        if(p!=null&&(p.control.kind.equals("button")||p.control.kind.equals("combo"))){
            ControllerConfig.Control c=p.control;boolean tap=now>=p.down&&now-p.down<250&&p.maxDistance<radius(c)*.3f;
            if(tap&&c.gesture.equals("TAP")){if(c.action.equals("BUTTONS"))pulses.put(c,now+60);else action(c);}
            if(tap&&c.gesture.equals("DOUBLE_TAP")&&!p.activated)taps.put(c.id,now);
        }
        update(now);
    }
    public void cancel(int id,long now){
        Pointer p=pointers.remove(id);if(p!=null&&p.role.equals("PRIMARY")&&p.control.kind.equals("stick"))pointers.values().removeIf(other->other.control==p.control);
        taps.clear();toggled.clear();pulses.clear();pendingAction=null;aimLatched=false;movementHeld=false;update(now);
    }
    public void release(){pointers.clear();taps.clear();toggled.clear();pulses.clear();pendingAction=null;modifier=false;chordHeld=false;movementToggleRequested=false;temporaryLT=0;aimLatched=false;movementHeld=false;heldX=heldY=gyroX=gyroY=0;gyroAt=0;state.clear();}
    public void update(long now){
        state.clear();
        modifier=false;for(Pointer p:pointers.values())if(p.control.kind.equals("modifier")||p.control.action.equals("MODIFIER")&&gestureActive(p,now))modifier=true;
        pulses.entrySet().removeIf(entry->now>=entry.getValue());
        for(ControllerConfig.Control c:toggled)emit(c);for(ControllerConfig.Control c:pulses.keySet())emit(c);
        if(movementHeld){state.lx=heldX;state.ly=heldY;}
        if(aimLatched)state.lt=1;state.lt=Math.max(state.lt,temporaryLT);
        for(Pointer p:pointers.values()){
            ControllerConfig.Control c=p.control;float r=Math.max(1,radius(c));float x=(p.x-p.cx)/r,y=(p.y-p.cy)/r;
            if(p.role.equals("FIRE")){state.rt=1;continue;}
            if(p.role.equals("CLICK")){state.buttons|=1<<(c.bit==0?PadState.L3:PadState.R3);continue;}
            switch(c.kind){
                case "stick":
                    float dead=c.deadzone<0?config.deadzone:c.deadzone;
                    float sensitivity=c.sensitivity<0?config.sensitivity:c.sensitivity;
                    float[] stick=PadState.stick(x,y,dead,1);
                    float length=(float)Math.hypot(stick[0],stick[1]);
                    if(length>0){float gain=PadState.clamp((float)Math.pow(length,c.response)*sensitivity,0,1)/length;stick[0]*=gain;stick[1]*=gain;}
                    if(c.bit==1&&config.mode(c).equals("TOUCHPAD")){
                        stick[0]=now-p.moved>60?0:camera(p.vx,config.cameraX);
                        stick[1]=now-p.moved>60?0:camera(p.vy,config.cameraY);
                    }
                    if(c.invertX)stick[0]=-stick[0];if(c.invertY)stick[1]=-stick[1];
                    if(c.bit==0){
                        if(!movementHeld){state.lx=stick[0];state.ly=stick[1];}
                        if(config.integrated){float v=integratedTrigger(y,config.triggerDeadzone,config.triggerSensitivity,config.curve);triggers(y>0?v:0,y<0?v:0);if(!config.preserveY)state.ly=0;}
                    }else{state.rx=stick[0];state.ry=stick[1];}
                    if(config.clickGesture.equals("LONG_PRESS")&&now-p.down>=450&&p.maxDistance<r*.15f)p.click=true;
                    if(p.click)state.buttons|=1<<(c.bit==0?PadState.L3:PadState.R3);
                    break;
                case "unified":
                    boolean vertical=config.unifiedOrientation.equals("VERTICAL");
                    float delta=(vertical?p.y-p.cy:p.x-p.cx)/(Math.max(.001f,config.unifiedTravel)*(vertical?height:width));
                    if(config.unifiedInvert)delta=-delta;
                    float amount=integratedTrigger(delta,config.unifiedDeadzone,config.unifiedSensitivity,config.unifiedCurve);
                    triggers(delta>0?amount:0,delta<0?amount:0);break;
                case "trigger":
                    float v;
                    if(config.mode(c).equals("DIGITAL"))v=1;
                    else {float displacement=config.mode(c).equals("VERTICAL")?p.y-p.startY:(c.bit==0?p.x-p.startX:p.startX-p.x);if(c.reverse)displacement=-displacement;v=PadState.clamp(displacement/(Math.max(.001f,config.travel)*(config.mode(c).equals("VERTICAL")?height:width))*config.triggerSensitivity,0,1);}
                    triggers(c.bit==0?v:0,c.bit==1?v:0);break;
                case "fire":state.rt=1;break;
                case "aim":if(config.aimMode.equals("HOLD"))state.lt=1;break;
                case "hold":case "modifier":case "next":case "previous":case "layoutHold":break;
                case "dpad":if(Math.hypot(x,y)>.2)state.hat=((int)Math.round(Math.atan2(x,-y)/(Math.PI/4))+8)%8;break;
                default:
                    
                    boolean active=gestureActive(p,now);
                    if(active){if(c.action.equals("BUTTONS"))emit(c);else if(!c.action.equals("MODIFIER")&&!p.actionSent){action(c);p.actionSent=true;}if(!c.gesture.equals("SECOND_FINGER"))p.activated=true;}
                    break;
            }
        }
        if(config.gyroEnabled&&gyroAt>0&&now>=gyroAt&&now-gyroAt<100&&(!config.gyroAimOnly||state.lt>0)){state.rx=PadState.clamp(state.rx+gyroX,-1,1);state.ry=PadState.clamp(state.ry+gyroY,-1,1);}
        if(movementToggleRequested){movementHeld=!movementHeld;heldX=state.lx;heldY=state.ly;movementToggleRequested=false;}
        boolean chord=Integer.bitCount(config.switchButtons)>=2&&(state.buttons&config.switchButtons)==config.switchButtons;
        if(chord&&!chordHeld)pendingAction="NEXT:";chordHeld=chord;
        // Apply aim after all trigger sources, so pointer ordering cannot change sensitivity.
        if(state.lt>0||modifier){state.rx*=config.aimSensitivity;state.ry*=config.aimSensitivity;}
    }
    private boolean gestureActive(Pointer p,long now){
        ControllerConfig.Control c=p.control;float r=Math.max(1,radius(c));
        if(c.gesture.equals("SECOND_FINGER")){int count=0;for(Pointer other:pointers.values())if(other.control==c)count++;return count>=2;}
        if(c.gesture.equals("LONG_PRESS")&&now-p.down>=450&&p.maxDistance<r*.3f)p.activated=true;
        if(c.gesture.equals("SWIPE")&&p.maxDistance>r*.7f)p.activated=true;
        return c.gesture.equals("HOLD")||p.activated;
    }
    private void emit(ControllerConfig.Control c){state.buttons|=modifier&&c.modifiedButtons>=0?c.modifiedButtons:c.buttons<0?1<<c.bit:c.buttons;triggers(c.outputLT,c.outputRT);}
    private void action(ControllerConfig.Control c){
        if(c.action.equals("MOVEMENT_HOLD")){movementToggleRequested=true;}
        else pendingAction=c.action+":"+c.target;
    }
    private float camera(float v,float gain){return Math.copySign(PadState.clamp((float)Math.pow(Math.abs(v),config.cameraCurve)*gain,0,1),v);}
    private void triggers(float lt,float rt){state.lt=Math.max(state.lt,lt);state.rt=Math.max(state.rt,rt);}
    public static float integratedTrigger(float y,float dead,float sensitivity,String curve){
        float value=PadState.clamp((Math.abs(y)-dead)/Math.max(.001f,1-dead),0,1);
        if(curve.equals("SOFT"))value*=value;else if(curve.equals("AGGRESSIVE"))value=(float)Math.sqrt(value);
        return PadState.clamp(value*sensitivity,0,1);
    }
}
