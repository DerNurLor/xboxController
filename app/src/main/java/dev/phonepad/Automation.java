package dev.phonepad;

import java.nio.*;
import java.util.*;

/** Bounded, nonblocking automation. Each running control owns its outputs independently. */
public final class Automation {
    public static final int DESKTOP_SIZE=39;
    public static final Map<String,Integer> KEYS=new LinkedHashMap<>();
    private static final Map<String,Integer> BUTTONS=new LinkedHashMap<>();
    static {
        for(int i=0;i<26;i++)KEYS.put("KEY_"+(char)('A'+i),4+i);
        for(int i=1;i<=9;i++)KEYS.put("KEY_"+i,29+i);KEYS.put("KEY_0",39);
        String[] names={"ENTER","ESC","BACKSPACE","TAB","SPACE","MINUS","EQUAL","LEFTBRACE","RIGHTBRACE","BACKSLASH","HASH","SEMICOLON","APOSTROPHE","GRAVE","COMMA","DOT","SLASH","CAPSLOCK"};
        for(int i=0;i<names.length;i++)KEYS.put("KEY_"+names[i],40+i);
        for(int i=1;i<=12;i++)KEYS.put("KEY_F"+i,57+i);
        String[] more={"PRINTSCREEN","SCROLLLOCK","PAUSE","INSERT","HOME","PAGEUP","DELETE","END","PAGEDOWN","RIGHT","LEFT","DOWN","UP","NUMLOCK","KPSLASH","KPASTERISK","KPMINUS","KPPLUS","KPENTER","KP1","KP2","KP3","KP4","KP5","KP6","KP7","KP8","KP9","KP0","KPDOT"};
        for(int i=0;i<more.length;i++)KEYS.put("KEY_"+more[i],70+i);
        String[] mods={"CTRL","SHIFT","ALT","META","RIGHTCTRL","RIGHTSHIFT","RIGHTALT","RIGHTMETA"};for(int i=0;i<mods.length;i++)KEYS.put("KEY_"+mods[i],224+i);
        String[] buttons={"A","B","X","Y","LB","RB","VIEW","MENU","HOME","L3","R3","SHARE"};int[] bits={0,1,3,4,6,7,10,11,12,13,14,15};for(int i=0;i<bits.length;i++)BUTTONS.put(buttons[i],bits[i]);
    }
    public static List<String> outputs(){List<String> out=new ArrayList<>(BUTTONS.keySet());out.addAll(Arrays.asList("LT=1","RT=1","LX=-1","LX=1","LY=-1","LY=1","RX=-1","RX=1","RY=-1","RY=1","DPAD_UP","DPAD_RIGHT","DPAD_DOWN","DPAD_LEFT"));out.addAll(KEYS.keySet());out.addAll(Arrays.asList("MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD","MOUSE_X=500","MOUSE_Y=500","WHEEL=5"));return out;}
    private final Set<Integer> keys=new HashSet<>();
    private final Map<String,Float> hardwareAxes=new HashMap<>();
    private final Map<Integer,Map<String,Float>> devices=new HashMap<>();
    private final Map<Integer,Map<String,Long>> transientInputs=new HashMap<>();
    public void transientInput(String name,float value,int device,long until){hardwareForDevice(name,value,device);transientInputs.computeIfAbsent(device,k->new HashMap<>()).put(name,until);}
    public boolean hardwareForDevice(String name,float value,int device){Map<String,Float> held=devices.computeIfAbsent(device,k->new HashMap<>());if(value>0f)held.put(name,value);else held.remove(name);if(held.isEmpty())devices.remove(device);float amount=0;for(Map<String,Float> inputs:devices.values())amount=Math.max(amount,inputs.getOrDefault(name,0f));hardwareAxes.put(name,amount);return amount>.15f?hardware.add(name):hardware.remove(name); }
    public void hardwareAxis(String name,float value){hardwareForDevice(name,value,0);}
    public float sourceAmount(String source){
        float amount=0;
        for(String alternative:source.split("\\|")){
            if(alternative.isEmpty())continue;
            float chord=1;
            for(String name:alternative.split("\\+"))chord=Math.min(chord,hardwareAxes.getOrDefault(name.trim(),0f));
            amount=Math.max(amount,chord);
        }
        return amount;
    }
    private final Set<String> hardware=new HashSet<>(),active=new HashSet<>(),previous=new HashSet<>();
    private final Map<String,Run> runs=new LinkedHashMap<>();
    private final Map<String,Long> downSince=new HashMap<>(),lastTap=new HashMap<>();
    private final Set<String> assignmentDown=new HashSet<>(),longFired=new HashSet<>();
    private final Map<String,Program> compiled=new HashMap<>();
    private final Map<String,Long> pulses=new HashMap<>();
    private final Map<String,float[]> vectors=new HashMap<>();
    public void pulse(String token,long until){pulses.put(token,until);}
    private Map<String,String> game=Collections.emptyMap();private long gameUntil;
    private int mouse,dpadMask;private double dx,dy,wheel;private long last,now;private float dt;
    private PadState state;
    private final PadState conditionState=new PadState();
    public String error="";
    public boolean gameState(Map<String,String> values,long now){boolean changed=!game.equals(values);game=new HashMap<>(values);gameUntil=now+1000;return changed;}
    public void clearGameState(){game=Collections.emptyMap();gameUntil=0;}
    public boolean hardware(String name,boolean down){return hardwareForDevice(name,down?1:0,0);}
    public boolean source(String expression){return sourceAmount(expression)>.15f;}
    public static void validateSource(String source){
        if(source.length()>1024||(!source.isEmpty()&&!source.matches("[A-Z0-9_]+(?:[+|][A-Z0-9_]+)*")))
            throw new IllegalArgumentException("Invalid hardware source");
    }
    public void clear(){downSince.clear();lastTap.clear();assignmentDown.clear();longFired.clear();pulses.clear();vectors.clear();keys.clear();mouse=0;dx=dy=wheel=0;runs.clear();active.clear();previous.clear();hardware.clear();devices.clear();transientInputs.clear();hardwareAxes.clear();last=0;}
    public void vector(String control,float x,float y){vectors.put(control,new float[]{PadState.clamp(x,-1,1),PadState.clamp(y,-1,1)});}
    public boolean running(){return !runs.isEmpty()||!hardware.isEmpty()||!pulses.isEmpty();}
    public void cancel(){runs.clear();pulses.clear();dx=dy=wheel=0;}
    public void begin(long time,PadState target){now=time;vectors.clear();for(Map.Entry<Integer,Map<String,Long>> device:transientInputs.entrySet())device.getValue().entrySet().removeIf(e->{if(e.getValue()>time)return false;hardwareForDevice(e.getKey(),0,device.getKey());return true;});dt=last==0?0:Math.min(50,Math.max(0,time-last))/1000f;last=time;conditionState.buttons=target.buttons;conditionState.lt=target.lt;conditionState.rt=target.rt;state=target;keys.clear();mouse=0;dpadMask=0;active.clear();if(time>gameUntil)clearGameState();}
    public boolean matches(String expression){return condition(expression,game,conditionState);}
    public static boolean condition(String expression,Map<String,String> game,PadState state){
        if(expression.trim().isEmpty())return true;
        for(String term:expression.split("&&")){
            String[] pair=term.trim().split("(!=|=)",2);if(pair.length!=2)return false;
            String key=pair[0].trim(),wanted=pair[1].trim(),actual=game.get(key);
            if(key.startsWith("input.")&&state!=null){String name=key.substring(6).toUpperCase(Locale.ROOT);if(BUTTONS.containsKey(name))actual=Boolean.toString((state.buttons&(1<<BUTTONS.get(name)))!=0);else if(name.equals("LT"))actual=Boolean.toString(state.lt>0);else if(name.equals("RT"))actual=Boolean.toString(state.rt>0);}
            // Missing/expired state never satisfies either comparison.
            if(actual==null|| (term.contains("!=")?actual.equals(wanted):!actual.equals(wanted)))return false;
        }return true;
    }
    public static void validateCondition(String expression){if(expression.length()>256)throw new IllegalArgumentException("Condition is too long");if(expression.isEmpty())return;for(String term:expression.split("&&",-1))if(!term.trim().matches("[A-Za-z0-9_.-]{1,48}\\s*(!=|=)\\s*[A-Za-z0-9_.-]{1,64}"))throw new IllegalArgumentException("Use state=value, optionally joined with &&");}
    public static void validateBinding(String binding){if(binding.length()>1024)throw new IllegalArgumentException("Too many mapped outputs");if(binding.trim().isEmpty())return;for(String token:binding.split("\\+",-1))validateOutput(token.trim());}
    private static void validateOutput(String token){if(BUTTONS.containsKey(token)||KEYS.containsKey(token)||Arrays.asList("MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD","DPAD_UP","DPAD_RIGHT","DPAD_DOWN","DPAD_LEFT").contains(token))return;String[] p=token.split("=",-1);if(p.length==2&&Arrays.asList("LX","LY","RX","RY","LT","RT","MOUSE_X","MOUSE_Y","WHEEL").contains(p[0])){try{float n=Float.parseFloat(p[1]);float limit=p[0].startsWith("MOUSE")?2000:p[0].equals("WHEEL")?30:1;if(Float.isFinite(n)&&Math.abs(n)<=limit&&(!(p[0].equals("LT")||p[0].equals("RT"))||n>=0))return;}catch(NumberFormatException ignored){}}throw new IllegalArgumentException("Unknown output: "+token);}
    public void bindScaled(String binding,float amount){if(binding.isEmpty())return;for(String token:binding.split("\\+")){String[] parts=token.trim().split("=",2);output(parts.length==2?parts[0]+"="+(Float.parseFloat(parts[1])*amount):token.trim());}}
    public void bind(String binding){if(!binding.isEmpty())for(String token:binding.split("\\+"))output(token.trim());}
    private void output(String token){
        Integer key=KEYS.get(token);if(key!=null){keys.add(key);return;}Integer button=BUTTONS.get(token);if(button!=null){state.buttons|=1<<button;return;}
        String[] mice={"MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD"};for(int i=0;i<mice.length;i++)if(mice[i].equals(token)){mouse|=1<<i;return;}
        if(token.startsWith("DPAD_")){int h=Arrays.asList("DPAD_UP","DPAD_RIGHT","DPAD_DOWN","DPAD_LEFT").indexOf(token)*2;if(h>=0)dpadMask|=1<<(h/2);return;}
        String[] p=token.split("=",2);if(p.length!=2)return;float v;try{v=Float.parseFloat(p[1]);}catch(NumberFormatException ex){return;}
        switch(p[0]){case "LX":state.lx=stronger(state.lx,v);break;case "LY":state.ly=stronger(state.ly,v);break;case "RX":state.rx=stronger(state.rx,v);break;case "RY":state.ry=stronger(state.ry,v);break;case "LT":state.lt=Math.max(state.lt,v);break;case "RT":state.rt=Math.max(state.rt,v);break;case "MOUSE_X":motion(v*dt,0,0);break;case "MOUSE_Y":motion(0,v*dt,0);break;case "WHEEL":motion(0,0,v*dt);break;}
    }
    private static float stronger(float old,float value){return Math.abs(value)>Math.abs(old)?value:old;}
    public void motion(double x,double y,double scroll){dx=Math.max(-32767,Math.min(32767,dx+x));dy=Math.max(-32767,Math.min(32767,dy+y));wheel=Math.max(-32767,Math.min(32767,wheel+scroll));}
    public byte[] desktop(){byte[] result=new byte[DESKTOP_SIZE];for(int k:keys)result[k/8]|=1<<(k%8);result[32]=(byte)mouse;ByteBuffer.wrap(result).order(ByteOrder.BIG_ENDIAN).position(33).putShort((short)dx).putShort((short)dy).putShort((short)wheel);dx-=(int)dx;dy-=(int)dy;wheel-=(int)wheel;return result;}
    public boolean directional(ControllerConfig.Control c,float x,float y){if(c.mapUp.isEmpty()&&c.mapDown.isEmpty()&&c.mapLeft.isEmpty()&&c.mapRight.isEmpty())return false;if(x<-.15f)bindScaled(c.mapLeft,-x);if(x>.15f)bindScaled(c.mapRight,x);if(y<-.15f)bindScaled(c.mapUp,-y);if(y>.15f)bindScaled(c.mapDown,y);return true;}
    public void activate(ControllerConfig.Control c){if(matches(c.condition))active.add(c.id);}
    public void finish(ControllerConfig config){
        for(ControllerConfig.Control c:config.controls){
            if(c.program.isEmpty())continue;boolean migrated=false;for(ControllerConfig.Macro m:config.macros)if(m.legacyOwner.equals(c.id))migrated=true;if(migrated)continue;
            if(!config.shown(c)||!matches(c.visibleWhen)){runs.remove(c.id);continue;}
            boolean down=active.contains(c.id),rising=down&&!previous.contains(c.id);
            for(String trigger:c.macroTriggers.split(","))if(!trigger.isEmpty()&&active.contains(trigger)){down=true;if(!previous.contains(trigger))rising=true;}
            if(c.eventDriven&&!c.condition.isEmpty()&&matches(c.condition)){down=true;rising=!previous.contains(c.id);active.add(c.id);}
            Run run=runs.get(c.id);
            if((!down&&c.macroMode.equals("HOLD"))||!matches(c.condition)){runs.remove(c.id);continue;}
            if(rising){if(run!=null&&c.macroMode.equals("TOGGLE")){runs.remove(c.id);continue;}if(run==null){try{Program p=compiled.get(c.program);if(p==null){p=compile(c.program);if(compiled.size()>128)compiled.clear();compiled.put(c.program,p);}run=new Run(p,now);runs.put(c.id,run);}catch(IllegalArgumentException bad){error=bad.getMessage();}}}
        }
        Set<String> liveAssignments=new HashSet<>();
        for(ControllerConfig.MacroAssignment link:config.macroAssignments){
            String key="assignment:"+link.id;liveAssignments.add(key);ControllerConfig.Macro macro=config.macro(link.macroId);ControllerConfig.Control control=config.get(link.controlId);
            boolean allowed=macro!=null&&macro.enabled&&link.enabled&&control!=null&&config.shown(control)&&matches(control.visibleWhen)&&matches(control.condition)&&matches(macro.condition);
            boolean down=allowed&&(active.contains(link.controlId)||(macro.eventDriven&&!macro.condition.isEmpty()&&matches(macro.condition)));
            boolean was=assignmentDown.contains(key),rising=down&&!was,falling=!down&&was;
            if(rising){downSince.put(key,now);longFired.remove(key);}
            boolean fire=rising;long duration=now-downSince.getOrDefault(key,now);
            switch(link.trigger){
                case "TAP":fire=falling&&duration<250;break;
                case "DOUBLE_TAP":fire=false;if(falling&&duration<250){Long previousTap=lastTap.remove(key);if(previousTap!=null&&now-previousTap<=350)fire=true;else lastTap.put(key,now);}break;
                case "LONG_PRESS":fire=down&&duration>=450&&longFired.add(key);break;
            }
            if(down)assignmentDown.add(key);else assignmentDown.remove(key);
            boolean hasSteer=macro!=null&&containsSteer(macro.program);
            if(hasSteer&&!link.trigger.equals("HOLD")){error="Steering macros require a While held trigger";runs.remove(key);continue;}
            if(!allowed||(!down&&link.trigger.equals("HOLD"))){runs.remove(key);continue;}
            Run run=runs.get(key);
            if(fire){if(run!=null&&link.trigger.equals("TOGGLE"))runs.remove(key);else if(run==null)try{Program program=compiled.get(macro.program);if(program==null){program=compile(macro.program);if(compiled.size()>128)compiled.clear();compiled.put(macro.program,program);}runs.put(key,new Run(program,now,control.id));}catch(IllegalArgumentException bad){error=bad.getMessage();}}
        }
        runs.keySet().removeIf(key->key.startsWith("assignment:")&&!liveAssignments.contains(key));
        assignmentDown.retainAll(liveAssignments);downSince.keySet().retainAll(liveAssignments);lastTap.keySet().retainAll(liveAssignments);longFired.retainAll(liveAssignments);
        Iterator<Map.Entry<String,Run>> it=runs.entrySet().iterator();while(it.hasNext()){Map.Entry<String,Run> entry=it.next();Run run=entry.getValue();if(now-run.started>300000){it.remove();continue;}if(!run.tick(this,now)){it.remove();continue;}for(String token:run.held)output(token);if(entry.getKey().startsWith("assignment:")&&run.steer!=null){float[] vector=vectors.get(run.owner);if(vector!=null){float[] stick=PadState.stick(vector[0],vector[1],config.deadzone,config.sensitivity);if(run.steer.equals("LEFT")){if(Math.hypot(stick[0],stick[1])>=Math.hypot(state.lx,state.ly)){state.lx=stick[0];state.ly=stick[1];}}else if(Math.hypot(stick[0],stick[1])>=Math.hypot(state.rx,state.ry)){state.rx=stick[0];state.ry=stick[1];}}}}
        for(ControllerConfig.Control c:config.controls)if(c.kind.equals("trackpad")&&config.shown(c)&&matches(c.visibleWhen)&&matches(c.condition)){for(String name:Arrays.asList("MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD"))if(source("PASSTHROUGH_"+name))output(name);break;}
        pulses.entrySet().removeIf(e->now>=e.getValue());for(String token:pulses.keySet())output(token);
        if(dpadMask!=0){int x=((dpadMask&2)!=0?1:0)-((dpadMask&8)!=0?1:0),y=((dpadMask&4)!=0?1:0)-((dpadMask&1)!=0?1:0);state.hat=x==0&&y==0?8:((int)Math.round(Math.atan2(x,-y)/(Math.PI/4))+8)%8;}
        previous.clear();previous.addAll(active);
    }
    static boolean containsSteer(String program){for(String line:program.split("\n"))if(line.trim().startsWith("steer "))return true;return false;}
    public static void validate(ControllerConfig.Control c){validateBinding(c.binding);validateBinding(c.mapUp);validateBinding(c.mapDown);validateBinding(c.mapLeft);validateBinding(c.mapRight);validateCondition(c.condition);validateCondition(c.visibleWhen);if(!c.program.isEmpty())compile(c.program);for(String source:Arrays.asList(c.source,c.sourceUp,c.sourceDown,c.sourceLeft,c.sourceRight))validateSource(source);}
    static final class Step {String op,arg;int jump;Step(String op,String arg){this.op=op;this.arg=arg;}}
    static final class Program {final List<Step> steps=new ArrayList<>();}
    public static Program compile(String text){
        if(text.length()>8192)throw new IllegalArgumentException("Use at most 8 KB per macro");Program p=new Program();Deque<Integer> blocks=new ArrayDeque<>();
        for(String raw:text.split("\\n")){String line=raw.trim();if(line.isEmpty()||line.startsWith("#"))continue;String[] parts=line.split("\\s+",2);String op=parts[0].toLowerCase(Locale.ROOT),arg=parts.length==2?parts[1].trim():"";Step s=new Step(op,arg);int at=p.steps.size();
            switch(op){case "press":case "release":validateBinding(arg);if(arg.isEmpty())throw new IllegalArgumentException("Choose an output for "+op);break;case "steer":if(!Arrays.asList("LEFT","RIGHT").contains(arg))throw new IllegalArgumentException("Choose LEFT or RIGHT stick");break;case "wait":integer(arg,1,60000);break;case "repeat":integer(arg,0,1000);blocks.push(at);break;case "if":validateCondition(arg);if(arg.isEmpty())throw new IllegalArgumentException("Choose a condition");blocks.push(at);break;case "else":if(blocks.isEmpty()||!p.steps.get(blocks.peek()).op.equals("if"))throw new IllegalArgumentException("Else needs an if block");p.steps.get(blocks.pop()).jump=at+1;blocks.push(at);break;case "end":if(blocks.isEmpty())throw new IllegalArgumentException("Unexpected end");s.jump=blocks.pop();Step open=p.steps.get(s.jump);open.jump=at+1;break;default:throw new IllegalArgumentException("Unknown macro step: "+op);}
            p.steps.add(s);if(p.steps.size()>256||blocks.size()>8)throw new IllegalArgumentException("Macro exceeds 256 steps or 8 nested blocks");
        }if(!blocks.isEmpty())throw new IllegalArgumentException("Close each repeat / if with end");return p;
    }
    /** Number of user-authored actions; branch separators and closing markers are structure, not steps. */
    public static int stepCount(String text){int count=0;for(Step step:compile(text).steps)if(!step.op.equals("else")&&!step.op.equals("end"))count++;return count;}
    static int integer(String value,int min,int max){try{int n=Integer.parseInt(value);if(n>=min&&n<=max)return n;}catch(NumberFormatException ignored){}throw new IllegalArgumentException("Use a number from "+min+" to "+max);}
    public static final class Preview {
        private final Automation engine=new Automation();private final Program program;private Run run;
        public final Map<String,String> game=new LinkedHashMap<>();public final List<String> events=new ArrayList<>();
        public final Set<String> held=new LinkedHashSet<>();public final PadState state=new PadState();
        public long time;public int node=-1;public boolean finished;public String evaluation="";
        public Preview(String text){program=compile(text);restart();}
        public void restart(){time=0;run=new Run(program,0);held.clear();events.clear();state.clear();node=-1;finished=false;evaluation="";engine.clear();}
        public long remaining(){return Math.max(0,run.until-time);}
        public void advance(long millis){time+=Math.min(remaining(),Math.max(0,millis));if(remaining()==0)step();}
        public void step(){if(finished)return;if(remaining()>0)time=run.until;
            engine.gameState(game,time);engine.begin(time,state);state.clear();boolean alive=run.tick(engine,time,1);node=run.lastPc;evaluation=run.evaluation;
            held.clear();if(alive)held.addAll(run.held);else finished=true;
            for(String output:held)engine.bind(output);
            if(alive&&node>=0){Step s=program.steps.get(node);events.add(time+" ms · "+(node+1)+" · "+s.op+" "+s.arg+(s.op.equals("if")?" · "+evaluation:""));if(events.size()>256)events.remove(0);}
            if(time>=300000){finished=true;held.clear();state.clear();events.add("Stopped: five-minute execution limit");}
        }
        public void stop(){finished=true;held.clear();state.clear();}
    }
    static final class Run {
        final Program program;final long started;final String owner;int pc,lastPc=-1;String evaluation="",steer;long until;final Set<String> held=new LinkedHashSet<>();final Map<Integer,Integer> repeats=new HashMap<>();
        Run(Program p,long now){this(p,now,"");}Run(Program p,long now,String owner){program=p;started=now;this.owner=owner;}
        boolean tick(Automation a,long now){return tick(a,now,64);}
        boolean tick(Automation a,long now,int limit){if(now<until)return true;for(int budget=0;budget<limit;budget++){if(pc>=program.steps.size())return false;lastPc=pc;Step s=program.steps.get(pc);switch(s.op){case "press":Collections.addAll(held,s.arg.split("\\s*\\+\\s*"));pc++;break;case "release":for(String token:s.arg.split("\\s*\\+\\s*"))held.remove(token);pc++;break;case "wait":until=now+Integer.parseInt(s.arg);pc++;return true;case "steer":steer=s.arg;return true;case "if":boolean result=a.matches(s.arg);evaluation=s.arg+" → "+(result?"TRUE":"FALSE");pc=result?pc+1:s.jump;break;case "else":pc=s.jump;break;case "repeat":repeats.putIfAbsent(pc,Integer.parseInt(s.arg));pc++;break;case "end":Step open=program.steps.get(s.jump);if(open.op.equals("repeat")){int n=repeats.getOrDefault(s.jump,1);if(n==0)pc=s.jump+1;else if(n>1){repeats.put(s.jump,n-1);pc=s.jump+1;}else{repeats.remove(s.jump);pc++;}}else pc++;break;default:return false;}}return true;}
    }
}
