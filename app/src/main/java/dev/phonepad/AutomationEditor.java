package dev.phonepad;

import android.app.*;
import android.content.*;
import android.hardware.input.InputManager;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Full-screen device workspace. Data continues to live in the active layout. */
final class AutomationEditor {
    private final PadView pad;
    private final Context context;
    private final Dialog dialog;
    private final LinearLayout root;
    private final FrameLayout body;
    private final InputManager manager;
    private final InputManager.InputDeviceListener listener;
    private LinearLayout toolbar,tabs,panel;
    private DeviceDiagram diagram;
    private String category="KEYBOARD",input="",target="",direction="",replace="";
    private String mappingId="";
    private final LinkedHashSet<String> keyboardSelection=new LinkedHashSet<>();
    private final Map<String,String> selections=new HashMap<>();
    private boolean panelOpen=false,pinned=false,targetArmed=false,inputArmed=false,registered=false;

    static Dialog show(PadView p){return new AutomationEditor(p,p.editorSelection()==null?"":p.editorSelection().id,false).open();}
    static Dialog show(PadView p,String id){return new AutomationEditor(p,id,false).open();}
    static Dialog showMacros(PadView p){return new AutomationEditor(p,p.editorSelection()==null?"":p.editorSelection().id,true).open();}
    private AutomationEditor(PadView p,String id,boolean macros){
        pad=p;context=p.getContext();target=id;panelOpen=!id.isEmpty();for(ControllerConfig.Control c:p.config.controls)c.migrateMappings(p.config.deadzone);p.config.migrateMacros();if(p.config.get(id)!=null)direction=directions(p.config.get(id)).get(0);if(macros)category="MACROS";
        dialog=new Dialog(context,R.style.AppTheme){@Override public boolean dispatchKeyEvent(KeyEvent e){String name=HardwareInput.name(e);if(!category.equals("MACROS")&&e.getAction()==KeyEvent.ACTION_DOWN&&name!=null&&e.getKeyCode()!=KeyEvent.KEYCODE_BACK){pickInput(name);return true;}return super.dispatchKeyEvent(e);}};
        root=EditorStyle.column(context);root.setBackgroundColor(EditorStyle.BG);
        toolbar=new LinearLayout(context);toolbar.setGravity(Gravity.CENTER_VERTICAL);root.addView(toolbar,new LinearLayout.LayoutParams(-1,dp(42)));
        tabs=new LinearLayout(context);root.addView(tabs,new LinearLayout.LayoutParams(-1,dp(38)));
        body=new FrameLayout(context);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        manager=(InputManager)context.getSystemService(Context.INPUT_SERVICE);
        listener=new InputManager.InputDeviceListener(){public void onInputDeviceAdded(int id){render();}public void onInputDeviceChanged(int id){render();}public void onInputDeviceRemoved(int id){pad.release();render();}};
    }
    private int dp(float v){return EditorStyle.dp(context,v);}
    private Button button(String t,boolean active,Runnable r){return EditorStyle.button(context,t,active,r);}
    private TextView text(String t,int size,int color){return EditorStyle.text(context,t,size,color);}
    private void add(LinearLayout row,View child,int width,int height){EditorStyle.add(row,child,width,height);}
    private Dialog open(){pad.release();pad.settingsOpen=true;dialog.setContentView(root);dialog.setOnDismissListener(d->{if(manager!=null&&registered)manager.unregisterInputDeviceListener(listener);pad.settingsOpen=false;pad.release();pad.editorRefresh();});if(manager!=null){manager.registerInputDeviceListener(listener,new Handler(Looper.getMainLooper()));registered=true;}render();EditorStyle.fullscreen(dialog,root);return dialog;}
    private void render(){
        toolbar.removeAllViews();add(toolbar,button("×",false,dialog::dismiss),40,34);toolbar.addView(text(category.equals("MACROS")?"Macros":"PC controls",18,EditorStyle.TEXT),new LinearLayout.LayoutParams(0,-1,1));
        if(!category.equals("MACROS")){List<InputDevice> devices=devices();String status="Select an action, then a control";TextView state=text(status,11,devices.isEmpty()?EditorStyle.MUTED:EditorStyle.TEAL);state.setMaxLines(1);state.setEllipsize(android.text.TextUtils.TruncateAt.END);if(context.getResources().getConfiguration().screenWidthDp>=700)toolbar.addView(state,new LinearLayout.LayoutParams(dp(170),-1));add(toolbar,button("↶",false,()->{pad.history(false);for(ControllerConfig.Control c:pad.config.controls)c.migrateMappings(pad.config.deadzone);render();}),40,32);add(toolbar,button("↷",false,()->{pad.history(true);for(ControllerConfig.Control c:pad.config.controls)c.migrateMappings(pad.config.deadzone);render();}),40,32);add(toolbar,button(panelOpen?"Panel ›":"‹ Panel",false,()->{panelOpen=!panelOpen;render();}),80,32);}
        tabs.removeAllViews();for(String kind:new String[]{"KEYBOARD","MOUSE","MACROS"}){Button tab=button(kind.equals("CONTROLLER")?"Controllers":kind.substring(0,1)+kind.substring(1).toLowerCase(Locale.ROOT),category.equals(kind),()->{if(!category.equals("KEYBOARD"))selections.put(category,input);category=kind;mappingId="";keyboardSelection.clear();input=kind.equals("KEYBOARD")?"":selections.getOrDefault(kind,"");inputArmed=!input.isEmpty();replace="";render();});add(tabs,tab,110,32);}
        if(!category.equals("MACROS")){add(tabs,button("−",false,()->diagram.zoomBy(.8f)),40,32);add(tabs,button("＋",false,()->diagram.zoomBy(1.25f)),40,32);}
        body.removeAllViews();if(category.equals("MACROS")){macroLibrary();return;}
        LinearLayout split=new LinearLayout(context);body.addView(split,new FrameLayout.LayoutParams(-1,-1));
        diagram=new DeviceDiagram(context,category,new DeviceDiagram.Listener(){public void select(String token){pickInput(token);}public void drop(String token,String data){String[] p=data.substring(7).split(":",2);bind(p[0],p.length>1?p[1]:"",token);}});
        split.addView(diagram,new LinearLayout.LayoutParams(0,-1,1));
        panel=EditorStyle.column(context);panel.setPadding(dp(6),dp(3),dp(6),dp(5));panel.setBackground(EditorStyle.background(context,EditorStyle.PANEL));
        if(panelOpen){if(context.getResources().getConfiguration().screenWidthDp>=680)split.addView(panel,new LinearLayout.LayoutParams(dp(260),-1));else{FrameLayout.LayoutParams overlay=new FrameLayout.LayoutParams(Math.min(dp(270),context.getResources().getDisplayMetrics().widthPixels-dp(72)),-1,Gravity.END);body.addView(panel,overlay);}}renderPanel();updateDiagram();
    }
    private void pickInput(String token){
        try{if(!token.equals("MOUSE_MOVE"))DesktopBindings.output(token);}catch(IllegalArgumentException ignored){return;}
        if(category.equals("KEYBOARD")){
            if(!keyboardSelection.remove(token))keyboardSelection.add(token);
            input=String.join("+",keyboardSelection);mappingId="";inputArmed=!input.isEmpty();
            if(!panelOpen){panelOpen=true;render();}else{renderPanel();updateDiagram();}return;
        }
        input=token;mappingId="";if(targetArmed&&!target.isEmpty()){targetArmed=false;bind(target,direction,token);return;}
        inputArmed=true;if(!panelOpen){panelOpen=true;render();}else{renderPanel();updateDiagram();}
    }
    private void pickTarget(String id,String dir){target=id;direction=dir;if(!input.isEmpty()){inputArmed=false;bind(id,dir,input);return;}targetArmed=true;replace="";renderPanel();updateDiagram();}
    private List<String> targetDirections(ControllerConfig.Control c){return input.equals("MOUSE_MOVE")?Collections.singletonList(""):directions(c);}
    private void updateDiagram(){
        if(diagram==null)return;Set<String> mapped=new HashSet<>();
        List<String> tokens=new ArrayList<>();for(String token:HardwareInput.sources())if(token.startsWith("KEY_")||token.startsWith("MOUSE_")||token.startsWith("WHEEL_"))tokens.add(token);tokens.add("MOUSE_MOVE");
        for(ControllerConfig.Control c:pad.config.controls)for(String token:tokens)for(String d:directions(c))if(DesktopBindings.contains(c,d,token))mapped.add(token);
        if(category.equals("KEYBOARD"))mapped.addAll(keyboardSelection);
        diagram.state(input,mapped,null);
    }
    private void renderPanel(){if(panel==null)return;panel.removeAllViews();
        LinearLayout head=new LinearLayout(context);head.addView(text(input.isEmpty()?"Select a PC action":DeviceDiagram.shortName(input),16,EditorStyle.TEAL),new LinearLayout.LayoutParams(0,dp(44),1));add(head,button(pinned?"Unpin":"Pin",pinned,()->{pinned=!pinned;renderPanel();}),54,40);add(head,button("×",false,()->{panelOpen=false;render();}),40,40);panel.addView(head);
        ScrollView scroll=new ScrollView(context);LinearLayout content=EditorStyle.column(context);scroll.addView(content);panel.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        if(!input.isEmpty()){
            content.addView(text("Assigned controls",12,EditorStyle.MUTED));boolean found=false;
            for(ControllerConfig.Control c:pad.config.controls)for(String d:targetDirections(c))if(DesktopBindings.contains(c,d,input)){
                found=true;LinearLayout row=new LinearLayout(context);
                row.addView(text(c.title()+directionLabel(d),14,EditorStyle.TEXT),new LinearLayout.LayoutParams(0,dp(44),1));
                final String token=input;add(row,button("×",false,()->edit(()->DesktopBindings.remove(pad.config.get(c.id),d,token))),40,40);content.addView(row);
            }
            content.addView(text(category.equals("KEYBOARD")?"Tap more keys, then choose a control.":found?"Select another control to assign this action.":"Choose the on-screen control that should send this action.",13,EditorStyle.TEXT));
        }else content.addView(text("Select a key, mouse button, scroll direction or Pointer on the diagram.",13,EditorStyle.TEXT));
        if(!input.isEmpty()&&!input.equals("MOUSE_MOVE"))content.addView(button("New custom button",false,()->{
            final String chosen=input;
            edit(()->{ControllerConfig.Control created=new ControllerConfig.Control("custom","button",0,.5f,.5f,.12f,.16f);created.buttons=0;created.label=names(chosen);DesktopBindings.assign(created,"",chosen);pad.addControl(created);});
        }));
        content.addView(text(targetArmed?(category.equals("KEYBOARD")?"Select keys, then choose a control.":"Now select a PC action"):"Layout controls",12,EditorStyle.MUTED));
        for(ControllerConfig.Control control:pad.config.controls){if(!mappable(control)||input.equals("MOUSE_MOVE")&&!DesktopBindings.pointerControl(control))continue;for(String dir:targetDirections(control)){
            boolean assigned=!input.isEmpty()&&DesktopBindings.contains(control,dir,input);
            Button item=button(control.title()+directionLabel(dir)+(assigned?"  ✓":""),assigned,()->pickTarget(control.id,dir));item.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);item.setCompoundDrawablesWithIntrinsicBounds(InputTokens.icon(context,control.title()),null,null,null);
            item.setOnLongClickListener(v->{v.startDragAndDrop(ClipData.newPlainText("Layout control",control.id),new View.DragShadowBuilder(v),"target:"+control.id+":"+dir,0);return true;});
            item.setOnDragListener((v,e)->{if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return e.getLocalState() instanceof String&&((String)e.getLocalState()).startsWith("input:");if(e.getAction()==DragEvent.ACTION_DRAG_ENTERED)v.setAlpha(.6f);if(e.getAction()==DragEvent.ACTION_DRAG_EXITED||e.getAction()==DragEvent.ACTION_DRAG_ENDED)v.setAlpha(1);if(e.getAction()==DragEvent.ACTION_DROP){v.setAlpha(1);bind(control.id,dir,((String)e.getLocalState()).substring(6));}return true;});add(content,item,-1,44);
        }}
    }
    private void slider(LinearLayout box,String title,int min,int max,int value,java.util.function.IntConsumer change){TextView label=text(title+"  "+value+"%",11,EditorStyle.MUTED);box.addView(label);SeekBar seek=new SeekBar(context);seek.setMax(max-min);seek.setProgress(Math.max(0,value-min));seek.setProgressTintList(android.content.res.ColorStateList.valueOf(EditorStyle.TEAL));box.addView(seek,new LinearLayout.LayoutParams(-1,dp(30)));seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user)label.setText(title+"  "+(min+n)+"%");}public void onStopTrackingTouch(SeekBar s){int n=min+s.getProgress();edit(()->change.accept(n));}});}
    private void bind(String id,String dir,String token){
        ControllerConfig.Control c=pad.config.get(id);if(c==null)return;
        if(token.equals("MOUSE_MOVE")&&!DesktopBindings.pointerControl(c)){Toast.makeText(context,"Choose a stick, trackpad or D-pad for pointer movement",Toast.LENGTH_SHORT).show();return;}
        try{if(!token.equals("MOUSE_MOVE"))DesktopBindings.output(token);}catch(IllegalArgumentException ignored){return;}
        target=id;direction=dir;input=token;replace="";inputArmed=true;targetArmed=false;panelOpen=true;
        edit(()->DesktopBindings.assign(pad.config.get(id),dir,token));
    }
    private void outputs(ControllerConfig.Control c){String id=c.id,dir=direction;String initial=dir.isEmpty()?c.binding:output(c,dir);chooseOutputs(pad,initial,chosen->edit(()->{ControllerConfig.Control live=pad.config.get(id);if(dir.isEmpty())live.binding=chosen;else setOutput(live,dir,chosen);}));}
    private void edit(Runnable mutation){pad.requestLayoutEdit(()->{pad.mutate(mutation);render();},()->{});}
    private void macroLibrary(){MacroLibrary.render(pad,body,this::render);}
    private List<InputDevice> devices(){ArrayList<InputDevice> out=new ArrayList<>();for(int id:InputDevice.getDeviceIds()){InputDevice d=InputDevice.getDevice(id);if(d==null||d.isVirtual()||Build.VERSION.SDK_INT>=29&&!d.isExternal())continue;int source=category.equals("MOUSE")?InputDevice.SOURCE_MOUSE:category.equals("KEYBOARD")?InputDevice.SOURCE_KEYBOARD:InputDevice.SOURCE_GAMEPAD;if(d.supportsSource(source)||category.equals("CONTROLLER")&&d.supportsSource(InputDevice.SOURCE_JOYSTICK))out.add(d);}return out;}
    private Set<String> available(){List<InputDevice> ds=devices();if(ds.isEmpty())return Collections.emptySet();if(category.equals("MOUSE"))return null;Set<String> out=new HashSet<>();for(InputDevice device:ds){for(Map.Entry<Integer,String> k:HardwareInput.NAMES.entrySet())if(device.hasKeys(k.getKey())[0])out.add(k.getValue());if(category.equals("CONTROLLER")){int[] axes={MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y};String[] names={"LX","LY","RX","RY","LT","RT","HATX","HATY"};for(int i=0;i<axes.length;i++)if(device.getMotionRange(axes[i])!=null){out.add("AXIS_"+names[i]+"_POS");out.add("AXIS_"+names[i]+"_NEG");}}}return out;}
    static boolean mappable(ControllerConfig.Control c){return Arrays.asList("button","combo","stick","trigger","unified","dpad","trackpad","fire","aim","hold").contains(c.kind);}
    static List<String> directions(ControllerConfig.Control c){if(c.kind.equals("stick")||c.kind.equals("dpad")||c.kind.equals("trackpad"))return !c.source.isEmpty()||!c.binding.isEmpty()?Arrays.asList("","up","down","left","right"):Arrays.asList("up","down","left","right");if(c.kind.equals("unified"))return Arrays.asList("up","down");return Collections.singletonList("");}
    static String source(ControllerConfig.Control c,String d){if(c.independentMappings){StringJoiner out=new StringJoiner("|");for(ControllerConfig.Mapping m:c.mappings)if(m.direction.equals(d))out.add(m.source);return out.toString();}switch(d){case "up":return c.sourceUp;case "down":return c.sourceDown;case "left":return c.sourceLeft;case "right":return c.sourceRight;default:return c.source;}}
    static void setSource(ControllerConfig.Control c,String d,String s){if(c.independentMappings){c.mappings.removeIf(m->m.direction.equals(d));for(String token:s.split("\\|"))if(!token.isEmpty()){ControllerConfig.Mapping m=new ControllerConfig.Mapping();m.source=token;m.direction=d;c.mappings.add(m);}return;}switch(d){case "up":c.sourceUp=s;break;case "down":c.sourceDown=s;break;case "left":c.sourceLeft=s;break;case "right":c.sourceRight=s;break;default:c.source=s;}}
    private static String output(ControllerConfig.Control c,String d){switch(d){case "up":return c.mapUp;case "down":return c.mapDown;case "left":return c.mapLeft;default:return c.mapRight;}}
    private static void setOutput(ControllerConfig.Control c,String d,String s){switch(d){case "up":c.mapUp=s;break;case "down":c.mapDown=s;break;case "left":c.mapLeft=s;break;default:c.mapRight=s;}}
    private static String directionLabel(String d){switch(d){case "up":return " ↑";case "down":return " ↓";case "left":return " ←";case "right":return " →";default:return "";}}
    private static boolean hasAnalogSource(String s){return s.contains("AXIS_")||s.contains("MOUSE_X")||s.contains("MOUSE_Y");}
    private static String without(String s,String token){ArrayList<String> all=new ArrayList<>(Arrays.asList(s.split("\\|")));all.remove(token);return String.join("|",all);}
    private static String names(String s){StringJoiner j=new StringJoiner(" + ");for(String t:s.split("\\+"))j.add(DeviceDiagram.shortName(t));return j.toString();}

    /** Shared visual output picker used by mappings and macro action nodes. */
    static void chooseOutputs(PadView pad,String initial,java.util.function.Consumer<String> result){chooseOutputs(pad,initial,result,false);}
    static Dialog chooseOutputs(PadView pad,String initial,java.util.function.Consumer<String> result,boolean desktopOnly){
        Context c=pad.getContext();Dialog d=new Dialog(c,R.style.AppTheme);LinearLayout root=EditorStyle.column(c);root.setBackgroundColor(EditorStyle.BG);LinearLayout header=new LinearLayout(c);root.addView(header);TextView title=EditorStyle.text(c,"Outputs",18,EditorStyle.TEXT);header.addView(title,new LinearLayout.LayoutParams(0,EditorStyle.dp(c,42),1));EditorStyle.add(header,EditorStyle.button(c,"Cancel",false,d::dismiss),72,34);
        Set<String> chosen=new LinkedHashSet<>();if(!initial.isEmpty())chosen.addAll(Arrays.asList(initial.split("\\+")));
        EditorStyle.add(header,EditorStyle.button(c,"Done",true,()->{result.accept(String.join("+",chosen));d.dismiss();}),68,34);
        root.addView(EditorStyle.text(c,"Select keys to press together. Tap a selected key to remove it.",12,EditorStyle.MUTED));
        LinearLayout tabs=new LinearLayout(c);root.addView(tabs);FrameLayout frame=new FrameLayout(c);root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));HorizontalScrollView selectedScroll=new HorizontalScrollView(c);LinearLayout selection=new LinearLayout(c);selectedScroll.addView(selection);root.addView(selectedScroll,new LinearLayout.LayoutParams(-1,EditorStyle.dp(c,40)));String[] kind={"KEYBOARD"};Runnable[] draw={null};
        draw[0]=()->{tabs.removeAllViews();for(String k:desktopOnly?new String[]{"KEYBOARD","MOUSE"}:new String[]{"KEYBOARD","MOUSE","CONTROLLER"})EditorStyle.add(tabs,EditorStyle.button(c,k.equals("CONTROLLER")?"Controller":k.equals("KEYBOARD")?"Keyboard":"Mouse",kind[0].equals(k),()->{kind[0]=k;draw[0].run();}),110,32);frame.removeAllViews();DeviceDiagram device=new DeviceDiagram(c,kind[0],new DeviceDiagram.Listener(){public void select(String token){String output=toOutput(token);if(!chosen.remove(output))chosen.add(output);draw[0].run();}public void drop(String token,String data){}});Set<String> marked=new HashSet<>();for(String token:device.tokens())if(chosen.contains(toOutput(token)))marked.add(token);device.state("",marked,null);frame.addView(device,new FrameLayout.LayoutParams(-1,-1));selection.removeAllViews();if(chosen.isEmpty())selection.addView(EditorStyle.text(c,"Touchscreen default",12,EditorStyle.MUTED));for(String output:chosen)EditorStyle.add(selection,EditorStyle.button(c,output+" ×",true,()->{if(!output.contains("=")){chosen.remove(output);draw[0].run();return;}new AlertDialog.Builder(c).setItems(new String[]{"Value","Remove"},(alert,which)->{if(which==1){chosen.remove(output);draw[0].run();}else outputValue(c,output,value->{chosen.remove(output);chosen.add(value);draw[0].run();});}).show();}),-2,32);if(kind[0].equals("CONTROLLER"))EditorStyle.add(tabs,EditorStyle.button(c,"Share",chosen.contains("SHARE"),()->{if(!chosen.remove("SHARE"))chosen.add("SHARE");draw[0].run();}),68,32);};draw[0].run();d.setContentView(root);EditorStyle.fullscreen(d,root);return d;
    }
    private static void outputValue(Context c,String output,java.util.function.Consumer<String> done){
        String[] p=output.split("=",2);int unit=p[0].startsWith("MOUSE")?1:p[0].equals("WHEEL")?1:100;
        int min=p[0].equals("LT")||p[0].equals("RT")?0:p[0].startsWith("MOUSE")?-2000:p[0].equals("WHEEL")?-30:-100;
        int max=p[0].startsWith("MOUSE")?2000:p[0].equals("WHEEL")?30:100;int[] value={Math.round(Float.parseFloat(p[1])*unit)};
        LinearLayout box=EditorStyle.column(c);TextView number=EditorStyle.text(c,p[0]+"  "+value[0]+(unit==100?"%":" /s"),16,EditorStyle.TEXT);box.addView(number);SeekBar slider=new SeekBar(c);slider.setMax(max-min);slider.setProgress(value[0]-min);box.addView(slider);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user){value[0]=min+n;number.setText(p[0]+"  "+value[0]+(unit==100?"%":" /s"));}}});
        new AlertDialog.Builder(c).setTitle("Output strength").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Done",(d,w)->done.accept(p[0]+"="+(value[0]/(float)unit))).show();
    }
    static String toOutput(String token){if(token.startsWith("PAD_")){String s=token.substring(4);return s.equals("LT")||s.equals("RT")?s+"=1":s;}if(token.startsWith("AXIS_HAT")){return token.contains("X")?(token.endsWith("NEG")?"DPAD_LEFT":"DPAD_RIGHT"):(token.endsWith("NEG")?"DPAD_UP":"DPAD_DOWN");}if(token.startsWith("AXIS_"))return token.substring(5,7)+"="+(token.endsWith("NEG")?"-1":"1");if(token.equals("WHEEL_UP")||token.equals("WHEEL_DOWN"))return "WHEEL="+(token.endsWith("UP")?"5":"-5");if(token.matches("MOUSE_[XY]_(POS|NEG)"))return token.substring(0,7)+"="+(token.endsWith("NEG")?"-500":"500");return token;}
}
