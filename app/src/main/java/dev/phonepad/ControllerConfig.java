package dev.phonepad;

import java.util.*;

/** Resolution-independent controller configuration. Geometry uses safe-surface fractions. */
public final class ControllerConfig {
    public String name="Default", base="Default", labels="XBOX", leftMode="FLOATING", rightMode="FIXED", clickGesture="DOUBLE_TAP";
    public String game="General",holdLayout="",aimLayout="",quickStyle="LIST";
    public int switchButtons=0;
    public String triggerMode="EDGE", curve="LINEAR";
    public boolean gyroEnabled=false,gyroAimOnly=true,gyroInvertX=false,gyroInvertY=false;
    public float gyroSensitivityX=0,gyroSensitivityY=0; // Zero inherits the legacy shared gain.
    public float gyroGainX(){return gyroSensitivityX>0?gyroSensitivityX:gyroSensitivity;}
    public float gyroGainY(){return gyroSensitivityY>0?gyroSensitivityY:gyroSensitivity;}
    public float gyroSensitivity=.6f,gyroSmoothing=.35f,gyroDeadzone=.015f;
    public boolean edgeSwitch=false,transferSticks=false;
    public boolean integrated=false, preserveY=false, hideTriggers=true, ltSteering=false, rtSteering=false, grid=true, snap=false;
    public float deadzone=.08f, sensitivity=1, triggerDeadzone=.12f, triggerSensitivity=1, travel=.16f, gridSize=.025f;
    public int haptics=1;
    public boolean gameplayLock=false, sameThumbFire=false;
    public boolean rtDoubleTapNitro=false;
    public float fireTapMs=180, fireGapMs=280, fireSlop=.025f;
    public String unifiedOrientation="VERTICAL", unifiedCurve="LINEAR", aimMode="HOLD";
    public boolean unifiedFloating=true, unifiedInvert=false, cameraFire=false;
    public float unifiedDeadzone=.08f, unifiedTravel=.18f, unifiedSensitivity=1;
    public float cameraX=1, cameraY=1, cameraCurve=1, cameraSmoothing=0, aimSensitivity=1;

    /** Additive editor data; legacy scalar fields remain readable. */
    public static final class Mapping {
        public String id=UUID.randomUUID().toString(), source="", direction="", output="";
        public float deadzone=0, sensitivity=1, response=1;
        public boolean inverted=false;
        public Mapping copy(){Mapping m=new Mapping();m.id=id;m.source=source;m.direction=direction;m.output=output;m.deadzone=deadzone;m.sensitivity=sensitivity;m.response=response;m.inverted=inverted;return m;}
    }
    public static final class Macro {
        public String id=UUID.randomUUID().toString(), name="New macro", program="", legacyOwner="", condition="", builtin="";
        public boolean enabled=true, eventDriven=false;
        public Macro copy(){Macro m=new Macro();m.id=id;m.name=name;m.program=program;m.legacyOwner=legacyOwner;m.condition=condition;m.builtin=builtin;m.enabled=enabled;m.eventDriven=eventDriven;return m;}
    }
    public static final class MacroAssignment {
        public String id=UUID.randomUUID().toString(), macroId="", controlId="", trigger="PRESS";
        public boolean enabled=true;
        public MacroAssignment copy(){MacroAssignment a=new MacroAssignment();a.id=id;a.macroId=macroId;a.controlId=controlId;a.trigger=trigger;a.enabled=enabled;return a;}
    }
    public final List<Macro> macros=new ArrayList<>();
    public final List<MacroAssignment> macroAssignments=new ArrayList<>();
    public Macro macro(String id){for(Macro m:macros)if(m.id.equals(id))return m;return null;}
    public MacroAssignment assign(String macroId,String controlId,String trigger){
        if(macro(macroId)==null||get(controlId)==null)throw new IllegalArgumentException("Choose a macro and control");
        MacroAssignment a=new MacroAssignment();a.macroId=macroId;a.controlId=controlId;a.trigger=trigger;macroAssignments.add(a);return a;
    }
    public void migrateMacros(){
        for(Control c:controls)if(!c.program.isEmpty()){
            boolean exists=false;for(Macro m:macros)if(m.legacyOwner.equals(c.id))exists=true;
            if(exists)continue;
            Macro m=new Macro();m.id="legacy:"+c.id;m.legacyOwner=c.id;m.name=c.title();m.program=c.program;m.condition=c.condition;m.eventDriven=c.eventDriven;macros.add(m);
            MacroAssignment own=assign(m.id,c.id,c.macroMode);own.id=UUID.nameUUIDFromBytes((m.id+":"+c.id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();for(String id:new LinkedHashSet<>(Arrays.asList(c.macroTriggers.split(","))))if(!id.equals(c.id)&&get(id)!=null){MacroAssignment a=assign(m.id,id,c.macroMode);a.id=UUID.nameUUIDFromBytes((m.id+":"+id).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();}
        }
    }
    /** Keep legacy toggles readable while migrating their behavior to editable macro definitions. */
    public void ensureBuiltinMacros(){
        Macro nitro=null,steer=null,steerRt=null;
        for(Macro m:macros){if(m.id.equals("builtin:double_tap_nitro")||m.builtin.equals("double_tap_nitro")){nitro=m;m.id="builtin:double_tap_nitro";m.builtin="double_tap_nitro";}if(m.id.equals("builtin:steering_lt")||m.builtin.equals("steering_lt")){steer=m;m.id="builtin:steering_lt";m.builtin="steering_lt";}if(m.id.equals("builtin:steering_rt")||m.builtin.equals("steering_rt")){steerRt=m;m.id="builtin:steering_rt";m.builtin="steering_rt";}}
        if(nitro==null){nitro=new Macro();nitro.id="builtin:double_tap_nitro";nitro.builtin="double_tap_nitro";nitro.name="Double-tap nitro";nitro.program="press A\nwait 3000\nrelease A";macros.add(nitro);}
        if(steer==null){steer=new Macro();steer.id="builtin:steering_lt";steer.builtin="steering_lt";steer.name="Steering · LT";steer.program="steer LEFT";macros.add(steer);}if(steerRt==null){steerRt=new Macro();steerRt.id="builtin:steering_rt";steerRt.builtin="steering_rt";steerRt.name="Steering · RT";steerRt.program="steer RIGHT";macros.add(steerRt);}
        if(rtDoubleTapNitro){Control rt=standard("RT");if(!hasAssignment(nitro.id,rt.id))assign(nitro.id,rt.id,"DOUBLE_TAP");rtDoubleTapNitro=false;}
        if(ltSteering){Control lt=standard("LT");if(!hasAssignment(steer.id,lt.id))assign(steer.id,lt.id,"HOLD");ltSteering=false;}
        if(rtSteering){Control rt=standard("RT");if(!hasAssignment(steerRt.id,rt.id))assign(steerRt.id,rt.id,"HOLD");rtSteering=false;}
    }
    private boolean hasAssignment(String macroId,String controlId){for(MacroAssignment a:macroAssignments)if(a.macroId.equals(macroId)&&a.controlId.equals(controlId))return true;return false;}
    public Macro duplicateMacro(String id){Macro m=macro(id).copy();m.id=UUID.randomUUID().toString();m.legacyOwner="";m.builtin="";m.name+=" copy";macros.add(m);return m;}
    public void deleteMacro(String id){Macro m=macro(id);if(m!=null&&!m.builtin.isEmpty())return;if(m!=null&&!m.legacyOwner.isEmpty()){Control c=get(m.legacyOwner);if(c!=null){c.program="";c.macroTriggers="";}}macros.removeIf(v->v.id.equals(id));macroAssignments.removeIf(a->a.macroId.equals(id));}
    public final List<Control> controls=new ArrayList<>();
    public static final class Control {
        public final String id,kind; public final int bit;
        public final List<Mapping> mappings=new ArrayList<>();
        public boolean independentMappings=false;
        public Mapping mapping(String id){for(Mapping m:mappings)if(m.id.equals(id))return m;return null;}
        /** Import each alternative once; subsequent edits never touch control tuning. */
        public void migrateMappings(float inheritedDeadzone){
            if(independentMappings)return;independentMappings=true;
            String[] sources={source,sourceUp,sourceDown,sourceLeft,sourceRight},dirs={"","up","down","left","right"},outputs={binding,mapUp,mapDown,mapLeft,mapRight};
            for(int i=0;i<sources.length;i++)for(String token:sources[i].split("\\|"))if(!token.isEmpty()){
                Mapping m=new Mapping();m.source=token;m.direction=dirs[i];m.output=outputs[i];m.deadzone=deadzone<0?(i==0?0:inheritedDeadzone):deadzone;m.sensitivity=sensitivity<0?1:sensitivity;m.response=response;m.inverted=i==1||i==2?invertY:i>2&&invertX;mappings.add(m);
            }
        }
        public String label="";
        public String binding="",program="",condition="",visibleWhen="",source="",macroMode="PRESS",macroTriggers="";
        public boolean eventDriven=false;
        public String mapUp="",mapDown="",mapLeft="",mapRight="";
        // Optional physical inputs per direction. Empty fields preserve older layouts.
        public String sourceUp="",sourceDown="",sourceLeft="",sourceRight="";
        public String mode="",gesture="HOLD",action="BUTTONS",target="";
        public int modifiedButtons=-1;
        public float hitWidth=1,hitHeight=1;
        public float x,y,w,h,hitbox=1.15f,opacity=1;
        public float deadzone=-1,sensitivity=-1,response=1;
        public boolean haptic=true,fullscreen=false;
        public boolean visible=true,reverse=false,invertX=false,invertY=false;
        public int buttons=-1;
        public float outputLT=0,outputRT=0;
        Control(String id,String kind,int bit,float x,float y,float w,float h){this.id=id;this.kind=kind;this.bit=bit;this.x=x;this.y=y;this.w=w;this.h=h;}
        public String title(){return label.isEmpty()?(id.startsWith("control-")?kind.toUpperCase(Locale.ROOT):id):label;}
        public Control copy(){return copyAs(id);}
        public Control copyAs(String newId){Control c=new Control(newId,kind,bit,x,y,w,h);c.independentMappings=independentMappings;for(Mapping m:mappings){Mapping mapped=m.copy();if(!newId.equals(id))mapped.id=UUID.randomUUID().toString();c.mappings.add(mapped);}c.sourceUp=sourceUp;c.sourceDown=sourceDown;c.sourceLeft=sourceLeft;c.sourceRight=sourceRight;c.mapUp=mapUp;c.mapDown=mapDown;c.mapLeft=mapLeft;c.mapRight=mapRight;c.binding=binding;c.program=program;c.condition=condition;c.visibleWhen=visibleWhen;c.source=source;c.macroMode=macroMode;c.macroTriggers=macroTriggers;c.eventDriven=eventDriven;c.label=label;c.hitbox=hitbox;c.visible=visible;c.reverse=reverse;c.opacity=opacity;c.deadzone=deadzone;c.sensitivity=sensitivity;c.response=response;c.invertX=invertX;c.invertY=invertY;c.buttons=buttons;c.outputLT=outputLT;c.outputRT=outputRT;c.haptic=haptic;c.fullscreen=fullscreen;c.mode=mode;c.gesture=gesture;c.action=action;c.target=target;c.modifiedButtons=modifiedButtons;c.hitWidth=hitWidth;c.hitHeight=hitHeight;return c;}
    }
    public boolean backgroundCamera(Control c){return c.fullscreen&&(c.kind.equals("trackpad")||c.kind.equals("stick")&&c.bit==1&&mode(c).equals("TOUCHPAD"));}
    public Control get(String id){for(Control c:controls)if(c.id.equals(id))return c;return null;}
    public static final int MAX_CONTROLS=128;
    /** New instances retain their mapping but have an independent stable identity. */
    public Control duplicateControl(String id){
        Control original=get(id);
        if(original==null)throw new IllegalArgumentException("Unknown control");
        return addInstance(original);
    }
    public Control addInstance(Control template){
        if(controls.size()>=MAX_CONTROLS)throw new IllegalArgumentException("Use at most 128 controls per layout");
        Control c=template.copyAs("control-"+UUID.randomUUID());
        c.label=template.title();c.visible=true;
        c.x=PadState.clamp(c.x+.04f,c.w/2,1-c.w/2);
        c.y=PadState.clamp(c.y+.04f,Math.max(.1f,c.h/2),1-c.h/2);
        controls.add(c);return c;
    }
    public void deleteControl(String id){controls.removeIf(c->c.id.equals(id));macroAssignments.removeIf(a->a.controlId.equals(id));}
    /** Explicit shared-control settings can restore a deleted standard slot on Apply. */
    public Control standard(String id){
        Control c=get(id);if(c!=null)return c;
        if(controls.size()>=MAX_CONTROLS)throw new IllegalArgumentException("Use at most 128 controls per layout");
        c=preset("Default").get(id).copy();c.visible=false;controls.add(c);return c;
    }
    public static boolean validControl(String kind,int bit){
        if(kind.equals("button"))return bit>=0&&bit<16;
        if(kind.equals("stick")||kind.equals("trigger"))return bit==0||bit==1;
        return bit==0&&Arrays.asList("trackpad","combo","dpad","unified","fire","aim","hold","modifier","next","previous","layoutHold").contains(kind);
    }
    public String mode(Control c){return c.mode.isEmpty()?(c.kind.equals("trigger")?triggerMode:c.bit==0?leftMode:rightMode):c.mode;}
    public boolean shown(Control c){return c.visible && !(integrated&&hideTriggers&&c.kind.equals("trigger"));}
    private void add(String id,String kind,int bit,float x,float y,float w,float h){controls.add(new Control(id,kind,bit,x,y,w,h));}
    public static ControllerConfig preset(String name){
        ControllerConfig p=new ControllerConfig();p.name=p.base=name;
        p.add("LS","stick",0,.17f,.59f,.17f,.36f);p.add("RS","stick",1,.80f,.75f,.15f,.32f);
        p.add("D-PAD","dpad",0,.36f,.76f,.115f,.25f);
        p.add("LT","trigger",0,.055f,.26f,.08f,.24f);p.add("RT","trigger",1,.945f,.26f,.08f,.24f);
        p.add("LB","button",PadState.LB,.30f,.14f,.115f,.105f);p.add("RB","button",PadState.RB,.70f,.14f,.115f,.105f);
        p.add("A","button",PadState.A,.80f,.49f,.067f,.14f);p.add("B","button",PadState.B,.87f,.35f,.067f,.14f);
        p.add("X","button",PadState.X,.73f,.35f,.067f,.14f);p.add("Y","button",PadState.Y,.80f,.21f,.067f,.14f);
        p.add("VIEW","button",PadState.VIEW,.46f,.42f,.048f,.085f);p.add("MENU","button",PadState.MENU,.54f,.42f,.048f,.085f);
        p.add("HOME","button",PadState.GUIDE,.50f,.27f,.043f,.08f);p.add("SHARE","button",PadState.SHARE,.50f,.56f,.05f,.08f);p.get("SHARE").visible=false;
        p.add("LT/RT","unified",0,.50f,.72f,.10f,.32f);
        p.add("L3","button",PadState.L3,.10f,.88f,.06f,.10f);
        p.add("R3","button",PadState.R3,.90f,.88f,.06f,.10f);
        p.add("L3+R3","combo",0,.50f,.87f,.10f,.10f);
        p.get("L3+R3").buttons=(1<<PadState.L3)|(1<<PadState.R3);
        p.add("FIRE","fire",0,.92f,.60f,.09f,.20f);
        p.add("MODIFIER","modifier",0,.50f,.55f,.10f,.10f);
        p.add("NEXT","next",0,.57f,.12f,.07f,.08f);
        p.add("PREVIOUS","previous",0,.43f,.12f,.07f,.08f);
        p.add("LAYOUT HOLD","layoutHold",0,.50f,.12f,.12f,.08f);
        p.add("AIM","aim",0,.62f,.40f,.08f,.12f);
        p.add("LS HOLD","hold",0,.25f,.88f,.10f,.10f);
        for(String id:new String[]{"LT/RT","L3","R3","L3+R3","AIM","LS HOLD","NEXT","PREVIOUS","LAYOUT HOLD","MODIFIER","FIRE"})p.get(id).visible=false;
        p.get("LS").hitbox=1.45f;p.get("RS").hitbox=1.25f;
        if(name.equals("FPS")){p.rightMode="TOUCHPAD";p.get("RS").w=.26f;p.get("RS").h=.38f;p.get("RS").x=.78f;}
        if(name.equals("Racing")){p.integrated=true;p.leftMode="FIXED";p.get("LS").x=.23f;p.get("LS").w=.24f;p.get("LS").h=.5f;p.get("D-PAD").visible=false;}
        switch(name){
            case "Classic Xbox":p.leftMode="FIXED";break;
            case "Compact Xbox":for(Control c:p.controls){c.x=.5f+(c.x-.5f)*.78f;c.w*=.85f;c.h*=.85f;}break;
            case "Minimal Controller":case "Retro Controller":
                for(Control c:p.controls)c.visible=Arrays.asList("D-PAD","A","B","MENU").contains(c.id);
                p.get("D-PAD").x=.20f;p.get("D-PAD").y=.60f;p.get("D-PAD").w=.20f;p.get("D-PAD").h=.45f;
                if(name.equals("Retro Controller")){p.get("VIEW").visible=true;p.get("A").y=p.get("B").y=.6f;}break;
            case "Two-Thumb FPS / GTA On Foot":
                p.gameplayLock=true;p.sameThumbFire=true;p.rightMode="TOUCHPAD";p.aimMode="TOGGLE";p.aimSensitivity=.45f;
                for(Control c:p.controls)c.visible=Arrays.asList("LS","RS","AIM","A","B","X","Y","MENU","LS HOLD").contains(c.id);
                p.get("LS").x=.20f;p.get("LS").y=.62f;p.get("LS").w=.25f;p.get("LS").h=.50f;
                p.get("RS").x=.77f;p.get("RS").y=.65f;p.get("RS").w=.40f;p.get("RS").h=.53f;
                p.get("AIM").x=.54f;p.get("AIM").y=.55f;p.get("LS HOLD").x=.44f;p.get("LS HOLD").y=.87f;p.get("LS HOLD").w=.11f;p.get("LS HOLD").h=.08f;
                p.get("A").x=.64f;p.get("B").x=.74f;p.get("X").x=.84f;p.get("Y").x=.94f;
                for(String id:new String[]{"A","B","X","Y"}){p.get(id).y=.24f;p.get(id).w=.06f;p.get(id).h=.12f;}break;
            case "FPS Standard":p.triggerMode="DIGITAL";p.get("L3").visible=true;p.aimSensitivity=.55f;break;
            case "FPS Touchpad":case "FPS Precision":case "GTA Combat":
                p.rightMode="TOUCHPAD";p.cameraFire=true;p.triggerMode="DIGITAL";p.get("RS").w=.32f;p.get("RS").h=.42f;p.get("RS").x=.76f;p.get("AIM").visible=true;p.aimSensitivity=.4f;
                if(name.equals("FPS Precision")){p.cameraX=p.cameraY=.65f;p.cameraSmoothing=.25f;p.aimSensitivity=.25f;}break;
            case "Third-Person Action":p.get("A").w=.09f;p.get("A").h=.19f;p.get("L3+R3").visible=true;break;
            case "GTA Walking":p.get("LS HOLD").visible=true;p.get("A").x=.88f;p.get("A").y=.60f;break;
            case "GTA Driving":case "Racing One-Stick":
                p.integrated=true;p.leftMode="FIXED";p.get("LS").x=.23f;p.get("LS").w=.24f;p.get("LS").h=.5f;p.get("D-PAD").visible=false;
                if(name.equals("Racing One-Stick"))p.get("RS").visible=false;break;
            case "Racing Standard":p.triggerMode="VERTICAL";p.get("LT").h=p.get("RT").h=.42f;p.get("LT").y=p.get("RT").y=.48f;break;
            case "Racing Unified Trigger":p.get("LT/RT").visible=true;p.get("LT/RT").x=.78f;p.get("LT/RT").y=.62f;p.get("LT/RT").h=.48f;p.get("A").x=.92f;p.get("A").y=.72f;p.get("B").x=.92f;p.get("B").y=.43f;p.get("X").x=.64f;p.get("X").y=.30f;p.get("Y").x=.78f;p.get("Y").y=.21f;p.get("LT").visible=p.get("RT").visible=p.get("RS").visible=false;p.leftMode="FIXED";break;
            case "Platformer":p.get("RS").visible=false;p.get("D-PAD").x=.20f;p.get("D-PAD").y=.65f;p.get("LS").visible=false;p.get("A").w=.10f;p.get("A").h=.22f;break;
            case "Fighting Game":p.get("LS").visible=p.get("RS").visible=false;p.get("D-PAD").x=.20f;p.get("D-PAD").y=.6f;p.get("D-PAD").w=.22f;p.get("D-PAD").h=.48f;String[] row={"X","Y","RB","A","B","RT"};for(int i=0;i<row.length;i++){Control c=p.get(row[i]);c.x=.62f+(i%3)*.12f;c.y=.4f+(i/3)*.27f;c.w=.09f;c.h=.18f;}p.triggerMode="DIGITAL";break;
            case "Twin-Stick Shooter":p.leftMode=p.rightMode="FLOATING";p.get("LS").x=.22f;p.get("RS").x=.78f;p.get("LS").y=p.get("RS").y=.65f;p.get("LS").w=p.get("RS").w=.22f;p.get("LS").h=p.get("RS").h=.46f;break;
            case "Flight Controller":p.leftMode=p.rightMode="FIXED";p.get("LS").deadzone=p.get("RS").deadzone=.03f;p.get("LS").response=p.get("RS").response=1.7f;p.get("RS").invertY=true;break;
            case "Tank / Vehicle":p.leftMode=p.rightMode="FIXED";p.get("LS").y=p.get("RS").y=.62f;p.get("L3+R3").visible=true;p.triggerMode="DIGITAL";break;
            case "One-Handed Left":case "One-Handed Right":
                boolean right=name.equals("One-Handed Right");for(Control c:p.controls)c.visible=Arrays.asList("LS","A","B","MENU").contains(c.id);
                p.get("LS").x=right?.76f:.24f;p.get("LS").y=.70f;p.get("A").x=right?.90f:.10f;p.get("A").y=.35f;p.get("B").x=right?.72f:.28f;p.get("B").y=.30f;p.get("MENU").x=right?.88f:.12f;break;
            case "Accessibility / Large Buttons":
                for(Control c:p.controls)c.visible=Arrays.asList("LS","A","B","MENU").contains(c.id);p.get("LS").w=.28f;p.get("LS").h=.58f;p.get("LS").x=.23f;for(String id:new String[]{"A","B"}){p.get(id).w=.14f;p.get(id).h=.30f;}p.get("A").x=.70f;p.get("A").y=.65f;p.get("B").x=.88f;p.get("B").y=.35f;break;
            case "Custom Blank Layout":for(Control c:p.controls)c.visible=false;break;
        }
        return p;
    }
    /** Expand center placement vertically while retaining control dimensions and shapes. */
    public void fitHeight(){
        float first=1,last=0;int count=0;
        for(Control c:controls)if(shown(c)){first=Math.min(first,c.y);last=Math.max(last,c.y);count++;}
        if(count<2||last-first<.001f)return;
        float lo=0,hi=1024;
        for(int i=0;i<40;i++){
            float factor=(lo+hi)/2,min=Float.MAX_VALUE,max=-Float.MAX_VALUE;
            for(Control c:controls)if(shown(c)){min=Math.min(min,c.y*factor-c.h/2);max=Math.max(max,c.y*factor+c.h/2);}
            if(max-min>.9f)hi=factor;else lo=factor;
        }
        float factor=(lo+hi)/2,min=Float.MAX_VALUE;
        for(Control c:controls)if(shown(c))min=Math.min(min,c.y*factor-c.h/2);
        for(Control c:controls)if(shown(c))c.y=c.y*factor+.085f-min;
    }
    public ControllerConfig copy(){ControllerConfig p=new ControllerConfig();p.name=name;p.base=base;p.gameplayLock=gameplayLock;p.sameThumbFire=sameThumbFire;p.rtDoubleTapNitro=rtDoubleTapNitro;p.fireTapMs=fireTapMs;p.fireGapMs=fireGapMs;p.fireSlop=fireSlop;p.game=game;p.holdLayout=holdLayout;p.aimLayout=aimLayout;p.quickStyle=quickStyle;p.switchButtons=switchButtons;p.edgeSwitch=edgeSwitch;p.transferSticks=transferSticks;p.gyroEnabled=gyroEnabled;p.gyroAimOnly=gyroAimOnly;p.gyroInvertX=gyroInvertX;p.gyroInvertY=gyroInvertY;p.gyroSensitivity=gyroSensitivity;p.gyroSensitivityX=gyroSensitivityX;p.gyroSensitivityY=gyroSensitivityY;p.gyroSmoothing=gyroSmoothing;p.gyroDeadzone=gyroDeadzone;p.labels=labels;p.leftMode=leftMode;p.rightMode=rightMode;p.clickGesture=clickGesture;p.triggerMode=triggerMode;p.curve=curve;p.integrated=integrated;p.preserveY=preserveY;p.hideTriggers=hideTriggers;p.ltSteering=ltSteering;p.rtSteering=rtSteering;p.grid=grid;p.snap=snap;p.deadzone=deadzone;p.sensitivity=sensitivity;p.triggerDeadzone=triggerDeadzone;p.triggerSensitivity=triggerSensitivity;p.travel=travel;p.gridSize=gridSize;p.haptics=haptics;p.unifiedOrientation=unifiedOrientation;p.unifiedCurve=unifiedCurve;p.aimMode=aimMode;p.unifiedFloating=unifiedFloating;p.unifiedInvert=unifiedInvert;p.cameraFire=cameraFire;p.unifiedDeadzone=unifiedDeadzone;p.unifiedTravel=unifiedTravel;p.unifiedSensitivity=unifiedSensitivity;p.cameraX=cameraX;p.cameraY=cameraY;p.cameraCurve=cameraCurve;p.cameraSmoothing=cameraSmoothing;p.aimSensitivity=aimSensitivity;for(Control c:controls)p.controls.add(c.copy());for(Macro m:macros)p.macros.add(m.copy());for(MacroAssignment a:macroAssignments)p.macroAssignments.add(a.copy());return p;}
}
