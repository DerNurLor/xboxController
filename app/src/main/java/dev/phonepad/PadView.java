package dev.phonepad;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.*;
import java.util.*;

/** Native rendering and MotionEvent adapter. All gameplay interpretation lives in ControllerInput. */
public final class PadView extends View {
    public interface Actions {void connect();void settings();void changed();default void released(){changed();}}
    public final ProfileStore profiles;
    public ControllerConfig config;
    public final ControllerInput input;
    public final PadState state;
    public GyroInput gyro;
    public boolean connected,editing,testMode;
    public String status="Not connected",transport="BLUETOOTH";
    private final Actions actions;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private float left=16,top=16,surfaceW=1100,surfaceH=480;
    private int insetL,insetT,insetR,insetB;
    private ControllerConfig.Control selected;
    private String temporaryReturn;
    private ControllerInput temporarySource;
    private int temporaryPointer=-1,edgePointer=-1;
    private float edgeX,edgeY;
    private final ArrayDeque<ControllerConfig> undo=new ArrayDeque<>(),redo=new ArrayDeque<>();
    private ControllerConfig savedConfig;
    private final Map<Integer,EditTouch> edits=new LinkedHashMap<>();
    private static final int ACCENT=0xffb2f96c,TEXT=0xffe6eef4,MUTED=0xff8899a9;
    private static final class EditTouch {ControllerConfig.Control c;float sx,sy,x,y,cx,cy,w,h;boolean moved,resizing;EditTouch(ControllerConfig.Control c,float x,float y){this.c=c;sx=this.x=x;sy=this.y=y;cx=c.x;cy=c.y;w=c.w;h=c.h;}}
    public PadView(Context context,Actions actions){
        super(context);this.actions=actions;profiles=new ProfileStore(context.getSharedPreferences("pad",0));
        try{config=profiles.load(profiles.active);}catch(IllegalArgumentException e){config=profiles.select("Default");}
        input=new ControllerInput(config);state=input.state;
        android.content.SharedPreferences prefs=context.getSharedPreferences("pad",0);
        if(!prefs.getBoolean("fullHeight.v1",false)){config.fitHeight();profiles.create(config.name.length()>44?"Full height layout":config.name+" · full height",config);config=profiles.load(profiles.active);input.configure(config);prefs.edit().putBoolean("fullHeight.v1",true).apply();}
        savedConfig=config.copy();
        setFocusable(true);setContentDescription("PhonePad touch gamepad. Landscape multitouch controls.");
        setOnApplyWindowInsetsListener((v,insets)->{int l=0,t=0,r=0,b=0;if(Build.VERSION.SDK_INT>=30){int types=WindowInsets.Type.displayCutout();if(insets.isVisible(WindowInsets.Type.navigationBars()))types|=WindowInsets.Type.navigationBars();Insets i=insets.getInsets(types);l=i.left;t=i.top;r=i.right;b=i.bottom;}else if(insets.getDisplayCutout()!=null){l=insets.getDisplayCutout().getSafeInsetLeft();r=insets.getDisplayCutout().getSafeInsetRight();t=insets.getDisplayCutout().getSafeInsetTop();b=insets.getDisplayCutout().getSafeInsetBottom();}if(l!=insetL||r!=insetR||t!=insetT||b!=insetB){insetL=l;insetR=r;insetT=t;insetB=b;geometry();}return insets;});
    }
    public void loadSettings(){invalidate();}
    public void fitLayoutHeight(){release();ControllerConfig fitted=config.copy();fitted.fitHeight();profiles.create(config.name.length()>44?"Full height layout":config.name+" · full height",fitted);selectProfile(profiles.active);}

    public void selectProfile(String id){release();config=profiles.select(id);input.configure(config);selected=null;undo.clear();redo.clear();savedConfig=config.copy();invalidate();}
    public void saveLayout(){if(savedConfig!=null&&!ProfileStore.encode(savedConfig).toString().equals(ProfileStore.encode(config).toString())){undo.addLast(savedConfig.copy());if(undo.size()>40)undo.removeFirst();redo.clear();}String selectedId=selected==null?null:selected.id;config=ProfileStore.decode(ProfileStore.encode(config));profiles.save(config);input.configure(config);selected=selectedId==null?null:config.get(selectedId);savedConfig=config.copy();invalidate();}
    public void mutate(Runnable change){release();change.run();saveLayout();}
    public void resetProfile(){release();config=profiles.reset();input.configure(config);invalidate();}
    public void startEditing(){release();testMode=false;editing=true;invalidate();}
    public void finishEditing(){release();editing=false;saveLayout();}
    public void startTest(){release();editing=false;testMode=true;invalidate();}
    public void release(){input.release();edits.clear();edgePointer=-1;if(temporaryReturn!=null){String restore=temporaryReturn;temporaryReturn=null;temporaryPointer=-1;temporarySource=null;config=profiles.load(restore);input.configure(config);}actions.released();invalidate();}
    public void cycleLayout(int direction){List<String> ids=profiles.layouts(config.game);int index=ids.indexOf(profiles.active);selectProfile(ids.get(Math.floorMod(index+direction,ids.size())));}
    private void temporaryLayout(int pointer){String target=config.holdLayout;if(target.isEmpty()||target.equals(profiles.active)||!profiles.ids().contains(target))return;release();temporaryReturn=profiles.active;temporaryPointer=pointer;config=profiles.load(target);input.configure(config);invalidate();}
    public void history(boolean forward){ArrayDeque<ControllerConfig> source=forward?redo:undo,destination=forward?undo:redo;if(source.isEmpty())return;release();destination.addLast(config.copy());config=source.removeLast();profiles.save(config);input.configure(config);savedConfig=config.copy();selected=null;invalidate();}
    private void previewEditing(){release();new android.app.AlertDialog.Builder(getContext()).setTitle(config.name).setView(new LayoutPreview(getContext(),config)).setPositiveButton("Back to editor",null).show();}
    public void refresh(long now){byte[] before=state.report();if(!editing){input.update(now);dispatchAction();}if(!Arrays.equals(before,state.report()))actions.changed();invalidate();}
    private void geometry(){release();float margin=8*getResources().getDisplayMetrics().density;left=insetL+margin;top=insetT+margin;surfaceW=Math.max(1,getWidth()-left-insetR-margin);surfaceH=Math.max(1,getHeight()-top-insetB-margin);input.resize(surfaceW,surfaceH);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){geometry();}
    private float ui(){return Math.max(1,Math.min(surfaceW/1100,surfaceH/480));}
    private float bar(){return 44*ui();}
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(0xff0c1118);canvas.save();canvas.translate(left,top);
        if(editing&&config.grid){paint.setColor(0x224b687f);paint.setStrokeWidth(1);float step=Math.max(8,config.gridSize*surfaceW);for(float x=0;x<surfaceW;x+=step)canvas.drawLine(x,bar(),x,surfaceH,paint);for(float y=bar();y<surfaceH;y+=step)canvas.drawLine(0,y,surfaceW,y,paint);}
        for(ControllerConfig.Control c:config.controls)if(config.shown(c)){int layer=canvas.saveLayerAlpha(null,Math.round(c.opacity*255));drawControl(canvas,c);canvas.restoreToCount(layer);}
        if(editing&&selected!=null){paint.setColor(0x886899a9);paint.setStrokeWidth(1);canvas.drawLine(selected.x*surfaceW,bar(),selected.x*surfaceW,surfaceH,paint);canvas.drawLine(0,selected.y*surfaceH,surfaceW,selected.y*surfaceH,paint);}
        // Compact top controls have identical drawing and hit-test bounds.
        float b=bar();paint.setColor(0xee0c1118);canvas.drawRect(surfaceW-5.6f*b,0,surfaceW,b,paint);
        label(canvas,editing?"PREVIEW":"LAYOUT",surfaceW-4.8f*b,b*.62f,11,TEXT);
        if(editing){label(canvas,"UNDO",b*.7f,b*.62f,11,undo.isEmpty()?MUTED:TEXT);label(canvas,"REDO",b*2.2f,b*.62f,11,redo.isEmpty()?MUTED:TEXT);}
        label(canvas,editing?"DONE":testMode?"CLOSE":"SETTINGS",surfaceW-b*.75f,b*.62f,12,TEXT);
        label(canvas,editing?"CONTROLS":connected?"●":"CONNECT",surfaceW-b*2.6f,b*.62f,12,connected?ACCENT:MUTED);
        if(editing)label(canvas,"EDIT · "+config.name,surfaceW*.35f,b*.62f,12,ACCENT);
        if(testMode)drawTest(canvas);
        canvas.restore();
    }
    private void drawControl(Canvas canvas,ControllerConfig.Control c){
        float x=c.x*surfaceW,y=c.y*surfaceH,w=c.w*surfaceW,h=c.h*surfaceH,r=input.radius(c);
        ControllerInput.Pointer p=input.owner(c);boolean held=input.active(c);
        paint.setStyle(Paint.Style.FILL);paint.setColor(held?0xff334a46:0xff1c2732);
        if(c.kind.equals("fire")){if(p!=null){x=p.cx;y=p.cy;}canvas.drawCircle(x,y,r,paint);label(canvas,"FIRE",x,y+5*ui(),12,held?ACCENT:TEXT);}
        else if(c.kind.equals("stick")){
            if(p!=null){x=p.cx;y=p.cy;}
            if(c.bit==1&&config.mode(c).equals("TOUCHPAD")){paint.setColor(held?0x334d6b79:0x192a4051);canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,24,24,paint);label(canvas,"CAMERA",x,y,11,MUTED);}
            else {canvas.drawCircle(x,y,r,paint);paint.setColor(0xff425361);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.5f);canvas.drawCircle(x,y,r,paint);paint.setStyle(Paint.Style.FILL);
                float vx=0,vy=0;if(p!=null){float[] s=PadState.stick((p.x-x)/r,(p.y-y)/r,config.deadzone,1);vx=s[0];vy=s[1];}
                paint.setColor(held?ACCENT:0xff4a606e);canvas.drawCircle(x+vx*r*.6f,y+vy*r*.6f,r*.32f,paint);
                if(config.integrated&&c.bit==0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(6*ui());paint.setColor(ACCENT);canvas.drawArc(x-r-5,y-r-5,x+r+5,y+r+5,210,120*state.rt,false,paint);paint.setColor(0xffe3aa72);canvas.drawArc(x-r-5,y-r-5,x+r+5,y+r+5,30,120*state.lt,false,paint);paint.setStyle(Paint.Style.FILL);}
                label(canvas,c.id,x,y+r*.76f,10,MUTED);
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
                float half=Math.min(w/2,24*ui());canvas.drawRoundRect(x-half,y-h/2,x+half,y+h/2,half,half,paint);paint.setColor(0xff6c9561);
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
            int color=c.id.equals("A")?ACCENT:c.id.equals("B")?0xffff8b90:c.id.equals("X")?0xff86baff:c.id.equals("Y")?0xffead080:TEXT;
            label(canvas,c.kind.equals("hold")&&input.movementHeld()?"RELEASE LS":c.kind.equals("aim")&&input.aiming()?"AIM ON":labelFor(c),x,y+6*ui(),c.id.matches("[ABXY]")?23:11,color);
        }
        if(editing){
            x=c.x*surfaceW;y=c.y*surfaceH;paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(c==selected?3:1);paint.setColor(c==selected?ACCENT:0xff688599);canvas.drawRect(x-w/2,y-h/2,x+w/2,y+h/2,paint);
            paint.setPathEffect(new DashPathEffect(new float[]{6,5},0));paint.setColor(overlaps(c)?0xffe5a375:0xff5f8d9f);
            if(input.circularHit(c))canvas.drawOval(x-r*c.hitbox*c.hitWidth,y-r*c.hitbox*c.hitHeight,x+r*c.hitbox*c.hitWidth,y+r*c.hitbox*c.hitHeight,paint);else canvas.drawRect(x-w*c.hitbox*c.hitWidth/2,y-h*c.hitbox*c.hitHeight/2,x+w*c.hitbox*c.hitWidth/2,y+h*c.hitbox*c.hitHeight/2,paint);
            paint.setPathEffect(null);paint.setStyle(Paint.Style.FILL);if(c==selected){paint.setColor(ACCENT);canvas.drawCircle(x+w/2,y+h/2,8*ui(),paint);}
        }
    }
    private boolean overlaps(ControllerConfig.Control c){for(ControllerConfig.Control o:config.controls)if(o!=c&&config.shown(o)&&Math.abs(o.x-c.x)<(o.w+c.w)*.32f&&Math.abs(o.y-c.y)<(o.h+c.h)*.32f)return true;return false;}
    private String labelFor(ControllerConfig.Control c){if(!config.labels.equals("PLAYSTATION"))return c.id;switch(c.id){case "A":return "✕";case "B":return "○";case "X":return "□";case "Y":return "△";case "LT":return "L2";case "RT":return "R2";case "LB":return "L1";case "RB":return "R1";default:return c.id;}}
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
        if((a==MotionEvent.ACTION_UP||a==MotionEvent.ACTION_POINTER_UP)&&id==temporaryPointer){release();return true;}
        if(!editing&&a==MotionEvent.ACTION_MOVE&&temporarySource!=null){int i=event.findPointerIndex(temporaryPointer);if(i>=0){temporarySource.move(temporaryPointer,event.getX(i)-left,event.getY(i)-top,now);temporarySource.update(now);if(temporarySource.state.lt<=0){release();return true;}input.temporaryLT=temporarySource.state.lt;}}
        if(!editing&&a==MotionEvent.ACTION_MOVE&&edgePointer>=0){int i=event.findPointerIndex(edgePointer);if(i>=0){float dx=event.getX(i)-left-edgeX,dy=event.getY(i)-top-edgeY;if(Math.abs(dx)>surfaceW*.12f&&Math.abs(dy)<surfaceH*.12f){cycleLayout(dx>0?-1:1);return true;}}}
        if(a==MotionEvent.ACTION_DOWN&&(input.pointerCount()>0||!edits.isEmpty()||temporaryReturn!=null))release();
        // Pointer transitions also carry fresh coordinates for every remaining finger.
        if(!editing&&(a==MotionEvent.ACTION_POINTER_DOWN||a==MotionEvent.ACTION_POINTER_UP||a==MotionEvent.ACTION_UP))
            for(int i=0;i<event.getPointerCount();i++)input.move(event.getPointerId(i),event.getX(i)-left,event.getY(i)-top,now);
        if(a==MotionEvent.ACTION_DOWN||a==MotionEvent.ACTION_POINTER_DOWN){
            if(editing&&y>=0&&y<bar()&&x<3*bar()){history(x>1.5f*bar());return true;}
            if(y>=0&&y<bar()&&x>surfaceW-5.6f*bar()){
                if(x<surfaceW-4*bar()){if(editing)previewEditing();else new ControllerSettings(getContext(),this,actions::connect).quick();return true;}
                release();if(x>surfaceW-1.6f*bar()){if(editing)finishEditing();else if(testMode){testMode=false;invalidate();}else actions.settings();}else if(editing)new ControllerSettings(getContext(),this,actions::connect).palette();else actions.connect();return true;
            }
            if(editing){boolean handle=selected!=null&&Math.hypot(x-(selected.x+selected.w/2)*surfaceW,y-(selected.y+selected.h/2)*surfaceH)<20*ui();ControllerConfig.Control c=handle?selected:input.target(x,y);if(c!=null){selected=c;EditTouch t=new EditTouch(c,x,y);t.resizing=handle;edits.put(id,t);}}
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
            if(editing){EditTouch t=edits.remove(id);if(t!=null){if(!t.moved&&edits.isEmpty())new ControllerSettings(getContext(),this,actions::connect).control(t.c);else if(edits.isEmpty())saveLayout();else for(EditTouch remaining:edits.values()){remaining.sx=remaining.x;remaining.sy=remaining.y;remaining.cx=remaining.c.x;remaining.cy=remaining.c.y;remaining.w=remaining.c.w;remaining.h=remaining.c.h;remaining.moved=true;}}}
            else input.up(id,now);
            if(a==MotionEvent.ACTION_UP)performClick();
        }
        if(editing)state.clear();else{input.update(now);dispatchAction();checkAimLayout(now);}actions.changed();invalidate();return true;
    }
    private void checkAimLayout(long now){
        if(temporaryReturn!=null||config.aimLayout.isEmpty()||config.aimLayout.equals(profiles.active)||!profiles.ids().contains(config.aimLayout)||state.lt<=0)return;
        for(Map.Entry<Integer,ControllerInput.Pointer> entry:input.activePointers().entrySet()){
            ControllerInput.Pointer p=entry.getValue();String kind=p.control.kind;
            if(!(kind.equals("trigger")&&p.control.bit==0||kind.equals("aim")&&config.aimMode.equals("HOLD")||kind.equals("unified")))continue;
            ControllerInput source=new ControllerInput(config.copy());source.resize(surfaceW,surfaceH);if(!source.down(entry.getKey(),p.startX,p.startY,p.down))continue;source.move(entry.getKey(),p.x,p.y,now);source.update(now);if(source.state.lt<=0)continue;
            String target=config.aimLayout;int id=entry.getKey();release();temporaryReturn=profiles.active;temporaryPointer=id;temporarySource=source;config=profiles.load(target);input.configure(config);input.temporaryLT=source.state.lt;input.update(now);return;
        }
    }
    private void dispatchAction(){String action=input.takeAction();if(action==null)return;int colon=action.indexOf(':');String type=action.substring(0,colon),target=action.substring(colon+1);if(type.equals("NEXT"))cycleLayout(1);else if(type.equals("PREVIOUS"))cycleLayout(-1);else if(type.equals("LAYOUT")&&profiles.ids().contains(target))selectProfile(target);else if(type.equals("PROFILE")&&profiles.games().contains(target))selectProfile(profiles.layouts(target).get(0));}
    private void updateEdits(){
        Set<ControllerConfig.Control> done=new HashSet<>();for(EditTouch t:edits.values()){if(!done.add(t.c))continue;EditTouch pair=null;for(EditTouch p:edits.values())if(p!=t&&p.c==t.c){pair=p;break;}
            if(pair!=null){float start=(float)Math.hypot(pair.sx-t.sx,pair.sy-t.sy);float ratio=(float)Math.hypot(pair.x-t.x,pair.y-t.y)/Math.max(1,start);t.c.w=PadState.clamp(t.w*ratio,.025f,.5f);t.c.h=PadState.clamp(t.h*ratio,.04f,.7f);t.moved=pair.moved=true;}
            else if(t.resizing){t.c.w=PadState.clamp(t.w+2*(t.x-t.sx)/surfaceW,.025f,.5f);t.c.h=PadState.clamp(t.h+2*(t.y-t.sy)/surfaceH,.04f,.7f);t.moved=true;}
            else {t.c.x=t.cx+(t.x-t.sx)/surfaceW;t.c.y=t.cy+(t.y-t.sy)/surfaceH;if(config.snap){t.c.x=Math.round(t.c.x/config.gridSize)*config.gridSize;t.c.y=Math.round(t.c.y*surfaceH/(config.gridSize*surfaceW))*config.gridSize*surfaceW/surfaceH;}}
            t.c.x=PadState.clamp(t.c.x,t.c.w/2,1-t.c.w/2);t.c.y=PadState.clamp(t.c.y,Math.max(bar()/surfaceH+t.c.h/2,t.c.h/2),1-t.c.h/2);
        }
    }
    private void feedback(){if(config.haptics==0)return;Vibrator vibrator=getContext().getSystemService(Vibrator.class);if(vibrator!=null&&vibrator.hasVibrator())vibrator.vibrate(VibrationEffect.createOneShot(config.haptics==3?18:config.haptics==2?12:7,config.haptics==3?180:config.haptics==2?100:45));}
    @Override public boolean performClick(){super.performClick();return true;}
}
