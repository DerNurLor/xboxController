package dev.phonepad;

import android.content.*;
import android.graphics.*;
import android.view.*;
import java.util.*;

/** Scalable, hit-tested devices. The same geometry draws and receives each input. */
final class DeviceDiagram extends View {
    interface Listener {void select(String token);void drop(String token,String target);}
    private static final class Part {
        final String token,label;final RectF box;final boolean round;Path outline;Region region;
        Part(String t,String l,float x,float y,float w,float h,boolean r){token=t;label=l;box=new RectF(x,y,x+w,y+h);round=r;}
    }
    private final ArrayList<Part> parts=new ArrayList<>();
    private final Paint paint=new Paint(3);
    private final Listener listener;
    private final String category;
    private Set<String> mapped=Collections.emptySet(),available;
    private String selected="",hover="";
    private float scale,ox,oy,downX,downY;
    private Part pressed;
    private boolean dragging,panning;
    private float zoom=1,panX,panY,panTouchX,panTouchY;
    private final ScaleGestureDetector zoomDetector;
    private final Runnable drag=()->beginDrag();
    DeviceDiagram(Context c,String category,Listener listener){super(c);this.category=category;this.listener=listener;setFocusable(true);setContentDescription(category+" input diagram. Pinch to zoom, two fingers to pan. Hold a key to drag it.");
        zoomDetector=new ScaleGestureDetector(c,new ScaleGestureDetector.SimpleOnScaleGestureListener(){public boolean onScale(ScaleGestureDetector detector){zoom=Math.max(1,Math.min(4,zoom*detector.getScaleFactor()));invalidate();return true;}});
        if(category.equals("KEYBOARD"))keyboard();else if(category.equals("MOUSE"))mouse();else controller();
        setOnDragListener((v,e)->{
            if(e.getAction()==DragEvent.ACTION_DRAG_STARTED)return e.getLocalState() instanceof String&&((String)e.getLocalState()).startsWith("target:");
            Part p=hit(e.getX(),e.getY());hover=p==null?"":p.token;invalidate();
            if(e.getAction()==DragEvent.ACTION_DROP&&p!=null){listener.drop(p.token,(String)e.getLocalState());hover="";return true;}
            if(e.getAction()==DragEvent.ACTION_DRAG_ENDED||e.getAction()==DragEvent.ACTION_DRAG_EXITED){hover="";invalidate();}return true;
        });
    }
    void state(String selected,Set<String> mapped,Set<String> available){this.selected=selected;this.mapped=mapped;this.available=available;invalidate();}
    Set<String> tokens(){LinkedHashSet<String> out=new LinkedHashSet<>();for(Part p:parts)out.add(p.token);return out;}
    private void key(String token,String label,float x,float y,float w,float h){parts.add(new Part(token,label,x,y,w,h,false));}
    private void circle(String token,String label,float x,float y,float d){parts.add(new Part(token,label,x,y,d,d,true));}
    private void row(float y,String[] names,float[] widths){float x=16;for(int i=0;i<names.length;i++){float w=widths==null?44:widths[i];key("KEY_"+names[i],keyName(names[i]),x,y,w-4,44);x+=w;}}
    private void keyboard(){
        String[] f={"ESC","F1","F2","F3","F4","F5","F6","F7","F8","F9","F10","F11","F12"};
        float x=16;for(int i=0;i<f.length;i++){if(i==1||i==5||i==9)x+=18;key("KEY_"+f[i],keyName(f[i]),x,28,42,40);x+=46;}
        row(90,new String[]{"GRAVE","1","2","3","4","5","6","7","8","9","0","MINUS","EQUAL","BACKSPACE"},new float[]{44,44,44,44,44,44,44,44,44,44,44,44,44,88});
        row(140,new String[]{"TAB","Q","W","E","R","T","Y","U","I","O","P","LEFTBRACE","RIGHTBRACE","BACKSLASH"},new float[]{66,44,44,44,44,44,44,44,44,44,44,44,44,66});
        row(190,new String[]{"CAPSLOCK","A","S","D","F","G","H","J","K","L","SEMICOLON","APOSTROPHE","ENTER"},new float[]{77,44,44,44,44,44,44,44,44,44,44,44,99});
        row(240,new String[]{"SHIFT","Z","X","C","V","B","N","M","COMMA","DOT","SLASH","RIGHTSHIFT"},new float[]{99,44,44,44,44,44,44,44,44,44,44,121});
        row(290,new String[]{"CTRL","META","ALT","SPACE","RIGHTALT","RIGHTMETA","RIGHTCTRL"},new float[]{66,55,55,308,55,55,66});
        String[][] nav={{"PRINTSCREEN","SCROLLLOCK","PAUSE"},{"INSERT","HOME","PAGEUP"},{"DELETE","END","PAGEDOWN"}};
        for(int r=0;r<3;r++)for(int col=0;col<3;col++)key("KEY_"+nav[r][col],keyName(nav[r][col]),690+col*45,r==0?28:40+r*50,41,44);
        key("KEY_UP","↑",735,240,41,44);key("KEY_LEFT","←",690,290,41,44);key("KEY_DOWN","↓",735,290,41,44);key("KEY_RIGHT","→",780,290,41,44);
        String[][] num={{"NUMLOCK","KPSLASH","KPASTERISK","KPMINUS"},{"KP7","KP8","KP9","KPPLUS"},{"KP4","KP5","KP6"},{"KP1","KP2","KP3","KPENTER"}};
        for(int r=0;r<4;r++)for(int col=0;col<num[r].length;col++)key("KEY_"+num[r][col],keyName(num[r][col]),842+col*37,90+r*50,33,(num[r][col].equals("KPPLUS")||num[r][col].equals("KPENTER"))?94:44);
        key("KEY_KP0","0",842,290,70,44);key("KEY_KPDOT",".",916,290,33,44);
    }
    static String keyName(String s){switch(s){case "BACKSPACE":return "⌫";case "CAPSLOCK":return "Caps";case "ENTER":return "Enter";case "RIGHTSHIFT":case "SHIFT":return "Shift";case "RIGHTCTRL":case "CTRL":return "Ctrl";case "RIGHTALT":case "ALT":return "Alt";case "RIGHTMETA":case "META":return "◇";case "LEFTBRACE":return "[";case "RIGHTBRACE":return "]";case "BACKSLASH":return "\\";case "SEMICOLON":return ";";case "APOSTROPHE":return "'";case "GRAVE":return "`";case "MINUS":case "KPMINUS":return "−";case "EQUAL":return "=";case "COMMA":return ",";case "DOT":case "KPDOT":return ".";case "SLASH":case "KPSLASH":return "/";case "KPASTERISK":return "*";case "KPPLUS":return "+";case "KPENTER":return "Enter";case "PRINTSCREEN":return "PrtSc";case "SCROLLLOCK":return "ScrLk";case "PAGEUP":return "PgUp";case "PAGEDOWN":return "PgDn";case "NUMLOCK":return "Num";case "SPACE":return "Space";case "UP":return "↑";case "DOWN":return "↓";case "LEFT":return "←";case "RIGHT":return "→";default:return s.startsWith("KP")?s.substring(2):s.length()>2?s.substring(0,1)+s.substring(1).toLowerCase(Locale.ROOT):s;}}
    private void mouse(){
        key("MOUSE_LEFT","Left",385,75,102,145);key("MOUSE_RIGHT","Right",519,75,102,145);key("MOUSE_MIDDLE","●",492,98,23,76);
        key("MOUSE_BACK","4",360,229,25,52);key("MOUSE_FORWARD","5",360,167,25,52);
        key("WHEEL_UP","Scroll ↑",702,104,155,43);key("WHEEL_DOWN","Scroll ↓",702,158,155,43);
        Path left=new Path();left.moveTo(489,52);left.cubicTo(420,52,379,100,376,210);left.lineTo(486,222);left.close();
        Path right=new Path();right.moveTo(520,52);right.cubicTo(589,52,630,100,633,210);right.lineTo(523,222);right.close();
        for(Part part:parts)if(part.token.equals("MOUSE_LEFT")||part.token.equals("MOUSE_RIGHT")){part.outline=part.token.equals("MOUSE_LEFT")?left:right;part.region=new Region();part.region.setPath(part.outline,new Region(0,0,1000,430));}
        key("MOUSE_MOVE","Pointer",400,260,210,85);
        key("MOUSE_Y_NEG","↑",183,202,52,48);key("MOUSE_X_NEG","←",126,256,52,48);key("MOUSE_X_POS","→",240,256,52,48);key("MOUSE_Y_POS","↓",183,310,52,48);
    }
    private void controller(){
        key("AXIS_LT_POS","LT",230,16,125,45);key("AXIS_RT_POS","RT",645,16,125,45);
        key("PAD_LB","LB",214,69,160,34);key("PAD_RB","RB",626,69,160,34);
        stick(330,183,"L");stick(598,283,"R");
        circle("PAD_Y","Y",704,116,51);circle("PAD_X","X",649,166,51);circle("PAD_B","B",759,166,51);circle("PAD_A","A",704,216,51);
        circle("PAD_HOME","◇",484,107,34);key("PAD_VIEW","▱",438,161,45,34);key("PAD_MENU","☰",520,161,45,34);
        key("AXIS_HATY_NEG","↑",430,240,38,38);key("AXIS_HATX_NEG","←",390,280,38,38);key("AXIS_HATX_POS","→",470,280,38,38);key("AXIS_HATY_POS","↓",430,320,38,38);
        // Digital trigger events are distinct from analog trigger travel on some controllers.
        key("PAD_LT","LT click",24,22,130,35);key("PAD_RT","RT click",845,22,130,35);
    }
    private void stick(float x,float y,String side){circle("PAD_"+side+"3",side+"3",x-22,y-22,44);key("AXIS_"+side+"Y_NEG","↑",x-20,y-73,40,38);key("AXIS_"+side+"Y_POS","↓",x-20,y+35,40,38);key("AXIS_"+side+"X_NEG","←",x-73,y-19,38,38);key("AXIS_"+side+"X_POS","→",x+35,y-19,38,38);}
    @Override protected void onDraw(Canvas c){super.onDraw(c);float designH=category.equals("KEYBOARD")?366:430;scale=Math.min(getWidth()/1000f,getHeight()/designH)*zoom;panX=Math.max(-Math.max(0,(1000*scale-getWidth())/2),Math.min(Math.max(0,(1000*scale-getWidth())/2),panX));panY=Math.max(-Math.max(0,(designH*scale-getHeight())/2),Math.min(Math.max(0,(designH*scale-getHeight())/2),panY));ox=(getWidth()-1000*scale)/2+panX;oy=(getHeight()-designH*scale)/2+panY;c.save();c.translate(ox,oy);c.scale(scale,scale);
        if(category.equals("KEYBOARD")){paint.setColor(EditorStyle.PANEL);c.drawRoundRect(4,14,996,348,12,12,paint);}
        else if(category.equals("MOUSE"))drawMouse(c);else drawController(c);
        for(Part p:parts){boolean active=p.token.equals(selected)||p.token.equals(hover),isMapped=mapped.contains(p.token);paint.setStyle(Paint.Style.FILL);paint.setColor(active?EditorStyle.TEAL:isMapped?EditorStyle.BLUE:EditorStyle.SURFACE);if(p.outline!=null)c.drawPath(p.outline,paint);else if(p.round)c.drawOval(p.box,paint);else c.drawRoundRect(p.box,p.token.equals("MOUSE_MIDDLE")?11:7,p.token.equals("MOUSE_MIDDLE")?11:7,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(active?2.5f:1);paint.setColor(active||isMapped?EditorStyle.TEAL:EditorStyle.LINE);if(p.outline!=null)c.drawPath(p.outline,paint);else if(p.round)c.drawOval(p.box,paint);else c.drawRoundRect(p.box,p.token.equals("MOUSE_MIDDLE")?11:7,p.token.equals("MOUSE_MIDDLE")?11:7,paint);paint.setStyle(Paint.Style.FILL);
            boolean missing=available!=null&&!available.contains(p.token);paint.setColor(active?EditorStyle.BG:EditorStyle.TEXT);paint.setTypeface(Typeface.create("sans-serif-medium",0));float size=category.equals("KEYBOARD")?Math.min(17,p.box.width()/Math.max(1,p.label.length())*1.7f):p.label.length()<3?22:18;paint.setTextSize(size);paint.setTextAlign(Paint.Align.CENTER);if(!p.token.equals("MOUSE_MIDDLE"))c.drawText(p.label,p.box.centerX(),p.box.centerY()-(paint.ascent()+paint.descent())/2,paint);else{paint.setColor(active?EditorStyle.BG:EditorStyle.MUTED);paint.setStrokeWidth(1);for(float y=p.box.top+12;y<p.box.bottom-8;y+=8)c.drawLine(p.box.left+4,y,p.box.right-4,y,paint);}
            if(isMapped){paint.setColor(EditorStyle.TEAL);c.drawCircle(p.box.right-5,p.box.bottom-5,2,paint);}
        }c.restore();
    }
    private void drawMouse(Canvas c){Path p=new Path();p.moveTo(506,44);p.cubicTo(630,44,660,183,640,300);p.cubicTo(622,434,392,434,373,302);p.cubicTo(352,183,375,44,506,44);body(c,p);paint.setColor(EditorStyle.LINE);paint.setStrokeWidth(2);c.drawLine(506,48,506,224,paint);c.drawLine(390,227,620,227,paint);c.drawLine(506,44,506,0,paint);caption(c,"POINTER",209,182);caption(c,"THUMB BUTTONS",209,113);caption(c,"WHEEL",777,78);paint.setColor(EditorStyle.TEAL);c.drawLine(315,120,365,182,paint);c.drawLine(530,137,692,127,paint);}
    private void drawController(Canvas c){Path p=new Path();p.moveTo(225,104);p.cubicTo(166,168,118,389,206,408);p.cubicTo(260,432,309,357,360,350);p.lineTo(642,350);p.cubicTo(697,357,746,432,800,408);p.cubicTo(888,389,837,164,782,104);p.cubicTo(645,80,359,80,225,104);body(c,p);paint.setColor(0xff08151e);c.drawCircle(330,183,83,paint);c.drawCircle(598,283,83,paint);paint.setColor(EditorStyle.LINE);paint.setStyle(Paint.Style.STROKE);c.drawCircle(330,183,83,paint);c.drawCircle(598,283,83,paint);paint.setStyle(Paint.Style.FILL);}
    private void body(Canvas c,Path p){paint.setColor(EditorStyle.PANEL);paint.setStyle(Paint.Style.FILL);c.drawPath(p,paint);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2);paint.setColor(EditorStyle.TEAL);c.drawPath(p,paint);paint.setStyle(Paint.Style.FILL);}
    private void caption(Canvas c,String label,float x,float y){paint.setTextSize(16);paint.setTextAlign(Paint.Align.CENTER);paint.setColor(EditorStyle.MUTED);c.drawText(label,x,y,paint);}
    private Part hit(float x,float y){if(scale<=0)return null;x=(x-ox)/scale;y=(y-oy)/scale;for(int i=parts.size()-1;i>=0;i--){Part p=parts.get(i);if(p.region!=null?p.region.contains((int)x,(int)y):p.box.contains(x,y))return p;}return null;}
    void zoomBy(float factor){zoom=Math.max(1,Math.min(4,zoom*factor));invalidate();}
    @Override public boolean onTouchEvent(MotionEvent e){zoomDetector.onTouchEvent(e);
        if(e.getPointerCount()>1){removeCallbacks(drag);pressed=null;float x=(e.getX(0)+e.getX(1))/2,y=(e.getY(0)+e.getY(1))/2;if(panning&&e.getActionMasked()==MotionEvent.ACTION_MOVE){panX+=x-panTouchX;panY+=y-panTouchY;}panTouchX=x;panTouchY=y;panning=true;getParent().requestDisallowInterceptTouchEvent(true);invalidate();return true;}
        if(panning){if(e.getActionMasked()==MotionEvent.ACTION_UP||e.getActionMasked()==MotionEvent.ACTION_CANCEL)panning=false;return true;}switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:getParent().requestDisallowInterceptTouchEvent(true);pressed=hit(e.getX(),e.getY());if(pressed==null)return true;downX=e.getX();downY=e.getY();dragging=false;postDelayed(drag,ViewConfiguration.getLongPressTimeout());return true;
        case MotionEvent.ACTION_MOVE:if(pressed!=null&&!dragging&&Math.hypot(e.getX()-downX,e.getY()-downY)>ViewConfiguration.get(getContext()).getScaledTouchSlop()){removeCallbacks(drag);beginDrag();}return true;
        case MotionEvent.ACTION_UP:removeCallbacks(drag);if(pressed!=null&&!dragging){listener.select(pressed.token);performClick();}pressed=null;return true;
        case MotionEvent.ACTION_CANCEL:removeCallbacks(drag);pressed=null;return true;
    }return true;}
    private void beginDrag(){if(pressed==null||dragging)return;dragging=true;String token=pressed.token;View.DragShadowBuilder shadow=new View.DragShadowBuilder(this){public void onProvideShadowMetrics(Point size,Point touch){size.set(160,70);touch.set(80,35);}public void onDrawShadow(Canvas c){Paint p=new Paint(3);p.setColor(EditorStyle.TEAL);c.drawRoundRect(0,0,160,70,10,10,p);p.setColor(EditorStyle.BG);p.setTextSize(20);p.setTextAlign(Paint.Align.CENTER);c.drawText(shortName(token),80,43,p);}};startDragAndDrop(ClipData.newPlainText("Physical input",token),shadow,"input:"+token,0);}
    @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);for(int i=0;i<parts.size();i++)info.addAction(new android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction(0x01010000+i,parts.get(i).token.replace('_',' ')));}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){int index=action-0x01010000;if(index>=0&&index<parts.size()){listener.select(parts.get(index).token);return true;}return super.performAccessibilityAction(action,args);}
    @Override protected void onDetachedFromWindow(){removeCallbacks(drag);super.onDetachedFromWindow();}
    @Override public boolean performClick(){super.performClick();return true;}
    static String shortName(String token){if(token.equals("MOUSE_MOVE"))return "Pointer movement";if(token.startsWith("KEY_"))return keyName(token.substring(4));return token.replace("MOUSE_", "Mouse ").replace("PAD_", "").replace("AXIS_", "").replace("_NEG", " −").replace("_POS", " +").replace('_',' ');}
}
