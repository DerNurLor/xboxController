package dev.phonepad;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** A visual timeline over the existing bounded macro language; no execution translation. */
final class MacroEditor {
    private static final class Node {
        String op,arg;
        Node(String op,String arg){this.op=op;this.arg=arg;}
        String line(){return op+(arg.isEmpty()?"":" "+arg);}
    }
    private final PadView pad;
    private final Context context;
    private final String id,original;
    private final Dialog dialog;
    private final LinearLayout root,header,rail,library;
    private final ArrayList<Node> nodes=new ArrayList<>();
    private final Runnable finished;
    private boolean saved,historyRestore,previousSettings;
    private String draftKey;
    private final ArrayDeque<String> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private String lastProgram;
    private Automation.Preview simulation;private boolean playing;
    private final android.os.Handler clock=new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable frame=new Runnable(){public void run(){if(!playing||simulation==null)return;simulation.advance(50);renderPreview();if(simulation.finished)playing=false;else clock.postDelayed(this,50);}};
    private LinearLayout previewPanel;private final ArrayList<View> nodeViews=new ArrayList<>();
    static Dialog show(PadView pad,String control,Runnable done){pad.config.migrateMacros();for(ControllerConfig.MacroAssignment a:pad.config.macroAssignments)if(a.controlId.equals(control))return showMacro(pad,a.macroId,done);MacroLibrary.create(pad,control,done);return null;}
    static Dialog showMacro(PadView pad,String id,Runnable done){ControllerConfig.Macro m=pad.config.macro(id);if(m==null)return null;MacroEditor editor=new MacroEditor(pad,m,done);editor.open();return editor.dialog;}
    private MacroEditor(PadView pad,ControllerConfig.Macro m,Runnable done){
        this.pad=pad;context=pad.getContext();id=m.id;finished=done;original=m.program;draftKey="macro-draft:"+pad.currentLayoutId()+":"+m.id;android.content.SharedPreferences drafts=context.getSharedPreferences("editor-drafts",0);load(drafts.getString(draftKey+":base","").equals(m.program)?drafts.getString(draftKey,m.program):m.program);lastProgram=program();
        dialog=new Dialog(context,R.style.AppTheme){@Override public void onBackPressed(){close();}};
        root=EditorStyle.column(context);root.setBackgroundColor(EditorStyle.BG);header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);root.addView(header,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout split=new LinearLayout(context);root.addView(split,new LinearLayout.LayoutParams(-1,0,1));ScrollView scroll=new ScrollView(context);rail=EditorStyle.column(context);rail.setPadding(dp(8),0,dp(8),dp(8));scroll.addView(rail);split.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));
        ScrollView sidebar=new ScrollView(context);library=EditorStyle.column(context);library.setPadding(dp(8),0,dp(8),dp(8));library.setBackground(EditorStyle.background(context,EditorStyle.PANEL));sidebar.addView(library);split.addView(sidebar,new LinearLayout.LayoutParams(dp(context.getResources().getConfiguration().screenWidthDp<600?150:205),-1));
    }
    private int dp(float n){return EditorStyle.dp(context,n);}
    private Button button(String t,boolean active,Runnable r){return EditorStyle.button(context,t,active,r);}
    private TextView text(String t,int size,int color){return EditorStyle.text(context,t,size,color);}
    private void add(LinearLayout row,View v,int w,int h){EditorStyle.add(row,v,w,h);}
    private void open(){pad.release();previousSettings=pad.settingsOpen;pad.settingsOpen=true;render();dialog.setContentView(root);dialog.setOnDismissListener(d->{playing=false;clock.removeCallbacks(frame);pad.settingsOpen=previousSettings;pad.release();finished.run();});EditorStyle.fullscreen(dialog,root);}
    private String program(){StringJoiner s=new StringJoiner("\n");for(Node n:nodes)s.add(n.line());return s.toString();}
    private boolean dirty(){return !program().equals(original.trim());}
    private void close(){if(saved||!dirty()){dialog.dismiss();return;}new AlertDialog.Builder(context).setTitle("Discard macro changes?").setNegativeButton("Keep editing",null).setPositiveButton("Discard",(d,w)->{clearDraft();dialog.dismiss();}).show();}
    private void save(){String program=program();try{Automation.compile(program);}catch(IllegalArgumentException error){new AlertDialog.Builder(context).setTitle("Check the timeline").setMessage(error.getMessage()).setPositiveButton("OK",null).show();return;}
        pad.requestLayoutEdit(()->{ControllerConfig.Macro live=pad.config.macro(id);if(live==null)return;pad.mutate(()->{live.program=program;if(Automation.containsSteer(program))for(ControllerConfig.MacroAssignment assignment:pad.config.macroAssignments)if(assignment.macroId.equals(id))assignment.trigger="HOLD";});saved=true;clearDraft();dialog.dismiss();},()->{});
    }
    private void render(){
        String current=program();if(!current.equals(original))context.getSharedPreferences("editor-drafts",0).edit().putString(draftKey,current).putString(draftKey+":base",original).apply();else clearDraft();if(!historyRestore&&!current.equals(lastProgram)){undo.addLast(lastProgram);if(undo.size()>80)undo.removeFirst();redo.clear();}lastProgram=current;historyRestore=false;
        playing=false;clock.removeCallbacks(frame);simulation=null;
        header.removeAllViews();add(header,button("×",false,this::close),40,40);ControllerConfig.Macro macro=pad.config.macro(id);TextView title=text(macro==null?"Macro":macro.name,16,EditorStyle.TEXT);title.setSingleLine();header.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        Button back=button("↶",false,()->history(false));back.setEnabled(!undo.isEmpty());add(header,back,40,40);Button forward=button("↷",false,()->history(true));forward.setEnabled(!redo.isEmpty());add(header,forward,40,40);add(header,button("Test",false,this::preview),60,40);add(header,button("Save",true,this::save),64,40);
        library.removeAllViews();library.addView(text("AVAILABLE ACTIONS",11,EditorStyle.MUTED));
        for(String[] item:new String[][]{{"press","↓  Press / group"},{"release","↑  Release"},{"steer","↗  Steer stick"},{"wait","◷  Delay"},{"repeat","↻  Repeat"},{"if","◇  Condition"}}){Button b=button(item[1],false,()->insert(item[0],nodes.size()));b.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);b.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("Macro action",item[0]),new View.DragShadowBuilder(v),"new:"+item[0],0);return true;});add(library,b,-1,38);}
        library.addView(text("TRIGGERS",11,EditorStyle.MUTED));add(library,button("Assign controls",false,()->MacroLibrary.assignments(pad,id,()->{})),-1,44);
        if(macro!=null&&!macro.condition.isEmpty())library.addView(text("When "+macro.condition,12,EditorStyle.TEAL));
        library.addView(text("Simultaneous outputs",11,EditorStyle.MUTED));add(library,button("＋ Press group",false,()->insert("press",nodes.size())),-1,34);
        if(!nodes.isEmpty())add(library,button("Clear timeline",false,()->new AlertDialog.Builder(context).setTitle("Clear all actions?").setNegativeButton("Cancel",null).setPositiveButton("Clear",(d,w)->{nodes.clear();render();}).show()),-1,34);
        rail.removeAllViews();nodeViews.clear();rail.addView(text("●  START",11,EditorStyle.TEAL));int depth=0;
        for(int i=0;i<nodes.size();i++){final int index=i;Node n=nodes.get(i);if(n.op.equals("end")||n.op.equals("else"))depth=Math.max(0,depth-1);LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(0,dp(3),0,dp(3));
            row.addView(new Connection(context),new LinearLayout.LayoutParams(dp(22+depth*12),dp(48)));
            LinearLayout card=new LinearLayout(context);card.setGravity(Gravity.CENTER_VERTICAL);card.setBackground(EditorStyle.background(context,n.op.equals("if")||n.op.equals("else")?0xff343052:n.op.equals("wait")?0xff263e40:n.op.equals("repeat")?0xff453b29:EditorStyle.PANEL));row.addView(card,new LinearLayout.LayoutParams(0,dp(45),1));
            Button type=button(symbol(n.op)+" "+label(n.op),false,()->edit(index));add(card,type,n.op.equals("release")?95:85,35);
            View value;if(n.op.equals("press")||n.op.equals("release"))value=new InputTokens(context,n.arg);else{TextView label=text(value(n),12,n.op.equals("wait")?EditorStyle.TEAL:EditorStyle.TEXT);label.setMaxLines(2);label.setEllipsize(android.text.TextUtils.TruncateAt.END);value=label;}value.setOnClickListener(v->edit(index));card.addView(value,new LinearLayout.LayoutParams(0,-1,1));
            add(card,button("＋",false,()->insertMenu(index+1)),40,40);add(card,button("⋮",false,()->actions(index)),40,40);row.setContentDescription("Action "+(i+1)+" "+n.op+" "+n.arg);
            row.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("Timeline action",Integer.toString(index)),new View.DragShadowBuilder(v),Integer.valueOf(index),0);return true;});
            View.OnLongClickListener drag=v->row.performLongClick();value.setOnLongClickListener(drag);type.setOnLongClickListener(drag);
            dropTarget(row,index);rail.addView(row);nodeViews.add(card);if(n.op.equals("repeat")||n.op.equals("if")||n.op.equals("else"))depth++;
        }
        Button end=button("＋ Add action",true,()->new AlertDialog.Builder(context).setItems(new String[]{"Press","Release","Steer stick","Delay","Repeat","Condition"},(d,n)->insert(new String[]{"press","release","steer","wait","repeat","if"}[n],nodes.size())).show());dropTarget(end,nodes.size());add(rail,end,-1,36);rail.addView(text("○  END",11,EditorStyle.MUTED));
    }
    private void dropTarget(View view,int index){view.setOnDragListener((v,e)->{Object state=e.getLocalState();if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return state instanceof Integer||state instanceof String&&((String)state).startsWith("new:");if(e.getAction()==DragEvent.ACTION_DRAG_ENTERED)v.setAlpha(.5f);if(e.getAction()==DragEvent.ACTION_DRAG_EXITED||e.getAction()==DragEvent.ACTION_DRAG_ENDED)v.setAlpha(1);if(e.getAction()==DragEvent.ACTION_DROP){v.setAlpha(1);if(state instanceof Integer)move((Integer)state,index);else insert(((String)state).substring(4),index);}return true;});}
    private int endOf(int index){Node n=nodes.get(index);if(!n.op.equals("repeat")&&!n.op.equals("if"))return index+1;int depth=1;for(int i=index+1;i<nodes.size();i++){String op=nodes.get(i).op;if(op.equals("repeat")||op.equals("if"))depth++;if(op.equals("end")&&--depth==0)return i+1;}return nodes.size();}
    private void move(int from,int to){if(nodes.get(from).op.equals("end")||nodes.get(from).op.equals("else")){toast("Move the whole condition or repeat block");return;}String before=program();int end=endOf(from);if(to>=from&&to<=end)return;List<Node> block=new ArrayList<>(nodes.subList(from,end));nodes.subList(from,end).clear();if(to>end)to-=block.size();nodes.addAll(Math.max(0,Math.min(to,nodes.size())),block);try{Automation.compile(program());}catch(IllegalArgumentException invalid){load(before);toast("Keep actions inside a valid branch");}render();}
    private void actions(int at){Node node=nodes.get(at);new AlertDialog.Builder(context).setItems(new String[]{"Edit","Duplicate","Move up","Move down","Remove"},(d,which)->{
        if(which==0){edit(at);return;}int end=endOf(at);
        if(which==1){if(node.op.equals("end")||node.op.equals("else")){toast("Duplicate the complete block");return;}ArrayList<Node> copy=new ArrayList<>();for(Node n:nodes.subList(at,end))copy.add(new Node(n.op,n.arg));nodes.addAll(end,copy);}
        if(which==2&&at>0)move(at,at-1);if(which==3&&end<nodes.size())move(at,endOf(end));if(which==4){if(node.op.equals("end")||node.op.equals("else")){toast("Remove the condition or repeat block instead");return;}nodes.subList(at,end).clear();}render();}).show();}
    private void insert(String type,int index){
        if(type.equals("steer")){new AlertDialog.Builder(context).setTitle("Steer which stick?").setItems(new String[]{"Left stick","Right stick"},(d,n)->{nodes.add(index,new Node("steer",n==0?"LEFT":"RIGHT"));render();}).show();}
        else if(type.equals("press")||type.equals("release")){AutomationEditor.chooseOutputs(pad,"",outputs->{if(outputs.isEmpty())return;nodes.add(index,new Node(type,outputs));render();});}
        else if(type.equals("wait"))number("Delay · ms",100,1,60000,n->{nodes.add(index,new Node("wait",Integer.toString(n)));render();});
        else if(type.equals("repeat"))number("Repeat · 0 = until stopped",3,0,1000,n->{nodes.add(index,new Node("repeat",Integer.toString(n)));nodes.add(index+1,new Node("end",""));render();});
        else condition("vehicle=true",value->{nodes.add(index,new Node("if",value));nodes.add(index+1,new Node("else",""));nodes.add(index+2,new Node("end",""));render();});
    }
    private void edit(int at){Node n=nodes.get(at);switch(n.op){case "press":case "release":AutomationEditor.chooseOutputs(pad,n.arg,s->{if(!s.isEmpty()){n.arg=s;render();}});break;case "steer":new AlertDialog.Builder(context).setTitle("Steer which stick?").setItems(new String[]{"Left stick","Right stick"},(d,which)->{n.arg=which==0?"LEFT":"RIGHT";render();}).show();break;case "wait":case "repeat":number(n.op.equals("wait")?"Delay · ms":"Repeat · 0 = until stopped",Integer.parseInt(n.arg),n.op.equals("wait")?1:0,n.op.equals("wait")?60000:1000,v->{n.arg=Integer.toString(v);render();});break;case "if":condition(n.arg,s->{n.arg=s;render();});break;case "else":case "end":new AlertDialog.Builder(context).setItems(new String[]{"Insert press before","Insert delay before"},(d,w)->insert(w==0?"press":"wait",at)).show();break;default:toast("Preserved annotation");}}
    private void number(String title,int initial,int min,int max,java.util.function.IntConsumer done){library.removeAllViews();library.addView(text(title,16,EditorStyle.TEXT));EditText field=new EditText(context);field.setSingleLine();field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);field.setText(Integer.toString(initial));field.setTextColor(EditorStyle.TEXT);library.addView(field);library.addView(text("Range "+min+"–"+max,12,EditorStyle.MUTED));for(int preset:new int[]{50,100,250,500})if(preset>=min&&preset<=max)add(library,button(Integer.toString(preset),false,()->field.setText(Integer.toString(preset))),-1,40);add(library,button("Apply",true,()->{try{int value=Integer.parseInt(field.getText().toString());if(value<min||value>max)throw new NumberFormatException();done.accept(value);}catch(NumberFormatException error){field.setError("Use "+min+" to "+max);}}),-1,44);add(library,button("Cancel",false,this::render),-1,44);}
    private void condition(String current,java.util.function.Consumer<String> done){library.removeAllViews();library.addView(text("Branch condition",16,EditorStyle.TEXT));EditText field=new EditText(context);field.setSingleLine();field.setText(current);field.setTextColor(EditorStyle.TEXT);library.addView(field);library.addView(text("TRUE follows the first path. FALSE follows Else. Missing game state is false.",12,EditorStyle.MUTED));for(String value:new String[]{"vehicle=true","vehicle=false","weapon=true","onFoot=true","input.LT=true","input.RT=true"})add(library,button(value,false,()->field.setText(value)),-1,40);add(library,button("Apply",true,()->{String value=field.getText().toString().trim();try{Automation.validateCondition(value);if(value.isEmpty())throw new IllegalArgumentException("Enter a state=value condition");done.accept(value);}catch(IllegalArgumentException error){field.setError(error.getMessage());}}),-1,44);add(library,button("Cancel",false,this::render),-1,44);}
    private void clearDraft(){context.getSharedPreferences("editor-drafts",0).edit().remove(draftKey).remove(draftKey+":base").apply();}
    private void load(String text){nodes.clear();for(String line:text.split("\n")){String trimmed=line.trim();if(trimmed.isEmpty())continue;String[] p=trimmed.split("\\s+",2);nodes.add(new Node(p[0].toLowerCase(Locale.ROOT),p.length>1?p[1]:""));}}
    private void history(boolean forward){ArrayDeque<String> from=forward?redo:undo,to=forward?undo:redo;if(from.isEmpty())return;to.addLast(program());load(from.removeLast());historyRestore=true;render();}
    private void insertMenu(int at){new AlertDialog.Builder(context).setItems(new String[]{"Press","Release","Steer stick","Delay","Repeat","Condition"},(d,n)->insert(new String[]{"press","release","steer","wait","repeat","if"}[n],at)).show();}
    private void preview(){try{simulation=new Automation.Preview(program());}catch(IllegalArgumentException error){toast(error.getMessage());return;}playing=false;clock.removeCallbacks(frame);library.removeAllViews();previewPanel=EditorStyle.column(context);library.addView(previewPanel);renderPreview();}
    private void renderPreview(){if(simulation==null||previewPanel==null)return;previewPanel.removeAllViews();previewPanel.addView(text("PREVIEW · PHONE ONLY",11,EditorStyle.TEAL));
        LinearLayout controls=new LinearLayout(context);previewPanel.addView(controls);add(controls,button(playing?"Ⅱ":"▶",false,()->{playing=!playing;if(playing){if(simulation.finished)simulation.restart();clock.post(frame);}else clock.removeCallbacks(frame);renderPreview();}),44,44);add(controls,button("Step",false,()->{playing=false;clock.removeCallbacks(frame);simulation.step();renderPreview();}),54,44);add(controls,button("■",false,()->{playing=false;clock.removeCallbacks(frame);simulation.stop();renderPreview();}),44,44);
        add(previewPanel,button("Restart",false,()->{playing=false;clock.removeCallbacks(frame);simulation.restart();renderPreview();}),-1,40);
        add(previewPanel,button("Simulated state",false,this::stateValues),-1,40);
        previewPanel.addView(text(simulation.time+" ms · "+(simulation.finished?"Finished":simulation.remaining()>0?"Wait "+simulation.remaining()+" ms":"Action "+(simulation.node+1)),13,EditorStyle.TEXT));
        previewPanel.addView(text(simulation.held.isEmpty()?"All controls released":"[ "+String.join(" ] [ ",simulation.held)+" ]",14,EditorStyle.TEAL));previewPanel.addView(text(simulation.evaluation+"\n"+simulation.game,12,EditorStyle.MUTED));
        for(int i=Math.max(0,simulation.events.size()-12);i<simulation.events.size();i++)previewPanel.addView(text(simulation.events.get(i),11,EditorStyle.MUTED));
        add(previewPanel,button("Back to actions",false,this::render),-1,40);
        int executable=0;for(int i=0;i<nodes.size();i++){boolean note=nodes.get(i).op.startsWith("#");nodeViews.get(i).setAlpha(!note&&executable==simulation.node?1f:.5f);if(!note)executable++;}
    }
    private void stateValues(){playing=false;clock.removeCallbacks(frame);EditText field=new EditText(context);StringJoiner values=new StringJoiner("\n");simulation.game.forEach((k,v)->values.add(k+"="+v));field.setText(values.toString());field.setHint("vehicle=false\nweapon=true");new AlertDialog.Builder(context).setTitle("Simulated game state").setView(field).setNegativeButton("Cancel",null).setPositiveButton("Apply",(d,w)->{simulation.game.clear();for(String line:field.getText().toString().split("\n")){String[] p=line.split("=",2);if(p.length==2)simulation.game.put(p[0].trim(),p[1].trim());}renderPreview();}).show();}
    private static String label(String op){switch(op){case "press":return "Press";case "release":return "Release";case "steer":return "Steer";case "wait":return "Delay";case "repeat":return "Repeat";case "if":return "TRUE ↳";case "else":return "FALSE ↳";case "end":return "End";default:return "Note";}}
    private static String symbol(String op){switch(op){case "press":return "↓";case "release":return "↑";case "steer":return "↗";case "wait":return "◷";case "repeat":return "↻";case "if":return "◇";default:return "·";}}
    private static String value(Node n){if(n.op.equals("steer"))return n.arg.equals("LEFT")?"Left stick · touch vector":"Right stick · touch vector";if(n.op.equals("wait"))return n.arg+" ms";if(n.op.equals("repeat"))return n.arg.equals("0")?"∞ until stopped":n.arg+" times";return n.arg.replace("KEY_","").replace("MOUSE_","Mouse ").replace("+"," + ");}
    private void toast(String s){Toast.makeText(context,s,Toast.LENGTH_SHORT).show();}
    private static final class Connection extends View {private final Paint p=new Paint(3);Connection(Context c){super(c);}protected void onDraw(Canvas c){float x=getWidth()-EditorStyle.dp(getContext(),11);p.setColor(EditorStyle.LINE);p.setStrokeWidth(EditorStyle.dp(getContext(),1));c.drawLine(x,0,x,getHeight(),p);p.setColor(EditorStyle.TEAL);c.drawCircle(x,getHeight()/2f,EditorStyle.dp(getContext(),3),p);}}
}
