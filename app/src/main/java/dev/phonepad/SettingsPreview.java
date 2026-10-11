package dev.phonepad;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;

/** Input sandbox: reads draft widgets only, never a transport or the live controller. */
public final class SettingsPreview extends View implements android.hardware.SensorEventListener {
    public interface Number {float get(String key,float fallback);}
    public interface Toggle {boolean get(String key,boolean fallback);}
    public interface Choice {int get(String key,int fallback);}
    private final String family;
    private final Number number;private final Toggle toggle;private final Choice choice;
    private final Paint paint=new Paint(3);
    private float x=.55f,y=.4f,originX=.5f,originY=.5f;
    private float velocityX,velocityY,gyroX,gyroY;
    private final GyroFilter gyroFilter=new GyroFilter();
    private android.hardware.SensorManager sensors;private android.hardware.Sensor sensor;private boolean registered;
    private final android.view.ViewTreeObserver.OnScrollChangedListener scroll=this::syncSensor;
    private long gyroAt;
    private final Runnable gyroRest=()->{gyroX=gyroY=0;invalidate();};
    private void syncSensor(){
        if(sensors==null)return;
        boolean wanted=family.contains("gyro")&&toggle.get("enable gyro",false)&&hasWindowFocus()&&getGlobalVisibleRect(new Rect());
        if(wanted==registered)return;
        if(wanted&&sensor!=null)registered=sensors.registerListener(this,sensor,android.hardware.SensorManager.SENSOR_DELAY_GAME,0);
        else{sensors.unregisterListener(this);registered=false;gyroFilter.reset();gyroX=gyroY=0;removeCallbacks(gyroRest);}
    }
    public void onAccuracyChanged(android.hardware.Sensor sensor,int accuracy){}
    public void onSensorChanged(android.hardware.SensorEvent event){
        if(!registered)return;
        long age=android.os.SystemClock.elapsedRealtimeNanos()-event.timestamp;
        if(age<0||age>=100_000_000L||event.timestamp<=gyroAt)return;
        for(int i=0;i<3;i++)if(!Float.isFinite(event.values[i]))return;
        gyroAt=event.timestamp;
        float[] rates=gyroFilter.sample(event.values[0],event.values[1],event.values[2],event.timestamp,number.get("filtering",.35f),number.get("rest dead",.015f));
        int rotation=getDisplay()==null?Surface.ROTATION_0:getDisplay().getRotation();
        float[] camera=GyroFilter.camera(rates[0],rates[1],rotation);gyroX=camera[0];gyroY=camera[1];
        removeCallbacks(gyroRest);postDelayed(gyroRest,Math.max(1,100-age/1_000_000L));invalidate();
    }
    private long moved;private boolean held;private int triggerSide;
    private final Runnable cameraRest=()->{velocityX=velocityY=0;invalidate();};
    private final CameraFireGesture fire=new CameraFireGesture();
    private final GameplayLock lock=new GameplayLock();
    private final Runnable unlock=()->{lock.update(android.os.SystemClock.uptimeMillis());invalidate();};
    public SettingsPreview(Context c,String title,Number number,Toggle toggle,Choice choice){super(c);family=title.toLowerCase(Locale.ROOT);this.number=number;this.toggle=toggle;this.choice=choice;setContentDescription("Interactive "+title+" preview. Drag to inspect output; sends no controller input.");setFocusable(true);lock.reset(true);sensors=c.getSystemService(android.hardware.SensorManager.class);sensor=sensors==null?null:sensors.getDefaultSensor(android.hardware.Sensor.TYPE_GYROSCOPE);}
    private void line(String text,float x,float y){paint.setColor(SettingsScreen.TEXT);paint.setStyle(Paint.Style.FILL);paint.setTextSize(13*getResources().getDisplayMetrics().scaledDensity);float available=Math.max(1,getWidth()-x-8*getResources().getDisplayMetrics().density);if(paint.measureText(text)>available)paint.setTextSize(paint.getTextSize()*available/paint.measureText(text));canvas.drawText(text,x,y,paint);}
    private Canvas canvas;
    @Override protected void onDraw(Canvas c){canvas=c;syncSensor();c.drawColor(SettingsScreen.SURFACE);float d=getResources().getDisplayMetrics().density,w=getWidth(),h=getHeight(),r=Math.min(w*.25f,h*.24f),cx=w*.30f,cy=h*.43f;
        paint.setColor(SettingsScreen.ACCENT);paint.setStrokeWidth(2*d);paint.setStyle(Paint.Style.STROKE);
        float ix=(x-originX)*2,iy=(y-originY)*2;
        if(family.contains("haptic")){
            float heavy=x,light=1-y,mix=new RumbleState(Math.round(heavy*255),Math.round(light*255),500,0,"Preview").mix(1,number.get("heavy mix",1),number.get("light mix",1),number.get("overall intensity",.7f),toggle.get("quiet",false));
            if(!toggle.get("haptics master",true))mix=0;bar(c,20*d,50*d,w*.7f,heavy,"Heavy");bar(c,20*d,88*d,w*.7f,light,"Light");bar(c,20*d,126*d,w*.7f,mix,"Single actuator mix");paint.setStyle(Paint.Style.STROKE);paint.setColor(SettingsScreen.ACCENT);Path wave=new Path();for(int i=0;i<=100;i++){float wx=20*d+i*(w-40*d)/100,wy=h-48*d+(float)Math.sin(i*.45f)*mix*14*d;if(i==0)wave.moveTo(wx,wy);else wave.lineTo(wx,wy);}c.drawPath(wave,paint);
        }else if(family.contains("lock")){
            c.drawRect(15*d,45*d,w-15*d,100*d,paint);line(toggle.get("prefer game mode",false)&&lock.locked?"Navigation hidden during play":"Paused — navigation available",25*d,75*d);line("Hold the edge handle · movement cancels",20*d,130*d);
        }else if(family.contains("trigger")||family.contains("gas / brake")){
            boolean unified=family.contains("unified"),racing=family.contains("gas");boolean vertical=unified?choice.get("orientation",0)==0:choice.get("mode",0)==1;
            float delta=vertical?y-originY:x-originX;if(toggle.get("invert direction",false))delta=-delta;
            float travel=number.get(unified?"activation distance":"travel fraction",.18f),dead=number.get("dead zone",.08f),gain=number.get("sensitivity",1);
            float value=ControllerInput.integratedTrigger(delta/Math.max(.04f,travel),dead,gain,new String[]{"LINEAR","SOFT","AGGRESSIVE"}[Math.min(2,choice.get("response",0))]);
            float lt=delta>0?value:0,rt=delta<0?value:0;
            if(!unified&&!racing){int mode=choice.get("mode",0);float motion=mode==1?y-originY:triggerSide==0?x-originX:originX-x;if(toggle.get(triggerSide==0?"reverse lt":"reverse rt",false))motion=-motion;float v=held?(mode==2?1:Math.max(0,Math.min(1,motion/travel*gain))):0;lt=triggerSide==0?v:0;rt=triggerSide==1?v:0;}
            if(unified&&!held){lt=rt=0;}
            if(racing){lt=ControllerInput.integratedTrigger(iy,.12f,gain,"LINEAR")*(iy>0?1:0);rt=ControllerInput.integratedTrigger(iy,.12f,gain,"LINEAR")*(iy<0?1:0);}
            if(!unified&&!racing){
                int mode=choice.get("mode",0);for(int side=0;side<2;side++){float tx=w*(side==0?.28f:.72f),ty=55*d;boolean reverse=toggle.get(side==0?"reverse lt":"reverse rt",false);line((side==0?"LT  ":"RT  ")+(mode==2?"●":mode==1?(reverse?"↑":"↓"):(side==0?!reverse:reverse)?"→":"←"),tx-20*d,25*d);paint.setStyle(Paint.Style.STROKE);c.drawRoundRect(tx-18*d,ty,tx+18*d,h*.48f,8*d,8*d,paint);paint.setStyle(Paint.Style.FILL);paint.setColor((SettingsScreen.ACCENT&0x00ffffff)|0x44000000);float region=Math.min(h*.48f-ty,travel*h);c.drawRect(tx-18*d,reverse?h*.48f-region:ty,tx+18*d,reverse?h*.48f:ty+region,paint);paint.setColor(SettingsScreen.ACCENT);}
            }
            c.drawLine(w*.2f,h*.3f,w*.8f,h*.3f,paint);c.drawCircle(x*w,y*h*.6f+20*d,8*d,paint);if(unified||racing)line(toggle.get("invert direction",false)?"← / ↑ LT     origin     RT ↓ / →":"← / ↑ RT     origin     LT ↓ / →",20*d,30*d);bar(c,20*d,h*.55f,w*.6f,lt,"LT");bar(c,20*d,h*.75f,w*.6f,rt,"RT");
            if(racing)line(String.format(Locale.ROOT,"LS X %.2f · physical Y %.2f · HID Y %.2f",ix,iy,toggle.get("preserve",false)?iy*(toggle.get("preview invert",false)?-1:1):0),20*d,h-12*d);
        }else if(family.contains("fps")){
            float gainX=number.get("camera x",1),gainY=number.get("camera y",1),curve=number.get("exponent",1),aim=toggle.get("preview aim",false)?number.get("while lt",1):1;
            float rx=Math.copySign(Math.min(1,(float)Math.pow(Math.abs(velocityX),curve)*gainX),velocityX)*aim,ry=Math.copySign(Math.min(1,(float)Math.pow(Math.abs(velocityY),curve)*gainY),velocityY)*aim;
            c.drawRect(20*d,35*d,w-20*d,h-65*d,paint);c.drawCircle(x*w,y*h,8*d,paint);if(fire.state()==CameraFireGesture.State.ARMED)c.drawCircle(x*w,y*h,number.get("tap movement",2.5f)/100*Math.min(w,h),paint);line(fire.firing(1)?"Fire triggered · drag to move camera":fire.state()==CameraFireGesture.State.ARMED?"Tap detected · press nearby and hold":held?"Camera movement":"Place finger here · tap, then hold",25*d,25*d);line(String.format(Locale.ROOT,"Camera X %+.2f  Y %+.2f · Fire %s",rx,ry,fire.firing(1)?"100%":"0%"),20*d,h-35*d);
        }else if(family.contains("gyro")){
            float gx=gyroX*number.get("horizontal sensitivity",.6f),gy=gyroY*number.get("vertical sensitivity",.6f);
            if(toggle.get("invert horizontal",false))gx=-gx;if(toggle.get("invert vertical",false))gy=-gy;
            c.save();c.rotate(PadState.clamp(gx,-1,1)*30,cx,cy);c.drawRoundRect(cx-r*.5f,cy-r,cx+r*.5f,cy+r,8*d,8*d,paint);c.restore();
            line(!toggle.get("enable gyro",false)?"Motion aiming is off":sensor==null?"No gyroscope available":registered?"Live sensor · tilt the phone":"Sensor paused",15*d,25*d);
            line(String.format(Locale.ROOT,"Preview RS %+.2f / %+.2f · LT gate bypassed",PadState.clamp(gx,-1,1),PadState.clamp(gy,-1,1)),15*d,h-40*d);
        }else{
            float dead=number.get("dead zone",.08f);if(dead<0)dead=.08f;float gain=number.get("sensitivity",1);if(gain<0)gain=1;float[] output=PadState.stick(ix,iy,dead,1);float length=(float)Math.hypot(output[0],output[1]);if(length>0){float scale=Math.min(1,(float)Math.pow(length,number.get("exponent",1))*gain)/length;output[0]*=scale;output[1]*=scale;}
            if(toggle.get("invert x",false))output[0]=-output[0];if(toggle.get("invert y",false))output[1]=-output[1];c.drawCircle(cx,cy,r,paint);paint.setColor((SettingsScreen.ACCENT&0x00ffffff)|0x66000000);paint.setStyle(Paint.Style.FILL);c.drawCircle(cx,cy,r*dead,paint);paint.setStyle(Paint.Style.STROKE);paint.setColor(SettingsScreen.ACCENT);c.drawCircle(cx+ix*r,cy+iy*r,7*d,paint);c.drawLine(cx,cy,cx+output[0]*r,cy+output[1]*r,paint);
            Path curve=new Path();for(int n=0;n<=100;n++){float v=n/100f,o=(float)Math.pow(Math.max(0,(v-dead)/(1-dead)),number.get("exponent",1))*gain;float px=w*.60f+v*w*.32f,py=cy+r-Math.min(1,o)*r*2;if(n==0)curve.moveTo(px,py);else curve.lineTo(px,py);}c.drawPath(curve,paint);line(String.format(Locale.ROOT,"Input %+.2f / %+.2f → output %+.2f / %+.2f",ix,iy,output[0],output[1]),15*d,h-40*d);
        }
        line(family.contains("gyro")?"Tilt to test · no game input is sent":"Drag to test · your game stays paused",15*d,h-12*d);
    }
    private void bar(Canvas c,float x,float y,float width,float value,String label){paint.setStyle(Paint.Style.FILL);paint.setColor(SettingsScreen.ACCENT);c.drawRect(x,y,x+width*Math.max(0,Math.min(1,value)),y+12*getResources().getDisplayMetrics().density,paint);line(label+" "+Math.round(value*100)+"%",x,y-4);}
    @Override public boolean onTouchEvent(MotionEvent e){long now=e.getEventTime();float px=e.getX()/Math.max(1,getWidth()),py=e.getY()/Math.max(1,getHeight());int a=e.getActionMasked();float density=getResources().getDisplayMetrics().density;boolean inCamera=e.getX()>=20*density&&e.getX()<=getWidth()-20*density&&e.getY()>=35*density&&e.getY()<=getHeight()-65*density;
        if(a==MotionEvent.ACTION_DOWN){held=true;triggerSide=px<.5f?0:1;getParent().requestDisallowInterceptTouchEvent(true);if(choice.get("left stick",0)==1||toggle.get("origin follows",false)||family.contains("independent trigger")){originX=px;originY=py;}else{originX=originY=.5f;}if(toggle.get("same-thumb",false)&&(!family.contains("fps")||inCamera))fire.down(1,"preview",e.getX(),e.getY(),now,number.get("tap interval",280),number.get("tap movement",2.5f)/100*Math.min(getWidth(),getHeight()));else fire.reset();lock.reset(toggle.get("prefer game mode",false));lock.down(1,px,py,now);postDelayed(unlock,1400);velocityX=velocityY=0;}
        else if(a==MotionEvent.ACTION_MOVE){float keep=number.get("smoothing",0);velocityX=velocityX*keep+(px-x)*getWidth()/Math.max(1,now-moved)*.6f*(1-keep);velocityY=velocityY*keep+(py-y)*getHeight()/Math.max(1,now-moved)*.6f*(1-keep);fire.move(1,e.getX(),e.getY());if(px<0||px>1||py<0||py>1||family.contains("fps")&&!inCamera)fire.stopFire();removeCallbacks(cameraRest);postDelayed(cameraRest,65);lock.move(1,px,py,.03f);}
        else if(a==MotionEvent.ACTION_UP){held=false;velocityX=velocityY=0;fire.up(1,now,number.get("tap duration",180),number.get("tap movement",2.5f)/100*Math.min(getWidth(),getHeight()));lock.up(1);removeCallbacks(unlock);performClick();}else if(a==MotionEvent.ACTION_CANCEL){held=false;velocityX=velocityY=0;fire.reset();lock.cancel();removeCallbacks(unlock);}
        x=px;y=py;moved=now;invalidate();return true;
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();getViewTreeObserver().addOnScrollChangedListener(scroll);}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);syncSensor();}
    @Override protected void onDetachedFromWindow(){removeCallbacks(gyroRest);gyroX=gyroY=0;removeCallbacks(unlock);removeCallbacks(cameraRest);getViewTreeObserver().removeOnScrollChangedListener(scroll);if(sensors!=null)sensors.unregisterListener(this);registered=false;super.onDetachedFromWindow();}
}
