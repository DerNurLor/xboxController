package dev.phonepad;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;

/** Native rendering and MotionEvent adapter. All gameplay interpretation lives in ControllerInput. */
public final class PadView extends View {
    public interface Actions {void connect();void settings();void changed();default void released(){changed();}default void transitioned(){changed();}default void emergency(){released();}default void feedback(int strength){}default void pause(){}}
    public final ProfileStore profiles;
    public ControllerConfig config;
    public final ControllerInput input;
    public final PadState state;
    public GyroInput gyro;
    public boolean connected,editing,testMode,settingsOpen,gameOverlayOpen;
    private long hintUntil;
    public final GameplayLock gameplayLock=new GameplayLock();
    public void returnToPlay(){settingsOpen=false;release();if(gameplayLock.locked||config.gameplayLock)startGameMode();invalidate();}
    public void unlock(){release();gameplayLock.reset(false);if(quickPanel!=null)quickPanel.refresh();invalidate();}
    public void startGameMode(){if(!connected){actions.connect();return;}release();editing=false;testMode=false;gameplayLock.reset(true);
        if(!getContext().getSharedPreferences("pad",0).getBoolean("gameHandleLearned.v1",false)){getContext().getSharedPreferences("pad",0).edit().putBoolean("gameHandleLearned.v1",true).apply();hintUntil=SystemClock.uptimeMillis()+6500;postDelayed(this::invalidate,6500);}
        if(quickPanel!=null)quickPanel.refresh();animate().alpha(1).setDuration(160).start();invalidate();}
    public void gameHandleLearned(){hintUntil=0;getContext().getSharedPreferences("pad",0).edit().putBoolean("gameHandleLearned.v1",true).apply();invalidate();}
    private float handleHeight(){return 48*getResources().getDisplayMetrics().density;}
    private boolean handleTouch(float x,float y){return Math.abs(x-surfaceW/2)<=84*getResources().getDisplayMetrics().density&&y>=0&&y<handleHeight();}
    public String status="Not connected",transport="BLUETOOTH";
    private final Actions actions;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float left=16,top=16,surfaceW=1100,surfaceH=480;
    private int insetL,insetT,insetR,insetB;
    private ControllerConfig.Control selected;
    private EditorWorkspace editor;
    final LinkedHashSet<String> editorSelectionIds=new LinkedHashSet<>();
    boolean multiSelect=false;private float guideX=Float.NaN,guideY=Float.NaN;
    private final Map<String,float[]> groupStart=new LinkedHashMap<>();
    float[] editorAnchor(ControllerConfig.Control c){return new float[]{left+c.x*surfaceW,top+(c.y-c.h/2)*surfaceH};}
    List<ControllerConfig.Control> selectedControls(){List<ControllerConfig.Control> out=new ArrayList<>();for(String id:editorSelectionIds){ControllerConfig.Control c=config.get(id);if(c!=null)out.add(c);}return out;}
    void alignSelection(String operation){mutate(()->EditorGeometry.transform(selectedControls(),operation));editorRefresh();}
    void toggleMultiSelect(){multiSelect=!multiSelect;editorRefresh();}

    public boolean canUndo(){return !undo.isEmpty();}
    public boolean canRedo(){return !redo.isEmpty();}
    public ControllerConfig.Control editorSelection(){return selected;}
    public void selectEditorControl(ControllerConfig.Control c){selected=c;if(!multiSelect)editorSelectionIds.clear();if(c!=null){if(multiSelect&&!editorSelectionIds.add(c.id))editorSelectionIds.remove(c.id);else editorSelectionIds.add(c.id);}invalidate();}
    public void editorRefresh(){if(editor!=null)editor.selected(selected);invalidate();}
    void editorPreview(Runnable change){change.run();invalidate();}
    private String temporaryReturn,temporaryLayoutId;
    public String currentLayoutId(){return temporaryReturn!=null?temporaryLayoutId:profiles.active;}
    private ControllerInput temporarySource;
    private int temporaryPointer=-1,edgePointer=-1;
    private float edgeX,edgeY;
    private final ArrayDeque<ControllerConfig> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private ControllerConfig savedConfig;
    private boolean displayedCalibration;
    private int lastCue;
    private void transitionFeedback(){
        int cue=state.buttons&((1<<PadState.L3)|(1<<PadState.R3));int added=cue&~lastCue;
        if(added!=0)for(ControllerConfig.Control c:config.controls){if(!c.haptic||!input.active(c))continue;int mapped=c.kind.equals("stick")?1<<(c.bit==0?PadState.L3:PadState.R3):c.buttons>=0?c.buttons:1<<c.bit;if((mapped&added)!=0){feedback();break;}}
        lastCue=cue;
    }

    private final Map<Integer,EditTouch> edits=new LinkedHashMap<>();
    private int ACCENT,TEXT,MUTED;private AppAppearance appearance;
    private static final class EditTouch {ControllerConfig.Control c;float sx,sy,x,y,cx,cy,w,h;boolean moved,resizing;EditTouch(ControllerConfig.Control c,float x,float y){this.c=c;sx=this.x=x;sy=this.y=y;cx=c.x;cy=c.y;w=c.w;h=c.h;}}
    public PadView(Context context,Actions actions){
        super(context);this.actions=actions;loadSettings();profiles=new ProfileStore(context.getSharedPreferences("pad",0));
        try{config=profiles.load(profiles.active);}catch(IllegalArgumentException e){config=profiles.select("Default");}
        input=new ControllerInput(config);state=input.state;
        android.content.SharedPreferences prefs=context.getSharedPreferences("pad",0);
        if(!prefs.getBoolean("fullHeight.v1",false)){config.fitHeight();profiles.create(config.name.length()>44?"Full height layout":config.name+" · full height",config);config=profiles.load(profiles.active);input.configure(config);prefs.edit().putBoolean("fullHeight.v1",true).apply();}
        savedConfig=config.copy();gameplayLock.reset(config.gameplayLock);
        setFocusable(true);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);setContentDescription("PhonePad touch gamepad. Landscape multitouch controls.");
        setOnApplyWindowInsetsListener((v,insets)->{int l=0,t=0,r=0,b=0;if(Build.VERSION.SDK_INT>=30){int types=WindowInsets.Type.displayCutout();if(insets.isVisible(WindowInsets.Type.navigationBars()))types|=WindowInsets.Type.navigationBars();Insets i=insets.getInsets(types);l=i.left;t=i.top;r=i.right;b=i.bottom;}else if(insets.getDisplayCutout()!=null){l=insets.getDisplayCutout().getSafeInsetLeft();r=insets.getDisplayCutout().getSafeInsetRight();t=insets.getDisplayCutout().getSafeInsetTop();b=insets.getDisplayCutout().getSafeInsetBottom();}if(l!=insetL||r!=insetR||t!=insetT||b!=insetB){insetL=l;insetR=r;insetT=t;insetB=b;geometry();}return insets;});
    }
    private QuickLayoutPanel quickPanel;
    public QuickLayoutPanel quickLayouts(){
        if(quickPanel==null){quickPanel=new QuickLayoutPanel(this);if(getParent() instanceof android.widget.FrameLayout)((android.widget.FrameLayout)getParent()).addView(quickPanel,new android.widget.FrameLayout.LayoutParams(-2,-2));}
        return quickPanel;
    }
    public void refreshQuickPanel(){quickLayouts().refresh();}
    public RectF controllerBounds(){return new RectF(left,top,left+surfaceW,top+surfaceH);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(()->{if(isAttachedToWindow())refreshQuickPanel();});}
    @Override protected void onDetachedFromWindow(){if(quickPanel!=null){quickPanel.dispose();QuickLayoutPanel removed=quickPanel;if(removed.getParent() instanceof android.view.ViewGroup){android.view.ViewGroup parent=(android.view.ViewGroup)removed.getParent();parent.post(()->{if(removed.getParent()==parent)parent.removeView(removed);});}quickPanel=null;}super.onDetachedFromWindow();}
    public void loadSettings(){appearance=new AppAppearance(getContext());ACCENT=appearance.accent;TEXT=appearance.text;MUTED=appearance.muted;if(quickPanel!=null)quickPanel.refresh();invalidate();}
    public void fitLayoutHeight(){release();ControllerConfig fitted=config.copy();fitted.fitHeight();profiles.create(config.name.length()>44?"Full height layout":config.name+" · full height",fitted);selectProfile(profiles.active);}

    public void selectProfile(String id){
        ControllerConfig next=profiles.select(id);
        if(editing||temporaryReturn!=null)release();
        transitionTo(next);selected=null;editorSelectionIds.clear();undo.clear();redo.clear();savedConfig=config.copy();invalidate();}
    private void transitionTo(ControllerConfig next){
        edits.clear();edgePointer=-1;input.transition(next,SystemClock.uptimeMillis());config=next;
        gameplayLock.reset(gameplayLock.locked||config.gameplayLock);actions.transitioned();if(!settingsOpen)feedback();invalidate();
    }
    public void saveLayout(){
        String selectedId=selected==null?null:selected.id;
        ControllerConfig next=ProfileStore.decode(ProfileStore.encode(config));
        boolean changed=savedConfig!=null&&!ProfileStore.encode(savedConfig).toString().equals(ProfileStore.encode(next).toString());
        try{profiles.save(next);}catch(RuntimeException failure){
            config=savedConfig.copy();input.configure(config);selected=null;invalidate();throw failure;
        }
        if(changed){undo.addLast(savedConfig.copy());if(undo.size()>40)undo.removeFirst();redo.clear();}
        config=next;input.configure(config);selected=selectedId==null?null:config.get(selectedId);savedConfig=config.copy();editorRefresh();invalidate();
    }
    public void addRacingControl(){mutate(()->{config.integrated=true;ControllerConfig.Control left=config.standard("LS");left.visible=true;selected=left;});editorRefresh();}
    public void addControl(ControllerConfig.Control template){release();selected=config.addInstance(template);saveLayout();}
    public void deleteControl(String id){mutate(()->config.deleteControl(id));editorSelectionIds.remove(id);selected=null;}
    public void mutate(Runnable change){release();change.run();saveLayout();}
    public void resetProfile(){release();config=profiles.reset();input.configure(config);selected=null;editorSelectionIds.clear();undo.clear();redo.clear();savedConfig=config.copy();invalidate();}
    private android.app.AlertDialog layoutEditDialog;
    private Runnable layoutEditAction;
    public void requestEditing(){requestLayoutEdit(this::startEditing,()->{});}
    public void requestLayoutEdit(Runnable after,Runnable cancel){
        release();if(!profiles.builtin(profiles.active)){after.run();return;}
        if(layoutEditDialog!=null&&layoutEditDialog.isShowing())return;
        final String source=profiles.active;layoutEditAction=after;
        layoutEditDialog=new android.app.AlertDialog.Builder(getContext()).setTitle("Edit built-in layout?")
            .setMessage("Edit "+config.name+" in place, or create a separate copy. A new copy will replace it in your quick-switch panels. Factory defaults remain available through Reset layout.")
            .setNegativeButton("Cancel",(d,w)->{layoutEditAction=null;cancel.run();})
            .setNeutralButton("Edit this layout",(d,w)->resolveLayoutEdit(source,false))
            .setPositiveButton("Create a copy",(d,w)->resolveLayoutEdit(source,true)).create();
        layoutEditDialog.setOnCancelListener(d->{layoutEditAction=null;cancel.run();});layoutEditDialog.show();
    }
    void resolveLayoutEdit(String source,boolean copy){
        if(layoutEditDialog!=null)layoutEditDialog.dismiss();
        if(!source.equals(profiles.active))return;
        if(copy)profiles.save(config.copy());else profiles.editBuiltin(config);
        selectProfile(profiles.active);Runnable after=layoutEditAction;layoutEditAction=null;if(after!=null)after.run();
    }
    public void startEditing(){release();gameplayLock.reset(false);testMode=false;editing=true;if(profiles.builtin(profiles.active)){profiles.save(config.copy());selectProfile(profiles.active);}if(editor==null&&getParent() instanceof android.widget.FrameLayout)editor=new EditorWorkspace(this,(android.widget.FrameLayout)getParent());geometry();invalidate();}
    public void finishEditing(){release();saveLayout();editing=false;selected=null;editorSelectionIds.clear();if(editor!=null){editor.close();editor=null;}geometry();unlock();}
    public void startTest(){release();editing=false;testMode=true;invalidate();}
    public void release(){HardwareInput.reset();if(hasPointerCapture())releasePointerCapture();lastCue=0;gameplayLock.cancel();input.release();edits.clear();edgePointer=-1;if(temporaryReturn!=null){String restore=temporaryReturn;temporaryReturn=null;temporaryPointer=-1;temporarySource=null;config=profiles.load(restore);input.configure(config);}actions.released();invalidate();}
    public void toolbarCycle(int direction){boolean playing=gameplayLock.locked;release();cycleLayout(direction);if(!playing)unlock();refreshQuickPanel();}
    public void cycleLayout(int direction){List<String> ids=profiles.layouts(config.game);if(ids.isEmpty())return;int index=ids.indexOf(profiles.active);selectProfile(ids.get(Math.floorMod(index+direction,ids.size())));}
    private void temporaryLayout(int pointer){if(temporaryReturn!=null)return;String target=config.holdLayout;if(target.isEmpty()||target.equals(profiles.active)||!profiles.ids().contains(target))return;temporaryReturn=profiles.active;temporaryLayoutId=target;temporaryPointer=pointer;transitionTo(profiles.load(target));}
    public void history(boolean forward){
        ArrayDeque<ControllerConfig> source=forward?redo:undo,destination=forward?undo:redo;
        if(source.isEmpty())return;release();
        ControllerConfig next=source.peekLast().copy();profiles.save(next);
        destination.addLast(config.copy());source.removeLast();config=next;
        input.configure(config);savedConfig=config.copy();selected=null;editorSelectionIds.clear();editorRefresh();invalidate();
    }
    private void previewEditing(){release();new android.app.AlertDialog.Builder(getContext()).setTitle(config.name).setView(new LayoutPreview(getContext(),config)).setPositiveButton("Back to editor",null).show();}
    @Override public void onPointerCaptureChange(boolean captured){super.onPointerCaptureChange(captured);if(!captured)release();}

    @Override public boolean onCapturedPointerEvent(MotionEvent event){return HardwareInput.motion(this,event);}
    void hardwareChanged(){input.update(SystemClock.uptimeMillis());actions.changed();invalidate();}
    public void refresh(long now){
        if(quickPanel!=null)quickPanel.sync();
        if(editing||settingsOpen)return;
        if(gameplayLock.holding()){if(gameplayLock.update(now)){gameHandleLearned();unlock();}invalidate();}
        byte[] before=state.report();int visual=visualState();
        input.update(now);transitionFeedback();dispatchAction();
        boolean changed=!Arrays.equals(before,state.report());
        if(changed)actions.changed();
        boolean calibration=gyro!=null&&gyro.calibrating();
        if(changed||visual!=visualState()||calibration!=displayedCalibration||input.automation.running())invalidate();
        displayedCalibration=calibration;
    }
    private int visualState(){
        int value=(input.modifierActive()?1:0)+(input.movementHeld()?2:0)+(gyro!=null&&gyro.calibrating()?4:0);
        for(ControllerConfig.Control c:config.controls)value=31*value+(input.active(c)?1:0);
        return value;
    }
    public long nextFrameDelay(){return !settingsOpen&&!editing&&(input.needsFrame()||gameplayLock.holding())?16:100;}
    private void geometry(){release();float margin=2*getResources().getDisplayMetrics().density;left=insetL+margin;top=insetT+margin+(editing?48*getResources().getDisplayMetrics().density:0);surfaceW=Math.max(1,getWidth()-left-insetR-margin);surfaceH=Math.max(1,getHeight()-top-insetB-margin);input.resize(surfaceW,surfaceH);if(editor!=null)editor.insets(insetL,insetT,insetR,insetB);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){geometry();}
    private float ui(){return Math.max(1,Math.min(surfaceW/1100,surfaceH/480));}
    public float toolbarScale(){return ui();}
    private float bar(){return 44*ui();}
    private void drawRemoveLayout(Canvas canvas,float x,float y){
        float u=ui();paint.setColor(profiles.builtin(profiles.active)?MUTED:TEXT);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f*u);
        canvas.drawRoundRect(x-6*u,y-5*u,x+6*u,y+9*u,1.5f*u,1.5f*u,paint);
        canvas.drawLine(x-9*u,y-8*u,x+9*u,y-8*u,paint);canvas.drawLine(x-3*u,y-11*u,x+3*u,y-11*u,paint);
        canvas.drawLine(x-2*u,y-2*u,x-2*u,y+6*u,paint);canvas.drawLine(x+2*u,y-2*u,x+2*u,y+6*u,paint);paint.setStyle(Paint.Style.FILL);
    }
    private boolean toolbarTouch(float x,float y){
        return y>=0&&(y<bar()||y<48*getResources().getDisplayMetrics().density&&input.target(x,y)==null);
    }
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(editing?EditorStyle.BG:appearance.background);canvas.save();canvas.translate(left,top);
        if(editing&&config.grid){paint.setColor(0x243c7773);paint.setStrokeWidth(1);float step=Math.max(8,config.gridSize*surfaceW);for(float x=0;x<surfaceW;x+=step)canvas.drawLine(x,0,x,surfaceH,paint);for(float y=0;y<surfaceH;y+=step)canvas.drawLine(0,y,surfaceW,y,paint);}
        for(ControllerConfig.Control c:config.controls)if((editing?config.shown(c):input.visible(c))&&(editing||!config.backgroundCamera(c))){
            if(c.opacity>=1)drawControl(canvas,c);
            else{int layer=canvas.saveLayerAlpha(null,Math.round(c.opacity*255));drawControl(canvas,c);canvas.restoreToCount(layer);}
        }
        if(editing){paint.setColor(EditorStyle.TEAL);paint.setStrokeWidth(1);if(!Float.isNaN(guideX))canvas.drawLine(guideX*surfaceW,0,guideX*surfaceW,surfaceH,paint);if(!Float.isNaN(guideY))canvas.drawLine(0,guideY*surfaceH,surfaceW,guideY*surfaceH,paint);}
        // Compact toolbar; its vertical touch target can expand into empty space.
        float b=bar();
        if(!editing&&!gameplayLock.locked){
            paint.setColor(appearance.surface);canvas.drawRoundRect(0,0,surfaceW,b,8*ui(),8*ui(),paint);
            label(canvas,connected?"● CONNECT":"CONNECT",surfaceW*.065f,b*.68f,11,connected?ACCENT:TEXT);
            label(canvas,gameplayLock.locked?"EXIT":"▶ PLAY",surfaceW*.215f,b*.68f,11,ACCENT);
            label(canvas,"‹",surfaceW*.33f,b*.72f,25,TEXT);label(canvas,"›",surfaceW*.67f,b*.72f,25,TEXT);
            paint.setTextSize(11*ui());String title=android.text.TextUtils.ellipsize(config.name,new android.text.TextPaint(paint),surfaceW*.26f,android.text.TextUtils.TruncateAt.END).toString();label(canvas,title,surfaceW*.5f,b*.78f,11,TEXT);
            label(canvas,"▦",surfaceW*.755f,b*.72f,22,TEXT);label(canvas,"✎",surfaceW*.8125f,b*.72f,22,TEXT);drawRemoveLayout(canvas,surfaceW*.86f,b/2);label(canvas,testMode?"×":"⚙",surfaceW*.91f,b*.72f,22,TEXT);label(canvas,"⋮",surfaceW*.97f,b*.72f,24,TEXT);
        }
        if(!editing){
            if(gameplayLock.locked){float density=getResources().getDisplayMetrics().density,cx=surfaceW/2;paint.setColor(MUTED);canvas.drawRoundRect(cx-72*density,6*density,cx+72*density,16*density,5*density,5*density,paint);
                if(gameplayLock.holding()){paint.setColor(ACCENT);canvas.drawRoundRect(cx-72*density,6*density,cx-72*density+144*density*gameplayLock.progress(SystemClock.uptimeMillis()),16*density,5*density,5*density,paint);}
                if(SystemClock.uptimeMillis()<hintUntil)label(canvas,"Hold the top handle to exit game mode",surfaceW*.5f,b+16*density,11,MUTED);
            }
        }
        if(testMode)drawTest(canvas);
        canvas.restore();
    }
    private void drawControl(Canvas canvas,ControllerConfig.Control c){
        float x=c.x*surfaceW,y=c.y*surfaceH,w=c.w*surfaceW,h=c.h*surfaceH,r=input.radius(c);
        ControllerInput.Pointer p=input.owner(c);boolean held=input.active(c);
        paint.setStyle(Paint.Style.FILL);paint.setColor(held?appearance.pressed:appearance.control);
        if(c.kind.equals("fire")){if(p!=null){x=p.cx;y=p.cy;}canvas.drawCircle(x,y,r,paint);label(canvas,"FIRE",x,y+5*ui(),12,held?ACCENT:TEXT);}
        else if(c.kind.equals("stick")){
            if(p!=null){x=p.cx;y=p.cy;}
            if(c.bit==1&&config.mode(c).equals("TOUCHPAD")){paint.setColor(held?0x334d6b79:0x192a4051);canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,24,24,paint);label(canvas,config.sameThumbFire?(state.rt>0?"FIRING · exit camera / lift to stop":"CAMERA · tap, then hold to fire"):"CAMERA",x,y,11,MUTED);}
            else {canvas.drawCircle(x,y,r,paint);paint.setColor(appearance.border);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);canvas.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.FILL);
                float vx=0,vy=0;if(p!=null){float[] s=PadState.stick((p.x-x)/r,(p.y-y)/r,config.deadzone,1);vx=s[0];vy=s[1];}
                paint.setColor(held?ACCENT:appearance.border);canvas.drawCircle(x+vx*r*.6f,y+vy*r*.6f,r*.32f,paint);
                if(config.integrated&&c.bit==0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(6*ui());paint.setColor(ACCENT);canvas.drawArc(x-r-5,y-r-5,x+r+5,y+r+5,210,120*state.rt,false,paint);paint.setColor(0xffe3aa72);canvas.drawArc(x-r-5,y-r-5,x+r+5,y+r+5,30,120*state.lt,false,paint);paint.setStyle(Paint.Style.FILL);}
                label(canvas,c.title(),x,y+r*.76f,10,MUTED);
            }
        }else if(c.kind.equals("unified")){
            if(p!=null&&config.unifiedFloating){x=p.cx;y=p.cy;}
            canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,20,20,paint);
            float cx=p==null?x:p.cx,cy=p==null?y:p.cy;paint.setColor(ACCENT);paint.setStrokeWidth(3*ui());
            if(config.unifiedOrientation.equals("VERTICAL")){canvas.drawLine(x-w*.3f,cy,x+w*.3f,cy,paint);canvas.drawCircle(x,cy+(state.lt-state.rt)*(config.unifiedInvert?-1:1)*h*.4f,8*ui(),paint);}
            else{canvas.drawLine(cx,y-h*.3f,cx,y+h*.3f,paint);canvas.drawCircle(cx+(state.lt-state.rt)*(config.unifiedInvert?-1:1)*w*.4f,y,8*ui(),paint);}
            if(config.unifiedOrientation.equals("VERTICAL")){label(canvas,config.unifiedInvert?"LT ↑":"RT ↑",x,y-h*.32f,11,TEXT);label(canvas,config.unifiedInvert?"RT ↓":"LT ↓",x,y+h*.36f,11,TEXT);}else{label(canvas,config.unifiedInvert?"LT ←":"RT ←",x-w*.28f,y-h*.25f,10,TEXT);label(canvas,config.unifiedInvert?"→ RT":"→ LT",x+w*.28f,y-h*.25f,10,TEXT);}
        }else if(c.kind.equals("trigger")){
            float value=c.bit==0?state.lt:state.rt;
            if(config.mode(c).equals("DIGITAL")){canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,22,22,paint);label(canvas,labelFor(c),x,y+5*ui(),16,held?ACCENT:TEXT);}
            else if(config.mode(c).equals("VERTICAL")){
                float half=Math.min(w/2,24*ui());canvas.drawRoundRect(x-half,y-h/2,x+half,y+h/2,half,half,paint);paint.setColor(ACCENT);
                canvas.save();canvas.clipRect(x-half,c.reverse?y+h/2-h*value:y-h/2,x+half,c.reverse?y+h/2:y-h/2+h*value);canvas.drawRoundRect(x-half,y-h/2,x+half,y+h/2,half,half,paint);canvas.restore();label(canvas,labelFor(c),x,y,13,TEXT);label(canvas,c.reverse?"↑":"↓",x,y+h*.30f,17,MUTED);
            }else{
                float rr=Math.min(w,h)*.46f;canvas.drawCircle(x,y,rr,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(5*ui());paint.setColor(ACCENT);canvas.drawArc(x-rr,y-rr,x+rr,y+rr,-90,360*value,false,paint);paint.setStyle(Paint.Style.FILL);label(canvas,labelFor(c),x,y,14,TEXT);boolean right=(c.bit==0)^c.reverse;label(canvas,right?"›":"‹",x+(right?1:-1)*(rr+12*ui()),y+7*ui(),25,MUTED);
            }
        }else if(c.kind.equals("dpad")){
            canvas.drawRoundRect(x-w*.18f,y-h/2,x+w*.18f,y+h/2,12,12,paint);canvas.drawRoundRect(x-w/2,y-h*.18f,x+w/2,y+h*.18f,12,12,paint);
            label(canvas,"▲",x,y-h*.28f,18,MUTED);label(canvas,"▼",x,y+h*.40f,18,MUTED);label(canvas,"‹",x-w*.35f,y+6*ui(),24,MUTED);label(canvas,"›",x+w*.35f,y+6*ui(),24,MUTED);
            if(state.hat!=8){double angle=state.hat*Math.PI/4-Math.PI/2;paint.setColor(ACCENT);canvas.drawCircle(x+(float)Math.cos(angle)*w*.34f,y+(float)Math.sin(angle)*h*.34f,8*ui(),paint);}
        }else{
            if(c.id.matches("[ABXY]"))canvas.drawCircle(x,y,r*(held?.94f:1),paint);else canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,18,18,paint);
            boolean ps=config.labels.equals("PLAYSTATION");int color=ControllerSymbols.faceColor(c.id,ps,appearance.light);
            if(!ControllerSymbols.draw(canvas,paint,c,ps,x,y,Math.min(w,h)*.25f,color))label(canvas,c.kind.equals("hold")&&input.movementHeld()?"RELEASE LS":c.kind.equals("aim")&&input.aiming()?"AIM ON":labelFor(c),x,y+6*ui(),c.id.matches("[ABXY]")?23:11,color);
        }
        if(editing){
            x=c.x*surfaceW;y=c.y*surfaceH;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(editorSelectionIds.contains(c.id)?3:1);paint.setColor(editorSelectionIds.contains(c.id)?EditorStyle.TEAL:0xff365464);canvas.drawRect(x-w/2,y-h/2,x+w/2,y+h/2,paint);
            paint.setPathEffect(new DashPathEffect(new float[]{6,5},0));paint.setColor(overlaps(c)?0xffe5a375:0xff5f8d9f);
            if(input.circularHit(c))canvas.drawOval(x-r*c.hitbox*c.hitWidth,y-r*c.hitbox*c.hitHeight,x+r*c.hitbox*c.hitWidth,y+r*c.hitbox*c.hitHeight,paint);else canvas.drawRect(x-w*c.hitbox*c.hitWidth/2,y-h*c.hitbox*c.hitHeight/2,x+w*c.hitbox*c.hitWidth/2,y+h*c.hitbox*c.hitHeight/2,paint);
            paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);if(c==selected){paint.setColor(EditorStyle.TEAL);canvas.drawRoundRect(x+w/2-7*ui(),y+h/2-7*ui(),x+w/2+7*ui(),y+h/2+7*ui(),2*ui(),2*ui(),paint);}
        }
    }
    private boolean overlaps(ControllerConfig.Control c){for(ControllerConfig.Control o:config.controls)if(o!=c&&config.shown(o)&&Math.abs(o.x-c.x)<(o.w+c.w)*.32f&&Math.abs(o.y-c.y)<(o.h+c.h)*.32f)return true;return false;}
    private String labelFor(ControllerConfig.Control c){return ControllerSymbols.label(c,config.labels.equals("PLAYSTATION"));}
    private void label(Canvas canvas,String s,float x,float y,float size,int color){paint.setStyle(Paint.Style.FILL);paint.setColor(color);paint.setTextSize(size*ui());paint.setTypeface(Typeface.DEFAULT);paint.setTextAlign(Paint.Align.CENTER);canvas.drawText(s,x,y,paint);}
    private void drawTest(Canvas canvas){
        String[] lines={"FINAL HID STATE · "+config.game+" / "+config.name,"Pointers "+(input.pointerCount()+(temporaryReturn!=null?1:0))+" · "+(input.movementHeld()?"LS HELD ":"")+(input.aiming()?"AIM ":"")+(input.modifierActive()?"MODIFIER ":"")+ (connected?"CONNECTED":"OFFLINE")+(gyro!=null&&gyro.calibrating()?" · GYRO CALIBRATING":""),String.format(Locale.ROOT,"LS %+.2f  %+.2f",state.lx,state.ly),String.format(Locale.ROOT,"RS %+.2f  %+.2f",state.rx,state.ry),String.format(Locale.ROOT,"LT %d%%  RT %d%%",Math.round(state.lt*100),Math.round(state.rt*100)),"D-pad "+(state.hat==8?"neutral":state.hat),bits("A B X Y",new int[]{PadState.A,PadState.B,PadState.X,PadState.Y}),bits("LB RB L3 R3",new int[]{PadState.LB,PadState.RB,PadState.L3,PadState.R3}),bits("View Menu Home",new int[]{PadState.VIEW,PadState.MENU,PadState.GUIDE}),"Report "+hex(state.report())};
        float y=surfaceH*.58f;for(String line:lines){label(canvas,line,surfaceW*.51f,y,9,TEXT);y+=15*ui();}
    }
    private String bits(String names,int[] ids){String[] n=names.split(" ");StringBuilder s=new StringBuilder();for(int i=0;i<n.length;i++)s.append(n[i]).append((state.buttons&(1<<ids[i]))!=0?":1 ":":0 ");return s.toString();}
    private String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte value:bytes)b.append(String.format(Locale.ROOT,"%02x",value&255));return b.toString();}
    @Override public boolean onTouchEvent(MotionEvent event){
        long now=event.getEventTime();int a=event.getActionMasked(),index=event.getActionIndex(),id=event.getPointerId(index);float x=event.getX(index)-left,y=event.getY(index)-top;
        if(a==MotionEvent.ACTION_CANCEL){release();return true;}
        if(settingsOpen)return true;
        if(a==MotionEvent.ACTION_MOVE){for(int i=0;i<event.getPointerCount();i++)gameplayLock.move(event.getPointerId(i),event.getX(i)-left,event.getY(i)-top,12*getResources().getDisplayMetrics().density);}
        if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP)gameplayLock.up(id);
        if(a==MotionEvent.ACTION_POINTER_DOWN)gameplayLock.cancel();
        if((a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP)&&id==temporaryPointer){
            for(int i=0;i<event.getPointerCount();i++)input.move(event.getPointerId(i),event.getX(i)-left,event.getY(i)-top,now);
            finishTemporary();return true;
        }
        if(!editing&&a==MotionEvent.ACTION_MOVE&&temporarySource!=null){int i=event.findPointerIndex(temporaryPointer);if(i>=0){temporarySource.move(temporaryPointer,event.getX(i)-left,event.getY(i)-top,now);temporarySource.update(now);if(temporarySource.state.lt<=0){for(int j=0;j<event.getPointerCount();j++)input.move(event.getPointerId(j),event.getX(j)-left,event.getY(j)-top,now);finishTemporary();return true;}input.temporaryLT=temporarySource.state.lt;}}
        if(!editing&&a==MotionEvent.ACTION_MOVE&&edgePointer>=0){int i=event.findPointerIndex(edgePointer);if(i>=0){float dx=event.getX(i)-left-edgeX,dy=event.getY(i)-top-edgeY;if(Math.abs(dx)>surfaceW*.12f&&Math.abs(dy)<surfaceH*.12f){cycleLayout(dx>0?-1:1);return true;}}}
        if(a==MotionEvent.ACTION_DOWN&&(input.pointerCount()>0||!edits.isEmpty()||temporaryReturn!=null))release();
        // Pointer transitions also carry fresh coordinates for every remaining finger.
        if(!editing&&(a==MotionEvent.ACTION_POINTER_DOWN||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_UP))
            for(int i=0;i<event.getPointerCount();i++)input.move(event.getPointerId(i),event.getX(i)-left,event.getY(i)-top,now);
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            if(gameplayLock.locked&&!editing&&a==MotionEvent.ACTION_DOWN&&handleTouch(x,y)){
                if(!gameOverlayOpen){gameplayLock.down(id,x,y,now);invalidate();}return true;
            }
            if(y<0)return true;
            if(!editing&&!gameplayLock.locked&&a==MotionEvent.ACTION_DOWN&&input.pointerCount()==0&&y<bar()&&y>=0){
                release();float position=x/surfaceW;
                if(position<.13f)actions.connect();
                else if(position<.29f){if(gameplayLock.locked)unlock();else startGameMode();}
                else if(position<.36f)toolbarCycle(-1);
                else if(position<.64f){} // Current layout title; never emits controller input.
                else if(position<.71f)toolbarCycle(1);
                else if(position<.79f)new ControllerSettings(getContext(),this,actions::connect).switcher();
                else if(position<.835f)requestEditing();
                else if(position<.885f)new ControllerSettings(getContext(),this,actions::connect).removeCurrentLayout();
                else if(position<.94f){if(testMode){testMode=false;invalidate();}else actions.settings();}
                else new ControllerSettings(getContext(),this,actions::connect).layoutActions();return true;
            }
            if(editing){boolean handle=selected!=null&&Math.hypot(x-(selected.x+selected.w/2)*surfaceW,y-(selected.y+selected.h/2)*surfaceH)<20*ui();ControllerConfig.Control c=handle?selected:input.target(x,y,false);if(c==null){selected=null;editorSelectionIds.clear();if(editor!=null)editor.selected(null);}if(c!=null){if(multiSelect){selectEditorControl(c);editorRefresh();return true;}if(!editorSelectionIds.contains(c.id)){editorSelectionIds.clear();editorSelectionIds.add(c.id);}selected=c;EditTouch t=new EditTouch(c,x,y);t.resizing=handle&&editorSelectionIds.size()==1;edits.put(id,t);groupStart.clear();for(ControllerConfig.Control item:selectedControls())groupStart.put(item.id,new float[]{item.x,item.y});}}
            else if(input.target(x,y)!=null&&input.target(x,y).kind.equals("next")){cycleLayout(1);return true;}
            else if(input.target(x,y)!=null&&input.target(x,y).kind.equals("previous")){cycleLayout(-1);return true;}
            else if(input.target(x,y)!=null&&input.target(x,y).kind.equals("layoutHold")){temporaryLayout(id);return true;}
            else if(config.edgeSwitch&&input.target(x,y)==null&&(x<surfaceW*.04f||x>surfaceW*.96f)){edgePointer=id;edgeX=x;edgeY=y;}
            else if(input.down(id,x,y,now)){ControllerConfig.Control c=input.target(x,y);if(c!=null&&c.haptic&&(!c.kind.equals("stick")||input.owner(c).click))feedback();}
            getParent().requestDisallowInterceptTouchEvent(true);
        }else if(a==MotionEvent.ACTION_MOVE){
            for(int i=0;i<event.getPointerCount();i++){int pointer=event.getPointerId(i);float px=event.getX(i)-left,py=event.getY(i)-top;if(editing){EditTouch t=edits.get(pointer);if(t!=null){t.x=px;t.y=py;t.moved|=Math.hypot(px-t.sx,py-t.sy)>6*ui();}}else input.move(pointer,px,py,now);}
            if(editing)updateEdits();
        }else if(a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP){
            if(editing){EditTouch t=edits.remove(id);if(t!=null){if(edits.isEmpty()){guideX=guideY=Float.NaN;if(t.moved)saveLayout();if(editor!=null)editor.selected(selected);} else for(EditTouch remaining:edits.values()){remaining.sx=remaining.x;remaining.sy=remaining.y;remaining.cx=remaining.c.x;remaining.cy=remaining.c.y;remaining.w=remaining.c.w;remaining.h=remaining.c.h;remaining.moved=true;}}}
            else input.up(id,now);
            if(a==MotionEvent.ACTION_UP)performClick();
        }
        if(editing)state.clear();else{input.update(now);transitionFeedback();dispatchAction();checkAimLayout(now);}actions.changed();invalidate();return true;
    }
    private void checkAimLayout(long now){
        if(temporaryReturn!=null||config.aimLayout.isEmpty()||config.aimLayout.equals(profiles.active)||!profiles.ids().contains(config.aimLayout)||state.lt<=0)return;
        for(Map.Entry<Integer,ControllerInput.Pointer> entry:input.activePointers().entrySet()){
            ControllerInput.Pointer p=entry.getValue();String kind=p.control.kind;
            if(!(kind.equals("trigger")&&p.control.bit==0||kind.equals("aim")&&config.aimMode.equals("HOLD")||kind.equals("unified")))continue;
            ControllerInput source=new ControllerInput(config.copy());source.resize(surfaceW,surfaceH);if(!source.down(entry.getKey(),p.startX,p.startY,p.down))continue;source.move(entry.getKey(),p.x,p.y,now);source.update(now);if(source.state.lt<=0)continue;
            String target=config.aimLayout;int id=entry.getKey();temporaryReturn=profiles.active;temporaryLayoutId=target;temporaryPointer=id;temporarySource=source;transitionTo(profiles.load(target));input.temporaryLT=source.state.lt;input.update(now);actions.transitioned();return;
        }
    }
    private void finishTemporary(){
        String restore=temporaryReturn;temporaryReturn=null;temporaryPointer=-1;temporarySource=null;
        if(restore!=null)transitionTo(profiles.load(restore));
    }
    private void dispatchAction(){String action=input.takeAction();if(action==null)return;int colon=action.indexOf(':');String type=action.substring(0,colon),target=action.substring(colon+1);if(type.equals("NEXT"))cycleLayout(1);else if(type.equals("PREVIOUS"))cycleLayout(-1);else if(type.equals("LAYOUT")&&profiles.ids().contains(target))selectProfile(target);else if(type.equals("PROFILE")&&profiles.games().contains(target)){List<String> ids=profiles.layouts(target);if(!ids.isEmpty())selectProfile(ids.get(0));}}
    private void updateEdits(){
        guideX=guideY=Float.NaN;
        if(editorSelectionIds.size()>1&&!edits.isEmpty()){
            EditTouch touch=edits.values().iterator().next();float dx=(touch.x-touch.sx)/surfaceW,dy=(touch.y-touch.sy)/surfaceH;
            float minX=-1,maxX=1,minY=-1,maxY=1;
            for(ControllerConfig.Control c:selectedControls()){float[] start=groupStart.get(c.id);if(start==null)continue;minX=Math.max(minX,c.w/2-start[0]);maxX=Math.min(maxX,1-c.w/2-start[0]);minY=Math.max(minY,Math.max(.1f,c.h/2)-start[1]);maxY=Math.min(maxY,1-c.h/2-start[1]);}
            if(config.snap){float[] start=groupStart.get(touch.c.id);if(start!=null){float px=touch.c.x,py=touch.c.y;touch.c.x=start[0]+dx;touch.c.y=start[1]+dy;float grid=config.gridSize;touch.c.x=Math.round(touch.c.x/grid)*grid;touch.c.y=Math.round(touch.c.y*surfaceH/(grid*surfaceW))*grid*surfaceW/surfaceH;float[] guides=EditorGeometry.snap(touch.c,config.controls,editorSelectionIds,8*getResources().getDisplayMetrics().density/surfaceW,8*getResources().getDisplayMetrics().density/surfaceH);guideX=guides[0];guideY=guides[1];dx=touch.c.x-start[0];dy=touch.c.y-start[1];touch.c.x=px;touch.c.y=py;}}
            dx=PadState.clamp(dx,minX,maxX);dy=PadState.clamp(dy,minY,maxY);for(ControllerConfig.Control c:selectedControls()){float[] start=groupStart.get(c.id);if(start!=null){c.x=start[0]+dx;c.y=start[1]+dy;}}return;
        }
        Set<ControllerConfig.Control> done=new HashSet<>();for(EditTouch t:edits.values()){if(!done.add(t.c))continue;EditTouch pair=null;for(EditTouch p:edits.values())if(p!=t&&p.c==t.c){pair=p;break;}
            if(pair!=null){float start=(float)Math.hypot(pair.sx-t.sx,pair.sy-t.sy);float ratio=(float)Math.hypot(pair.x-t.x,pair.y-t.y)/Math.max(1,start);t.c.w=PadState.clamp(t.w*ratio,.025f,.5f);t.c.h=PadState.clamp(t.h*ratio,.04f,.7f);t.moved=pair.moved=true;}
            else if(t.resizing){t.c.w=PadState.clamp(t.w+2*(t.x-t.sx)/surfaceW,.025f,.5f);t.c.h=PadState.clamp(t.h+2*(t.y-t.sy)/surfaceH,.04f,.7f);t.moved=true;}
            else {t.c.x=t.cx+(t.x-t.sx)/surfaceW;t.c.y=t.cy+(t.y-t.sy)/surfaceH;if(config.snap){t.c.x=Math.round(t.c.x/config.gridSize)*config.gridSize;t.c.y=Math.round(t.c.y*surfaceH/(config.gridSize*surfaceW))*config.gridSize*surfaceW/surfaceH;}}
            if(config.snap&&!t.resizing){float[] guides=EditorGeometry.snap(t.c,config.controls,editorSelectionIds,8*getResources().getDisplayMetrics().density/surfaceW,8*getResources().getDisplayMetrics().density/surfaceH);guideX=guides[0];guideY=guides[1];}
            t.c.x=PadState.clamp(t.c.x,t.c.w/2,1-t.c.w/2);t.c.y=PadState.clamp(t.c.y,t.c.h/2,1-t.c.h/2);
        }
    }
    private void feedback(){if(config.haptics>0)actions.feedback(config.haptics);}
    private static final int ACCESS_SETTINGS=0x0f010001,ACCESS_LAYOUTS=0x0f010002,ACCESS_CONNECT=0x0f010003,ACCESS_RELEASE=0x0f010004,ACCESS_EDIT=0x0f010005,ACCESS_UNLOCK=0x0f010006,ACCESS_STOP=0x0f010007,ACCESS_PREVIOUS=0x0f010008,ACCESS_NEXT=0x0f010009,ACCESS_REMOVE=0x0f01000a;
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
        super.onInitializeAccessibilityNodeInfo(info);
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_CONNECT,"Connection"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_UNLOCK,gameplayLock.locked?"Exit game mode":"Start game mode"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_PREVIOUS,"Previous layout"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_NEXT,"Next layout"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_LAYOUTS,"Choose layout"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_SETTINGS,"Open settings"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_STOP,"Emergency release and disconnect"));
        info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_RELEASE,"Release all inputs"));
        if(!gameplayLock.locked)info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_EDIT,editing?"Manage controls":"Edit layout"));
        if(!editing&&!gameplayLock.locked)info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(ACCESS_REMOVE,"Remove layout"));
        if(Build.VERSION.SDK_INT>=30)info.setStateDescription(config.game+" / "+config.name+" · "+(connected?"Connected":"Offline"));
    }
    @Override public boolean performAccessibilityAction(int action,Bundle arguments){
        if((gameplayLock.locked&&(action==ACCESS_EDIT||action==ACCESS_REMOVE))||(editing&&action==ACCESS_REMOVE))return super.performAccessibilityAction(action,arguments);
        switch(action){
            case ACCESS_UNLOCK:if(gameplayLock.locked){unlock();}else startGameMode();return true;
            case ACCESS_PREVIOUS:toolbarCycle(-1);return true;
            case ACCESS_NEXT:toolbarCycle(1);return true;
            case ACCESS_STOP:release();actions.emergency();return true;
            case ACCESS_SETTINGS:release();actions.settings();return true;
            case ACCESS_LAYOUTS:release();new ControllerSettings(getContext(),this,actions::connect).switcher();return true;
            case ACCESS_CONNECT:release();actions.connect();return true;
            case ACCESS_RELEASE:release();return true;
            case ACCESS_REMOVE:new ControllerSettings(getContext(),this,actions::connect).removeCurrentLayout();return true;
            case ACCESS_EDIT:release();if(editing)new ControllerSettings(getContext(),this,actions::connect).palette();else requestEditing();return true;
            default:return super.performAccessibilityAction(action,arguments);
        }
    }
    @Override public boolean performClick(){super.performClick();return true;}
}
