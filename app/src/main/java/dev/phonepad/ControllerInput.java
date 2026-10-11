package dev.phonepad;

import java.util.*;

/** Pure Java pointer interpreter. Sources aggregate into one transport-neutral state. */
public final class ControllerInput {
    public final PadState state=new PadState();
    /** Touch source visualization only; never injected as controller output. */
    public final PadState sourceState=new PadState();
    public final Automation automation=new Automation();
    public boolean visible(ControllerConfig.Control c){return config.shown(c)&&automation.matches(c.visibleWhen);}
    public final CameraFireGesture cameraFireGesture=new CameraFireGesture();
    public ControllerConfig config;
    private final Map<Integer,Pointer> pointers=new LinkedHashMap<>();
    private final Map<String,Long> taps=new HashMap<>();
    private final Set<ControllerConfig.Control> toggled=new HashSet<>();
    private final Map<ControllerConfig.Control,Long> pulses=new HashMap<>();
    private long nitroUntil;
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
        public int pedalDirection;
        public boolean click,activated,actionSent,transferred,multiTouch;
        // Secondary contacts never write the primary stick's axes.
        public String role="PRIMARY";
        Pointer(ControllerConfig.Control c,float x,float y,long time){control=c;this.x=startX=x;this.y=startY=y;down=moved=time;}
    }
    public ControllerInput(ControllerConfig config){this.config=config;}
    public void configure(ControllerConfig c){release();config=c;}
    /** Transfer only primary sticks with unchanged response semantics; no gesture state crosses. */
    public void transition(ControllerConfig next,long now){
        Map<Integer,Pointer> retained=new LinkedHashMap<>();
        if(config.transferSticks&&next.transferSticks){
            for(Map.Entry<Integer,Pointer> entry:pointers.entrySet()){
                Pointer old=entry.getValue();ControllerConfig.Control c=next.get(old.control.id);
                if(!old.role.equals("PRIMARY")||!compatibleStick(old.control,c,next))continue;
                Pointer p=new Pointer(c,old.x,old.y,now);
                float ratio=radius(c)/Math.max(1,radius(old.control));
                p.cx=p.x-(old.x-old.cx)*ratio;p.cy=p.y-(old.y-old.cy)*ratio;
                p.vx=old.vx;p.vy=old.vy;p.moved=old.moved;p.transferred=true;
                retained.put(entry.getKey(),p);
            }
        }
        release();config=next;pointers.putAll(retained);update(now);
    }
    private boolean compatibleStick(ControllerConfig.Control a,ControllerConfig.Control b,ControllerConfig next){
        if(b==null||!a.kind.equals("stick")||!b.kind.equals("stick")||a.bit!=b.bit||!next.shown(b))return false;
        if(a.bit==0&&(config.integrated||next.integrated||movementHeld))return false;
        if(a.bit==1&&(config.sameThumbFire||next.sameThumbFire||state.lt>0||modifier||config.gyroEnabled||next.gyroEnabled))return false;
        return config.mode(a).equals(next.mode(b))&&a.invertX==b.invertX&&a.invertY==b.invertY&&a.response==b.response
            &&(a.deadzone<0?config.deadzone:a.deadzone)==(b.deadzone<0?next.deadzone:b.deadzone)
            &&(a.sensitivity<0?config.sensitivity:a.sensitivity)==(b.sensitivity<0?next.sensitivity:b.sensitivity)
            &&(a.bit==0||config.cameraX==next.cameraX&&config.cameraY==next.cameraY&&config.cameraCurve==next.cameraCurve&&config.cameraSmoothing==next.cameraSmoothing&&config.aimSensitivity==next.aimSensitivity);
    }
    public void resize(float w,float h){release();width=Math.max(1,w);height=Math.max(1,h);}
    public boolean needsFrame(){return automation.running()||!pointers.isEmpty()||!pulses.isEmpty()||nitroUntil>0||config.gyroEnabled;}
    public int pointerCount(){return pointers.size();}
    public boolean active(ControllerConfig.Control c){return owner(c)!=null||toggled.contains(c)||pulses.containsKey(c)||c.kind.equals("aim")&&aimLatched||c.kind.equals("hold")&&movementHeld;}
    public boolean movementHeld(){return movementHeld;}
    public boolean aiming(){return aimLatched||state.lt>0;}
    public float radius(ControllerConfig.Control c){return Math.min(c.w*width,c.h*height)/2;}
    public Pointer owner(ControllerConfig.Control c){for(Pointer p:pointers.values())if(p.control==c&&p.role.equals("PRIMARY"))return p;return null;}
    public boolean circularHit(ControllerConfig.Control c){return (c.kind.equals("stick")&&!(c.bit==1&&config.mode(c).equals("TOUCHPAD")))||c.id.matches("[ABXY]");}
    public boolean hit(ControllerConfig.Control c,float x,float y){return hit(c,x,y,true);}
    private boolean hit(ControllerConfig.Control c,float x,float y,boolean background){
        if(!visible(c))return false;
        if(background&&config.backgroundCamera(c))return x>=0&&x<=width&&y>=0&&y<=height;
        float dx=x-c.x*width,dy=y-c.y*height;
        if(circularHit(c))return Math.hypot(dx/c.hitWidth,dy/c.hitHeight)<=radius(c)*c.hitbox;
        return Math.abs(dx)<=c.w*width*c.hitbox*c.hitWidth/2 && Math.abs(dy)<=c.h*height*c.hitbox*c.hitHeight/2;
    }
    /** Explicit controls win over stick regions; nearest target wins ties. */
    public ControllerConfig.Control target(float x,float y){return target(x,y,true);}
    public ControllerConfig.Control target(float x,float y,boolean background){
        ControllerConfig.Control best=null;float distance=Float.MAX_VALUE;int priority=9;
        for(ControllerConfig.Control c:config.controls){if(!hit(c,x,y,background))continue;int rank=background&&config.backgroundCamera(c)?2:c.kind.equals("stick")?1:0;float d=(float)Math.hypot((x-c.x*width)/Math.max(1,c.w*width),(y-c.y*height)/Math.max(1,c.h*height));if(rank<priority||rank==priority&&d<distance){priority=rank;distance=d;best=c;}}
        return best;
    }
    public boolean down(int id,float x,float y,long now){
        if(pointers.containsKey(id)||!Float.isFinite(x)||!Float.isFinite(y))return false;
        ControllerConfig.Control c=target(x,y);if(c==null)return false;
        Pointer existing=owner(c),p=new Pointer(c,x,y,now);p.cx=c.x*width;p.cy=c.y*height;
        if(c.kind.equals("trackpad")&&existing!=null){existing.multiTouch=true;p.multiTouch=true;p.role="SCROLL";}
        if(c.kind.equals("stick")){
            // Several visual instances may map to an axis, but only one may own it.
            for(Pointer other:pointers.values())if(other.control!=c&&other.control.kind.equals("stick")&&other.control.bit==c.bit)return false;
            if(existing!=null){
                if(c.bit==1&&config.mode(c).equals("TOUCHPAD")&&config.cameraFire){if(cameraFireGesture.state()==CameraFireGesture.State.FIRING)return false;cameraFireGesture.reset();p.role="FIRE";}
                else if(config.clickGesture.equals("SECOND_FINGER"))p.role="CLICK";
                else return false;
            }else{
                String mode=config.mode(c);
                if(mode.equals("FLOATING")){p.cx=x;p.cy=y;}
                Long last=taps.remove(c.id);p.click=config.clickGesture.equals("DOUBLE_TAP")&&last!=null&&now>=last&&now-last<350;
                if(c.bit==1&&mode.equals("TOUCHPAD")&&config.sameThumbFire){p.click=false;if(cameraFireRegion(c,x,y))cameraFireGesture.down(id,c.id,x,y,now,config.fireGapMs,config.fireSlop*Math.min(width,height));else cameraFireGesture.reset();}
                if(c.bit==0)movementHeld=false;
            }
        }else if(c.kind.equals("fire")){p.cx=x;p.cy=y;}else if(c.kind.equals("unified")){
            if(existing!=null)return false;
            if(config.unifiedFloating){p.cx=x;p.cy=y;}
        }else if(c.kind.equals("dpad")){for(Pointer other:pointers.values())if(other.control.kind.equals("dpad"))return false;}
        else if(c.kind.equals("trigger")&&existing!=null)return false;
        if(c.kind.equals("trigger")&&c.bit==1&&config.rtDoubleTapNitro){
            Long last=taps.remove(c.id);
            if(last!=null&&now>=last&&now-last<350)nitroUntil=now+3000;
        }
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
    private boolean cameraFireRegion(ControllerConfig.Control c,float x,float y){if(config.backgroundCamera(c))return x>=0&&x<=width&&y>=0&&y<=height;return Math.abs(x-c.x*width)<=c.w*width/2&&Math.abs(y-c.y*height)<=c.h*height/2;}
    public void move(int id,float x,float y,long now){
        Pointer p=pointers.get(id);if(p==null||now<p.moved||!Float.isFinite(x)||!Float.isFinite(y))return;
        if(p.control.kind.equals("trackpad")&&visible(p.control)&&automation.matches(p.control.condition)&&p.role.equals("PRIMARY")){float gain=p.control.sensitivity<0?1:p.control.sensitivity;if(p.multiTouch)automation.motion(0,0,(p.y-y)*gain/40);else automation.motion((x-p.x)*gain,(y-p.y)*gain,0);}
        float dt=Math.max(1,now-p.moved);
        float sensitivity=p.control.sensitivity<0?config.sensitivity:p.control.sensitivity;
        float vx=PadState.clamp((x-p.x)/dt*.6f*sensitivity,-1,1),vy=PadState.clamp((y-p.y)/dt*.6f*sensitivity,-1,1);
        float keep=(float)Math.pow(config.cameraSmoothing,dt/16f);
        p.vx=p.vx*keep+vx*(1-keep);p.vy=p.vy*keep+vy*(1-keep);
        cameraFireGesture.move(id,x,y);if(cameraFireGesture.firing(id)&&!cameraFireRegion(p.control,x,y))cameraFireGesture.stopFire();p.x=x;p.y=y;p.moved=now;p.maxDistance=Math.max(p.maxDistance,(float)Math.hypot(x-p.startX,y-p.startY));
    }
    public void up(int id,long now){
        Pointer p=pointers.remove(id);
        if(p!=null&&p.control.kind.equals("trackpad")&&visible(p.control)&&automation.matches(p.control.condition)&&p.role.equals("PRIMARY")&&now-p.down<250&&p.maxDistance<radius(p.control)*.12f)automation.pulse(p.multiTouch?"MOUSE_RIGHT":"MOUSE_LEFT",now+80);
        cameraFireGesture.up(id,now,config.fireTapMs,config.fireSlop*Math.min(width,height));
        if(p!=null&&p.role.equals("PRIMARY")&&p.control.kind.equals("stick")){
            if(!p.transferred&&now>=p.down&&now-p.down<250&&p.maxDistance<radius(p.control)*.18f&&!p.click)taps.put(p.control.id,now);
            // Fire/click contacts depend on this camera/stick lifetime. Never transfer ownership.
            pointers.values().removeIf(other->other.control==p.control);
        }
        if(p!=null&&(p.control.kind.equals("button")||p.control.kind.equals("combo"))){
            ControllerConfig.Control c=p.control;boolean tap=now>=p.down&&now-p.down<250&&p.maxDistance<radius(c)*.3f;
            if(tap&&c.gesture.equals("TAP")){if(c.action.equals("BUTTONS"))pulses.put(c,now+60);else action(c);}
            if(tap&&c.gesture.equals("DOUBLE_TAP")&&!p.activated)taps.put(c.id,now);
        }
        if(p!=null&&p.control.kind.equals("trigger")&&p.control.bit==1&&config.rtDoubleTapNitro
                &&now>=p.down&&now-p.down<250&&p.maxDistance<radius(p.control)*.5f)taps.put(p.control.id,now);
        update(now);
    }
    public void cancel(int id,long now){
        automation.cancel();
        Pointer p=pointers.remove(id);if(p!=null&&p.role.equals("PRIMARY")&&p.control.kind.equals("stick"))pointers.values().removeIf(other->other.control==p.control);
        cameraFireGesture.reset();taps.clear();toggled.clear();pulses.clear();nitroUntil=0;pendingAction=null;aimLatched=false;movementHeld=false;update(now);
    }
    public void release(){automation.clear();cameraFireGesture.reset();pointers.clear();taps.clear();toggled.clear();pulses.clear();nitroUntil=0;pendingAction=null;modifier=false;chordHeld=false;movementToggleRequested=false;temporaryLT=0;aimLatched=false;movementHeld=false;heldX=heldY=gyroX=gyroY=0;gyroAt=0;state.clear();sourceState.clear();}
    public void update(long now){
        cameraFireGesture.expire(now,config.fireGapMs);automation.begin(now,state);state.clear();sourceState.clear();
        modifier=false;boolean leftStickCaptured=false,rightStickCaptured=false;for(Pointer p:pointers.values()){if(p.control.kind.equals("modifier")||p.control.action.equals("MODIFIER")&&gestureActive(p,now))modifier=true;if(p.role.equals("PRIMARY")&&p.control.kind.equals("stick")){if(p.control.bit==0)leftStickCaptured=true;else if(p.control.bit==1)rightStickCaptured=true;}}
        pulses.entrySet().removeIf(entry->now>=entry.getValue());
        if(nitroUntil>0&&now>=nitroUntil)nitroUntil=0;
        if(nitroUntil>now)state.buttons|=1<<PadState.A;
        for(ControllerConfig.Control c:toggled)emit(c);for(ControllerConfig.Control c:pulses.keySet())emit(c);
        if(movementHeld){state.lx=heldX;state.ly=heldY;}
        if(aimLatched)state.lt=1;state.lt=Math.max(state.lt,temporaryLT);
        for(Map.Entry<Integer,Pointer> contact:pointers.entrySet()){
            Pointer p=contact.getValue();
            ControllerConfig.Control c=p.control;float r=Math.max(1,radius(c));float x=(p.x-p.cx)/r,y=(p.y-p.cy)/r;
            if(!visible(c)||!automation.matches(c.condition))continue;
            if(p.role.equals("FIRE")){state.rt=1;continue;}
            if(p.role.equals("CLICK")){state.buttons|=1<<(c.bit==0?PadState.L3:PadState.R3);continue;}
            if(gestureActive(p,now))automation.activate(c);
            if(!c.kind.equals("button")&&!c.kind.equals("combo")&&!c.kind.equals("trigger")&&(!c.binding.isEmpty()||macroOverrides(c))){emit(c);continue;}
            switch(c.kind){
                case "stick":
                    float dead=c.deadzone<0?config.deadzone:c.deadzone;
                    float sensitivity=c.sensitivity<0?config.sensitivity:c.sensitivity;
                    float[] stick=PadState.stick(x,y,dead,1);
                    float length=(float)Math.hypot(stick[0],stick[1]);
                    if(length>0){float gain=PadState.clamp((float)Math.pow(length,c.response)*sensitivity,0,1)/length;stick[0]*=gain;stick[1]*=gain;}
                    if(c.bit==1&&config.mode(c).equals("TOUCHPAD")){
                        if(config.sameThumbFire&&cameraFireGesture.firing(contact.getKey()))state.rt=1;
                        stick[0]=now-p.moved>60?0:camera(p.vx,config.cameraX);
                        stick[1]=now-p.moved>60?0:camera(p.vy,config.cameraY);
                    }
                    if(c.invertX)stick[0]=-stick[0];if(c.invertY)stick[1]=-stick[1];
                    if(c.bit==0){sourceState.lx=stick[0];sourceState.ly=stick[1];}else{sourceState.rx=stick[0];sourceState.ry=stick[1];}
                    if(automation.directional(c,stick[0],stick[1]))break;
                    if(c.bit==0){
                        if(!movementHeld){state.lx=stick[0];state.ly=stick[1];}
                        if(config.integrated){
                            float distance=(float)Math.hypot(x,y);
                            // Keep the selected pedal while steering through the horizontal axis.
                            // Center resets it; a deliberate opposite vertical motion changes pedal.
                            if(distance<=config.triggerDeadzone)p.pedalDirection=0;
                            else if(y < -config.triggerDeadzone)p.pedalDirection=-1;
                            else if(y > config.triggerDeadzone)p.pedalDirection=1;
                            float v=integratedTrigger(distance,config.triggerDeadzone,config.triggerSensitivity,config.curve);
                            triggers(p.pedalDirection>0?v:0,p.pedalDirection<0?v:0);
                            if(!config.preserveY)state.ly=0;
                        }
                    }else{state.rx=stick[0];state.ry=stick[1];}
                    if(!(config.sameThumbFire&&c.bit==1&&config.mode(c).equals("TOUCHPAD"))&&!p.transferred&&config.clickGesture.equals("LONG_PRESS")&&now-p.down>=450&&p.maxDistance<r*.15f)p.click=true;
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
                    if(c.bit==0)sourceState.lt=Math.max(sourceState.lt,v);else sourceState.rt=Math.max(sourceState.rt,v);
                    if(macroOverrides(c)){if(v>.05f)automation.activate(c);break;}
                    if(!c.binding.isEmpty()){automation.bindScaled(v>.05f?c.binding:"",v);break;}
                    triggers(c.bit==0?v:0,c.bit==1?v:0);
                    boolean macroSteering=false,hasMacro=false;
                    for(ControllerConfig.MacroAssignment a:config.macroAssignments)if(a.controlId.equals(c.id)){ControllerConfig.Macro m=config.macro(a.macroId);if(m!=null){hasMacro=true;if(Automation.containsSteer(m.program))macroSteering=true;}}
                    if(hasMacro)automation.activate(c);
                    if(macroSteering){float steerX=PadState.clamp((p.x-p.startX)/(Math.max(.001f,config.travel)*width)*config.triggerSensitivity,-1,1),steerY=PadState.clamp((p.y-p.startY)/(Math.max(.001f,config.travel)*height)*config.triggerSensitivity,-1,1);automation.vector(c.id,steerX,steerY);}
                    else {boolean triggerSteersLeft=c.bit==0&&config.ltSteering,triggerSteersRight=c.bit==1&&config.rtSteering;if((triggerSteersLeft&&!leftStickCaptured||triggerSteersRight&&!rightStickCaptured)){float steerX=PadState.clamp((p.x-p.startX)/(Math.max(.001f,config.travel)*width)*config.triggerSensitivity,-1,1),steerY=PadState.clamp((p.y-p.startY)/(Math.max(.001f,config.travel)*height)*config.triggerSensitivity,-1,1);float[] triggerStick=PadState.stick(steerX,steerY,config.deadzone,config.sensitivity);if(triggerSteersLeft&&Math.hypot(triggerStick[0],triggerStick[1])>=Math.hypot(state.lx,state.ly)){state.lx=triggerStick[0];state.ly=triggerStick[1];}else if(triggerSteersRight&&Math.hypot(triggerStick[0],triggerStick[1])>=Math.hypot(state.rx,state.ry)){state.rx=triggerStick[0];state.ry=triggerStick[1];}}}
                    break;
                case "trackpad":break;
                case "fire":state.rt=1;break;
                case "aim":if(config.aimMode.equals("HOLD"))state.lt=1;break;
                case "hold":case "modifier":case "next":case "previous":case "layoutHold":break;
                case "dpad":if(Math.hypot(x,y)>.2)sourceState.hat=((int)Math.round(Math.atan2(x,-y)/(Math.PI/4))+8)%8;if(automation.directional(c,PadState.clamp(x,-1,1),PadState.clamp(y,-1,1)))break;if(Math.hypot(x,y)>.2)state.hat=((int)Math.round(Math.atan2(x,-y)/(Math.PI/4))+8)%8;break;
                default:
                    
                    boolean active=gestureActive(p,now);
                    if(active){if(c.action.equals("BUTTONS"))emit(c);else if(!c.action.equals("MODIFIER")&&!p.actionSent){action(c);p.actionSent=true;}if(!c.gesture.equals("SECOND_FINGER"))p.activated=true;}
                    break;
            }
        }
        for(ControllerConfig.Control c:config.controls)if(!c.independentMappings&&visible(c)&&automation.source(c.source)&&automation.matches(c.condition)){automation.activate(c);if(!macroOverrides(c)&&!c.binding.isEmpty())automation.bindScaled(c.binding,hardwareResponse(automation.sourceAmount(c.source),c.deadzone<0?0:c.deadzone,c.sensitivity<0?1:c.sensitivity,c.response));else if(!macroOverrides(c)&&c.kind.equals("trigger"))triggers(c.bit==0?hardwareResponse(automation.sourceAmount(c.source),c.deadzone<0?0:c.deadzone,c.sensitivity<0?1:c.sensitivity,c.response):0,c.bit==1?hardwareResponse(automation.sourceAmount(c.source),c.deadzone<0?0:c.deadzone,c.sensitivity<0?1:c.sensitivity,c.response):0);else emit(c); }
        for(ControllerConfig.Control c:config.controls)if(!c.independentMappings&&visible(c)&&automation.matches(c.condition)){
            float x=automation.sourceAmount(c.sourceRight)-automation.sourceAmount(c.sourceLeft);
            float y=automation.sourceAmount(c.sourceDown)-automation.sourceAmount(c.sourceUp);
            if(x==0&&y==0)continue;
            float dead=c.deadzone<0?config.deadzone:c.deadzone,gain=c.sensitivity<0?1:c.sensitivity;
            x=hardwareResponse(x,dead,gain,c.response)*(c.invertX?-1:1);
            y=hardwareResponse(y,dead,gain,c.response)*(c.invertY?-1:1);
            automation.activate(c);if(macroOverrides(c))continue;
            if(automation.directional(c,x,y))continue;
            if(c.kind.equals("stick")){
                String axis=c.bit==0?"L":"R";
                automation.bind(axis+"X="+x+"+"+axis+"Y="+y);
            }else if(c.kind.equals("dpad")){
                if(x<-.15f)automation.bind("DPAD_LEFT");if(x>.15f)automation.bind("DPAD_RIGHT");
                if(y<-.15f)automation.bind("DPAD_UP");if(y>.15f)automation.bind("DPAD_DOWN");
            }else if(c.kind.equals("trackpad"))automation.bind("MOUSE_X="+(x*1000)+"+MOUSE_Y="+(y*1000));
            else if(c.kind.equals("unified"))triggers(Math.max(0,-y),Math.max(0,y));
        }
        for(ControllerConfig.Control c:config.controls)if(c.independentMappings&&visible(c)&&automation.matches(c.condition)){
            float x=0,y=0;boolean activated=false;
            for(ControllerConfig.Mapping m:c.mappings){float amount=hardwareResponse(automation.sourceAmount(m.source),m.deadzone,m.sensitivity,m.response);if(amount<=0)continue;
                activated=true;float signed=m.inverted?-amount:amount;
                if(!m.output.isEmpty()){automation.bindScaled(m.output,signed);continue;}
                switch(m.direction){case "left":x-=signed;break;case "right":x+=signed;break;case "up":y-=signed;break;case "down":y+=signed;break;default:if(c.kind.equals("trigger"))triggers(c.bit==0?amount:0,c.bit==1?amount:0);else emit(c);}
            }
            if(activated)automation.activate(c);x=PadState.clamp(x,-1,1);y=PadState.clamp(y,-1,1);
            if(c.kind.equals("stick")){String axis=c.bit==0?"L":"R";automation.bind(axis+"X="+x+"+"+axis+"Y="+y);}
            else if(c.kind.equals("dpad")){if(x<-.15f)automation.bind("DPAD_LEFT");if(x>.15f)automation.bind("DPAD_RIGHT");if(y<-.15f)automation.bind("DPAD_UP");if(y>.15f)automation.bind("DPAD_DOWN");}
            else if(c.kind.equals("trackpad"))automation.bind("MOUSE_X="+(x*1000)+"+MOUSE_Y="+(y*1000));
            else if(c.kind.equals("unified"))triggers(Math.max(0,-y),Math.max(0,y));
        }
        automation.finish(config);
        if(config.gyroEnabled&&gyroAt>0&&now>=gyroAt&&now-gyroAt<100&&(!config.gyroAimOnly||state.lt>0)){state.rx=PadState.clamp(state.rx+gyroX,-1,1);state.ry=PadState.clamp(state.ry+gyroY,-1,1);}
        if(movementToggleRequested){movementHeld=!movementHeld;heldX=state.lx;heldY=state.ly;movementToggleRequested=false;}
        boolean chord=Integer.bitCount(config.switchButtons)>=2&&(state.buttons&config.switchButtons)==config.switchButtons;
        if(chord&&!chordHeld)pendingAction="NEXT:";chordHeld=chord;
        // Apply aim after all trigger sources, so pointer ordering cannot change sensitivity.
        if(state.lt>0||modifier){state.rx*=config.aimSensitivity;state.ry*=config.aimSensitivity;}
    }
    private static float hardwareResponse(float value,float dead,float sensitivity,float response){
        float amount=Math.abs(value);if(amount<=dead)return 0;
        return Math.copySign(PadState.clamp((float)Math.pow((amount-dead)/Math.max(.01f,1-dead),response)*sensitivity,0,1),value);
    }
    private boolean gestureActive(Pointer p,long now){
        ControllerConfig.Control c=p.control;float r=Math.max(1,radius(c));
        if(c.gesture.equals("SECOND_FINGER")){int count=0;for(Pointer other:pointers.values())if(other.control==c)count++;return count>=2;}
        if(c.gesture.equals("LONG_PRESS")&&now-p.down>=450&&p.maxDistance<r*.3f)p.activated=true;
        if(c.gesture.equals("SWIPE")&&p.maxDistance>r*.7f)p.activated=true;
        return c.gesture.equals("HOLD")||p.activated;
    }
    // Legacy programs remain as migration backup; only a retained owner assignment
    // suppresses the old button action once its definition has been migrated.
    private boolean macroOverrides(ControllerConfig.Control c){
        if(c.program.isEmpty())return false;
        for(ControllerConfig.Macro macro:config.macros)if(c.id.equals(macro.legacyOwner)){
            for(ControllerConfig.MacroAssignment assignment:config.macroAssignments)
                if(assignment.controlId.equals(c.id)&&assignment.macroId.equals(macro.id))return true;
            return false;
        }
        return true;
    }
    private void emit(ControllerConfig.Control c){if(!visible(c)||!automation.matches(c.condition))return;if(c.kind.equals("button")||c.kind.equals("combo")){sourceState.buttons|=modifier&&c.modifiedButtons>=0?c.modifiedButtons:c.buttons<0?1<<c.bit:c.buttons;sourceState.lt=Math.max(sourceState.lt,c.outputLT);sourceState.rt=Math.max(sourceState.rt,c.outputRT);}else if(c.kind.equals("fire"))sourceState.rt=1;else if(c.kind.equals("aim"))sourceState.lt=1;automation.activate(c);if(macroOverrides(c)){automation.activate(c);return;}if(!c.binding.isEmpty()){automation.bind(c.binding);return;}if(!c.kind.equals("button")&&!c.kind.equals("combo"))return;state.buttons|=modifier&&c.modifiedButtons>=0?c.modifiedButtons:c.buttons<0?1<<c.bit:c.buttons;triggers(c.outputLT,c.outputRT);}
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
