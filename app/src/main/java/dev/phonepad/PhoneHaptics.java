package dev.phonepad;

import android.content.*;
import android.os.*;
import android.media.AudioAttributes;
import android.provider.Settings;

/** One actuator sink; finite effects and independent local/host leases. Main thread only. */
public final class PhoneHaptics {
    private final Context context;
    private final Vibrator vibrator;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final SharedPreferences prefs;
    private RumbleState host=new RumbleState(0,0,0,0,"None");
    private RumbleState lastHost=host;
    private long hostCount,dropped,coalesced,starts,stops,lastLog;
    private long localEnd,hardwareEnd,lastWrite;
    private float local;
    private int amplitude;
    private boolean active;
    public boolean enabled,hostEnabled,localEnabled,quiet,texture;
    public float heavyWeight,lightWeight,strength;
    private final Runnable refresh=this::render;
    private final AudioAttributes game=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build();
    private final AudioAttributes touch=new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build();
    public PhoneHaptics(Context context){
        this.context=context;prefs=context.getSharedPreferences("haptics.v1",0);
        VibratorManager manager=Build.VERSION.SDK_INT>=31?context.getSystemService(VibratorManager.class):null;
        vibrator=manager==null?context.getSystemService(Vibrator.class):manager.getDefaultVibrator();load();
    }
    public void load(){enabled=prefs.getBoolean("enabled",true);hostEnabled=prefs.getBoolean("host",true);localEnabled=prefs.getBoolean("local",true);quiet=prefs.getBoolean("quiet",false);texture=prefs.getBoolean("texture",false);heavyWeight=prefs.getFloat("heavy",1);lightWeight=prefs.getFloat("light",1);strength=prefs.getFloat("strength",.7f);stop();}
    public void save(){prefs.edit().putBoolean("enabled",enabled).putBoolean("host",hostEnabled).putBoolean("local",localEnabled).putBoolean("quiet",quiet).putBoolean("texture",texture).putFloat("heavy",heavyWeight).putFloat("light",lightWeight).putFloat("strength",strength).apply();stop();}
    public void active(boolean value){active=value;if(!value)stop();}
    public void host(int low,int high,int millis,String source){
        RumbleState received=new RumbleState(low,high,millis,SystemClock.uptimeMillis(),source);lastHost=received;hostCount++;
        if(!active||!enabled||!hostEnabled){dropped++;return;}
        if(received.active(received.received)&&received.received-lastWrite<25)coalesced++;
        host=received;render();
    }
    public void local(int level){
        if(!active||!enabled||!localEnabled||Settings.System.getInt(context.getContentResolver(),Settings.System.HAPTIC_FEEDBACK_ENABLED,1)==0)return;
        local=level==3?.7f:level==2?.4f:.18f;localEnd=SystemClock.uptimeMillis()+(level==3?24:16);render();
    }
    public void test(boolean heavy){android.util.Log.i("PhonePadRumble","LOCAL synthetic channel test (not host feedback)");stop();local=0;host=new RumbleState(heavy?160:0,heavy?0:160,350,SystemClock.uptimeMillis(),"Synthetic test");render();}
    public void testBoth(){android.util.Log.i("PhonePadRumble","LOCAL synthetic both-channel test (not host feedback)");stop();host=new RumbleState(160,160,350,SystemClock.uptimeMillis(),"Synthetic test");render();}
    public void stopHost(){host=new RumbleState(0,0,0,SystemClock.uptimeMillis(),"Stopped");render();}
    public void stop(){host=new RumbleState(0,0,0,SystemClock.uptimeMillis(),"Stopped");local=0;localEnd=0;if(amplitude!=0){stops++;android.util.Log.d("PhonePadRumble","STOP lifecycle");}amplitude=0;hardwareEnd=0;handler.removeCallbacks(refresh);if(vibrator!=null)vibrator.cancel();}
    public String status(){return (vibrator==null||!vibrator.hasVibrator()?"No vibrator":vibrator.hasAmplitudeControl()?"Amplitude control available":"On/off fallback · intensity uses duty cycle")+"\nHeavy "+Math.round(lastHost.heavy*100)+"% · Light "+Math.round(lastHost.light*100)+"% · Requested mix "+amplitude+"/255\nSource: "+lastHost.source+" · updates "+hostCount+" · last update "+lastHost.received+" ms uptime · duration "+(lastHost.expires-lastHost.received)+" ms\nDropped "+dropped+" · coalesced "+coalesced+" · starts "+starts+" · stops "+stops;}
    private void render(){
        handler.removeCallbacks(refresh);long now=SystemClock.uptimeMillis();
        if(vibrator==null||!vibrator.hasVibrator()||!enabled){amplitude=0;return;}
        float h=host.mix(now,heavyWeight,lightWeight,strength,quiet);
        float mixed=Math.max(h,now<localEnd?local*strength*(quiet?.4f:1):0);
        int target=Math.round(mixed*255);
        if(target==0){if(amplitude!=0){vibrator.cancel();stops++;android.util.Log.d("PhonePadRumble","STOP / expiry source="+host.source);}amplitude=0;return;}
        long end=Math.max(host.active(now)?host.expires:now,now<localEnd?localEnd:now);
        // Bound the hardware lease even if the UI thread is interrupted. Coalesce host floods.
        if(now-lastWrite>=25&&(Math.abs(target-amplitude)>=8||hardwareEnd-now<=25)){
            int duration=(int)Math.max(1,Math.min(120,end-now));
            if(vibrator.hasAmplitudeControl()){
                if(texture&&h>0&&duration>=40){long on=Math.max(10,Math.round(duration*.7));vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,on,duration-on},new int[]{0,target,0},-1),game);}
                else vibrator.vibrate(VibrationEffect.createOneShot(duration,target),h>0?game:touch);
            }else{long on=Math.max(1,Math.round(duration*mixed));vibrator.vibrate(VibrationEffect.createWaveform(new long[]{0,on,Math.max(1,duration-on)},-1),h>0?game:touch);}
            if(amplitude==0){starts++;android.util.Log.d("PhonePadRumble","START source="+host.source+" heavy="+host.heavy+" light="+host.light);}
            amplitude=target;hardwareEnd=now+duration;lastWrite=now;
        }
        if(now-lastLog>=1000){android.util.Log.d("PhonePadRumble",status());lastLog=now;}
        handler.postDelayed(refresh,Math.max(1,Math.min(25,end-now)));
    }
}
