package dev.phonepad;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.*;
import android.widget.*;
import org.json.JSONArray;
import java.util.*;

/** Device-local shortcuts, independent of layout geometry and controller mappings. */
public final class QuickLayoutPanel extends LinearLayout {
    private final PadView pad;
    private final SharedPreferences prefs;
    private String rendered="",layoutId="",prefix="";
    private float x,y;
    private String anchor;
    private boolean dragging,moved;
    private float downX,downY,startX,startY;
    private AlertDialog dialog;private boolean choosingLayouts;
    private float size(){float value=prefs.getFloat(preferenceKey("size"),1);return Float.isFinite(value)?Math.max(.75f,Math.min(1.5f,value)):1;}
    private int itemHeight(){return dp(48*size());}
    private boolean fullNames(){return prefs.getBoolean(preferenceKey("names"),false);}
    private int itemWidth(){return fullNames()?dp(96*size()):itemHeight();}
    private String shortcutLabel(String id,int index){return fullNames()?pad.profiles.name(id):Integer.toString(index+1);}
    String preferenceKey(String name){return prefix+name;}
    private boolean sameLayout(String owner){return owner.equals(pad.currentLayoutId());}
    private void loadScope(){
        String active=pad.currentLayoutId();if(active.equals(layoutId))return;
        dispose();layoutId=active;prefix=QuickPanelPreferences.prefix(prefs,active);rendered="";dragging=false;moved=false;
        x=clamp(prefs.getFloat(preferenceKey("x"),.5f));y=clamp(prefs.getFloat(preferenceKey("y"),1));anchor=prefs.getString(preferenceKey("anchor"),"Bottom");
    }
    public boolean enabled(){loadScope();return prefs.getBoolean(preferenceKey("enabled"),false);}
    public void setEnabled(boolean value){loadScope();prefs.edit().putBoolean(preferenceKey("enabled"),value).apply();refresh();}
    private boolean vertical(){String direction=prefs.getString(preferenceKey("direction"),"Auto");return direction.equals("Vertical")||direction.equals("Auto")&&(anchor.equals("Left")||anchor.equals("Right"));}
    public QuickLayoutPanel(PadView pad){
        super(pad.getContext());this.pad=pad;prefs=getContext().getSharedPreferences("pad",0);
        loadScope();
        setGravity(Gravity.CENTER);setPadding(dp(4),dp(4),dp(4),dp(4));setElevation(dp(6));
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xef1b2934);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),0xff427569);setBackground(bg);
        setClickable(true);setVisibility(GONE);setContentDescription("Quick switch layout panel");
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event){
        if(event.getActionMasked()==MotionEvent.ACTION_DOWN||event.getActionMasked()==MotionEvent.ACTION_POINTER_DOWN){pad.gameplayLock.cancel();pad.invalidate();}
        return super.onInterceptTouchEvent(event);
    }
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private static float clamp(float value){return Float.isFinite(value)?Math.max(0,Math.min(1,value)):.5f;}
    private boolean editable(){return !pad.gameplayLock.locked&&!pad.editing;}
    private List<String> selected(){
        List<String> result=new ArrayList<>();List<String> available=pad.profiles.ids();
        try{JSONArray array=new JSONArray(prefs.getString(preferenceKey("ids"),"[]"));for(int i=0;i<array.length();i++){String id=array.optString(i);if(available.contains(id)&&!result.contains(id))result.add(id);}}catch(Exception ignored){}
        return result;
    }
    public void refresh(){rendered="";sync();}
    public void sync(){
        loadScope();boolean show=prefs.getBoolean(preferenceKey("enabled"),false)&&!pad.settingsOpen&&!pad.editing&&!pad.testMode&&!pad.gameOverlayOpen;
        if(!show){setVisibility(GONE);dragging=false;return;}
        if(dialog!=null&&dialog.isShowing()&&choosingLayouts&&!editable())dialog.dismiss();
        String key=prefs.getString(preferenceKey("ids"),"[]")+pad.profiles.active+pad.config.name+editable()+anchor+size()+prefs.getString(preferenceKey("direction"),"Auto")+fullNames();
        if(!key.equals(rendered)){rendered=key;rebuild();}
        setVisibility(VISIBLE);if(!dragging)place();
    }
    private Button button(String label,String description,Runnable action){
        Button button=new Button(getContext());button.setText(label);button.setAllCaps(false);button.setGravity(Gravity.CENTER);button.setTextSize(12*size());button.setTextColor(SettingsScreen.TEXT);button.setContentDescription(description);button.setTooltipText(description);button.setMinWidth(itemHeight());button.setMinimumWidth(itemHeight());button.setMinHeight(itemHeight());button.setMinimumHeight(itemHeight());button.setPadding(dp(6),0,dp(6),0);
        GradientDrawable bg=new GradientDrawable();bg.setColor(new AppAppearance(getContext()).control);bg.setCornerRadius(dp(10));button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x558ed9c5),bg,null));button.setOnClickListener(v->action.run());return button;
    }
    private void rebuild(){
        AppAppearance appearance=new AppAppearance(getContext());GradientDrawable background=new GradientDrawable();background.setColor(appearance.surface);background.setCornerRadius(dp(16));background.setStroke(dp(1),appearance.accent);setBackground(background);
        removeAllViews();boolean vertical=vertical();setOrientation(vertical?VERTICAL:HORIZONTAL);
        if(!pad.editing){
            Button grip=button("⠿","Move or configure quick panel",this::configure);
            grip.setOnTouchListener((v,event)->drag(v,event));addView(grip,new LinearLayout.LayoutParams(itemHeight(),itemHeight()));
        }
        LinearLayout layouts=new LinearLayout(getContext());layouts.setOrientation(vertical?VERTICAL:HORIZONTAL);layouts.setGravity(Gravity.CENTER);
        List<String> shortcuts=selected();
        for(int index=0;index<shortcuts.size();index++){String id=shortcuts.get(index);
            Button item=button(shortcutLabel(id,index),"Switch to "+pad.profiles.name(id),()->{if(!pad.profiles.ids().contains(id)){refresh();return;}boolean playing=pad.gameplayLock.locked;pad.release();pad.selectProfile(id);if(!playing)pad.unlock();refresh();});
            item.setMaxWidth(itemWidth());item.setSingleLine(!fullNames());item.setEllipsize(null);
            if(id.equals(pad.profiles.active)){item.setTextColor(SettingsScreen.ACCENT);item.setTypeface(null,android.graphics.Typeface.BOLD);}
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(itemWidth(),fullNames()?-2:itemHeight());lp.setMargins(dp(2),dp(2),dp(2),dp(2));layouts.addView(item,lp);
        }
        if(editable())layouts.addView(button("+","Add layouts",this::chooseLayouts),new LinearLayout.LayoutParams(itemHeight(),itemHeight()));
        else if(layouts.getChildCount()==0)layouts.addView(button("▦","Choose layout",()->{pad.release();new ControllerSettings(getContext(),pad,()->{}).switcher();}),new LinearLayout.LayoutParams(itemHeight(),itemHeight()));
        if(vertical){ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(false);scroll.addView(layouts);addView(scroll,new LinearLayout.LayoutParams(-2,-2));}
        else{HorizontalScrollView scroll=new HorizontalScrollView(getContext());scroll.setHorizontalScrollBarEnabled(false);scroll.addView(layouts);addView(scroll,new LinearLayout.LayoutParams(-2,-2));}
        requestLayout();post(this::place);
    }
    @Override protected void onMeasure(int w,int h){
        RectF bounds=pad.controllerBounds();int maxW=Math.max(dp(48),(int)(bounds.width()*.85f)),maxH=Math.max(dp(48),(int)(bounds.height()*.7f));
        super.onMeasure(MeasureSpec.makeMeasureSpec(maxW,MeasureSpec.AT_MOST),MeasureSpec.makeMeasureSpec(maxH,MeasureSpec.AT_MOST));
    }
    private void place(){
        RectF b=pad.controllerBounds();float roomX=Math.max(0,b.width()-getMeasuredWidth()),roomY=Math.max(0,b.height()-getMeasuredHeight());
        float px=b.left+x*roomX,py=b.top+y*roomY;
        if(anchor.equals("Left"))px=b.left;if(anchor.equals("Right"))px=b.left+roomX;if(anchor.equals("Top"))py=b.top;if(anchor.equals("Bottom"))py=b.top+roomY;
        setX(px);setY(py);
    }
    private boolean drag(View grip,MotionEvent event){
        if(pad.editing)return true;
        switch(event.getActionMasked()){
            case MotionEvent.ACTION_DOWN:pad.release();dragging=true;moved=false;downX=event.getRawX();downY=event.getRawY();startX=getX();startY=getY();return true;
            case MotionEvent.ACTION_POINTER_DOWN:dragging=false;place();return true;
            case MotionEvent.ACTION_MOVE:
                if(!dragging)return true;
                float dx=event.getRawX()-downX,dy=event.getRawY()-downY;
                if(Math.hypot(dx,dy)>ViewConfiguration.get(getContext()).getScaledTouchSlop())moved=true;
                if(moved){RectF b=pad.controllerBounds();setX(Math.max(b.left,Math.min(b.right-getWidth(),startX+dx)));setY(Math.max(b.top,Math.min(b.bottom-getHeight(),startY+dy)));}return true;
            case MotionEvent.ACTION_UP:
                if(!dragging)return true;dragging=false;
                if(moved){RectF b=pad.controllerBounds();x=clamp((getX()-b.left)/Math.max(1,b.width()-getWidth()));y=clamp((getY()-b.top)/Math.max(1,b.height()-getHeight()));anchor="Free";savePosition();refresh();}else grip.performClick();return true;
            case MotionEvent.ACTION_CANCEL:dragging=false;place();return true;
            default:return true;
        }
    }
    private void savePosition(){prefs.edit().putFloat(preferenceKey("x"),x).putFloat(preferenceKey("y"),y).putString(preferenceKey("anchor"),anchor).apply();}
    public void choosePosition(){
        if(pad.editing)return;loadScope();final String owner=layoutId;pad.release();if(!sameLayout(owner)){refresh();return;}choosingLayouts=false;String[] positions={"Free","Top","Bottom","Left","Right"};
        show(new AlertDialog.Builder(getContext()).setTitle("Panel position").setSingleChoiceItems(positions,Arrays.asList(positions).indexOf(anchor),(d,n)->{if(pad.editing||!sameLayout(owner)){d.dismiss();return;}anchor=positions[n];savePosition();refresh();d.dismiss();}).setNegativeButton("Cancel",null).create());
    }
    public void chooseLayouts(){
        if(!editable())return;loadScope();final String owner=layoutId;pad.release();if(!sameLayout(owner)){refresh();return;}choosingLayouts=true;
        List<String> ids=pad.profiles.ids(),current=selected();boolean[] checked=new boolean[ids.size()];
        for(int i=0;i<ids.size();i++)checked[i]=current.contains(ids.get(i));
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(VERTICAL);body.setPadding(dp(16),dp(4),dp(16),dp(8));
        TextView intro=new TextView(getContext());intro.setText("Choose which layouts appear in your quick switch panel.");intro.setTextColor(SettingsScreen.MUTED);intro.setTextSize(14);intro.setPadding(0,0,0,dp(10));body.addView(intro);
        EditText search=new EditText(getContext());search.setSingleLine(true);search.setTextSize(15);search.setHint("Search layouts");search.setContentDescription("Search layouts by name or game");
        GradientDrawable searchBackground=new GradientDrawable();searchBackground.setColor(new AppAppearance(getContext()).surface);searchBackground.setCornerRadius(dp(12));search.setBackground(searchBackground);search.setPadding(dp(14),0,dp(14),0);body.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView count=new TextView(getContext());count.setTextColor(SettingsScreen.ACCENT);count.setTextSize(12);count.setPadding(dp(2),dp(8),dp(2),dp(6));body.addView(count);
        ScrollView scroll=new ScrollView(getContext());scroll.setFillViewport(false);LinearLayout rows=new LinearLayout(getContext());rows.setOrientation(VERTICAL);scroll.addView(rows);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=new TextView(getContext());empty.setText("No matching layouts");empty.setTextColor(SettingsScreen.MUTED);empty.setGravity(Gravity.CENTER);empty.setVisibility(GONE);body.addView(empty,new LinearLayout.LayoutParams(-1,dp(72)));
        Runnable render=()->{
            rows.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0,selectedCount=0;
            for(boolean value:checked)if(value)selectedCount++;
            count.setText(selectedCount==1?"1 layout selected":selectedCount+" layouts selected");
            for(int i=0;i<ids.size();i++){
                final int index=i;String id=ids.get(i);ControllerConfig config=pad.profiles.load(id);String name=config.name,game=config.game;
                if(!query.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(query)&&!game.toLowerCase(Locale.ROOT).contains(query))continue;
                shown++;LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(6),dp(12),dp(6));GradientDrawable rowBackground=new GradientDrawable();rowBackground.setColor(new AppAppearance(getContext()).surface);rowBackground.setCornerRadius(dp(12));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x228ed9c5),rowBackground,null));
                CheckBox choice=new CheckBox(getContext());choice.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{SettingsScreen.ACCENT,SettingsScreen.MUTED}));choice.setChecked(checked[i]);choice.setContentDescription("Include "+name);row.addView(choice,new LinearLayout.LayoutParams(dp(42),dp(48)));
                LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(getContext());title.setText(name);title.setTextColor(SettingsScreen.TEXT);title.setTextSize(15);title.setMaxLines(3);title.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);labels.addView(title,new LinearLayout.LayoutParams(-1,-2));TextView subtitle=new TextView(getContext());subtitle.setText(game+(pad.profiles.builtin(id)?" · Built-in":" · Custom"));subtitle.setTextColor(SettingsScreen.MUTED);subtitle.setTextSize(12);labels.addView(subtitle);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
                choice.setOnCheckedChangeListener((v,on)->{checked[index]=on;int total=0;for(boolean value:checked)if(value)total++;count.setText(total==1?"1 layout selected":total+" layouts selected");});row.setOnClickListener(v->choice.setChecked(!choice.isChecked()));LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.setMargins(0,dp(3),0,dp(3));rows.addView(row,rowParams);
            }
            empty.setVisibility(shown==0?VISIBLE:GONE);scroll.setVisibility(shown==0?GONE:VISIBLE);
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){render.run();}public void afterTextChanged(android.text.Editable e){}});render.run();
        show(new AlertDialog.Builder(getContext()).setTitle("Quick switch layouts").setView(body).setNegativeButton("Cancel",null).setPositiveButton("Save",(d,w)->{
            if(!editable()||!sameLayout(owner))return;List<String> chosen=new ArrayList<>();for(int i=0;i<ids.size();i++)if(checked[i]&&pad.profiles.ids().contains(ids.get(i)))chosen.add(ids.get(i));
            prefs.edit().putString(preferenceKey("ids"),new JSONArray(chosen).toString()).putBoolean(preferenceKey("enabled"),true).apply();refresh();
        }).create(),640);
    }
    public void configure(){
        if(!editable())return;loadScope();final String owner=layoutId;pad.release();if(!sameLayout(owner)){refresh();return;}choosingLayouts=true;
        List<String> ids=pad.profiles.ids(),current=selected();boolean[] checked=new boolean[ids.size()];for(int i=0;i<ids.size();i++)checked[i]=current.contains(ids.get(i));
        LinearLayout body=new LinearLayout(getContext());body.setOrientation(VERTICAL);body.setPadding(dp(16),dp(4),dp(16),dp(12));
        TextView scope=new TextView(getContext());scope.setText("Choose layouts for · "+pad.profiles.name(layoutId));scope.setTextColor(SettingsScreen.MUTED);scope.setTextSize(13);scope.setPadding(0,0,0,dp(10));body.addView(scope);
        EditText search=new EditText(getContext());search.setSingleLine(true);search.setTextSize(15);search.setHint("Search layouts");search.setContentDescription("Search layouts by name or game");GradientDrawable searchBg=new GradientDrawable();searchBg.setColor(new AppAppearance(getContext()).surface);searchBg.setCornerRadius(dp(12));search.setBackground(searchBg);search.setPadding(dp(14),0,dp(14),0);body.addView(search,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView count=new TextView(getContext());count.setTextColor(SettingsScreen.ACCENT);count.setTextSize(12);count.setPadding(dp(2),dp(8),dp(2),dp(6));body.addView(count);
        ScrollView list=new ScrollView(getContext());list.setVerticalScrollBarEnabled(false);LinearLayout rows=new LinearLayout(getContext());rows.setOrientation(VERTICAL);list.addView(rows);body.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        TextView empty=new TextView(getContext());empty.setText("No matching layouts");empty.setTextColor(SettingsScreen.MUTED);empty.setGravity(Gravity.CENTER);body.addView(empty,new LinearLayout.LayoutParams(-1,dp(56)));
        Switch names=new Switch(getContext());names.setText("Show full names on panel");names.setTextColor(SettingsScreen.TEXT);names.setChecked(fullNames());body.addView(names,new LinearLayout.LayoutParams(-1,dp(48)));
        TextView sizeLabel=new TextView(getContext());sizeLabel.setTextColor(SettingsScreen.TEXT);sizeLabel.setTextSize(13);body.addView(sizeLabel);SeekBar scale=new SeekBar(getContext());scale.setContentDescription("Quick panel size");scale.setMax(75);scale.setProgress(Math.round((size()-.75f)*100));body.addView(scale,new LinearLayout.LayoutParams(-1,dp(40)));
        LinearLayout directions=new LinearLayout(getContext());body.addView(directions);String[] values={"Auto","Horizontal","Vertical"};
        Runnable render=()->{
            rows.removeAllViews();String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);int shown=0,total=0;for(boolean value:checked)if(value)total++;count.setText(total==1?"1 layout selected":total+" layouts selected");
            for(int i=0;i<ids.size();i++){final int index=i;String id=ids.get(i);ControllerConfig config=pad.profiles.load(id);String name=config.name,game=config.game;if(!query.isEmpty()&&!name.toLowerCase(Locale.ROOT).contains(query)&&!game.toLowerCase(Locale.ROOT).contains(query))continue;shown++;
                LinearLayout row=new LinearLayout(getContext());row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(12),dp(6),dp(12),dp(6));GradientDrawable rowBg=new GradientDrawable();rowBg.setColor(new AppAppearance(getContext()).surface);rowBg.setCornerRadius(dp(12));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x228ed9c5),rowBg,null));
                CheckBox check=new CheckBox(getContext());check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{SettingsScreen.ACCENT,SettingsScreen.MUTED}));check.setChecked(checked[i]);check.setContentDescription("Include "+name);row.addView(check,new LinearLayout.LayoutParams(dp(42),dp(48)));
                LinearLayout labels=new LinearLayout(getContext());labels.setOrientation(VERTICAL);labels.setGravity(Gravity.CENTER_VERTICAL);TextView title=new TextView(getContext());title.setText(name);title.setTextColor(SettingsScreen.TEXT);title.setTextSize(15);title.setMaxLines(2);labels.addView(title);TextView subtitle=new TextView(getContext());subtitle.setText(game+(pad.profiles.builtin(id)?" · Built-in":" · Custom"));subtitle.setTextColor(SettingsScreen.MUTED);subtitle.setTextSize(12);labels.addView(subtitle);row.addView(labels,new LinearLayout.LayoutParams(0,-2,1));check.setOnCheckedChangeListener((v,on)->{checked[index]=on;int selectedCount=0;for(boolean selected:checked)if(selected)selectedCount++;count.setText(selectedCount==1?"1 layout selected":selectedCount+" layouts selected");});row.setOnClickListener(v->check.setChecked(!check.isChecked()));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(3),0,dp(3));rows.addView(row,lp);
            }
            list.setVisibility(shown==0?GONE:VISIBLE);empty.setVisibility(shown==0?VISIBLE:GONE);sizeLabel.setText("Panel size · "+Math.round(size()*100)+"%");for(int i=0;i<directions.getChildCount();i++)((Button)directions.getChildAt(i)).setTextColor(values[i].equals(prefs.getString(preferenceKey("direction"),"Auto"))?SettingsScreen.ACCENT:SettingsScreen.TEXT);
        };
        search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int length,int after){}public void onTextChanged(CharSequence s,int start,int before,int length){render.run();}public void afterTextChanged(android.text.Editable e){}});
        names.setOnCheckedChangeListener((v,on)->{if(sameLayout(owner))prefs.edit().putBoolean(preferenceKey("names"),on).apply();});
        for(String value:values){Button choice=button(value,"Panel direction: "+value,()->{if(!sameLayout(owner))return;prefs.edit().putString(preferenceKey("direction"),value).apply();render.run();});directions.addView(choice,new LinearLayout.LayoutParams(0,dp(44),1));}
        scale.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int value,boolean user){if(user&&sameLayout(owner)){prefs.edit().putFloat(preferenceKey("size"),.75f+value/100f).apply();sizeLabel.setText("Panel size · "+Math.round(size()*100)+"%");}}});
        body.addView(button("Attach to screen edge", "Panel position",this::choosePosition));render.run();
        ScrollView scroll=new ScrollView(getContext());scroll.addView(body);show(new AlertDialog.Builder(getContext()).setTitle("Quick switch panel").setView(scroll).setNegativeButton("Cancel",null).setPositiveButton("Done",(d,w)->{if(!editable()||!sameLayout(owner))return;List<String> chosen=new ArrayList<>();for(int i=0;i<ids.size();i++)if(checked[i]&&pad.profiles.ids().contains(ids.get(i)))chosen.add(ids.get(i));prefs.edit().putString(preferenceKey("ids"),new JSONArray(chosen).toString()).putBoolean(preferenceKey("enabled"),true).apply();refresh();}).create(),640);
    }
    private void show(AlertDialog next){show(next,320);}
    private void show(AlertDialog next,int maxHeightDp){if(dialog!=null)dialog.dismiss();dialog=next;dialog.show();GradientDrawable surface=new GradientDrawable();surface.setColor(new AppAppearance(getContext()).surface);surface.setCornerRadius(dp(20));surface.setStroke(dp(1),new AppAppearance(getContext()).accent);dialog.getWindow().setBackgroundDrawable(surface);dialog.getWindow().setLayout(Math.min(dp(480),getResources().getDisplayMetrics().widthPixels-dp(32)),Math.min(dp(maxHeightDp),getResources().getDisplayMetrics().heightPixels-dp(32)));if(dialog.getButton(AlertDialog.BUTTON_POSITIVE)!=null)dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(SettingsScreen.ACCENT);if(dialog.getButton(AlertDialog.BUTTON_NEGATIVE)!=null)dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(SettingsScreen.MUTED);}
    public void dispose(){if(dialog!=null)dialog.dismiss();dialog=null;}
}
