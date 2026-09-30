package dev.phonepad;

import java.util.*;

/** Resolution-independent controller configuration. Geometry uses safe-surface fractions. */
public final class ControllerConfig {
    public String name="Default", base="Default", labels="XBOX", leftMode="FLOATING", rightMode="FIXED", clickGesture="DOUBLE_TAP";
    public String game="General",holdLayout="",aimLayout="",quickStyle="LIST";
    public int switchButtons=0;
    public String triggerMode="EDGE", curve="LINEAR";
    public boolean gyroEnabled=false,gyroAimOnly=true,gyroInvertX=false,gyroInvertY=false;
    public float gyroSensitivity=.6f,gyroSmoothing=.35f,gyroDeadzone=.015f;
    public boolean edgeSwitch=false;
    public boolean integrated=false, preserveY=false, hideTriggers=true, grid=true, snap=false;
    public float deadzone=.08f, sensitivity=1, triggerDeadzone=.12f, triggerSensitivity=1, travel=.16f, gridSize=.025f;
    public int haptics=1;
    public String unifiedOrientation="VERTICAL", unifiedCurve="LINEAR", aimMode="HOLD";
    public boolean unifiedFloating=true, unifiedInvert=false, cameraFire=false;
    public float unifiedDeadzone=.08f, unifiedTravel=.18f, unifiedSensitivity=1;
    public float cameraX=1, cameraY=1, cameraCurve=1, cameraSmoothing=0, aimSensitivity=1;

    public final List<Control> controls=new ArrayList<>();
    public static final class Control {
        public final String id,kind; public final int bit;
        public String mode="",gesture="HOLD",action="BUTTONS",target="";
        public int modifiedButtons=-1;
        public float hitWidth=1,hitHeight=1;
        public float x,y,w,h,hitbox=1.15f,opacity=1;
        public float deadzone=-1,sensitivity=-1,response=1;
        public boolean haptic=true;
        public boolean visible=true,reverse=false,invertX=false,invertY=false;
        public int buttons=-1;
        public float outputLT=0,outputRT=0;
        Control(String id,String kind,int bit,float x,float y,float w,float h){this.id=id;this.kind=kind;this.bit=bit;this.x=x;this.y=y;this.w=w;this.h=h;}
        public Control copy(){Control c=new Control(id,kind,bit,x,y,w,h);c.hitbox=hitbox;c.visible=visible;c.reverse=reverse;c.opacity=opacity;c.deadzone=deadzone;c.sensitivity=sensitivity;c.response=response;c.invertX=invertX;c.invertY=invertY;c.buttons=buttons;c.outputLT=outputLT;c.outputRT=outputRT;c.haptic=haptic;c.mode=mode;c.gesture=gesture;c.action=action;c.target=target;c.modifiedButtons=modifiedButtons;c.hitWidth=hitWidth;c.hitHeight=hitHeight;return c;}
    }
    public Control get(String id){for(Control c:controls)if(c.id.equals(id))return c;return null;}
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
    public ControllerConfig copy(){ControllerConfig p=new ControllerConfig();p.name=name;p.base=base;p.game=game;p.holdLayout=holdLayout;p.aimLayout=aimLayout;p.quickStyle=quickStyle;p.switchButtons=switchButtons;p.edgeSwitch=edgeSwitch;p.gyroEnabled=gyroEnabled;p.gyroAimOnly=gyroAimOnly;p.gyroInvertX=gyroInvertX;p.gyroInvertY=gyroInvertY;p.gyroSensitivity=gyroSensitivity;p.gyroSmoothing=gyroSmoothing;p.gyroDeadzone=gyroDeadzone;p.labels=labels;p.leftMode=leftMode;p.rightMode=rightMode;p.clickGesture=clickGesture;p.triggerMode=triggerMode;p.curve=curve;p.integrated=integrated;p.preserveY=preserveY;p.hideTriggers=hideTriggers;p.grid=grid;p.snap=snap;p.deadzone=deadzone;p.sensitivity=sensitivity;p.triggerDeadzone=triggerDeadzone;p.triggerSensitivity=triggerSensitivity;p.travel=travel;p.gridSize=gridSize;p.haptics=haptics;p.unifiedOrientation=unifiedOrientation;p.unifiedCurve=unifiedCurve;p.aimMode=aimMode;p.unifiedFloating=unifiedFloating;p.unifiedInvert=unifiedInvert;p.cameraFire=cameraFire;p.unifiedDeadzone=unifiedDeadzone;p.unifiedTravel=unifiedTravel;p.unifiedSensitivity=unifiedSensitivity;p.cameraX=cameraX;p.cameraY=cameraY;p.cameraCurve=cameraCurve;p.cameraSmoothing=cameraSmoothing;p.aimSensitivity=aimSensitivity;for(Control c:controls)p.controls.add(c.copy());return p;}
}
