package dev.phonepad;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Read-only layout preview; never constructs input or touches stored configuration. */
public final class LayoutPreview extends View {
    private final ControllerConfig config;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    public LayoutPreview(Context context,ControllerConfig config){super(context);this.config=config.copy();setMinimumHeight((int)(200*getResources().getDisplayMetrics().density));setContentDescription("Preview of "+config.name);}
    @Override protected void onDraw(Canvas canvas){
        canvas.drawColor(0xff0c1118);float width=getWidth(),height=getHeight();
        for(ControllerConfig.Control c:config.controls)if(config.shown(c)){
            float x=c.x*width,y=c.y*height,w=c.w*width,h=c.h*height;
            paint.setColor(0xff334a46);paint.setAlpha(Math.round(c.opacity*255));
            if(c.kind.equals("stick")&&!config.mode(c).equals("TOUCHPAD")||c.id.matches("[ABXY]"))canvas.drawCircle(x,y,Math.min(w,h)/2,paint);
            else canvas.drawRoundRect(x-w/2,y-h/2,x+w/2,y+h/2,10,10,paint);
            paint.setColor(0xffe6eef4);paint.setTextAlign(Paint.Align.CENTER);paint.setTextSize(Math.max(12,height*.045f));canvas.drawText(c.id,x,y+4,paint);
        }
    }
}
