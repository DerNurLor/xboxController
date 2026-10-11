package dev.phonepad;
import java.util.*;

public final class EditorV2Test {
    static int checks;
    static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    static void near(float actual,float expected,String message){check(Math.abs(actual-expected)<.001,message+": "+actual);}
    static ControllerConfig.Mapping mapping(ControllerConfig.Control c,String source,String direction,float sensitivity){ControllerConfig.Mapping m=new ControllerConfig.Mapping();m.source=source;m.direction=direction;m.sensitivity=sensitivity;c.mappings.add(m);return m;}
    public static void main(String[] args){
        ControllerConfig config=ControllerConfig.preset("Default");ControllerConfig.Control rs=config.get("RS");rs.sourceLeft="MOUSE_X_NEG";rs.sourceRight="MOUSE_X_POS";rs.sourceUp="MOUSE_Y_NEG";rs.sourceDown="MOUSE_Y_POS";rs.migrateMappings(.08f);
        check(rs.mappings.size()==4,"all opposite directions migrated");Set<String> ids=new HashSet<>();for(ControllerConfig.Mapping m:rs.mappings)ids.add(m.id);check(ids.size()==4,"stable unique identities");rs.migrateMappings(.08f);check(rs.mappings.size()==4,"idempotent migration");
        ControllerConfig.Mapping right=rs.mappings.get(3),left=rs.mappings.get(2);right.sensitivity=.25f;right.deadzone=0;left.sensitivity=.75f;left.deadzone=0;
        ControllerInput input=new ControllerInput(config);input.automation.hardwareAxis("MOUSE_X_POS",1);input.update(100);near(input.state.rx,.25f,"right independent tuning");input.automation.hardwareAxis("MOUSE_X_POS",0);input.automation.hardwareAxis("MOUSE_X_NEG",1);input.update(120);near(input.state.rx,-.75f,"left independent tuning");
        input.automation.hardwareAxis("MOUSE_X_POS",1);input.update(140);near(input.state.rx,-.5f,"opposite contributions combine after independent response");
        ControllerConfig copied=config.copy();copied.get("RS").mapping(right.id).sensitivity=1;near(right.sensitivity,.25f,"copy does not alias mapping");check(copied.get("RS").mapping(right.id)!=right,"independent mapping objects");
        rs.mappings.remove(left);input.update(160);near(input.state.rx,.25f,"deleting one mapping retains opposite");
        ControllerConfig.Control a=config.get("A");a.program="press X\nwait 100\nrelease X";a.macroTriggers="B,Y";config.migrateMacros();check(config.macros.size()==1&&config.macroAssignments.size()==3,"legacy macro migration preserves all references");String macro=config.macros.get(0).id;String assignment=config.macroAssignments.get(0).id;config.migrateMacros();check(config.macros.size()==1&&config.macroAssignments.size()==3,"idempotent macro migration");check(config.copy().macroAssignments.get(0).id.equals(assignment),"stable assignment identity");
        ControllerConfig.Macro extra=new ControllerConfig.Macro();extra.program="press Y\nwait 200\nrelease Y";config.macros.add(extra);config.assign(extra.id,"A","PRESS");check(config.macroAssignments.stream().filter(v->v.controlId.equals("A")).count()==2,"multiple macros per button");
        Automation engine=new Automation();PadState state=new PadState();engine.begin(100,state);engine.activate(a);engine.finish(config);check((state.buttons&(1<<3))!=0&&(state.buttons&(1<<4))!=0,"simultaneous macro outputs union");
        state.clear();engine.begin(210,state);engine.finish(config);check((state.buttons&(1<<3))==0&&(state.buttons&(1<<4))!=0,"one macro ending does not release another");
        ControllerConfig.Macro clone=config.duplicateMacro(macro);clone.program="press B\nwait 50";check(!clone.id.equals(macro)&&!config.macro(macro).program.equals(clone.program),"duplicate macro independent");config.macroAssignments.removeIf(v->v.macroId.equals(clone.id));check(config.macro(clone.id)!=null,"unassign retains definition");
        config.macro(macro).program="press LB\nwait 100";engine.clear();state.clear();engine.begin(300,state);engine.activate(config.get("B"));engine.finish(config);check((state.buttons&(1<<6))!=0,"shared edits reach another assigned control");
        config.macro(macro).enabled=false;state.clear();engine.begin(310,state);engine.finish(config);check(state.buttons==0,"disable cancels owned output");
ControllerConfig legacy=ControllerConfig.preset("Default");legacy.get("A").program="press X\nwait 100";legacy.get("A").source="KEY_1";legacy.migrateMacros();
        ControllerInput restored=new ControllerInput(legacy);restored.automation.hardware("KEY_1",true);restored.update(100);check((restored.state.buttons&(1<<3))!=0&&(restored.state.buttons&1)==0,"legacy macro overrides original action");
        legacy.macroAssignments.clear();restored.update(120);check((restored.state.buttons&1)!=0&&(restored.state.buttons&(1<<3))==0,"removing migrated assignment restores button action");
        ControllerConfig.Control duplicate=rs.copyAs("other-stick");check(!duplicate.mappings.get(0).id.equals(rs.mappings.get(0).id),"duplicated controls receive independent mapping identities");
                ControllerConfig routed=ControllerConfig.preset("Default");for(String[] pair:new String[][]{{"PAD_Y","KEY_SPACE"},{"MOUSE_LEFT","KEY_SPACE"}}){ControllerConfig.Control button=routed.get(pair[0].equals("PAD_Y")?"A":"B");button.independentMappings=true;ControllerConfig.Mapping map=new ControllerConfig.Mapping();map.source=pair[0];map.output=pair[1];button.mappings.add(map);}ControllerInput mapped=new ControllerInput(routed);mapped.automation.hardware("PAD_Y",true);mapped.update(100);int space=Automation.KEYS.get("KEY_SPACE");byte[] desktop=mapped.automation.desktop();check((desktop[space/8]&(1<<(space%8)))!=0,"gamepad Y produces desktop Space report");mapped.automation.hardware("PAD_Y",false);mapped.update(120);check((mapped.automation.desktop()[space/8]&(1<<(space%8)))==0,"gamepad Y release releases desktop Space");mapped.automation.hardware("MOUSE_LEFT",true);mapped.update(140);check((mapped.automation.desktop()[space/8]&(1<<(space%8)))!=0,"mouse input produces desktop Space report");mapped.automation.hardware("MOUSE_LEFT",false);mapped.update(160);check((mapped.automation.desktop()[space/8]&(1<<(space%8)))==0,"mouse release releases desktop Space");
        for(String trigger:new String[]{"PRESS","HOLD","TOGGLE","TAP","DOUBLE_TAP","LONG_PRESS"})trigger(trigger);
        String code="if vehicle=false\npress A\nwait 100\nrelease A\nelse\npress B\nwait 50\nrelease B\nend";
        Automation.Preview preview=new Automation.Preview(code);preview.game.put("vehicle","false");preview.step();check(preview.evaluation.endsWith("TRUE"),"preview true branch");preview.step();check(preview.held.contains("A"),"preview press");preview.step();check(preview.remaining()==100,"preview delay");preview.advance(40);check(preview.remaining()==60&&preview.held.contains("A"),"delay countdown");preview.step();check(preview.held.isEmpty()&&preview.time==100,"step advances wait and releases");while(!preview.finished)preview.step();
        preview.restart();preview.game.put("vehicle","true");preview.step();check(preview.evaluation.endsWith("FALSE"),"preview false branch");preview.step();check(preview.held.contains("B")&&!preview.held.contains("A"),"false branch chosen");preview.stop();check(preview.held.isEmpty()&&preview.state.buttons==0,"preview stop releases simulation");
        Automation.Preview repeated=new Automation.Preview("repeat 2\npress A\nwait 20\nrelease A\nend");for(int i=0;i<30&&!repeated.finished;i++)repeated.step();check(repeated.finished&&repeated.time==40,"preview uses runtime repeat semantics");
        boolean invalid=false;try{Automation.compile("if vehicle=true\npress A");}catch(IllegalArgumentException expected){invalid=true;}check(invalid,"invalid open branches rejected");
        List<ControllerConfig.Control> group=new ArrayList<>();group.add(new ControllerConfig.Control("1","button",0,.2f,.2f,.1f,.1f));group.add(new ControllerConfig.Control("2","button",0,.5f,.5f,.2f,.2f));group.add(new ControllerConfig.Control("3","button",0,.8f,.8f,.1f,.1f));
        EditorGeometry.transform(group,"left");near(group.get(0).x-group.get(0).w/2,group.get(1).x-group.get(1).w/2,"align edges");EditorGeometry.transform(group,"width");near(group.get(0).w,group.get(1).w,"match width");EditorGeometry.transform(group,"distributeY");near(group.get(1).y-group.get(1).h/2-(group.get(0).y+group.get(0).h/2),group.get(2).y-group.get(2).h/2-(group.get(1).y+group.get(1).h/2),"equal edge gaps");
        group.get(0).x=.496f;float[] guides=EditorGeometry.snap(group.get(0),group,Collections.singleton("1"),.01f,.01f);near(group.get(0).x,.5f,"canvas center snapping");check(!Float.isNaN(guides[0]),"smart guide returned");
        for(String op:new String[]{"left","right","top","bottom","centerX","centerY","width","height","distributeX","distributeY"}){EditorGeometry.transform(group,op);for(ControllerConfig.Control c:group)check(c.x>=c.w/2&&c.x<=1-c.w/2&&c.y>=c.h/2&&c.y<=1-c.h/2,"alignment stays in bounds");}
        check(DesktopBindings.output("MOUSE_X=123.0+KEY_CTRL").equals("MOUSE_X=123.0+KEY_CTRL"),"combination preserves edited movement strength");
        check(DesktopBindings.output("WHEEL=-3.0+KEY_SHIFT").equals("WHEEL=-3.0+KEY_SHIFT"),"combination preserves edited scrolling strength");
        ControllerConfig chordConfig=ControllerConfig.preset("Custom Blank Layout");chordConfig.controls.clear();
        ControllerConfig.Control chord=new ControllerConfig.Control("chord","button",PadState.B,.5f,.5f,.2f,.2f);
        DesktopBindings.assign(chord,"","KEY_CTRL+KEY_C+KEY_CTRL");
        check(chord.binding.equals("KEY_CTRL+KEY_C"),"chord is deduplicated");
        check(DesktopBindings.contains(chord,"","KEY_C+KEY_CTRL"),"chord matches independent of order");
        chordConfig.controls.add(chord);ControllerInput chordInput=new ControllerInput(chordConfig);
        check(chordInput.down(1,550,240,100),"mapped B touch captured");chordInput.update(110);
        check(chordInput.state.buttons==0,"mapped B does not inject an extra controller button");
        check((chordInput.sourceState.buttons&(1<<PadState.B))!=0,"mapped B remains visible as source");
        byte[] chordDesktop=chordInput.automation.desktop();
        check((chordDesktop[0]&(1<<6))!=0&&(chordDesktop[28]&1)!=0,"Ctrl+C chord held simultaneously");
        chordInput.release();chordInput.update(120);
        check(chordInput.sourceState.buttons==0,"source releases with input");
        check(chordInput.automation.desktop()[0]==0&&chordInput.automation.desktop()[28]==0,"chord releases together");
        DesktopBindings.remove(chord,"","KEY_CTRL+KEY_C");check(chord.binding.isEmpty(),"chord removal restores default");
        for(int trigger=0;trigger<2;trigger++){
            chordConfig.controls.clear();ControllerConfig.Control pedal=new ControllerConfig.Control("pedal","trigger",trigger,.5f,.5f,.2f,.2f);pedal.mode="DIGITAL";pedal.binding="MOUSE_LEFT";chordConfig.controls.add(pedal);
            ControllerInput pedalInput=new ControllerInput(chordConfig);pedalInput.down(1,550,240,100);pedalInput.update(110);
            check(pedalInput.state.lt==0&&pedalInput.state.rt==0,"mapped trigger is not injected");
            check((trigger==0?pedalInput.sourceState.lt:pedalInput.sourceState.rt)==1,"mapped trigger remains visible as source");
        }
        System.out.println("Editor V2 checks passed: "+checks);
    }
    static void trigger(String mode){ControllerConfig c=ControllerConfig.preset("Default");ControllerConfig.Macro m=new ControllerConfig.Macro();m.program="press X\nwait 2000";c.macros.add(m);c.assign(m.id,"A",mode);Automation e=new Automation();PadState s=new PadState();e.begin(100,s);e.activate(c.get("A"));e.finish(c);
        boolean immediate=Arrays.asList("PRESS","HOLD","TOGGLE").contains(mode);check(((s.buttons&(1<<3))!=0)==immediate,mode+" initial edge");
        s.clear();e.begin(mode.equals("LONG_PRESS")?600:150,s);if(mode.equals("LONG_PRESS"))e.activate(c.get("A"));e.finish(c);
        if(mode.equals("HOLD"))check(s.buttons==0,"held release cancels");if(mode.equals("TAP")||mode.equals("LONG_PRESS"))check((s.buttons&(1<<3))!=0,mode+" fires at intended edge");
        if(mode.equals("DOUBLE_TAP")){s.clear();e.begin(200,s);e.activate(c.get("A"));e.finish(c);s.clear();e.begin(230,s);e.finish(c);check((s.buttons&(1<<3))!=0,"double tap release fires");}
        e.clear();s.clear();e.begin(3000,s);e.finish(c);check(s.buttons==0,mode+" cancellation neutral");
    }
}
