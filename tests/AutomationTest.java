package dev.phonepad;

import java.util.*;
import java.nio.*;

public final class AutomationTest {
    static int checks;
    static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
    static ControllerInput engine(String binding,String program){ControllerConfig config=ControllerConfig.preset("Custom Blank Layout");config.controls.clear();ControllerConfig.Control c=new ControllerConfig.Control("test","button",0,.5f,.5f,.2f,.3f);c.buttons=0;c.binding=binding;c.program=program;config.controls.add(c);return new ControllerInput(config);}
    static void down(ControllerInput e,int id,long now){check(e.down(id,550,240,now),"touch captured");}
    static boolean key(byte[] data,int key){return (data[key/8]&(1<<(key%8)))!=0;}
    static void desktopMappings(){
        ControllerInput e=engine("A","");
        ControllerConfig.Control button=e.config.get("test");button.source="KEY_CTRL+KEY_A";
        DesktopBindings.assign(button,"","MOUSE_LEFT");down(e,1,100);
        check(e.automation.desktop()[32]==1&&e.state.buttons==0,"mouse selection directly assigns output");
        check(button.source.equals("KEY_CTRL+KEY_A"),"output assignment preserves hardware sources");
        e.up(1,120);check(e.automation.desktop()[32]==0,"automatic mouse output releases");
        DesktopBindings.assign(button,"","KEY_C");down(e,1,140);check(key(e.automation.desktop(),6),"keyboard selection directly assigns output");e.release();
        ControllerConfig config=ControllerConfig.preset("Custom Blank Layout");config.controls.clear();
        ControllerConfig.Control stick=new ControllerConfig.Control("pointer","stick",0,.5f,.5f,.5f,.6f);config.controls.add(stick);
        DesktopBindings.assign(stick,"","MOUSE_MOVE");e=new ControllerInput(config);
        for(int axis=0;axis<4;axis++){
            long now=200+axis*200;down(e,1,now);
            float x=axis==0?400:axis==1?700:550,y=axis==2?100:axis==3?380:240;
            e.move(1,x,y,now+20);e.update(now+40);byte[] frame=e.automation.desktop();
            int dx=ByteBuffer.wrap(frame).getShort(33),dy=ByteBuffer.wrap(frame).getShort(35);
            check(axis==0?dx<0&&dy==0:axis==1?dx>0&&dy==0:axis==2?dy<0&&dx==0:dy>0&&dx==0,"pointer assignment emits each direction");
            check(e.state.lx==0&&e.state.ly==0,"pointer does not also move controller axes");
            e.up(1,now+60);e.automation.desktop();e.update(now+100);
            check(ByteBuffer.wrap(e.automation.desktop()).getShort(33)==0,"released pointer does not drift");e.release();
        }
        stick.binding="A";DesktopBindings.assign(stick,"up","MOUSE_Y_NEG");check(stick.binding.isEmpty(),"direction assignment removes whole-control override");check(stick.mapUp.equals("MOUSE_Y=-500"),"single direction gets signed output");
        stick.mapUp="MOUSE_Y=-500+MOUSE_X=500+MOUSE_Y=500+MOUSE_X=-500";stick.mapDown=stick.mapLeft=stick.mapRight="";
        String[] tokens={"MOUSE_Y_NEG","MOUSE_Y_POS","MOUSE_X_NEG","MOUSE_X_POS"},directions={"up","down","left","right"};
        for(int i=0;i<4;i++){ControllerConfig.Mapping m=new ControllerConfig.Mapping();m.source=tokens[i];m.direction=directions[i];stick.mappings.add(m);}
        check(DesktopBindings.repairCancelledPointer(stick),"repair recognized cancelling legacy mapping");
        check(DesktopBindings.contains(stick,"","MOUSE_MOVE"),"legacy pointer repair wires all directions");
        check(stick.mappings.size()==4&&!DesktopBindings.repairCancelledPointer(stick),"repair preserves external mappings and is idempotent");
        stick.mapUp="MOUSE_Y=-500+KEY_W";String before=stick.mapUp;
        check(!DesktopBindings.repairCancelledPointer(stick)&&stick.mapUp.equals(before),"custom combinations remain untouched");
        DesktopBindings.remove(stick,"up","MOUSE_Y_NEG");check(stick.mapUp.equals("KEY_W"),"remove only selected output");
    }
    public static void main(String[] args){
        desktopMappings();
        ControllerInput e=engine("KEY_CTRL+KEY_C+MOUSE_LEFT+A","");down(e,1,100);byte[] report=e.automation.desktop();check(key(report,224)&&key(report,6)&&report[32]==1,"keyboard/mouse chord");check(e.state.buttons==1,"controller chord");down(e,2,101);e.up(1,120);check(key(e.automation.desktop(),6),"another finger retains key");e.up(2,130);check(!key(e.automation.desktop(),6),"last finger releases key");
        e=engine("","press A+KEY_W\nwait 100\nrelease A+KEY_W\npress B\nwait 80");down(e,1,100);check(e.state.buttons==1&&key(e.automation.desktop(),26),"macro first step");e.up(1,120);check(e.state.buttons==1,"once macro continues after finger lift");e.update(200);check(e.state.buttons==2&&!key(e.automation.desktop(),26),"sequence advances and releases W");e.update(280);check(e.state.buttons==0,"completion releases B");
        e=engine("","repeat 0\npress A\nwait 30\nrelease A\nwait 30\nend");e.config.get("test").macroMode="HOLD";down(e,1,100);e.update(130);check(e.state.buttons==0,"loop release phase");e.update(160);check(e.state.buttons==1,"loop repeat");e.up(1,170);check(e.state.buttons==0&&!e.automation.running(),"hold release stops loop");
        e=engine("","if vehicle=true\npress A\nelse\npress B\nend\nwait 100");e.automation.gameState(Collections.singletonMap("vehicle","true"),90);down(e,1,100);check(e.state.buttons==1,"game state branch");e.release();e.automation.gameState(Collections.singletonMap("vehicle","false"),190);down(e,1,200);check(e.state.buttons==2,"else branch");
        e=engine("KEY_W","");e.config.get("test").condition="vehicle=false";down(e,1,100);check(!key(e.automation.desktop(),26),"unknown state blocks output");e.automation.gameState(Collections.singletonMap("vehicle","false"),100);e.update(110);check(key(e.automation.desktop(),26),"state enables output");e.update(1101);check(!key(e.automation.desktop(),26),"stale state releases output");
        e=engine("","repeat 3\nrepeat 2\npress A\nwait 10\nrelease A\nwait 10\nend\nend");down(e,1,100);for(int i=1;i<=12;i++)e.update(100+i*10);check(e.state.buttons==0&&!e.automation.running(),"nested loops complete");
        e=engine("","repeat 0\npress A\nend");down(e,1,100);check(e.state.buttons==1,"zero-wait loop yields within budget");e.release();check(e.state.buttons==0&&!e.automation.running(),"release stops loop");
        e=engine("","press A\nwait 10000");e.config.get("test").macroMode="TOGGLE";down(e,1,100);e.up(1,120);down(e,1,140);check(e.state.buttons==0,"second press cancels toggle");
        e=engine("RX=1+KEY_W","");e.config.get("test").source="AXIS_RX_POS";e.automation.hardwareAxis("AXIS_RX_POS",.6f);e.update(100);check(Math.abs(e.state.rx-.6)<.01&&key(e.automation.desktop(),26),"connected analog source maps proportionally");e.automation.hardwareAxis("AXIS_RX_POS",0);e.update(120);check(e.state.rx==0&&!key(e.automation.desktop(),26),"hardware neutral releases");
        e=engine("","press A\nwait 1000");e.config.get("test").eventDriven=true;e.config.get("test").condition="vehicle=false";e.automation.gameState(Collections.singletonMap("vehicle","false"),100);e.update(110);check(e.state.buttons==1,"event-driven macro activates");e.update(1200);check(e.state.buttons==0,"expired state cancels macro");
        e=engine("","");e.config.controls.clear();ControllerConfig.Control trackpad=new ControllerConfig.Control("pad","trackpad",0,.5f,.5f,.5f,.6f);e.config.controls.add(trackpad);down(e,1,100);e.move(1,570,250,110);e.update(110);report=e.automation.desktop();check(ByteBuffer.wrap(report).getShort(33)==20&&ByteBuffer.wrap(report).getShort(35)==10,"trackpad relative motion");check(ByteBuffer.wrap(e.automation.desktop()).getShort(33)==0,"motion drains once");e.release();down(e,1,200);e.up(1,220);check(e.automation.desktop()[32]==1,"trackpad tap clicks");e.update(301);check(e.automation.desktop()[32]==0,"tap releases");
        e=engine("KEY_W","");e.config.get("test").source="KEY_A";e.automation.hardwareForDevice("KEY_A",1,1);e.automation.hardwareForDevice("KEY_A",1,2);e.update(100);e.automation.hardwareForDevice("KEY_A",0,1);e.update(120);check(key(e.automation.desktop(),26),"second device keeps its key");e.automation.hardwareForDevice("KEY_A",0,2);e.update(140);check(!key(e.automation.desktop(),26),"last device releases key");
        e.automation.transientInput("KEY_A",1,1,200);e.update(160);check(key(e.automation.desktop(),26),"transient mouse source activates");e.update(201);check(!key(e.automation.desktop(),26),"transient mouse source releases");
        e.automation.hardware("KEY_A",true);e.config.get("test").visibleWhen="vehicle=true";e.update(220);check(!key(e.automation.desktop(),26),"hidden hardware mapping cannot emit");
        e=engine("","press A\nwait 1000");e.config.get("test").eventDriven=true;e.config.get("test").condition="vehicle=true";e.config.get("test").visible=false;e.automation.gameState(Collections.singletonMap("vehicle","true"),100);e.update(110);check(e.state.buttons==0,"disabled event control cannot start");
        e=engine("","");e.config.controls.clear();ControllerConfig.Control direction=new ControllerConfig.Control("test","stick",0,.5f,.5f,.2f,.3f);e.config.controls.add(direction);direction.mapRight="KEY_D+MOUSE_X=500";down(e,1,100);e.move(1,650,240,116);e.update(116);check(key(e.automation.desktop(),7)&&e.state.lx==0,"direction mapping replaces stick");e.up(1,130);check(!key(e.automation.desktop(),7),"direction mapping releases");
        e=engine("DPAD_UP+DPAD_RIGHT","");down(e,1,100);check(e.state.hat==1,"grouped D-pad outputs combine diagonally");e.up(1,120);check(e.state.hat==8,"grouped D-pad release");
        e=engine("A","");e.config.get("test").source="KEY_CTRL+KEY_W|PAD_A";
        e.automation.hardware("KEY_W",true);e.update(100);check(e.state.buttons==0,"legacy chord still requires all keys");
        e.automation.hardware("KEY_CTRL",true);e.update(120);check(e.state.buttons==1,"legacy chord works inside alternatives");
        e.automation.hardware("KEY_W",false);e.automation.hardware("KEY_CTRL",false);e.automation.hardware("PAD_A",true);e.update(140);check(e.state.buttons==1,"alternative source activates mapping");
        e.release();e.update(160);check(e.state.buttons==0,"alternative mappings release on interruption");
        ControllerConfig directional=ControllerConfig.preset("Default");ControllerConfig.Control ls=directional.get("LS");ls.sourceLeft="KEY_A";ls.sourceRight="KEY_D|AXIS_LX_POS";ls.sourceUp="KEY_W";ls.sourceDown="KEY_S";
        ControllerConfig copied=directional.copy();check(copied.get("LS").sourceRight.equals(ls.sourceRight),"directional source copy preserves alternatives");
        e=new ControllerInput(copied);e.automation.hardware("KEY_D",true);e.automation.hardware("KEY_W",true);e.update(100);check(e.state.lx==1&&e.state.ly==-1,"hardware directions drive both stick axes");
        e.automation.hardware("KEY_A",true);e.update(120);check(e.state.lx==0&&e.state.ly==-1,"opposite physical directions cancel");
        e.release();e.config.get("LS").sourceRight="AXIS_LX_POS";e.config.get("LS").deadzone=.2f;e.config.get("LS").sensitivity=1;e.automation.hardwareAxis("AXIS_LX_POS",.6f);e.update(160);check(Math.abs(e.state.lx-.5)<.001,"directional axis honors dead zone");
        e.config.get("LS").invertX=true;e.update(180);check(Math.abs(e.state.lx+.5)<.001,"directional axis inversion");
        e.release();e.update(200);check(e.state.lx==0,"directional axes release");
        e=engine("","press A+KEY_W+MOUSE_LEFT\nwait 20\nrelease A+KEY_W+MOUSE_LEFT\nwait 10");ControllerConfig.Control other=new ControllerConfig.Control("other","button",1,.2f,.5f,.1f,.1f);e.config.controls.add(other);e.config.get("test").macroTriggers="other";e.config.get("test").source="KEY_1";other.source="KEY_2";e.automation.hardware("KEY_2",true);e.update(300);check(e.state.buttons==3&&key(e.automation.desktop(),26),"shared macro trigger preserves simultaneous outputs");e.automation.hardware("KEY_2",false);e.update(320);check(e.state.buttons==0&&!key(e.automation.desktop(),26),"shared trigger sequence releases outputs");
        for(String source:new String[]{"KEY_A|","|KEY_A","KEY_A++KEY_B"}){boolean rejected=false;try{Automation.validateSource(source);}catch(IllegalArgumentException ex){rejected=true;}check(rejected,"malformed source rejected");}
        String[] bad={"exec something","repeat 2\npress A","wait 0","if vehicle\nend","press UNKNOWN"};for(String text:bad){boolean failed=false;try{Automation.compile(text);}catch(IllegalArgumentException ex){failed=true;}check(failed,"invalid macro rejected: "+text);}
        check(Automation.stepCount("press A\nwait 100\nif vehicle=true\npress B\nelse\npress X\nend") == 5,"macro gallery count excludes branch markers");
        check(Automation.stepCount("# note\n") == 0,"empty/comment macro has zero steps");
        System.out.println("Automation checks passed: "+checks);
    }
}
