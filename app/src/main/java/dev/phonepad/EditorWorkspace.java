package dev.phonepad;

import android.app.AlertDialog;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Editor chrome around the existing canvas. Geometry, history and persistence stay in PadView. */
final class EditorWorkspace {
    private final PadView pad;
    private final FrameLayout host,overlay;
    private final LinearLayout toolbar;
    private final ScrollView properties;
    private final LinearLayout contextual;
    private final ScrollView controls;
    private boolean controlsOpen;
    private int safeTop;
    private final TextView name;
    private final Button undo,redo;
    private String sizeControlId;
    private float sizeBaseW,sizeBaseH;
    private int sizeProgress=50;
    EditorWorkspace(PadView pad,FrameLayout host){
        this.pad=pad;this.host=host;overlay=new FrameLayout(pad.getContext());
        toolbar=new LinearLayout(pad.getContext());toolbar.setGravity(Gravity.CENTER_VERTICAL);toolbar.setBackgroundColor(EditorStyle.BG);
        toolbar.addView(button("×",this::finish));
        toolbar.addView(button("☰",this::layers));toolbar.addView(button("Select",pad::toggleMultiSelect));
        name=new TextView(pad.getContext());name.setTextColor(EditorStyle.TEXT);name.setTextSize(14);name.setSingleLine();name.setEllipsize(android.text.TextUtils.TruncateAt.END);toolbar.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        Button edit=button("Edit",()->{});edit.setEnabled(false);edit.setTextColor(EditorStyle.TEAL);toolbar.addView(edit);
        toolbar.addView(button("Test",this::preview));
        toolbar.addView(button("Mapper",()->AutomationEditor.show(pad)));
        toolbar.addView(button("Macros",()->AutomationEditor.showMacros(pad)));
        undo=button("↶",()->{pad.history(false);selected(null);});undo.setContentDescription("Undo");
        redo=button("↷",()->{pad.history(true);selected(null);});redo.setContentDescription("Redo");toolbar.addView(undo);toolbar.addView(redo);
        toolbar.addView(button("＋",this::add));
        toolbar.addView(button("⋮",this::options));
        Button save=button("Save",this::finish);save.setTextColor(EditorStyle.BG);save.setBackground(EditorStyle.background(pad.getContext(),EditorStyle.TEAL));toolbar.addView(save);

        FrameLayout.LayoutParams bar=new FrameLayout.LayoutParams(-1,dp(48),Gravity.TOP);HorizontalScrollView toolbarScroll=new HorizontalScrollView(pad.getContext());toolbarScroll.setHorizontalScrollBarEnabled(false);toolbarScroll.setFillViewport(true);toolbarScroll.addView(toolbar);overlay.addView(toolbarScroll,bar);
        properties=new ScrollView(pad.getContext());properties.setBackground(EditorStyle.background(pad.getContext(),EditorStyle.PANEL));FrameLayout.LayoutParams panel=new FrameLayout.LayoutParams(dp(230),-1,Gravity.END);panel.topMargin=dp(48);overlay.addView(properties,panel);properties.setVisibility(View.GONE);
        controls=new ScrollView(pad.getContext());controls.setBackground(EditorStyle.background(pad.getContext(),EditorStyle.PANEL));FrameLayout.LayoutParams side=new FrameLayout.LayoutParams(dp(158),-1,Gravity.START);side.topMargin=dp(48);overlay.addView(controls,side);controls.setVisibility(View.GONE);
        contextual=new LinearLayout(pad.getContext());contextual.setBackground(EditorStyle.background(pad.getContext(),EditorStyle.PANEL));overlay.addView(contextual,new FrameLayout.LayoutParams(-2,dp(44)));contextual.setVisibility(View.GONE);
        host.addView(overlay,new FrameLayout.LayoutParams(-1,-1));refresh();
    }
    private int dp(float n){return Math.round(n*pad.getResources().getDisplayMetrics().density);}
    private Button button(String text,Runnable action){Button b=EditorStyle.button(pad.getContext(),text,false,action);b.setBackgroundColor(Color.TRANSPARENT);b.setLayoutParams(new LinearLayout.LayoutParams(-2,dp(42)));return b;}
    void insets(int left,int top,int right,int bottom){safeTop=top;toolbar.setPadding(left,top,right,0);((View)toolbar.getParent()).getLayoutParams().height=dp(48)+top;toolbar.getLayoutParams().height=dp(48)+top;FrameLayout.LayoutParams panel=(FrameLayout.LayoutParams)properties.getLayoutParams();panel.topMargin=dp(48)+top;panel.leftMargin=left;panel.rightMargin=right;panel.bottomMargin=bottom;properties.setLayoutParams(panel);FrameLayout.LayoutParams side=(FrameLayout.LayoutParams)controls.getLayoutParams();side.topMargin=dp(48)+top;side.leftMargin=left;side.bottomMargin=bottom;controls.setLayoutParams(side);}
    void refresh(){name.setText(pad.config.name);undo.setEnabled(pad.canUndo());redo.setEnabled(pad.canRedo());if(controlsOpen)renderControls();}
    void close(){host.removeView(overlay);}
    private void finish(){pad.finishEditing();}
    void selected(ControllerConfig.Control c){
        refresh();contextual.removeAllViews();contextual.setVisibility(View.GONE);properties.removeAllViews();properties.setVisibility(c==null&&pad.editorSelectionIds.isEmpty()?View.GONE:View.VISIBLE);
        if(pad.editorSelectionIds.size()>1){LinearLayout multi=EditorStyle.column(pad.getContext());properties.addView(multi);multi.addView(button(pad.editorSelectionIds.size()+" selected · "+(pad.multiSelect?"Done":"Select more"),pad::toggleMultiSelect));String[] labels={"Align left","Align right","Align top","Align bottom","Center horizontally","Center vertically","Distribute horizontally","Distribute vertically","Match width","Match height"},ops={"left","right","top","bottom","centerX","centerY","distributeX","distributeY","width","height"};for(int i=0;i<ops.length;i++){final String op=ops[i];multi.addView(button(labels[i],()->pad.alignSelection(op)));}return;}if(c==null)return;
        if(!c.id.equals(sizeControlId)){sizeControlId=c.id;sizeBaseW=c.w;sizeBaseH=c.h;sizeProgress=50;}
        FrameLayout.LayoutParams placement=(FrameLayout.LayoutParams)properties.getLayoutParams();placement.gravity=!controlsOpen&&c.x>.5f?Gravity.START:Gravity.END;properties.setLayoutParams(placement);
        final String id=c.id;
        contextual.addView(button("＋",()->MacroLibrary.create(pad,id,()->selected(pad.editorSelection()))));contextual.addView(button("Edit",()->sheet(id,2)));contextual.addView(button("Connect",()->AutomationEditor.show(pad,id)));contextual.setVisibility(View.VISIBLE);
        float[] anchor=pad.editorAnchor(c);FrameLayout.LayoutParams quick=(FrameLayout.LayoutParams)contextual.getLayoutParams();quick.leftMargin=Math.round(Math.max(0,Math.min(host.getWidth()-dp(180),anchor[0]-dp(90))));quick.topMargin=Math.round(Math.max(dp(48)+safeTop,anchor[1]-dp(48)));contextual.setLayoutParams(quick);
        LinearLayout body=new LinearLayout(pad.getContext());body.setOrientation(LinearLayout.VERTICAL);body.setPadding(dp(6),0,dp(6),dp(6));properties.addView(body);
        body.addView(button(c.title()+"   ×",()->{pad.selectEditorControl(null);selected(null);}));
        LinearLayout actions=new LinearLayout(pad.getContext());actions.addView(button("Duplicate",()->{pad.addControl(pad.config.get(id));selected(pad.editorSelection());}));actions.addView(button("Delete",()->{pad.deleteControl(id);selected(null);}));body.addView(actions);
        Switch visible=new Switch(pad.getContext());visible.setText("Visible");visible.setTextColor(EditorStyle.TEXT);visible.setMinHeight(dp(48));visible.setChecked(c.visible);body.addView(visible);visible.setOnCheckedChangeListener((v,on)->pad.mutate(()->pad.config.get(id).visible=on));
        if(c.kind.equals("stick")&&c.bit==1){Switch fullscreen=new Switch(pad.getContext());fullscreen.setText("Fullscreen camera background");fullscreen.setTextColor(EditorStyle.TEXT);fullscreen.setMinHeight(dp(48));fullscreen.setChecked(c.fullscreen);body.addView(fullscreen);TextView cameraHint=new TextView(pad.getContext());cameraHint.setText("Swipe empty screen space to aim. Other controls stay above it.");cameraHint.setTextColor(EditorStyle.MUTED);cameraHint.setTextSize(12);body.addView(cameraHint);fullscreen.setOnCheckedChangeListener((v,on)->pad.mutate(()->{ControllerConfig.Control camera=pad.config.get(id);camera.fullscreen=on;if(on)camera.mode="TOUCHPAD";}));}

        if(c.kind.equals("stick")&&c.bit==0){Switch racing=new Switch(pad.getContext());racing.setText("Steering + gas / brake");racing.setTextColor(EditorStyle.TEXT);racing.setChecked(pad.config.integrated);body.addView(racing);racing.setOnCheckedChangeListener((v,on)->pad.mutate(()->pad.config.integrated=on));}
        if(c.kind.equals("button")||c.kind.equals("combo")){
            body.addView(EditorStyle.text(pad.getContext(),"BEHAVIOR",11,EditorStyle.MUTED));GridLayout gestures=new GridLayout(pad.getContext());gestures.setColumnCount(3);
            for(String[] gesture:new String[][]{{"HOLD","◎ Hold"},{"TAP","• Tap"},{"DOUBLE_TAP","•• Double"},{"LONG_PRESS","◷ Long"},{"SWIPE","→ Swipe"},{"SECOND_FINGER","2 Touch"},{"TOGGLE","⏻ Toggle"}}){Button choice=EditorStyle.button(pad.getContext(),gesture[1],c.gesture.equals(gesture[0]),()->pad.mutate(()->{ControllerConfig.Control live=pad.config.get(id);live.gesture=gesture[0];if(gesture[0].equals("TOGGLE"))live.action="BUTTONS";}));GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=0;p.height=dp(34);p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);p.setMargins(dp(1),dp(1),dp(1),dp(1));gestures.addView(choice,p);}body.addView(gestures);body.addView(button("Gesture action…",()->new ControllerSettings(pad.getContext(),pad,()->{}).interaction(id)));
        }
        body.addView(button("Bindings",()->AutomationEditor.show(pad,id)));
        MacroLibrary.attached(pad,body,id,()->selected(pad.editorSelection()));
        slider(body,"Size",0,100,sizeProgress,n->{sizeProgress=n;float factor=.5f+n/100f;pad.editorPreview(()->{ControllerConfig.Control current=pad.config.get(id);if(current!=null){current.w=PadState.clamp(sizeBaseW*factor,.025f,.5f);current.h=PadState.clamp(sizeBaseH*factor,.04f,.7f);}});},n->pad.saveLayout());
        slider(body,"Opacity",10,100,Math.round(c.opacity*100),n->pad.editorPreview(()->{ControllerConfig.Control current=pad.config.get(id);if(current!=null)current.opacity=n/100f;}),n->pad.saveLayout());
        body.addView(button("Position & size",()->sheet(id,0)));body.addView(button("Interaction & touch area",()->sheet(id,1)));body.addView(button("Appearance",()->sheet(id,2)));
        if(c.kind.equals("stick")||c.kind.equals("trigger")||c.kind.equals("unified")||c.kind.equals("aim")||c.kind.equals("hold")||c.kind.equals("layoutHold"))body.addView(button("Behavior",()->sheet(id,3)));
    }
    private void slider(LinearLayout body,String label,int min,int max,int initial,java.util.function.IntConsumer preview,java.util.function.IntConsumer save){TextView title=new TextView(pad.getContext());title.setText(label+" · "+(label.equals("Size")?(50+initial)+"%":initial+"%"));title.setTextColor(EditorStyle.MUTED);body.addView(title);SeekBar slider=new SeekBar(pad.getContext());slider.setMax(max-min);slider.setProgress(initial-min);slider.setContentDescription(label);body.addView(slider,new LinearLayout.LayoutParams(-1,dp(40)));slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onStartTrackingTouch(SeekBar s){}public void onProgressChanged(SeekBar s,int n,boolean user){if(user){int value=min+n;title.setText(label+" · "+(label.equals("Size")?(50+value)+"%":value+"%"));preview.accept(value);}}public void onStopTrackingTouch(SeekBar s){save.accept(min+s.getProgress());}});}
    private void sheet(String id,int section){new ControllerSettings(pad.getContext(),pad,()->{}).properties(id,section);}
    private void layers(){controlsOpen=!controlsOpen;controls.setVisibility(controlsOpen?View.VISIBLE:View.GONE);renderControls();selected(pad.editorSelection());}
    private void renderControls(){controls.removeAllViews();LinearLayout list=EditorStyle.column(pad.getContext());controls.addView(list);list.addView(EditorStyle.text(pad.getContext(),"CONTROLS",11,EditorStyle.MUTED));for(ControllerConfig.Control c:pad.config.controls){LinearLayout row=new LinearLayout(pad.getContext());Button show=button(c.visible?"◉":"○",()->pad.mutate(()->pad.config.get(c.id).visible=!c.visible));show.setContentDescription((c.visible?"Hide ":"Show ")+c.title());row.addView(show,new LinearLayout.LayoutParams(dp(34),dp(36)));Button choose=button(c.title(),()->{pad.selectEditorControl(pad.config.get(c.id));selected(pad.editorSelection());});choose.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);choose.setSingleLine();row.addView(choose,new LinearLayout.LayoutParams(0,dp(36),1));list.addView(row);}}
    private void options(){String[] labels={"Alignment grid","Snap to grid"};boolean[] values={pad.config.grid,pad.config.snap};new AlertDialog.Builder(pad.getContext()).setMultiChoiceItems(labels,values,(d,n,on)->pad.mutate(()->{if(n==0)pad.config.grid=on;else pad.config.snap=on;})).setPositiveButton("Done",null).show();}
    private void add(){ControllerConfig templates=ControllerConfig.preset("Default");String[] names=new String[templates.controls.size()+3];for(int i=0;i<templates.controls.size();i++)names[i]=templates.controls.get(i).kind.equals("unified")?"Unified LT / RT":templates.controls.get(i).title();names[templates.controls.size()]="Steering + gas / brake (left stick)";names[names.length-2]="Custom button";names[names.length-1]="Mouse trackpad";
        android.app.Dialog palette=new android.app.Dialog(pad.getContext(),R.style.AppTheme);LinearLayout content=EditorStyle.column(pad.getContext());content.setPadding(dp(8),dp(8),dp(8),dp(8));content.setBackgroundColor(EditorStyle.BG);content.addView(button("Add control   ×",palette::dismiss));ScrollView scroll=new ScrollView(pad.getContext());GridLayout grid=new GridLayout(pad.getContext());grid.setColumnCount(3);scroll.addView(grid);content.addView(scroll);
        for(int i=0;i<names.length;i++){final int n=i;Button item=EditorStyle.button(pad.getContext(),names[i],false,()->{palette.dismiss();if(n==templates.controls.size())pad.addRacingControl();else if(n>templates.controls.size()){ControllerConfig.Control c=new ControllerConfig.Control("custom",n==names.length-1?"trackpad":"button",0,.5f,.5f,n==names.length-1?.35f:.09f,n==names.length-1?.5f:.16f);c.label=n==names.length-1?"Trackpad":"Custom";c.buttons=0;pad.addControl(c);}else pad.addControl(templates.controls.get(n));selected(pad.editorSelection());});GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=dp(150);lp.height=dp(42);lp.setMargins(dp(3),dp(3),dp(3),dp(3));grid.addView(item,lp);}
        palette.setContentView(content);palette.show();palette.getWindow().setLayout(dp(488),Math.min(dp(320),pad.getHeight()));
    }

    private void preview(){
        // Isolated controller state: never changes the live PadView or sends HID reports.
        android.app.Dialog dialog=new android.app.Dialog(pad.getContext(),new AppAppearance(pad.getContext()).light?R.style.AppThemeLight:R.style.AppTheme);LinearLayout content=new LinearLayout(pad.getContext());content.setOrientation(LinearLayout.VERTICAL);content.setBackgroundColor(SettingsScreen.BACKGROUND);content.addView(button("‹ Back to editor · test touches stay on this phone",dialog::dismiss));
        LayoutPreview preview=new LayoutPreview(pad.getContext(),pad.config,true);content.addView(preview,new LinearLayout.LayoutParams(-1,0,1));dialog.setContentView(content);dialog.show();dialog.getWindow().setLayout(-1,-1);
    }
}
