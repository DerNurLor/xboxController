package dev.phonepad;

import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.view.MotionEvent;

/** Read-only layout preview; never constructs input or touches stored configuration. */
public final class LayoutPreview extends View {
    private final ControllerConfig config;private final AppAppearance appearance;
    private ControllerInput sandbox;
    private final Runnable tick=new Runnable(){public void run(){if(sandbox==null||!isAttachedToWindow())return;sandbox.update(android.os.SystemClock.uptimeMillis());invalidate();if(sandbox.pointerCount()>0||sandbox.needsFrame())postDelayed(this,16);}};
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public LayoutPreview(Context context,ControllerConfig config){super(context);appearance=new AppAppearance(context);this.config=config.copy();setMinimumHeight((int)(200*getResources().getDisplayMetrics().density));setContentDescription("Preview of "+config.name);}
    public LayoutPreview(Context context,ControllerConfig config,boolean interactive){this(context,config);if(interactive)sandbox=new ControllerInput(this.config);}
    @Override protected void onSizeChanged(int w,int h,int ow,int oh){if(sandbox!=null)sandbox.resize(w,h);}
    @Override public boolean onTouchEvent(MotionEvent e){if(sandbox==null)return false;long now=e.getEventTime();int action=e.getActionMasked(),n=e.getActionIndex(),id=e.getPointerId(n);if(action==MotionEvent.ACTION_CANCEL)sandbox.release();else{if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN)sandbox.down(id,e.getX(n),e.getY(n),now);for(int i=0;i<e.getPointerCount();i++)sandbox.move(e.getPointerId(i),e.getX(i),e.getY(i),now);if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP)sandbox.up(id,now);sandbox.update(now);}removeCallbacks(tick);post(tick);invalidate();return true;}
    @Override protected void onDetachedFromWindow(){removeCallbacks(tick);if(sandbox!=null)sandbox.release();super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(appearance.background);float width=getWidth(),height=getHeight();
        for(ControllerConfig.Control c:config.controls)if(config.shown(c)&&!config.backgroundCamera(c)){
            float x=c.x*width,y=c.y*height,w=c.w*width,h=c.h*height;
            paint.setColor(sandbox!=null&&sandbox.active(c)?appearance.pressed:appearance.control);paint.setAlpha(Math.round(c.opacity*255));
            if(c.kind.equals("stick")&&!config.mode(c).equals("TOUCHPAD")||c.id.matches("[ABXY]"))canvas.drawCircle(x,y,Math.min(w,h)/2,paint);
            else canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,10,10,paint);
            paint.setColor(appearance.text);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.max(12,height*.045f));if(!ControllerSymbols.draw(canvas,paint,c,config.labels.equals("PLAYSTATION"),x,y,Math.min(w,h)*.25f,ControllerSymbols.faceColor(c.id,config.labels.equals("PLAYSTATION"),appearance.light)))canvas.drawText(ControllerSymbols.label(c,config.labels.equals("PLAYSTATION")),x,y+4,paint);
        }
        if(sandbox!=null){paint.setColor(appearance.text);paint.setTextSize(12*getResources().getDisplayMetrics().density);paint.setTextAlign(Paint.Align.CENTER);PadState s=sandbox.state;canvas.drawText(String.format(java.util.Locale.ROOT,"Left %+.0f%% / %+.0f%%   Right %+.0f%% / %+.0f%%   LT %.0f%%   RT %.0f%%",s.lx*100,s.ly*100,s.rx*100,s.ry*100,s.lt*100,s.rt*100),width/2,height-8*getResources().getDisplayMetrics().density,paint);}
    }
}
