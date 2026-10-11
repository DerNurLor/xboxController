package dev.phonepad;

import android.app.AlertDialog;
import android.content.*;
import android.text.InputType;
import android.widget.*;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** Goal-oriented navigation around the existing profile and device preferences. */
public final class ControllerSettings {
    private final Context context;private final PadView pad;private final Runnable connection;private final boolean openedInGame;
    public ControllerSettings(Context context,PadView pad,Runnable connection){this.context=context;this.pad=pad;this.connection=connection;openedInGame=pad.gameplayLock.locked;}
    private SettingsScreen screen;
    private final ArrayDeque<Runnable> history=new ArrayDeque<>();
    private Runnable currentRoute;
    private String routeKey="";
    private boolean returning,returnToPlayAfterLayoutAction;
    private boolean suggestWifiAfterBluetooth,wifiSuggestionShown;
    private String layoutKindFilter="All layouts",layoutCategoryFilter="All categories",layoutTagFilter="Any tag";
    private void route(String key,Runnable render){
        ensureScreen();screen.leave();
        if(!returning&&!key.equals(routeKey)&&currentRoute!=null){Runnable previous=currentRoute;int selected=screen.selection();history.addLast(()->{previous.run();screen.selected(selected);});if(history.size()>64)history.removeFirst();}
        routeKey=key;currentRoute=render;
    }
    private void back(){
        screen.leave();
        if(routeKey.equals("wifiPage")&&context instanceof MainActivity){
            screen.close();
            ((MainActivity)context).scanCompanionQr();
            return;
        }
        if(history.isEmpty()){boolean unlocked=routeKey.equals("setup")||!openedInGame;screen.close();if(unlocked)pad.unlock();return;}
        Runnable previous=history.removeLast();returning=true;
        try{previous.run();}finally{returning=false;}
    }

    private final List<Runnable> resets=new ArrayList<>();
    private final Map<String,EditText> numbers=new LinkedHashMap<>();
    private final Map<String,Switch> switches=new LinkedHashMap<>();
    private final Map<String,Spinner> choices=new LinkedHashMap<>();
    private SettingsPreview visualization;
    private Runnable autoSave;
    private String savedForm="";
    private ControllerConfig pageBaseline;
    private String pageLayoutId;
    private Button pageUndo;
    private boolean binding, saving;
    private LinearLayout currentBody;
    private final Map<EditText,Float> defaults=new LinkedHashMap<>();
    private static final String[] CATEGORIES={"Home","Controls","Play","Connection","More"};
    private void ensureScreen(){
        if(screen!=null&&screen.showing())return;
        pad.release();pad.settingsOpen=true;pad.refreshQuickPanel();
        if(context instanceof MainActivity)((MainActivity)context).haptics.active(false);
        screen=new SettingsScreen(context,CATEGORIES,n->{int[] routes={0,13,14,10,12};selectCategory(routes[n]);},()->{if(pad.editing){pad.settingsOpen=false;pad.editorRefresh();}else{pad.settingsOpen=false;if(openedInGame&&!routeKey.equals("setup"))pad.startGameMode();else pad.unlock();}if(context instanceof MainActivity)((MainActivity)context).haptics.active(true);},pad.editing);screen.navigation(this::back);
    }
    private void selectCategory(int category){screen.leave();history.clear();currentRoute=null;routeKey="";category(category);}
    public void show(){ensureScreen();category(13);}
    private void category(int i){ensureScreen();screen.back(pad.editing?"‹ Editor":"‹ Setup",pad.editing?screen::close:this::setup);screen.leave();screen.selected(i==0?0:i==10?3:i==2||i==12||i==11?4:i==5||i==7||i==8||i==9||i==14?2:1);try{switch(i){
        case 0:setup();break;
        case 13:menu("Controls",new String[]{"Sticks","Triggers & racing","Game profiles","Quick switch layout"},n->{switch(n){case 0:sticks();break;case 1:triggers();break;case 2:games();break;case 3:quickPanel();break;}});break;
        case 14:menu("Play",new String[]{"Vibration","Game mode","Camera & aiming","Motion aiming"},n->{switch(n){case 0:haptics();break;case 1:lockSettings();break;case 2:fps();break;case 3:gyro();break;case 4:feedback();break;}});break;
        case 1:layouts();break;
        case 2:appearance();break;
        case 3:sticks();break;case 4:triggers();break;case 5:fps();break;case 6:palette();break;case 7:gyro();break;case 8:haptics();break;case 9:lockSettings();break;
        case 10:connectionPage();break;
        case 11:route("diagnostics",()->category(11));LinearLayout d=box();text(d,pad.status+"\n"+pad.transport);text(d,pad.gyro==null?"Gyroscope unavailable":pad.gyro.status());text(d,context instanceof MainActivity?((MainActivity)context).haptics.status():"Haptics unavailable");text(d,"Bluetooth feedback requires vendor report 2. Wi-Fi feedback requires the authenticated companion. Game compatibility depends on the host.");d.addView(screen.button("Connection details",()->connectionAction(5)));d.addView(screen.button("Copy diagnostics",()->{((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PhonePad diagnostics",pad.status+"\n"+pad.transport+"\n"+(context instanceof MainActivity?((MainActivity)context).haptics.status():"")));screen.status("Copied");}));page("Diagnostics",d);break;
        case 12:menu("More",new String[]{"Layout options","Layout transitions","Controller test","Diagnostics","Appearance"},n->{if(n==0)layoutManagement();else if(n==1)temporary();else if(n==3)category(11);else if(n==4)category(2);else{screen.close();pad.startTest();}});break;
    }}catch(IllegalArgumentException|IllegalStateException failure){notice(failure.getMessage());}}
    private void page(String title,LinearLayout body){ensureScreen();if(pad.editing)screen.back("‹ Editor",screen::close);visualization=null;screen.page(title,body,null,null,null);}
    private void menu(String title,String[] items,java.util.function.IntConsumer selected){
        route("menu:"+title,()->menu(title,items,selected));screen.back(pad.editing?"‹ Editor":"‹ Setup",pad.editing?screen::close:this::setup);LinearLayout b=box();for(int i=0;i<items.length;i++){final int n=i;b.addView(screen.button(items[i]+"   ›",()->{try{selected.accept(n);}catch(IllegalArgumentException|IllegalStateException ex){notice(ex.getMessage());}}),new LinearLayout.LayoutParams(-1,-2));}page(title,b);
    }
    private void compact(AlertDialog dialog){
        dialog.show();android.view.Window window=dialog.getWindow();if(window==null)return;
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();
        int width=Math.min((int)(560*metrics.density),(int)(metrics.widthPixels*.92f));
        window.setLayout(width,android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        window.getDecorView().post(()->{int height=window.getDecorView().getHeight(),limit=(int)(metrics.heightPixels*.88f);if(height>limit)window.setLayout(width,limit);});
    }
    private int dp(float n){return Math.round(n*context.getResources().getDisplayMetrics().density);}
    private LinearLayout box(){if(screen!=null)screen.leave();autoSave=null;pageUndo=null;pageBaseline=pad.config.copy();pageLayoutId=pad.profiles.active;resets.clear();numbers.clear();defaults.clear();switches.clear();choices.clear();visualization=null;LinearLayout b=new LinearLayout(context);b.setOrientation(LinearLayout.VERTICAL);b.setPadding(dp(8),dp(2),dp(8),dp(6));currentBody=b;return b;}
    private void text(LinearLayout b,String text){TextView t=new TextView(context);t.setText(text);t.setTextColor(SettingsScreen.MUTED);t.setTextSize(14);t.setPadding(0,dp(8),0,dp(8));b.addView(t);}
    private String human(String title){return title.replaceAll(" \\(.*?\\)","").replace("Camera response exponent","Response curve").replace("Response exponent","Response curve").replace("RS sensitivity while LT active","Aim sensitivity").replace("Haptics master","Vibration").replace("Aim activation","Aim behavior").replace("Hold destination","While holding the layout button").replace("While LT held → layout","While aiming").replace("Stick / camera sensitivity","Sensitivity").replace("Stick dead zone","Deadzone").replace("Overall intensity","Vibration intensity").replace("Host game rumble","Game vibration").replace("Local touch feedback","Touch feedback").replace("Enable gyro-assisted RS","Motion aiming").replace("Travel fraction of width / height","Travel distance").replace("Trigger sensitivity","Sensitivity").replace("Mode for both triggers","Direction").replace("Enable same-thumb camera fire","Fire without lifting your camera thumb").replace("First tap duration","Tap speed").replace("Second tap interval","Double-tap window").replace("Tap movement tolerance","Movement tolerance");}
    private LinearLayout row(LinearLayout b,String title){
        TextView label=new TextView(context);label.setText(human(title));label.setTextColor(SettingsScreen.TEXT);label.setTextSize(15);label.setPadding(0,dp(6),0,dp(6));
        LinearLayout caption=new LinearLayout(context);caption.setGravity(Gravity.CENTER_VERTICAL);caption.addView(label,new LinearLayout.LayoutParams(0,-2,1));caption.addView(screen.info(human(title),SettingHelp.description(title)),new LinearLayout.LayoutParams(dp(48),dp(48)));
        LinearLayout controls=new LinearLayout(context);controls.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout row=new LinearLayout(context){@Override protected void onMeasure(int width,int height){boolean stacked=View.MeasureSpec.getSize(width)<dp(340*Math.max(1,context.getResources().getConfiguration().fontScale));setOrientation(stacked?VERTICAL:HORIZONTAL);for(int i=0;i<getChildCount();i++){LinearLayout.LayoutParams p=(LinearLayout.LayoutParams)getChildAt(i).getLayoutParams();p.width=stacked?-1:0;p.weight=stacked?0:(i==0?.42f:.58f);}super.onMeasure(width,height);}};
        row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(48));row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));row.addView(controls,new LinearLayout.LayoutParams(0,-2,1));b.addView(row,new LinearLayout.LayoutParams(-1,-2));return controls;
    }
    private EditText field(LinearLayout b,String title,String value,boolean number){LinearLayout row=row(b,title);EditText e=new EditText(context);e.setSingleLine(true);e.setTextSize(15);e.setText(value);e.setContentDescription(human(title));if(number)e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);row.addView(e,new LinearLayout.LayoutParams(0,-2,1));return e;}
    private EditText number(LinearLayout b,String title,float initial){
        LinearLayout row=row(b,title);float[] range=SettingHelp.range(title,initial);float def=SettingHelp.defaultValue(title,initial);boolean descending=title.startsWith("First tap duration");
        EditText e=new EditText(context);e.setSingleLine(true);e.setTextSize(14);e.setSelectAllOnFocus(true);e.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);e.setText(String.format(Locale.ROOT,"%.3f",initial));e.setContentDescription(human(title)+" exact value");numbers.put(title,e);defaults.put(e,def);
        SeekBar slider=new SeekBar(context);slider.setMax(1000);slider.setMinimumHeight(dp(48));slider.setContentDescription(human(title));e.setTag(slider);row.addView(slider,new LinearLayout.LayoutParams(0,dp(48),1));row.addView(e,new LinearLayout.LayoutParams(dp(92),dp(48)));e.setVisibility(View.GONE);
        Button display=screen.button("",()->exactNumber(title,e,range));display.setTextSize(14);row.addView(display,new LinearLayout.LayoutParams(dp((title.contains("-1 uses")?92:72)*Math.max(1,context.getResources().getConfiguration().fontScale)),dp(48)));boolean[] changing={false};
        Runnable sync=()->{display.setText(friendlyValue(title,e.getText().toString()));display.setVisibility(e.hasFocus()?View.GONE:View.VISIBLE);try{float v=Float.parseFloat(e.getText().toString());changing[0]=true;slider.setProgress(Math.round((descending?1-(v-range[0])/(range[1]-range[0]):(v-range[0])/(range[1]-range[0]))*1000));changing[0]=false;}catch(Exception ignored){}if(visualization!=null)visualization.invalidate();};
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){saveChange();}public void onProgressChanged(SeekBar s,int n,boolean user){if(user&&!changing[0])e.setText(String.format(Locale.ROOT,"%.3f",range[0]+(range[1]-range[0])*(descending?1000-n:n)/1000f));}});
        e.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){sync.run();}public void afterTextChanged(android.text.Editable e){}});sync.run();
        e.setOnEditorActionListener((v,action,event)->{saveChange();if(e.getError()==null){e.clearFocus();e.setVisibility(View.GONE);display.setVisibility(View.VISIBLE);}return false;});e.setOnFocusChangeListener((v,focus)->{display.setVisibility(focus?View.GONE:View.VISIBLE);if(!focus){e.setVisibility(View.GONE);saveChange();}});
        resets.add(()->e.setText(String.format(Locale.ROOT,"%.3f",def)));return e;
    }
    private void exactNumber(String title,EditText stored,float[] range){
        EditText exact=new EditText(context);exact.setSingleLine(true);exact.setInputType(InputType.TYPE_CLASS_NUMBER|InputType.TYPE_NUMBER_FLAG_DECIMAL|InputType.TYPE_NUMBER_FLAG_SIGNED);exact.setText(stored.getText());exact.setSelectAllOnFocus(true);exact.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        AlertDialog dialog=new AlertDialog.Builder(context).setTitle(human(title)).setMessage("Choose a value from "+range[0]+" to "+range[1]+".").setView(exact).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();compact(dialog);
        Runnable save=()->{try{float number=value(exact,range[0],range[1]);stored.setText(Float.toString(number));saveChange();dialog.dismiss();}catch(IllegalArgumentException ignored){}};dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->save.run());exact.setOnEditorActionListener((v,a,event)->{save.run();return true;});
    }
    private String friendlyValue(String title,String raw){try{float v=Float.parseFloat(raw);String key=title.toLowerCase(Locale.ROOT);if(key.contains("-1 uses")&&v<0)return "Layout default";if(key.contains("rad/s"))return new java.math.BigDecimal(raw).stripTrailingZeros().toPlainString()+" rad/s";if(key.contains("ms"))return Math.round(v)+" ms";if(key.contains("%"))return String.format(Locale.ROOT,"%.1f%%",v);if(key.contains("sensitivity")||key.contains("dead zone")||key.contains("fraction")||key.contains("intensity")||key.contains("weight")||key.contains("opacity")||key.contains("smoothing")||key.contains("filtering")||key.contains("activation distance"))return Math.round(v*100)+"%";return new java.math.BigDecimal(raw).stripTrailingZeros().toPlainString();}catch(Exception ignored){return "Edit value";}}
    private float value(EditText e,float min,float max){float v;try{v=Float.parseFloat(e.getText().toString());}catch(Exception ex){e.setError("Enter a number");throw new IllegalArgumentException("Enter a number");}if(!Float.isFinite(v)||v<min||v>max){e.setError("Use "+min+"–"+max);throw new IllegalArgumentException("Value must be between "+min+" and "+max);}e.setError(null);return v;}
    private Switch toggle(LinearLayout b,String title,boolean value){Switch v=new Switch(context);v.setText(human(title));v.setTextSize(15);v.setTextColor(SettingsScreen.TEXT);v.setChecked(value);v.setMinHeight(dp(48));v.setPadding(0,dp(6),dp(8),dp(6));LinearLayout line=new LinearLayout(context);line.setGravity(Gravity.CENTER_VERTICAL);line.addView(v,new LinearLayout.LayoutParams(0,-2,1));line.addView(screen.info(human(title),SettingHelp.description(title)),new LinearLayout.LayoutParams(dp(48),dp(48)));b.addView(line);switches.put(title,v);boolean def=SettingHelp.defaultToggle(title,value);resets.add(()->v.setChecked(def));v.setOnCheckedChangeListener((button,checked)->{if(visualization!=null)visualization.invalidate();if(!binding)button.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);saveChange();});return v;}
    private Spinner choice(LinearLayout b,String title,String[] labels,String[] values,String current){LinearLayout row=row(b,title);Spinner v=new Spinner(context);v.setContentDescription(human(title));String[] human=labels.clone();for(int i=0;i<human.length;i++)if(!human[i].isEmpty()&&human[i].equals(human[i].toUpperCase(Locale.ROOT)))human[i]=human[i].substring(0,1)+human[i].substring(1).toLowerCase(Locale.ROOT).replace('_',' ');v.setAdapter(new ArrayAdapter<String>(context,android.R.layout.simple_spinner_dropdown_item,human));int selected=Math.max(0,Arrays.asList(values).indexOf(current));v.setSelection(selected);row.addView(v,new LinearLayout.LayoutParams(0,dp(52),1));choices.put(title,v);resets.add(()->v.setSelection(SettingHelp.defaultChoice(title,selected)));final int[] previous={selected};v.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View w,int n,long id){if(n!=previous[0]){previous[0]=n;if(visualization!=null)visualization.invalidate();saveChange();}}});return v;}
    private void saveChange(){if(autoSave==null||binding||saving||fingerprint(currentBody).equals(savedForm))return;saving=true;try{autoSave.run();savedForm=fingerprint(currentBody);screen.status("Saved");if(pageUndo!=null)pageUndo.setVisibility(View.VISIBLE);}catch(IllegalArgumentException|IllegalStateException ex){screen.status(ex.getMessage()==null?"Check highlighted values":ex.getMessage());}finally{saving=false;}}
    private void fold(LinearLayout body,String title,int first){LinearLayout details=new LinearLayout(context);details.setOrientation(LinearLayout.VERTICAL);while(body.getChildCount()>first){View child=body.getChildAt(first);body.removeViewAt(first);details.addView(child);}details.setVisibility(View.GONE);Button toggle=screen.button(title+"   +",()->{boolean open=details.getVisibility()==View.GONE;details.setVisibility(open?View.VISIBLE:View.GONE);if(open)screen.reveal(details);});body.addView(toggle);body.addView(details);}
    private LinearLayout dependent(LinearLayout body,Switch parent,int first){
        LinearLayout detail=new LinearLayout(context);detail.setOrientation(LinearLayout.VERTICAL);
        while(body.getChildCount()>first){View child=body.getChildAt(first);body.removeViewAt(first);detail.addView(child);}body.addView(detail);
        Runnable sync=()->detail.setVisibility(parent.isChecked()?View.VISIBLE:View.GONE);sync.run();
        parent.setOnCheckedChangeListener((v,on)->{sync.run();if(visualization!=null)visualization.invalidate();saveChange();});return detail;
    }
    private void dependentChoice(Spinner parent,View detail,java.util.function.IntPredicate visible){
        Runnable sync=()->detail.setVisibility(visible.test(parent.getSelectedItemPosition())?View.VISIBLE:View.GONE);sync.run();
        parent.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int n,long id){sync.run();if(visualization!=null)visualization.invalidate();saveChange();}});
    }
    private float draftNumber(String key,float fallback){for(Map.Entry<String,EditText> e:numbers.entrySet())if(e.getKey().toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))try{return Float.parseFloat(e.getValue().getText().toString());}catch(Exception ignored){}return fallback;}
    private boolean draftToggle(String key,boolean fallback){for(Map.Entry<String,Switch> e:switches.entrySet())if(e.getKey().toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))return e.getValue().isChecked();return fallback;}
    private int draftChoice(String key,int fallback){for(Map.Entry<String,Spinner> e:choices.entrySet())if(e.getKey().toLowerCase(Locale.ROOT).contains(key.toLowerCase(Locale.ROOT)))return e.getValue().getSelectedItemPosition();return fallback;}
    private void form(String title,LinearLayout b,Runnable save){
        ensureScreen();boolean transaction=title.equals("Wi-Fi companion")||title.startsWith("New ")||title.startsWith("Duplicate ")||title.startsWith("Import ")||title.startsWith("Rename ");
        if(transaction){screen.page(title,b,()->{try{save.run();if(returnToPlayAfterLayoutAction)finishLayoutAction();else category(0);}catch(IllegalArgumentException|IllegalStateException ex){notice(ex.getMessage());}},null,null);return;}
        String family=title.toLowerCase(Locale.ROOT);LinearLayout preview=null;android.graphics.drawable.GradientDrawable surface=new android.graphics.drawable.GradientDrawable();surface.setColor(SettingsScreen.SURFACE);surface.setCornerRadius(dp(12));b.setBackground(surface);
        if(family.contains("stick")||family.contains("analog behavior")||family.contains("trigger")||family.contains("gas / brake")||family.contains("fps")||family.contains("gyro")||family.equals("haptics")||family.equals("game mode")){
            visualization=new SettingsPreview(context,title.equals("Game mode")?"Gameplay lock":title,this::draftNumber,this::draftToggle,this::draftChoice);preview=screen.panel(family.contains("fps")?"TRY IT · CAMERA & FIRE":"LIVE PREVIEW");preview.addView(visualization,new LinearLayout.LayoutParams(-1,dp(190)));

        }
        List<Runnable> pageResets=new ArrayList<>(resets);
        b.addView(screen.button("Reset this section…",()->new AlertDialog.Builder(context).setTitle("Reset "+title+"?").setMessage("Restore the controls on this page to their defaults. Other settings stay as they are.").setNegativeButton("Keep settings",null).setPositiveButton("Reset",(d,w)->{binding=true;for(Runnable reset:pageResets)reset.run();binding=false;saveChange();}).show()));
        List<Runnable> restore=new ArrayList<>();remember(b,restore);pageUndo=screen.button("Undo changes on this page",()->{binding=true;for(Runnable r:restore)r.run();binding=false;saveChange();pageUndo.setVisibility(View.GONE);});pageUndo.setVisibility(View.GONE);b.addView(pageUndo);
        screen.page(title,preview==null?b:screen.columns(b,preview),null,null,null);savedForm=fingerprint(b);autoSave=save;screen.onLeave(()->{saveChange();autoSave=null;});
        // Text fields commit when editing finishes. Sliders commit on release; switches immediately.
        bindText(b);
    }
    private void remember(View v,List<Runnable> restore){if(v instanceof EditText){String value=((EditText)v).getText().toString();restore.add(()->((EditText)v).setText(value));}else if(v instanceof CompoundButton){boolean value=((CompoundButton)v).isChecked();restore.add(()->((CompoundButton)v).setChecked(value));}else if(v instanceof Spinner){int value=((Spinner)v).getSelectedItemPosition();restore.add(()->((Spinner)v).setSelection(value));}else if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)remember(g.getChildAt(i),restore);}}
    private String fingerprint(View view){StringBuilder s=new StringBuilder();if(view instanceof EditText)s.append(((EditText)view).getText());else if(view instanceof CompoundButton)s.append(((CompoundButton)view).isChecked());else if(view instanceof Spinner)s.append(((Spinner)view).getSelectedItemPosition());else if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++)s.append('|').append(fingerprint(g.getChildAt(i)));}return s.toString();}
    public void setup(){route("setup",this::setup);
        ensureScreen();screen.back("‹ Controller",()->{screen.close();pad.unlock();});screen.selected(0);LinearLayout b=box();
        LinearLayout ready=screen.panel("Current layout");TextView readiness=screen.label(pad.connected?"READY TO PLAY":"NOT CONNECTED",17);ready.addView(readiness);ready.addView(screen.label(pad.config.name,24));computerStatus(ready);
        TextView guidance=screen.label(pad.connected?"Your controller is connected.":"Your controller is ready. Connect a computer to start playing.",14);ready.addView(guidance);
        Button start=screen.button(pad.connected?"Start game mode":"Connect computer",()->{if(pad.connected){screen.close();pad.startGameMode();}else{suggestWifiAfterBluetooth=true;connectionAction(0);}});start.setTextColor(SettingsScreen.BACKGROUND);start.setBackgroundColor(SettingsScreen.ACCENT);ready.addView(start);ready.addView(screen.button("Edit layout",this::editLayout));ready.addView(screen.button("Change layout",this::switcher));
        Runnable update=new Runnable(){public void run(){readiness.setText(pad.connected?"READY TO PLAY":"NOT CONNECTED");guidance.setText(pad.connected?"Your controller is connected.":"Your controller is ready. Connect a computer to start playing.");start.setText(pad.connected?"Start game mode":"Connect computer");if(start.isAttachedToWindow())start.postDelayed(this,500);}};start.post(update);
        page("Home",ready);

    }
    private void quickPanel(){
        route("quickPanel",this::quickPanel);LinearLayout body=box();
        QuickLayoutPanel panel=pad.quickLayouts();
        Switch enabled=toggle(body,"Quick switch layout panel",panel.enabled());
        enabled.setOnCheckedChangeListener((v,on)->panel.setEnabled(on));
        text(body,"Panel settings apply only to "+pad.config.name+". Drag the grip to move it; tap for size, direction, full names and edge position. Use + outside game mode to add shortcuts.");
        Button choose=screen.button("Choose layouts",()->pad.quickLayouts().chooseLayouts()),position=screen.button("Size, direction & position",()->{screen.close();pad.quickLayouts().configure();});choose.setEnabled(!pad.gameplayLock.locked);body.addView(choose);body.addView(position);if(pad.gameplayLock.locked)text(body,"Exit game mode to add or remove shortcuts. Size, direction and position can change during play.");
        page("Quick switch layout",body);
    }
    private void editLayout(){screen.close();pad.requestEditing();}
    private String hapticSummary(){if(!(context instanceof MainActivity))return "Touch feedback";PhoneHaptics h=((MainActivity)context).haptics;return h.enabled?"On · "+Math.round(h.strength*100)+"%":"Off";}
    public void quickSettings(){route("quickSettings",this::quickSettings);
        ensureScreen();screen.back("‹ Game mode",()->{screen.close();pad.startGameMode();});LinearLayout b=box();computerStatus(b);b.addView(screen.button("Layout · "+pad.config.name+"   ›",this::switcher));
        PhoneHaptics h=context instanceof MainActivity?((MainActivity)context).haptics:null;EditText intensity=h==null?null:number(b,"Overall intensity (0–1)",h.strength);
        b.addView(screen.button("Open full settings   ›",()->{pad.unlock();show();}));
        form("Quick settings",b,()->{if(h!=null){h.strength=value(intensity,0,1);h.save();}});
    }
    private void bindText(View view){if(view instanceof EditText){if(numbers.containsValue(view))return;view.setOnFocusChangeListener((v,focus)->{if(!focus)saveChange();});((EditText)view).setOnEditorActionListener((v,a,e)->{saveChange();return false;});}else if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)bindText(group.getChildAt(i));}}
    private void naming(String title,String initial,Consumer<String> save){route("naming:"+title,()->naming(title,initial,save));LinearLayout b=box();EditText e=field(b,"Name",initial,false);form(title,b,()->{save.accept(e.getText().toString());pad.selectProfile(pad.profiles.active);});}
    private void finishLayoutAction(){
        if(!returnToPlayAfterLayoutAction)return;returnToPlayAfterLayoutAction=false;
        if(screen!=null&&screen.showing())screen.close();
    }
    public void layoutActions(){
        pad.release();AppAppearance appearance=new AppAppearance(context);
        LinearLayout menu=new LinearLayout(context);menu.setOrientation(LinearLayout.VERTICAL);menu.setPadding(dp(3),dp(3),dp(3),dp(3));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(appearance.surface);background.setCornerRadius(dp(18));menu.setBackground(background);menu.setElevation(dp(12));
        android.widget.PopupWindow popup=new android.widget.PopupWindow(menu,dp(200),android.view.ViewGroup.LayoutParams.WRAP_CONTENT,true);popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(dp(12));
        Runnable dismiss=popup::dismiss;
        addLayoutAction(menu,"＋","Create a new layout",()->{dismiss.run();layoutName("New layout","My layout",name->{ControllerConfig fresh=ControllerConfig.preset("Custom Blank Layout");fresh.game=pad.config.game;pad.profiles.create(name,fresh);activateLayout();});});
        addLayoutAction(menu,"⧉","Duplicate current",()->{dismiss.run();String sourceId=pad.profiles.active;layoutName("Duplicate layout",pad.config.name+" copy",name->pad.profiles.duplicate(name,sourceId));});
        boolean builtin=pad.profiles.builtin(pad.profiles.active);
        if(!builtin)addLayoutAction(menu,"✎","Rename layout",()->{dismiss.run();layoutName("Rename layout",pad.config.name,pad.profiles::rename);});
        View divider=new View(context);divider.setBackgroundColor(appearance.border);LinearLayout.LayoutParams dividerParams=new LinearLayout.LayoutParams(-1,dp(1));dividerParams.setMargins(dp(8),dp(2),dp(8),dp(2));menu.addView(divider,dividerParams);
        addLayoutAction(menu,"⋯","More",()->{dismiss.run();new ControllerSettings(context,pad,connection).showLayoutOptions();});
        int[] location=new int[2];pad.getLocationOnScreen(location);android.graphics.RectF bounds=pad.controllerBounds();int y=location[1]+Math.round(bounds.top+44*pad.toolbarScale())+dp(4);
        popup.showAtLocation(pad.getRootView(),android.view.Gravity.TOP|android.view.Gravity.RIGHT,dp(12),y);
    }
    private void addLayoutAction(LinearLayout menu,String icon,String title,Runnable action){
        LinearLayout row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);row.setGravity(Gravity.CENTER);row.setPadding(dp(3),0,dp(3),0);
        android.graphics.drawable.RippleDrawable ripple=new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x338ed9c5),new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT),null);row.setBackground(ripple);row.setClickable(true);row.setFocusable(true);
        TextView symbol=new TextView(context);symbol.setText(icon);symbol.setTextSize(14);symbol.setTextColor(SettingsScreen.ACCENT);symbol.setGravity(Gravity.CENTER);row.addView(symbol,new LinearLayout.LayoutParams(dp(18),dp(32)));
        TextView name=new TextView(context);name.setText(title);name.setTextColor(SettingsScreen.TEXT);name.setTextSize(12);name.setGravity(Gravity.CENTER);name.setSingleLine(true);name.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams label=new LinearLayout.LayoutParams(-2,dp(32));label.setMarginStart(dp(4));row.addView(name,label);row.setContentDescription(title);row.setOnClickListener(v->action.run());menu.addView(row,new LinearLayout.LayoutParams(-1,dp(34)));
    }
    private void layoutName(String title,String initial,Consumer<String> save){
        EditText input=new EditText(context);input.setSingleLine(true);input.setText(initial);input.selectAll();input.setHint("Layout name");
        new AlertDialog.Builder(context).setTitle(title).setView(input).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{try{save.accept(input.getText().toString());pad.selectProfile(pad.profiles.active);activateLayout();}catch(IllegalArgumentException|IllegalStateException failure){notice(failure.getMessage());}}).show();
    }
    private void activateLayout(){pad.unlock();pad.refreshQuickPanel();pad.invalidate();}
    public void showLayoutOptions(){ensureScreen();screen.selected(1);layoutManagement();}
    private void layouts(){ensureScreen();screen.selected(1);menu("Layouts · "+pad.config.name,new String[]{"Choose layout","Edit current layout","Add / manage controls","Create layout","Duplicate layout","Layout transitions","More layout options"},i->{
        switch(i){case 0:switcher();break;case 1:screen.close();pad.requestEditing();break;case 2:palette();break;
        case 3:naming("New layout","My layout",name->{ControllerConfig c=ControllerConfig.preset("Custom Blank Layout");c.game=pad.config.game;pad.profiles.create(name,c);});break;
        case 4:String sourceId=pad.profiles.active;naming("Duplicate layout",pad.config.name+" copy",name->pad.profiles.duplicate(name,sourceId));break;
        case 5:temporary();break;case 6:layoutManagement();break;}
    });}
    private void layoutManagement(){menu("Layout options · "+pad.config.name,new String[]{"Rename layout","Delete layout","Reset layout","Labels & editor grid","Export layout JSON","Import layout JSON","Fit layout to height","Move to game"},i->{
        switch(i){
            case 0:if(pad.profiles.builtin(pad.profiles.active))notice("Duplicate this built-in preset to rename it.");else naming("Rename layout",pad.config.name,pad.profiles::rename);break;
            case 1:deleteLayout(pad.profiles.active);break;
            case 2:compact(new AlertDialog.Builder(context).setTitle("Reset "+pad.config.name+"?").setMessage("Restore controls and behavior from "+pad.config.base+".").setPositiveButton("Reset",(d,w)->{pad.resetProfile();finishLayoutAction();}).setNegativeButton("Cancel",null).create());break;
            case 3:editorSettings();break;
            case 4:((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PhonePad layout",pad.profiles.exportCurrent()));finishLayoutAction();notice("Layout JSON copied.");break;
            case 5:importPage(false);break;
            case 6:pad.fitLayoutHeight();finishLayoutAction();break;
            case 7:List<String> names=pad.profiles.games();menu("Move layout to game",names.toArray(new String[0]),n->pad.requestLayoutEdit(()->{pad.mutate(()->pad.config.game=names.get(n));finishLayoutAction();},()->{}));break;
        }
    });}
    private void notice(String message){new AlertDialog.Builder(context).setMessage(message).setPositiveButton("OK",null).show();}
    public void switcher(){
        ensureScreen();screen.selected(1);List<String> allIds=pad.profiles.layouts(pad.config.game);
        route("layout-picker",()->switcher());screen.back(pad.editing?"‹ Editor":"‹ Setup",pad.editing?screen::close:this::setup);
        LinearLayout body=box();
        List<String> types=Arrays.asList("All layouts","Built-in","Custom");List<String> categories=new ArrayList<>();categories.add("All categories");categories.addAll(pad.profiles.layoutCategories(pad.config.game));
        List<String> tags=new ArrayList<>();tags.add("Any tag");tags.addAll(pad.profiles.layoutTags(pad.config.game));
        if(!types.contains(layoutKindFilter))layoutKindFilter="All layouts";if(!categories.contains(layoutCategoryFilter))layoutCategoryFilter="All categories";if(!tags.contains(layoutTagFilter))layoutTagFilter="Any tag";
        layoutFilter(body,"Type",types,layoutKindFilter,value->{layoutKindFilter=value;switcher();});
        layoutFilter(body,"Category",categories,layoutCategoryFilter,value->{layoutCategoryFilter=value;switcher();});
        layoutFilter(body,"Tag",tags,layoutTagFilter,value->{layoutTagFilter=value;switcher();});
        List<String> ids=new ArrayList<>();for(String id:allIds){boolean builtin=pad.profiles.template(id);if(layoutKindFilter.equals("Built-in")&&!builtin||layoutKindFilter.equals("Custom")&&builtin)continue;if(!layoutCategoryFilter.equals("All categories")&&!pad.profiles.category(id).equalsIgnoreCase(layoutCategoryFilter))continue;if(!layoutTagFilter.equals("Any tag")&&!pad.profiles.tags(id).contains(layoutTagFilter))continue;ids.add(id);}
        if(ids.isEmpty())text(body,"No layouts match these filters. Try another category or tag.");
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();float widthDp=metrics.widthPixels/metrics.density;
        float contentWidth=pad.editing?Math.min(widthDp,360):widthDp*.74f;boolean twoColumns=true;
        int cardWidth=Math.round(Math.max(112,(contentWidth-32)/2));int previewHeight=dp(Math.max(118,Math.min(230,cardWidth*.62f)));
        LinearLayout row=null;
        for(int i=0;i<ids.size();i++){
            if(!twoColumns||i%2==0){row=new LinearLayout(context);row.setOrientation(LinearLayout.HORIZONTAL);body.addView(row,new LinearLayout.LayoutParams(-1,-2));}
            String id=ids.get(i);LinearLayout card=layoutCard(id,previewHeight);
            LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(twoColumns?0:-1,-2,twoColumns?1:0);cell.setMargins(dp(4),dp(4),dp(4),dp(8));row.addView(card,cell);
            if(twoColumns&&i%2==1)row=null;
        }
        if(twoColumns&&ids.size()%2==1&&row!=null){View spacer=new View(context);row.addView(spacer,new LinearLayout.LayoutParams(0,1,1));}
        page("Choose a layout",body);
    }
    private void layoutFilter(LinearLayout body,String title,List<String> options,String selected,Consumer<String> changed){
        LinearLayout row=new LinearLayout(context);row.setGravity(Gravity.CENTER_VERTICAL);TextView label=screen.label(title,14);row.addView(label,new LinearLayout.LayoutParams(dp(86),dp(48)));
        Spinner spinner=new Spinner(context);spinner.setContentDescription("Filter layouts by "+title.toLowerCase(Locale.ROOT));spinner.setAdapter(new ArrayAdapter<String>(context,android.R.layout.simple_spinner_dropdown_item,options));int index=Math.max(0,options.indexOf(selected));spinner.setSelection(index);row.addView(spinner,new LinearLayout.LayoutParams(0,dp(48),1));final int[] previous={index};spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onNothingSelected(android.widget.AdapterView<?> parent){}public void onItemSelected(android.widget.AdapterView<?> parent,View view,int position,long itemId){if(position!=previous[0]){previous[0]=position;changed.accept(options.get(position));}}});body.addView(row);
    }
    private int visibleControlCount(ControllerConfig config){int count=0;for(ControllerConfig.Control control:config.controls)if(config.shown(control)&&!config.backgroundCamera(control))count++;return count;}
    private LinearLayout layoutCard(String id,int previewHeight){
        ControllerConfig c=pad.profiles.load(id);boolean current=id.equals(pad.profiles.active),builtin=pad.profiles.template(id);
        LinearLayout card=new LinearLayout(context);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(10),dp(9),dp(10),dp(8));
        android.graphics.drawable.GradientDrawable background=new android.graphics.drawable.GradientDrawable();background.setColor(SettingsScreen.SURFACE);background.setCornerRadius(dp(16));background.setStroke(dp(current?2:1),current?SettingsScreen.ACCENT:new AppAppearance(context).border);card.setBackground(background);
        LinearLayout heading=new LinearLayout(context);heading.setGravity(android.view.Gravity.CENTER_VERTICAL);TextView title=screen.label(pad.profiles.name(id),16);title.setMaxLines(2);title.setEllipsize(android.text.TextUtils.TruncateAt.END);heading.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView badge=screen.label(current?"CURRENT":builtin?(pad.profiles.builtin(id)?"BUILT-IN":"EDITED BUILT-IN"):"CUSTOM",10);badge.setTextColor(current?SettingsScreen.ACCENT:SettingsScreen.MUTED);badge.setPadding(dp(8),dp(4),dp(8),dp(4));heading.addView(badge);card.addView(heading);
        TextView details=screen.label(visibleControlCount(c)+" visible · "+pad.profiles.category(id),12);details.setTextColor(SettingsScreen.MUTED);details.setPadding(0,dp(2),0,dp(2));card.addView(details);
        String tagText=pad.profiles.tagsText(id);if(!tagText.isEmpty()){TextView tagLine=screen.label("#"+tagText.replace(", ","   #"),11);tagLine.setTextColor(SettingsScreen.ACCENT);tagLine.setPadding(0,0,0,dp(7));card.addView(tagLine);}
        LayoutPreview visual=new LayoutPreview(context,c);visual.setBackgroundColor(SettingsScreen.BACKGROUND);visual.setOnClickListener(v->preview(id));card.addView(visual,new LinearLayout.LayoutParams(-1,previewHeight));
        LinearLayout actions=new LinearLayout(context);actions.setGravity(android.view.Gravity.CENTER_VERTICAL);Button use=screen.button(current?"✓ In use":"Use",()->{if(!current){pad.selectProfile(id);screen.close();}});use.setTextColor(current?SettingsScreen.ACCENT:SettingsScreen.TEXT);use.setEnabled(!current);actions.addView(use,new LinearLayout.LayoutParams(0,dp(44),1));
        Button more=screen.button("⋯",()->preview(id));more.setContentDescription("Preview and options for "+pad.profiles.name(id));actions.addView(more,new LinearLayout.LayoutParams(dp(40),dp(44)));card.addView(actions);return card;
    }
    private void preview(String id){route("preview:"+id,()->preview(id));ensureScreen();ControllerConfig c=pad.profiles.load(id);boolean current=id.equals(pad.profiles.active);LinearLayout b=box();
        TextView summary=screen.label((current?"CURRENT · ":"")+(pad.profiles.template(id)?"Built-in preset":"Custom layout")+" · "+visibleControlCount(c)+" visible controls · "+pad.profiles.category(id),13);summary.setTextColor(current?SettingsScreen.ACCENT:SettingsScreen.MUTED);summary.setPadding(0,0,0,dp(4));b.addView(summary);
        if(!pad.profiles.tags(id).isEmpty()){TextView tagLine=screen.label("#"+pad.profiles.tagsText(id).replace(", ","   #"),12);tagLine.setTextColor(SettingsScreen.ACCENT);tagLine.setPadding(0,0,0,dp(10));b.addView(tagLine);}
        android.util.DisplayMetrics metrics=context.getResources().getDisplayMetrics();float contentWidth=metrics.widthPixels/metrics.density*(pad.editing?.9f:.7f);int previewHeight=dp(Math.max(220,Math.min(360,contentWidth/2)));
        android.widget.FrameLayout frame=new android.widget.FrameLayout(context);frame.setBackgroundColor(SettingsScreen.BACKGROUND);frame.addView(new LayoutPreview(context,c),new android.widget.FrameLayout.LayoutParams(-1,-1));b.addView(frame,new LinearLayout.LayoutParams(-1,previewHeight));
        LinearLayout actions=new LinearLayout(context);actions.setGravity(android.view.Gravity.CENTER_VERTICAL);Button use=screen.button(current?"✓ In use":"Use layout",()->{pad.selectProfile(id);screen.close();});use.setTextColor(SettingsScreen.ACCENT);use.setEnabled(!current);actions.addView(use,new LinearLayout.LayoutParams(0,dp(52),1));actions.addView(screen.button("Edit",()->{pad.selectProfile(id);editLayout();}),new LinearLayout.LayoutParams(0,dp(52),1));b.addView(actions);
        if(!pad.profiles.builtin(id))b.addView(screen.button("Tags & category",()->organizeLayout(id)));
        if(!pad.profiles.builtin(id))b.addView(screen.button(pad.profiles.template(id)?"Restore built-in layout":"Delete layout",()->deleteLayout(id)));
        page(pad.profiles.name(id),b);
    }
    private void organizeLayout(String id){
        if(pad.profiles.builtin(id)){notice("Duplicate this built-in layout to add your own category and tags.");return;}
        route("layout-labels:"+id,()->organizeLayout(id));ensureScreen();LinearLayout body=box();
        text(body,"Use a category for a broad group and comma-separated tags for details. Both are saved with this layout.");
        EditText category=field(body,"Category",pad.profiles.category(id),false);category.setHint("Racing, shooter, accessibility…");category.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(32)});
        EditText tags=field(body,"Tags (comma-separated)",pad.profiles.tagsText(id),false);tags.setHint("drift, nitro, one-handed");tags.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(240)});
        screen.page("Tags & category",body,()->{try{pad.profiles.setLayoutLabels(id,category.getText().toString(),tags.getText().toString());returning=true;try{switcher();}finally{returning=false;}}catch(IllegalArgumentException|IllegalStateException failure){screen.status(failure.getMessage()==null?"Could not save layout labels":failure.getMessage());}},null,null);
    }
    public void removeCurrentLayout(){pad.release();deleteLayout(pad.profiles.active);}
    private void deleteLayout(String id){
        final boolean wasGameMode=pad.gameplayLock.locked;
        if(pad.profiles.builtin(id)){notice("Built-in presets cannot be deleted.");return;}
        boolean restore=pad.profiles.template(id);
        compact(new AlertDialog.Builder(context).setTitle((restore?"Restore ":"Delete ")+pad.profiles.name(id)+"?")
            .setMessage(restore?"Remove your edits and restore the original built-in layout.":"This permanently deletes the layout and removes it from quick-switch panels.")
            .setNegativeButton("Cancel",null).setPositiveButton(restore?"Restore":"Delete",(d,w)->{
                try{if(screen!=null)screen.leave();pad.release();pad.profiles.delete(id);pad.selectProfile(pad.profiles.active);if(!wasGameMode)pad.unlock();pad.editorRefresh();pad.refreshQuickPanel();
                    history.clear();currentRoute=null;routeKey="";if(returnToPlayAfterLayoutAction)finishLayoutAction();else if(screen!=null&&screen.showing())switcher();
                }catch(RuntimeException failure){notice(failure.getMessage());}
            }).create());
    }
    public void quick(){pad.release();if(pad.config.quickStyle.equals("RADIAL")){radial(0);return;}menu(pad.config.game+" · "+pad.config.name,new String[]{"Next layout","Previous layout","Choose / preview layout","Controller test","Settings","Disable same-thumb fire now"},i->{switch(i){case 0:pad.cycleLayout(1);screen.close();break;case 1:pad.cycleLayout(-1);screen.close();break;case 2:switcher();break;case 3:screen.close();pad.startTest();break;case 4:show();break;case 5:pad.mutate(()->pad.config.sameThumbFire=false);screen.close();break;}});}
    private void games(){menu("Game profiles",new String[]{"Choose game","Create game","Duplicate current game","Rename current game","Delete current game","Export game JSON","Import game JSON"},i->{
        switch(i){case 0:List<String> names=pad.profiles.games();menu("Choose game",names.toArray(new String[0]),n->game(names.get(n)));break;
        case 1:naming("New game","My game",pad.profiles::createEmptyGame);break;
        case 2:naming("Duplicate game",pad.config.game+" copy",s->pad.profiles.duplicateGame(pad.config.game,s));break;
        case 3:naming("Rename game",pad.config.game,s->pad.profiles.renameGame(pad.config.game,s));break;
        case 4:if(pad.config.game.equals("General")){notice("General contains the recoverable built-in templates.");break;}new AlertDialog.Builder(context).setTitle("Delete "+pad.config.game+" and all its layouts?").setPositiveButton("Delete",(d,w)->{pad.profiles.deleteGame(pad.config.game);pad.selectProfile(pad.profiles.active);}).setNegativeButton("Cancel",null).show();break;
        case 5:((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PhonePad game",pad.profiles.exportGame(pad.config.game)));notice("Game profile copied as JSON.");break;
        case 6:importPage(true);break;
    }});}
    private void importPage(boolean game){
        route("import:"+game,()->importPage(game));LinearLayout body=box();EditText json=field(body,game?"Paste game profile JSON":"Paste exported JSON","",false);json.setSingleLine(false);json.setMaxLines(6);
        form(game?"Import game":"Import layout",body,()->{pad.release();if(game)pad.profiles.importGame(json.getText().toString());else pad.profiles.importProfile(json.getText().toString());pad.selectProfile(pad.profiles.active);});
    }
    private void game(String name){
        List<String> ids=pad.profiles.layouts(name);
        List<String> options=new ArrayList<>();for(String id:ids)options.add(pad.profiles.name(id));
        options.add("＋ Create layout");options.add("Duplicate game");options.add("Rename game");options.add("Export game");options.add("Delete game");
        menu(name+(ids.isEmpty()?" · no layouts":""),options.toArray(new String[0]),i->{
            if(i<ids.size()){preview(ids.get(i));return;}
            switch(i-ids.size()){
                case 0:naming("New layout in "+name,"Layout A",title->{ControllerConfig c=ControllerConfig.preset("Custom Blank Layout");c.game=name;pad.profiles.create(title,c);});break;
                case 1:naming("Duplicate game",name+" copy",title->pad.profiles.duplicateGame(name,title));break;
                case 2:naming("Rename game",name,title->pad.profiles.renameGame(name,title));break;
                case 3:((ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("PhonePad game",pad.profiles.exportGame(name)));notice("Game profile copied as JSON.");break;
                case 4:if(name.equals("General")){notice("General contains built-in templates.");break;}
                    compact(new AlertDialog.Builder(context).setTitle("Delete "+name+"?").setMessage("This deletes the game and its layouts.").setPositiveButton("Delete",(d,w)->{pad.release();pad.profiles.deleteGame(name);pad.selectProfile(pad.profiles.active);}).setNegativeButton("Cancel",null).create());break;
            }
        });
    }
    private void radial(int page){List<String> ids=pad.profiles.layouts(pad.config.game);int start=page*8,end=Math.min(ids.size(),start+8);List<String> slice=ids.subList(start,end);List<String> names=new ArrayList<>();for(String id:slice)names.add(pad.profiles.name(id));AlertDialog[] dialog=new AlertDialog[1];RadialLayouts view=new RadialLayouts(context,names,index->{pad.selectProfile(slice.get(index));dialog[0].dismiss();});dialog[0]=new AlertDialog.Builder(context).setTitle("Layouts · "+pad.config.game).setView(view).setPositiveButton("More",(d,w)->radial(end==ids.size()?0:page+1)).setNeutralButton("Settings",(d,w)->show()).setNegativeButton("Close",null).create();dialog[0].show();}
    private void temporary(){route("temporary",this::temporary);ControllerConfig c=pad.config.copy();LinearLayout b=box();List<String> ids=new ArrayList<>();ids.add("");ids.addAll(pad.profiles.layouts(c.game));String[] values=ids.toArray(new String[0]),names=new String[values.length];names[0]="Disabled";for(int i=1;i<names.length;i++)names[i]=pad.profiles.name(values[i]);Spinner target=choice(b,"Hold destination",names,values,c.holdLayout);Spinner aim=choice(b,"While LT held → layout",names,values,c.aimLayout);String[] styles={"LIST","RADIAL"};int advanced=b.getChildCount();Spinner style=choice(b,"Quick layout menu",styles,styles,c.quickStyle);ControllerConfig.Control chord=ControllerConfig.preset("Default").get("NEXT").copy();chord.buttons=c.switchButtons;mappingButton(b,chord,false);text(b,"The button combination above switches to the next layout. Select at least two buttons, or none to disable.");Switch visible=toggle(b,"Show temporary-layout control",c.standard("LAYOUT HOLD").visible),edge=toggle(b,"Switch with swipe from empty edge",c.edgeSwitch),transfer=toggle(b,"Keep compatible sticks across layouts (both layouts must enable)",c.transferSticks);View visibility=(View)visible.getParent();dependentChoice(target,visibility,n->n>0);b.removeView(visibility);b.addView(visibility,advanced);fold(b,"Advanced switching",advanced+1);form("Temporary layout",b,()->{c.switchButtons=chord.buttons<0?0:chord.buttons;if(Integer.bitCount(c.switchButtons)==1)throw new IllegalArgumentException("Choose at least two buttons for a switching combination");c.aimLayout=values[aim.getSelectedItemPosition()];c.quickStyle=styles[style.getSelectedItemPosition()];c.edgeSwitch=edge.isChecked();c.transferSticks=transfer.isChecked();c.holdLayout=values[target.getSelectedItemPosition()];c.standard("LAYOUT HOLD").visible=visible.isChecked();commit(c);});}
    private void commit(ControllerConfig c){
        if(!pad.profiles.active.equals(pageLayoutId))throw new IllegalStateException("Layout changed. Reopen these settings.");
        if(pad.profiles.builtin(pad.profiles.active)){
            ControllerConfig draft=c.copy(),baseline=pageBaseline.copy();LinearLayout owner=currentBody;
            pad.requestLayoutEdit(()->{
                try{pad.release();pad.config=ProfileStore.mergeChanges(baseline,draft,pad.config);pad.config.name=pad.profiles.name(pad.profiles.active);pad.saveLayout();
                    if(currentBody==owner){pageBaseline=draft.copy();pageLayoutId=pad.profiles.active;savedForm=fingerprint(currentBody);screen.status("Saved");if(pageUndo!=null)pageUndo.setVisibility(View.VISIBLE);}
                    else if(screen.showing()&&currentRoute!=null)currentRoute.run();
                }catch(IllegalArgumentException|IllegalStateException failure){notice(failure.getMessage());}
            },()->{if(currentBody==owner&&screen.showing()){autoSave=null;screen.onLeave(()->{});if(currentRoute!=null)currentRoute.run();}});
            throw new IllegalStateException("Choose how to save this built-in layout.");
        }
        pad.release();pad.config=ProfileStore.mergeChanges(pageBaseline,c,pad.config);pad.config.name=pad.profiles.name(pad.profiles.active);pad.saveLayout();
        pageBaseline=c.copy();pageLayoutId=pad.profiles.active;
    }
    private void sticks(){route("sticks",this::sticks);
        ControllerConfig c=pad.config.copy();LinearLayout b=box();String[] lm={"FIXED","FLOATING"},rm={"FIXED","FLOATING","TOUCHPAD"},gestures={"DOUBLE_TAP","LONG_PRESS","SECOND_FINGER","OFF"};
        Spinner left=choice(b,"Left stick",new String[]{"Fixed","Floating"},lm,c.mode(c.standard("LS"))),right=choice(b,"Right stick",new String[]{"Fixed","Floating","Touchpad / camera"},rm,c.mode(c.standard("RS"))),gesture=choice(b,"L3 / R3 gesture",new String[]{"Double tap, hold second touch","Long press, then drag","Second finger hold","Off"},gestures,c.clickGesture);
        EditText dead=number(b,"Stick dead zone (0–0.4)",c.deadzone),sensitivity=number(b,"Stick / camera sensitivity (0.25–3)",c.sensitivity);
        for(int n=0;n<3;n++){View child=b.getChildAt(0);b.removeViewAt(0);b.addView(child);}fold(b,"Stick behavior & clicks",2);b.addView(screen.button("Left stick precision & response   ›",()->properties(pad.config.standard("LS").id,3)));b.addView(screen.button("Right stick precision & response   ›",()->properties(pad.config.standard("RS").id,3)));form("Sticks",b,()->{c.leftMode=lm[left.getSelectedItemPosition()];c.rightMode=rm[right.getSelectedItemPosition()];c.standard("LS").mode=c.standard("RS").mode="";c.clickGesture=gestures[gesture.getSelectedItemPosition()];c.deadzone=value(dead,0,.4f);c.sensitivity=value(sensitivity,.25f,3);commit(c);});
    }
    private void triggers(){menu("Triggers & racing",new String[]{"Independent LT / RT","Left stick gas / brake","Unified LT / RT"},i->{if(i==0)independentTriggers();else if(i==1)racing();else unified();});}
    private void setValues(EditText[] fields,float[] values){binding=true;for(int i=0;i<fields.length;i++)fields[i].setText(Float.toString(values[i]));binding=false;saveChange();}
    private void presetControls(LinearLayout b,String group,EditText[] fields,float[][] values){
        String[] names={"Balanced","Precise","Responsive"};Button status=screen.button("",()->new AlertDialog.Builder(context).setTitle(group).setItems(names,(dialog,index)->setValues(fields,values[index])).show());status.setTextColor(SettingsScreen.ACCENT);
        Runnable update=()->{String selected="Custom";for(int i=0;i<values.length;i++){boolean match=true;for(int j=0;j<fields.length;j++)try{match&=Math.abs(Float.parseFloat(fields[j].getText().toString())-values[i][j])<.0001f;}catch(NumberFormatException ex){match=false;}if(match)selected=names[i];}status.setText(group+" · "+selected+"  ▾");};
        for(EditText field:fields)field.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int a,int c,int f){}public void onTextChanged(CharSequence s,int a,int before,int count){update.run();}public void afterTextChanged(android.text.Editable e){}});update.run();b.addView(status,0);
    }
    private void addFeelPresets(LinearLayout b,EditText travel,EditText gain,float bt,float bg,float pt,float pg,float rt,float rg){presetControls(b,"Trigger feel",new EditText[]{travel,gain},new float[][]{{bt,bg},{pt,pg},{rt,rg}});}
    private void independentTriggers(){route("independentTriggers",this::independentTriggers);ControllerConfig c=pad.config.copy();LinearLayout b=box();String[] modes={"EDGE","VERTICAL","DIGITAL"};Spinner mode=choice(b,"Mode for both triggers",new String[]{"↔ Slide inward","↓ Pull down","● Hold to press"},modes,c.triggerMode);EditText travel=number(b,"Travel fraction of width / height (0.04–0.5)",c.travel),sensitivity=number(b,"Trigger sensitivity (0.25–3)",c.triggerSensitivity);text(b,"Short ↔ Long · Move this far to fully press. Tap a value for exact tuning.");Switch lt=toggle(b,"Reverse LT direction",c.standard("LT").reverse),rt=toggle(b,"Reverse RT direction",c.standard("RT").reverse);addFeelPresets(b,travel,sensitivity,.16f,1,.24f,.8f,.10f,1.3f);View modeRow=(View)mode.getParent().getParent();b.removeView(modeRow);LinearLayout tuning=new LinearLayout(context);tuning.setOrientation(LinearLayout.VERTICAL);while(b.getChildCount()>0){View child=b.getChildAt(0);b.removeViewAt(0);tuning.addView(child);}b.addView(modeRow);b.addView(tuning);fold(tuning,"Advanced",4);dependentChoice(mode,tuning,n->n!=2);form("Independent triggers",b,()->{c.triggerMode=modes[mode.getSelectedItemPosition()];c.standard("LT").mode=c.standard("RT").mode="";c.travel=value(travel,.04f,.5f);c.triggerSensitivity=value(sensitivity,.25f,3);c.standard("LT").reverse=lt.isChecked();c.standard("RT").reverse=rt.isChecked();commit(c);});}
    private void racing(){route("racing",this::racing);ControllerConfig c=pad.config.copy();LinearLayout b=box();Switch preserve=toggle(b,"Preserve LS vertical output",c.preserveY),hide=toggle(b,"Hide separate LT / RT",c.hideTriggers);toggle(b,"Preview invert LS Y (sandbox only)",false);text(b,"Push up for gas or down for brake, then steer sideways. Distance from center sets pedal strength. Center or lift to release.");EditText dead=number(b,"Trigger activation dead zone (0–0.5)",c.triggerDeadzone),sensitivity=number(b,"Trigger sensitivity (0.25–3)",c.triggerSensitivity);String[] curves={"LINEAR","SOFT","AGGRESSIVE"};Spinner curve=choice(b,"Trigger curve",new String[]{"Linear","Soft · precise near center","Aggressive"},curves,c.curve);View gainRow=(View)sensitivity.getParent().getParent();b.removeView(gainRow);b.addView(gainRow,1);fold(b,"Advanced",2);text(b,"Add Steering + gas / brake from the layout editor’s + Add menu.");form("Left stick gas / brake",b,()->{c.preserveY=preserve.isChecked();c.hideTriggers=hide.isChecked();c.triggerDeadzone=value(dead,0,.5f);c.triggerSensitivity=value(sensitivity,.25f,3);c.curve=curves[curve.getSelectedItemPosition()];commit(c);});}
    private void unified(){route("unified",this::unified);
        ControllerConfig c=pad.config.copy();LinearLayout b=box();
        Switch floating=toggle(b,"Origin follows touchdown",c.unifiedFloating),invert=toggle(b,"Invert direction",c.unifiedInvert);
        text(b,"Up / left = RT. Down / right = LT. Return to origin or release to neutralize. Edit size and activation area in Controls.");
        String[] orientations={"VERTICAL","HORIZONTAL"},curves={"LINEAR","SOFT","AGGRESSIVE"};
        Spinner orientation=choice(b,"Orientation",orientations,orientations,c.unifiedOrientation),curve=choice(b,"Response",curves,curves,c.unifiedCurve);
        EditText dead=number(b,"Dead zone (0–0.5)",c.unifiedDeadzone),travel=number(b,"Activation distance / display (0.04–0.5)",c.unifiedTravel),gain=number(b,"Sensitivity (0.25–3)",c.unifiedSensitivity);
        fold(b,"Advanced",4);text(b,"Add Unified LT / RT from the layout editor’s + Add menu.");form("Unified analog trigger",b,()->{c.unifiedFloating=floating.isChecked();c.unifiedInvert=invert.isChecked();c.unifiedOrientation=orientations[orientation.getSelectedItemPosition()];c.unifiedCurve=curves[curve.getSelectedItemPosition()];c.unifiedDeadzone=value(dead,0,.5f);c.unifiedTravel=value(travel,.04f,.5f);c.unifiedSensitivity=value(gain,.25f,3);commit(c);});
    }
    private void gyro(){route("gyro",this::gyro);
        ControllerConfig c=pad.config.copy();LinearLayout b=box();
        Switch enabled=toggle(b,"Enable gyro-assisted RS",c.gyroEnabled);
        Switch aim=toggle(b,"Only while LT is active",c.gyroAimOnly);
        EditText gain=number(b,"Horizontal sensitivity (0.05–5)",c.gyroGainX()),vertical=number(b,"Vertical sensitivity (0.05–5)",c.gyroGainY());
        Button calibrate=screen.button("Calibrate gyro",()->{if(pad.gyro!=null)pad.gyro.calibrate();});calibrate.setEnabled(pad.gyro!=null&&pad.gyro.available());
        LinearLayout calibration=new LinearLayout(context);calibration.addView(calibrate,new LinearLayout.LayoutParams(0,-2,1));calibration.addView(screen.info("Calibrate","Hold the phone still for one second. Calibration also works while motion aiming is off."),new LinearLayout.LayoutParams(dp(48),dp(48)));b.addView(calibration);
        if(pad.gyro==null||!pad.gyro.available())text(b,"This phone has no gyroscope.");
        int advanced=b.getChildCount();
        Switch ix=toggle(b,"Invert horizontal",c.gyroInvertX),iy=toggle(b,"Invert vertical",c.gyroInvertY);
        EditText smoothing=number(b,"Filtering (0–0.95)",c.gyroSmoothing),dead=number(b,"Rest dead zone (0–0.2 rad/s)",c.gyroDeadzone);
        b.removeView(calibration);fold(b,"Advanced",advanced-1);dependent(b,enabled,1);b.addView(calibration);form("Gyroscope",b,()->{c.gyroEnabled=enabled.isChecked();c.gyroAimOnly=aim.isChecked();c.gyroInvertX=ix.isChecked();c.gyroInvertY=iy.isChecked();c.gyroSensitivityX=value(gain,.05f,5);c.gyroSensitivityY=value(vertical,.05f,5);c.gyroSmoothing=value(smoothing,0,.95f);c.gyroDeadzone=value(dead,0,.2f);commit(c);});
    }
    private void fps(){route("fps",this::fps);
        ControllerConfig c=pad.config.copy();LinearLayout b=box();
        Switch fullscreen=toggle(b,"Fullscreen camera background",c.standard("RS").fullscreen);
        EditText camera=number(b,"Camera sensitivity (0.1–5)",c.cameraX),precision=number(b,"RS sensitivity while LT active (0.1–1)",c.aimSensitivity);
        String[] modes={"HOLD","TOGGLE"};Spinner mode=choice(b,"Aim activation",new String[]{"Hold to aim","Tap to toggle"},modes,c.aimMode);
        Switch same=toggle(b,"Enable same-thumb camera fire",c.sameThumbFire);
        int advancedStart=b.getChildCount();
        EditText x=number(b,"Camera X sensitivity (0.1–5)",c.cameraX),y=number(b,"Camera Y sensitivity (0.1–5)",c.cameraY),curve=number(b,"Camera response exponent (0.25–3)",c.cameraCurve),smooth=number(b,"Camera smoothing (0–0.95)",c.cameraSmoothing);
        // The simple control changes both axes only when the user actually changes it.
        camera.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence t,int st,int count,int after){}public void onTextChanged(CharSequence t,int st,int before,int count){if(!binding){x.setText(t);y.setText(t);}}public void afterTextChanged(android.text.Editable e){}});
        int timingStart=b.getChildCount();
        EditText tap=number(b,"First tap duration (80–250 ms)",c.fireTapMs),gap=number(b,"Second tap interval (120–400 ms)",c.fireGapMs),slop=number(b,"Tap movement tolerance (0.5–5 %)",c.fireSlop*100);
        text(b,"Tap, then hold nearby to fire while moving the camera. Lift or leave the camera area to stop.");
        LinearLayout timing=dependent(b,same,timingStart);presetControls(timing,"Gesture feel",new EditText[]{tap,gap,slop},new float[][]{{180,280,2.5f},{140,220,1.5f},{220,350,3.5f}});
        Switch fire=toggle(b,"Second finger in camera region holds RT",c.cameraFire),aim=toggle(b,"Show aim control",c.standard("AIM").visible),hold=toggle(b,"Show movement hold control",c.standard("LS HOLD").visible),floatingFire=toggle(b,"Show floating fire button",c.standard("FIRE").visible);
        toggle(b,"Preview aim held (sandbox only)",false);
        fold(b,"Advanced",advancedStart);form("FPS & Two-Thumb",b,()->{c.standard("RS").fullscreen=fullscreen.isChecked();if(fullscreen.isChecked()){c.standard("RS").visible=true;c.standard("RS").mode="TOUCHPAD";}c.sameThumbFire=same.isChecked();if(c.sameThumbFire){c.rightMode="TOUCHPAD";for(ControllerConfig.Control control:c.controls)if(control.kind.equals("stick")&&control.bit==1)control.mode="TOUCHPAD";}c.fireTapMs=value(tap,80,250);c.fireGapMs=value(gap,120,400);c.fireSlop=value(slop,.5f,5)/100;c.standard("FIRE").visible=floatingFire.isChecked();c.cameraFire=fire.isChecked();c.standard("AIM").visible=aim.isChecked();c.standard("LS HOLD").visible=hold.isChecked();c.aimMode=modes[mode.getSelectedItemPosition()];c.cameraX=value(x,.1f,5);c.cameraY=value(y,.1f,5);c.cameraCurve=value(curve,.25f,3);c.cameraSmoothing=value(smooth,0,.95f);c.aimSensitivity=value(precision,.1f,1);commit(c);});
    }
    private void computerStatus(LinearLayout body){TextView state=new TextView(context);state.setTextColor(SettingsScreen.TEXT);state.setTextSize(17);state.setPadding(0,dp(8),0,dp(12));body.addView(state);Runnable update=new Runnable(){public void run(){state.setText(pad.connected?pad.status:"Not connected · "+pad.status);if(state.isAttachedToWindow())state.postDelayed(this,1000);}};state.post(update);state.setText(pad.connected?pad.status:"No computer connected");}
    private void connectionAction(int i){if(context instanceof MainActivity)((MainActivity)context).connectionAction(i,this);else connection.run();}
    public void connectionPage(){
        route("connectionPage",this::connectionPage);screen.selected(3);LinearLayout body=box();computerStatus(body);
        LinearLayout bluetooth=screen.panel("BLUETOOTH");text(bluetooth,"Connect directly to a paired computer.");bluetooth.addView(screen.button("Choose Bluetooth computer   ›",()->connectionAction(0)));body.addView(bluetooth);
        LinearLayout companion=screen.panel("WI-FI COMPANION");TextView hint=screen.label("Use the PC companion for game vibration and wireless input.",14);hint.setTextColor(SettingsScreen.MUTED);companion.addView(hint);
        Runnable update=new Runnable(){public void run(){boolean bluetoothReady=context instanceof MainActivity&&((MainActivity)context).bluetoothConnected();hint.setText(bluetoothReady?"Bluetooth is ready. Switch to the Wi-Fi companion for game vibration — no key to type.":"Use the PC companion for game vibration and wireless input.");if(bluetoothReady&&suggestWifiAfterBluetooth&&!wifiSuggestionShown){wifiSuggestionShown=true;new AlertDialog.Builder(context).setTitle("Connect the Wi-Fi companion too?").setMessage("Bluetooth connects your controller. The Wi-Fi companion adds game vibration and wireless input.").setNegativeButton("Later",null).setPositiveButton("Find computer",(d,w)->scanQr()).show();}if(hint.isAttachedToWindow())hint.postDelayed(this,1000);}};hint.post(update);
        companion.addView(screen.button("Connect companion   ›",this::scanQr));body.addView(companion);
        Button disconnect=screen.button("Disconnect",()->{connectionAction(4);connectionPage();});disconnect.setEnabled(pad.connected);body.addView(disconnect);Runnable refresh=new Runnable(){public void run(){disconnect.setEnabled(pad.connected);if(disconnect.isAttachedToWindow())disconnect.postDelayed(this,500);}};disconnect.post(refresh);
        LinearLayout more=new LinearLayout(context);more.setOrientation(LinearLayout.VERTICAL);more.addView(screen.button("Pair a new Bluetooth computer",()->connectionAction(1)));more.addView(screen.button("Bluetooth settings",()->connectionAction(2)));more.addView(screen.button("Connection details",()->connectionAction(5)));fold(more,"More options",0);body.addView(more);page("Connection",body);
    }
    void computerRow(LinearLayout body,String name,String status,Runnable connect){body.addView(screen.button("▣  "+name+"\n"+status+" · Connect",connect));}
    void devicePicker(String[] names,Runnable[] connect,boolean all,Runnable more,Runnable pair){route("devicePicker:"+all,()->devicePicker(names,connect,all,more,pair));ensureScreen();screen.selected(3);screen.back(pad.editing?"‹ Editor":"‹ Setup",pad.editing?screen::close:this::setup);LinearLayout b=box();text(b,all?"PAIRED DEVICES":"PAIRED COMPUTERS");if(names.length==0)text(b,"No paired computers found. Pair with your computer, then return here.");for(int i=0;i<names.length;i++){final int n=i;b.addView(screen.button("▣  "+names[i],connect[n]));}b.addView(screen.button("Pair another computer",pair));b.addView(screen.button(all?"Show computers only":"Show all paired devices",more));text(b,"Device type is reported by Bluetooth. Connection confirms compatibility.");page("Choose a computer",b);}
    public void connectionStatus(String status){route("connectionStatus",()->connectionStatus(status));LinearLayout b=box();text(b,status);text(b,"Standard HID input. Bluetooth rumble requires a host that understands vendor report 2; Wi-Fi rumble requires the companion. Windows/XInput and actual games need separate verification.");page("Connection diagnostics",b);}
    void pairScanned(CompanionLink.Computer computer,String token){
        route("qr-pairing",()->pairScanned(computer,token));ensureScreen();LinearLayout body=box();TextView status=screen.label("Connecting to "+computer.name+"…",15);body.addView(status);body.addView(screen.button("Cancel",()->screen.close()));page("Wi-Fi companion",body);
        SharedPreferences prefs=context.getSharedPreferences("connection",0);final boolean[] alive={true};final CompanionLink[] worker={null};
        worker[0]=new CompanionLink(new CompanionLink.Listener(){
            public void computer(CompanionLink.Computer ignored){}
            public void status(String message){status.post(()->{if(alive[0])status.setText(message);});}
            public void paired(CompanionLink.Computer paired,String key){status.post(()->{if(!alive[0])return;prefs.edit().putString("key."+paired.id,key).putString("name."+paired.id,paired.name).apply();((MainActivity)context).connectWifi(paired.host,paired.port,key);connectionPage();});}
        });
        screen.onLeave(()->{alive[0]=false;worker[0].close();});worker[0].pairQr(computer,android.os.Build.MODEL,token);
    }
    public void wifiPage(){
        route("wifiPage",this::wifiPage);LinearLayout body=box();text(body,"Open PhonePad companion on your PC. Keep both devices on the same Wi-Fi.");
        TextView status=screen.label("Looking for computers…",15);body.addView(status);LinearLayout computers=new LinearLayout(context);computers.setOrientation(LinearLayout.VERTICAL);body.addView(computers);
        body.addView(screen.button("Search again",this::wifiPage));body.addView(screen.button("Enter data manually   ›",this::manualWifi));page("Wi-Fi companion",body);
        SharedPreferences prefs=context.getSharedPreferences("connection",0);final CompanionLink[] worker={null};final boolean[] alive={true};
        CompanionLink.Listener listener=new CompanionLink.Listener(){
            public void status(String text){status.post(()->{if(alive[0])status.setText(text);});}
            public void computer(CompanionLink.Computer computer){status.post(()->{if(!alive[0])return;
                String saved=prefs.getString("key."+computer.id,"");Button item=screen.button("▣  "+computer.name+"\n"+computer.host+(saved.isEmpty()?" · Pair":" · Connect"),()->{
                    if(!alive[0])return;
                    if(!saved.isEmpty()){((MainActivity)context).connectWifi(computer.host,computer.port,saved);connectionPage();return;}
                    worker[0].close();worker[0]=new CompanionLink(this);worker[0].pair(computer,android.os.Build.MODEL);status.setText("Requesting approval on "+computer.name+"…");
                });if(!saved.isEmpty()){item.setTooltipText("Hold to forget this computer");item.setOnLongClickListener(v->{new AlertDialog.Builder(context).setTitle("Forget "+computer.name+"?").setMessage("You will need to approve pairing on this computer again.").setNegativeButton("Cancel",null).setPositiveButton("Forget",(d,w)->{prefs.edit().remove("key."+computer.id).remove("name."+computer.id).apply();wifiPage();}).show();return true;});}computers.addView(item);screen.spaceRows(computers);
            });}
            public void paired(CompanionLink.Computer computer,String key){status.post(()->{if(!alive[0])return;
                prefs.edit().putString("key."+computer.id,key).putString("name."+computer.id,computer.name).apply();
                ((MainActivity)context).connectWifi(computer.host,computer.port,key);connectionPage();
            });}
        };
        worker[0]=new CompanionLink(listener);int port;try{port=Integer.parseInt(prefs.getString("port","39570"));}catch(Exception e){port=39570;}
        worker[0].scan(prefs.getString("host",""),port);screen.onLeave(()->{alive[0]=false;worker[0].close();});
    }
    private void scanQr(){
        if(context instanceof MainActivity){if(screen!=null)screen.close();((MainActivity)context).scanCompanionQr();}
        else wifiPage();
    }
    private void manualWifi(){
        route("manualWifi",this::manualWifi);SharedPreferences prefs=context.getSharedPreferences("connection",0);LinearLayout body=box();
        EditText host=field(body,"Computer IP address",prefs.getString("host",""),false),port=field(body,"UDP port (default 39570)",prefs.getString("port","39570"),true),code=field(body,"Code","",false);code.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.AllCaps(),new android.text.InputFilter.LengthFilter(8)});
        text(body,"Enter the code shown below the QR code in PhonePad Companion.");page("Manual connection",body);body.addView(screen.button("Connect",()->{String address=host.getText().toString().trim(),shortCode=code.getText().toString().trim().toUpperCase(Locale.ROOT);int udpPort;try{udpPort=Integer.parseInt(port.getText().toString());if(address.isEmpty()||udpPort<1||udpPort>65535||!shortCode.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}"))throw new IllegalArgumentException();}catch(IllegalArgumentException e){screen.status("Enter address, port and code.");return;}prefs.edit().putString("host",address).putString("port",Integer.toString(udpPort)).apply();TextView status=screen.label("Finding companion…",15);body.addView(status);final boolean[] alive={true};final CompanionLink[] worker={null};CompanionLink.Listener listener=new CompanionLink.Listener(){public void status(String message){status.post(()->{if(alive[0])status.setText(message);});}public void computer(CompanionLink.Computer computer){if(!computer.host.equals(address)||computer.port!=udpPort)return;status.post(()->{if(!alive[0])return;worker[0].close();worker[0]=new CompanionLink(this);status.setText("Connecting to "+computer.name+"…");worker[0].pairCode(computer,android.os.Build.MODEL,shortCode);});}public void paired(CompanionLink.Computer computer,String savedKey){status.post(()->{if(!alive[0])return;prefs.edit().putString("key."+computer.id,savedKey).putString("name."+computer.id,computer.name).apply();((MainActivity)context).connectWifi(computer.host,computer.port,savedKey);connectionPage();});}};worker[0]=new CompanionLink(listener);worker[0].scan(address,udpPort);screen.onLeave(()->{alive[0]=false;worker[0].close();});}));screen.spaceRows(body);
    }
    private void appearance(){
        route("appearance",this::appearance);screen.selected(4);LinearLayout body=box();SharedPreferences prefs=context.getSharedPreferences("appearance",0);
        text(body,"THEME");appearanceChoices(body,"theme",new String[]{"Dark","Light","System"},prefs.getString("theme","Dark"));
        text(body,"ACCENT COLOR");appearanceChoices(body,"accent",new String[]{"Mint","Blue","Violet","Amber"},prefs.getString("accent","Mint"));
        body.addView(screen.button("Xbox / PlayStation symbols & alignment   ›",this::editorSettings));body.addView(screen.button("Control opacity, size & visibility   ›",this::palette));body.addView(screen.button("Arrange controls   ›",()->{screen.close();pad.requestEditing();}));page("Appearance",body);
    }
    private void appearanceChoices(LinearLayout body,String key,String[] values,String current){
        LinearLayout options=new LinearLayout(context);options.setOrientation(LinearLayout.HORIZONTAL);body.addView(options);
        for(String value:values){Button button=screen.button((value.equals(current)?"●  ":"○  ")+value,()->{
            context.getSharedPreferences("appearance",0).edit().putString(key,value).apply();AppAppearance.apply(context);pad.loadSettings();screen.close();screen=null;appearance();
        });if(value.equals(current))button.setTextColor(SettingsScreen.ACCENT);button.setGravity(Gravity.CENTER);button.setTooltipText("Use "+value+" "+(key.equals("theme")?"theme":"accent"));options.addView(button,new LinearLayout.LayoutParams(0,-2,1));}
    }
    private void lockSettings(){route("lockSettings",this::lockSettings);ControllerConfig c=pad.config.copy();LinearLayout b=box();Switch lock=toggle(b,"Prefer game mode when returning to controller",c.gameplayLock);text(b,"Hold the visible edge handle to exit game mode. Movement or a second touch cancels the hold. Connect, layout switching and Settings remain available in the top bar.");text(b,"Android navigation is hidden during play. Your layout and input mappings stay unchanged.");form("Game mode",b,()->{c.gameplayLock=lock.isChecked();commit(c);});}
    private void haptics(){route("haptics",this::haptics);
        ensureScreen();if(!(context instanceof MainActivity)){feedback();return;}PhoneHaptics h=((MainActivity)context).haptics;LinearLayout b=box();
        Switch host=toggle(b,"Host game rumble",h.hostEnabled),local=toggle(b,"Local touch feedback",h.localEnabled);EditText strength=number(b,"Overall intensity (0–1)",h.strength);
        text(b,"Game vibration plays when your computer sends rumble.");if(!h.enabled)text(b,"Enable vibration to adjust touch and game feedback.");b.addView(screen.button("Test vibration",()->{h.testBoth();screen.status("Testing · synthetic vibration");}));
        int advanced=b.getChildCount();Switch enabled=toggle(b,"Haptics master",h.enabled),quiet=toggle(b,"Reduced / quiet mode",h.quiet),texture=toggle(b,"Pulsed texture",h.texture);
        EditText heavy=number(b,"Heavy mix weight (0–1)",h.heavyWeight),light=number(b,"Light mix weight (0–1)",h.lightWeight);
        b.addView(screen.button("Test heavy channel",()->h.test(true)));b.addView(screen.button("Test light channel",()->h.test(false)));b.addView(screen.button("Stop vibration test",h::stop));b.addView(screen.button("Touch strength for this layout",this::feedback));fold(b,"Advanced haptics",advanced);
        View enabledRow=(View)enabled.getParent();((ViewGroup)enabledRow.getParent()).removeView(enabledRow);b.addView(enabledRow,0);LinearLayout hapticOptions=dependent(b,enabled,1);
        Runnable dependencies=()->{boolean on=enabled.isChecked();hapticOptions.setVisibility(on?View.VISIBLE:View.GONE);host.setEnabled(on);local.setEnabled(on);quiet.setEnabled(on);texture.setEnabled(on);for(EditText field:new EditText[]{strength,heavy,light}){field.setEnabled(on);((SeekBar)field.getTag()).setEnabled(on);}};enabled.setOnCheckedChangeListener((button,on)->{dependencies.run();saveChange();});dependencies.run();
        form("Haptics",b,()->{float st=value(strength,0,1),hw=value(heavy,0,1),lw=value(light,0,1);h.enabled=enabled.isChecked();h.hostEnabled=host.isChecked();h.localEnabled=local.isChecked();h.quiet=quiet.isChecked();h.texture=texture.isChecked();h.strength=st;h.heavyWeight=hw;h.lightWeight=lw;h.save();});
    }
    private void feedback(){route("feedback",this::feedback);ControllerConfig draft=pad.config.copy();LinearLayout body=box();String[] levels={"0","1","2","3"};Spinner level=choice(body,"Local cue strength",new String[]{"Off","Light (default)","Medium","Strong"},levels,Integer.toString(draft.haptics));text(body,"Touch strength for this layout. Global vibration and each control’s feedback must also be on.");form("Local touch feedback",body,()->{draft.haptics=level.getSelectedItemPosition();commit(draft);});}
    private void editorSettings(){route("editorSettings",this::editorSettings);ControllerConfig c=pad.config.copy();LinearLayout b=box();String[] names={"XBOX","PLAYSTATION"};Spinner label=choice(b,"Button labels",new String[]{"Xbox","PlayStation"},names,c.labels);Switch grid=toggle(b,"Show grid",c.grid),snap=toggle(b,"Snap positions to grid",c.snap);EditText size=number(b,"Grid spacing (% of width)",c.gridSize*100);form("Labels & editor",b,()->{c.labels=names[label.getSelectedItemPosition()];c.grid=grid.isChecked();c.snap=snap.isChecked();c.gridSize=value(size,1,10)/100;commit(c);});}
    public void palette(){
        pad.release();String[] names=new String[pad.config.controls.size()+1];names[0]="＋ Add control";
        Map<String,Integer> counts=new HashMap<>();for(int i=0;i<pad.config.controls.size();i++){ControllerConfig.Control c=pad.config.controls.get(i);int n=counts.merge(c.title(),1,Integer::sum);names[i+1]=c.title()+(n>1?" · "+n:"")+(pad.config.shown(c)?"":" · hidden");}
        menu("Controls · add, edit or restore",names,i->{if(i==0)addControl();else control(pad.config.controls.get(i-1));});
    }
    private void addControl(){
        ControllerConfig templates=ControllerConfig.preset("Default");String[] names=new String[templates.controls.size()];
        for(int i=0;i<names.length;i++)names[i]=templates.controls.get(i).title();
        menu("Add control instance",names,i->pad.requestLayoutEdit(()->{pad.addControl(templates.controls.get(i));screen.close();pad.startEditing();},()->{}));
    }
    private void mappingButton(LinearLayout b,ControllerConfig.Control c,boolean modified){
        Button button=screen.button("",()->{});button.setText(modified?"Buttons while modifier is held":"Map buttons / combination");b.addView(button);LinearLayout options=new LinearLayout(context);options.setOrientation(LinearLayout.VERTICAL);options.setVisibility(android.view.View.GONE);b.addView(options);
        String[] names={"A","B","X","Y","LB","RB","View","Menu","Home","L3","R3","Share"};int[] bits={PadState.A,PadState.B,PadState.X,PadState.Y,PadState.LB,PadState.RB,PadState.VIEW,PadState.MENU,PadState.GUIDE,PadState.L3,PadState.R3,PadState.SHARE};int mask=modified?c.modifiedButtons:c.buttons;if(mask<0)mask=c.buttons<0?1<<c.bit:c.buttons;
        CheckBox[] checks=new CheckBox[bits.length];for(int i=0;i<bits.length;i++){checks[i]=new CheckBox(context);checks[i].setText(names[i]);checks[i].setChecked((mask&(1<<bits[i]))!=0);options.addView(checks[i]);}
        Runnable update=()->{int result=0;for(int i=0;i<bits.length;i++)if(checks[i].isChecked())result|=1<<bits[i];if(modified)c.modifiedButtons=result;else c.buttons=result;saveChange();};for(CheckBox box:checks)box.setOnCheckedChangeListener((v,on)->update.run());
        Button defaults=screen.button("",()->{});defaults.setText(modified?"Use normal mapping":"Use default mapping");options.addView(defaults);Runnable reset=()->{for(int i=0;i<bits.length;i++)checks[i].setChecked((1<<c.bit&1<<bits[i])!=0);if(modified)c.modifiedButtons=-1;else c.buttons=-1;};defaults.setOnClickListener(v->reset.run());resets.add(reset);
        button.setOnClickListener(v->options.setVisibility(options.getVisibility()==android.view.View.VISIBLE?android.view.View.GONE:android.view.View.VISIBLE));
    }
    public void control(ControllerConfig.Control source){
        if(source.kind.equals("button")||source.kind.equals("combo")){AutomationEditor.show(pad,source.id);return;}
        pad.release();String[] labels={"Position & size","Touch area","Visibility & label","Behavior","Input mapping","Reset control","Duplicate control","Delete control"};List<Integer> sections=new ArrayList<>(Arrays.asList(0,1,2));if(Arrays.asList("stick","trigger","unified","aim","hold","layoutHold").contains(source.kind))sections.add(3);sections.addAll(Arrays.asList(5,6,7));String[] items=new String[sections.size()];for(int n=0;n<items.length;n++)items[n]=labels[sections.get(n)];menu("Edit "+source.title(),items,n->{int i=sections.get(n);
            if(i==5){
                ControllerConfig copy=pad.config.copy();ControllerConfig.Control template=ControllerConfig.preset(copy.base).get(source.id);
                if(template==null)for(ControllerConfig.Control candidate:ControllerConfig.preset("Default").controls)if(candidate.kind.equals(source.kind)&&candidate.bit==source.bit){template=candidate;break;}
                if(template!=null){int index=copy.controls.indexOf(copy.get(source.id));ControllerConfig.Control reset=template.copyAs(source.id);if(!source.id.equals(template.id))reset.label=source.title();copy.controls.set(index,reset);commit(copy);}return;
            }
            if(i==6){pad.requestLayoutEdit(()->{pad.addControl(source);screen.close();pad.startEditing();},()->{});return;}
            if(i==7){new AlertDialog.Builder(context).setTitle("Delete "+source.title()+"?").setMessage("Remove this control from the layout. Other controls keep their mappings.").setNegativeButton("Keep",null).setPositiveButton("Delete",(d,w)->pad.requestLayoutEdit(()->{pad.deleteControl(source.id);palette();},()->{})).show();return;}
            properties(source.id,i);
        });
    }
    public void properties(String id,int section){route("properties:"+id+":"+section,()->properties(id,section));
        ensureScreen();
        ControllerConfig copy=pad.config.copy();ControllerConfig.Control c=copy.get(id);LinearLayout b=box();
        if(section==0){EditText x=number(b,"Horizontal center (%)",c.x*100),y=number(b,"Vertical center (%)",c.y*100),w=number(b,"Width (%)",c.w*100),h=number(b,"Height (%)",c.h*100);form(c.title()+" · position & size",b,()->{c.x=value(x,0,100)/100;c.y=value(y,0,100)/100;c.w=value(w,2.5f,50)/100;c.h=value(h,4,70)/100;commit(copy);});}
        else if(section==1){EditText hit=number(b,"Overall touch multiplier (1–2)",c.hitbox),w=number(b,"Touch width multiplier (0.5–3)",c.hitWidth),h=number(b,"Touch height multiplier (0.5–3)",c.hitHeight);form(c.title()+" · touch area",b,()->{c.hitbox=value(hit,1,2);c.hitWidth=value(w,.5f,3);c.hitHeight=value(h,.5f,3);commit(copy);});}
        else if(section==2){Switch visible=toggle(b,"Visible",c.visible),haptic=toggle(b,"Touch feedback",c.haptic);EditText label=field(b,"Control label",c.title(),false),opacity=number(b,"Opacity (0.1–1)",c.opacity);form(c.title()+" · appearance",b,()->{c.label=label.getText().toString().trim();if(c.label.length()>60)throw new IllegalArgumentException("Use a label of at most 60 characters");c.haptic=haptic.isChecked();c.visible=visible.isChecked();c.opacity=value(opacity,.1f,1);commit(copy);});}
        else if(section==3){
            if(c.kind.equals("unified")){unified();return;}if(c.kind.equals("aim")||c.kind.equals("hold")){fps();return;}if(c.kind.equals("layoutHold")){temporary();return;}
            if(!c.kind.equals("stick")&&!c.kind.equals("trigger")){notice("This control has no extra behavior settings.");return;}
            String[] modes=c.kind.equals("stick")?(c.bit==0?new String[]{"FIXED","FLOATING"}:new String[]{"FIXED","FLOATING","TOUCHPAD"}):new String[]{"EDGE","VERTICAL","DIGITAL"};Spinner mode=choice(b,"Mode",modes,modes,copy.mode(c));
            if(c.kind.equals("trigger")){Switch reverse=toggle(b,"Reverse direction",c.reverse);form(c.title()+" · behavior",b,()->{c.mode=modes[mode.getSelectedItemPosition()];c.reverse=reverse.isChecked();commit(copy);});return;}
            Switch fullscreen=c.bit==1?toggle(b,"Fullscreen camera background",c.fullscreen):null;
            EditText dead=number(b,"Dead zone (-1 uses shared setting)",c.deadzone),gain=number(b,"Sensitivity (-1 uses shared setting)",c.sensitivity),curve=number(b,"Response exponent (0.25–3)",c.response);Switch ix=toggle(b,"Invert X",c.invertX),iy=toggle(b,"Invert Y",c.invertY);
            form(c.title()+" · analog behavior",b,()->{c.deadzone=value(dead,-1,.4f);c.sensitivity=value(gain,-1,5);if(c.deadzone<0&&c.deadzone!=-1||c.sensitivity<=0&&c.sensitivity!=-1)throw new IllegalArgumentException("Use -1 to inherit or a positive sensitivity / nonnegative dead zone");c.response=value(curve,.25f,3);c.invertX=ix.isChecked();c.invertY=iy.isChecked();c.mode=modes[mode.getSelectedItemPosition()];if(fullscreen!=null){c.fullscreen=fullscreen.isChecked();if(c.fullscreen)c.mode="TOUCHPAD";}commit(copy);});
        }else if(c.kind.equals("button")||c.kind.equals("combo")){
            AutomationEditor.show(pad,c.id);
        }else{
            notice("Choose a dedicated control to edit its behavior.");
        }
    }
    private void outputMapping(String id){
        route("output:"+id,()->outputMapping(id));ControllerConfig copy=pad.config.copy();ControllerConfig.Control c=copy.get(id);LinearLayout mapping=box();mappingButton(mapping,c,false);mappingButton(mapping,c,true);EditText lt=number(mapping,"LT pressure (0–1)",c.outputLT),rt=number(mapping,"RT pressure (0–1)",c.outputRT);form(c.title()+" · output",mapping,()->{c.outputLT=value(lt,0,1);c.outputRT=value(rt,0,1);commit(copy);});
    }
    void interaction(String id){ControllerConfig copy=pad.config.copy();gesture(copy,copy.get(id));}
    private void gesture(ControllerConfig copy,ControllerConfig.Control c){route("gesture:"+c.id,()->{ControllerConfig fresh=pad.config.copy();gesture(fresh,fresh.get(c.id));});
        LinearLayout b=box();String[] gestures={"HOLD","TAP","DOUBLE_TAP","LONG_PRESS","SWIPE","SECOND_FINGER","TOGGLE"},actions={"BUTTONS","NEXT","PREVIOUS","LAYOUT","PROFILE","MOVEMENT_HOLD","MODIFIER"};
        Spinner gesture=choice(b,"Gesture",new String[]{"Hold","Tap","Double tap and hold","Long press and hold","Swipe and hold","Second finger hold","Toggle"},gestures,c.gesture),action=choice(b,"Action",new String[]{"Mapped buttons / triggers","Next layout","Previous layout","Choose layout below","Choose game below","Movement hold","Temporary modifier"},actions,c.action);
        List<String> destinations=new ArrayList<>();destinations.add("");destinations.addAll(pad.profiles.ids());destinations.addAll(pad.profiles.games());String[] values=destinations.toArray(new String[0]),names=new String[values.length];names[0]="No destination";for(int i=1;i<names.length;i++)names[i]=pad.profiles.ids().contains(values[i])?"Layout · "+pad.profiles.name(values[i]):"Game · "+values[i];Spinner destination=choice(b,"Destination",names,values,c.target);
        dependentChoice(action,(View)destination.getParent().getParent(),n->n==3||n==4);form(c.title()+" · gesture",b,()->{c.gesture=gestures[gesture.getSelectedItemPosition()];c.action=actions[action.getSelectedItemPosition()];c.target=values[destination.getSelectedItemPosition()];if(c.action.equals("MODIFIER")&&c.gesture.equals("TAP"))throw new IllegalArgumentException("A temporary modifier needs a held gesture");if(c.gesture.equals("TOGGLE")&&!c.action.equals("BUTTONS"))throw new IllegalArgumentException("Use Tap for switching; Toggle is for mapped inputs");if(c.action.equals("LAYOUT")&&!pad.profiles.ids().contains(c.target)||c.action.equals("PROFILE")&&!pad.profiles.games().contains(c.target))throw new IllegalArgumentException("Choose a matching destination");commit(copy);});
    }
}
