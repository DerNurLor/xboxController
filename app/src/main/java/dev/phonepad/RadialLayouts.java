package dev.phonepad;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.List;
import java.util.function.IntConsumer;

/** Bounded eight-sector selector with an explicit neutral center. */
public final class RadialLayouts extends View {
    private final List<String> names;private final IntConsumer selected;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public RadialLayouts(Context context,List<String> names,IntConsumer selected){super(context);this.names=names;this.selected=selected;setMinimumHeight((int)(250*getResources().getDisplayMetrics().density));setContentDescription("Radial layout selector");}
    @Override protected void onDraw(Canvas canvas){float x=getWidth()/2f,y=getHeight()/2f,r=Math.min(x,y)*.90f;canvas.drawColor(0xff0c1118);for(int i=0;i<names.size();i++){float start=-90+i*360f/names.size();paint.setColor(i%2==0?0xff263b47:0xff334a46);canvas.drawArc(x-r,y-r,x+r,y+r,start,360f/names.size()-2,true,paint);double a=Math.toRadians(start+180f/names.size());paint.setColor(0xffe6eef4);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.max(14,r*.10f));String name=names.get(i);if(name.length()>17)name=name.substring(0,16)+"…";canvas.drawText(name,x+(float)Math.cos(a)*r*.68f,y+(float)Math.sin(a)*r*.68f,paint);}paint.setColor(0xff0c1118);canvas.drawCircle(x,y,r*.22f,paint);}
    @Override public boolean onTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_UP&&!names.isEmpty()){float x=event.getX()-getWidth()/2f,y=event.getY()-getHeight()/2f,r=Math.min(getWidth(),getHeight())*.45f;double distance=Math.hypot(x,y);if(distance>r*.22f&&distance<=r){double angle=(Math.toDegrees(Math.atan2(y,x))+450)%360;selected.accept(Math.min(names.size()-1,(int)(angle/360*names.size())));}performClick();}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
