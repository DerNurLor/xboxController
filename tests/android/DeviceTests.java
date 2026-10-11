package dev.phonepad;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.FrameLayout;
import java.util.*;

/** Exercises the real MotionEvent adapter and Android SharedPreferences/JSON, without host input. */
public final class DeviceTests extends Instrumentation {
    private Bundle arguments;private boolean feedbackOnly;private boolean performanceOnly;private boolean persistenceOnly;private Throwable failure;private int checks,pauses,connections;private PadView view;private final List<byte[]> reports=new ArrayList<>();
    @Override public void onCreate(Bundle args){super.onCreate(args);arguments=args;feedbackOnly="feedback".equals(args.getString("phase"));performanceOnly="performance".equals(args.getString("phase"));persistenceOnly="persistence".equals(args.getString("phase"));start();}
    @Override public void onStart(){Bundle result=new Bundle();try{
        if("chords".equals(arguments.getString("phase"))){chordsPhase(result);return;}
        if("automation".equals(arguments.getString("phase"))){automationPhase(result);return;}if("macro-migration".equals(arguments.getString("phase"))){macroMigrationPhase(result);return;}
        if("geometry".equals(arguments.getString("phase"))){Activity a=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();Thread.sleep(300);runOnMainSync(()->{try{FrameLayout content=a.findViewById(android.R.id.content);exportGeometry((PadView)content.getChildAt(0),a);}catch(Exception ex){failure=ex;}});if(failure!=null)throw new AssertionError(failure);result.putString("stream","\nPASS: current layout geometry exported.\n");finish(Activity.RESULT_OK,result);return;}
        if("bluetooth-feedback".equals(arguments.getString("phase"))){bluetoothFeedback(result);return;}
        if("quick-panel".equals(arguments.getString("phase"))){quickPanelPhase(result);return;}
        if("native-desktop".equals(arguments.getString("phase"))){nativeDesktop(result);return;}
        if("desktop-transport".equals(arguments.getString("phase"))){desktopTransport(result);return;}
        if("nearby".equals(arguments.getString("phase"))){nearbyPhase(result);return;}
        if("gyro".equals(arguments.getString("phase"))){gyroDevice(result);return;}
        if(feedbackOnly){feedback(result);return;}
        if(performanceOnly){performance(result);return;}
        if(persistenceOnly){SharedPreferences durable=getTargetContext().getSharedPreferences("instrumentation-durable",0);ProfileStore store=new ProfileStore(durable);check(durable.getInt("writerPid",-1)!=android.os.Process.myPid(),"new Android process");check(ProfileStore.encode(store.load(store.active)).toString().equals(durable.getString("expected","")),"full configuration after process restart");check(!store.builtin("Default")&&ProfileStore.encode(store.load("Default")).toString().equals(durable.getString("overrideExpected","")),"in-place template edits survive process restart");check(store.games().contains("Durable empty")&&store.layouts("Durable empty").isEmpty(),"empty game survives process restart");PhoneHaptics hapticReload=new PhoneHaptics(hapticPersistenceContext(getTargetContext()));check(!hapticReload.enabled&&!hapticReload.hostEnabled&&hapticReload.localEnabled&&hapticReload.quiet&&hapticReload.texture&&Math.abs(hapticReload.strength-.37f)<.001&&Math.abs(hapticReload.heavyWeight-.22f)<.001&&Math.abs(hapticReload.lightWeight-.91f)<.001,"global haptic preferences survive process restart");getTargetContext().getSharedPreferences("instrumentation-haptics-durable",0).edit().clear().commit();durable.edit().clear().commit();result.putString("stream","\nPASS: profile and haptic preferences persistence across actual Android process restart.\n");finish(Activity.RESULT_OK,result);return;}
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();runOnMainSync(()->{try{java.lang.reflect.Field startup=MainActivity.class.getDeclaredField("startup");startup.setAccessible(true);ControllerSettings initial=(ControllerSettings)startup.get(activity);if(initial!=null){java.lang.reflect.Field sf=ControllerSettings.class.getDeclaredField("screen");sf.setAccessible(true);((SettingsScreen)sf.get(initial)).close();}}catch(NoSuchFieldException absent){/* Current activity has no startup settings dialog. */}catch(Exception ex){failure=ex;}});
        runOnMainSync(()->{FrameLayout content=activity.findViewById(android.R.id.content);View original=content.getChildAt(0);SharedPreferences testPrefs=activity.getSharedPreferences("instrumentation-layouts",0);testPrefs.edit().clear().commit();
            try{
                Context context=new ContextWrapper(activity){@Override public SharedPreferences getSharedPreferences(String name,int mode){return testPrefs;}};
                view=new PadView(context,new PadView.Actions(){public void connect(){connections++;}public void settings(){}public void pause(){pauses++;}public void changed(){if(view!=null)reports.add(view.state.report());}});activity.setContentView(view);view.layout(0,0,2200,1000);view.config.haptics=0;
                if("configuration".equals(arguments.getString("phase"))){configurationAudit(activity,testPrefs);return;}
                if("settings".equals(arguments.getString("phase"))){v5Device(activity);return;}
                if(Build.VERSION.SDK_INT>=30){float expected=view.getHeight()-4*activity.getResources().getDisplayMetrics().density;WindowInsets synthetic=new WindowInsets.Builder().setInsets(WindowInsets.Type.systemGestures(),android.graphics.Insets.of(0,94,0,130)).setInsets(WindowInsets.Type.navigationBars(),android.graphics.Insets.of(0,0,0,130)).setVisible(WindowInsets.Type.navigationBars(),false).build();view.dispatchApplyWindowInsets(synthetic);check(Math.abs(view.input.height-expected)<1,"hidden bars do not reserve vertical gesture bands");view.dispatchApplyWindowInsets(new WindowInsets.Builder(synthetic).setVisible(WindowInsets.Type.navigationBars(),true).build());check(Math.abs(view.input.height-(expected-130))<1,"visible navigation bar remains safe");view.dispatchApplyWindowInsets(synthetic);}
                touch(MotionEvent.ACTION_DOWN,new int[]{0},xy("LS"));float[] ls=xy("LS");ls[1]-=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_MOVE,new int[]{0},ls);check(view.state.ly<-.99,"LS range");
                float[] rs=xy("RS");touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{0,4},join(ls,rs));rs[0]+=view.input.radius(view.config.get("RS"));touch(MotionEvent.ACTION_MOVE,new int[]{0,4},join(ls,rs));check(view.state.rx>.99&&view.state.ly<-.99,"independent LS RS");
                view.config.triggerMode="DIGITAL";float[] rt=xy("RT"),a=xy("A"),rb=xy("RB");touch(MotionEvent.ACTION_POINTER_DOWN|(2<<8),new int[]{0,4,7},join(ls,rs,rt));touch(MotionEvent.ACTION_POINTER_DOWN|(3<<8),new int[]{0,4,7,11},join(ls,rs,rt,a));touch(MotionEvent.ACTION_POINTER_DOWN|(4<<8),new int[]{0,4,7,11,15},join(ls,rs,rt,a,rb));check(view.state.ly<-.99&&view.state.rx>.99&&view.state.rt==1&&(view.state.buttons&1)!=0&&(view.state.buttons&(1<<PadState.RB))!=0,"five MotionEvent pointers");
                touch(MotionEvent.ACTION_POINTER_UP|(2<<8),new int[]{0,4,7,11,15},join(ls,rs,rt,a,rb));check(view.state.rt==0&&view.state.rx>.99&&(view.state.buttons&1)!=0,"pointer up releases only RT");touch(MotionEvent.ACTION_CANCEL,new int[]{0,4,11,15},join(ls,rs,a,rb));neutral();
                view.selectProfile("Racing");view.config.haptics=0;
                for(float[] delta:new float[][]{{0,0},{1,0},{-1,0},{0,-1},{0,-.5f},{0,1},{.7f,-.7f},{-.7f,.7f}}){float[] center=xy("LS");touch(MotionEvent.ACTION_DOWN,new int[]{2},center);float r=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_MOVE,new int[]{2},new float[]{center[0]+delta[0]*r,center[1]+delta[1]*r});check(view.state.ly==0,"racing Y centered");float v=ControllerInput.integratedTrigger((float)Math.hypot(delta[0],delta[1]),view.config.triggerDeadzone,1,"LINEAR");check(Math.abs(view.state.rt-(delta[1]<0?v:0))<.002,"racing RT");check(Math.abs(view.state.lt-(delta[1]>0?v:0))<.002,"racing LT");byte[] report=reports.get(reports.size()-1);check((report[12]&255)==Math.round(view.state.rt*255),"emitted HID RT");check((report[11]&255)==Math.round(view.state.lt*255),"emitted HID LT");touch(MotionEvent.ACTION_UP,new int[]{2},center);neutral();}
                view.startEditing();float[] center=xy("LS");touch(MotionEvent.ACTION_DOWN,new int[]{1},center);touch(MotionEvent.ACTION_MOVE,new int[]{1},new float[]{center[0]+35,center[1]+20});neutral();touch(MotionEvent.ACTION_UP,new int[]{1},new float[]{center[0]+35,center[1]+20});float oldWidth=view.config.get("LS").w;center=xy("LS");touch(MotionEvent.ACTION_DOWN,new int[]{1},new float[]{center[0]-30,center[1]});touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},new float[]{center[0]-30,center[1],center[0]+30,center[1]});touch(MotionEvent.ACTION_MOVE,new int[]{1,2},new float[]{center[0]-45,center[1],center[0]+45,center[1]});check(view.config.get("LS").w>oldWidth,"pinch resizes native control");neutral();touch(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{1,2},new float[]{center[0]-45,center[1],center[0]+45,center[1]});touch(MotionEvent.ACTION_UP,new int[]{1},new float[]{center[0]-45,center[1]});view.finishEditing();neutral();check(!view.profiles.builtin(view.profiles.active),"editing copies built-in");
                view.config.get("A").w=.09f;view.config.get("A").h=.20f;view.config.get("RT").visible=false;view.config.get("RS").hitbox=1.7f;view.config.sensitivity=1.6f;view.config.triggerDeadzone=.2f;view.config.get("LT").reverse=true;view.config.haptics=2;view.config.unifiedInvert=true;view.config.cameraFire=true;view.config.cameraX=1.7f;view.config.get("LS").response=1.5f;view.config.get("L3+R3").visible=true;view.config.get("A").haptic=false;view.config.get("LS").hitWidth=1.3f;view.config.get("LS").mode="FIXED";view.config.quickStyle="RADIAL";view.config.gyroSmoothing=.42f;view.saveLayout();
                SharedPreferences durable=activity.getSharedPreferences("instrumentation-durable",0);durable.edit().clear().commit();ProfileStore durableStore=new ProfileStore(durable);ControllerConfig durableConfig=view.config.copy();durableConfig.duplicateControl("A");durableConfig.deleteControl("X");durableConfig.transferSticks=true;durableConfig.get("RS").fullscreen=true;durableConfig.addInstance(ControllerConfig.preset("Default").get("LT/RT"));durableConfig.unifiedOrientation="HORIZONTAL";durableConfig.unifiedCurve="AGGRESSIVE";durableConfig.unifiedTravel=.31f;durableConfig.unifiedSensitivity=1.7f;durableConfig.name="Restart test";durableStore.create("Restart test",durableConfig);durableStore.createEmptyGame("Durable empty");durable.edit().putString("expected",ProfileStore.encode(durableConfig).toString()).putInt("writerPid",android.os.Process.myPid()).commit();
                String durableActive=durableStore.active;durableStore.select("Default");ControllerConfig override=durableStore.load("Default");override.addInstance(ControllerConfig.preset("Default").get("LT/RT"));override.unifiedInvert=true;durableStore.editBuiltin(override);durableStore.select(durableActive);durable.edit().putString("overrideExpected",ProfileStore.encode(override).toString()).commit();
                String saved=ProfileStore.encode(view.config).toString();String id=view.profiles.active;ProfileStore reloaded=new ProfileStore(testPrefs);check(reloaded.active.equals(id),"active profile survives reload");check(ProfileStore.encode(reloaded.load(id)).toString().equals(saved),"complete JSON roundtrip");
                PadView restarted=new PadView(context,new PadView.Actions(){public void connect(){}public void settings(){}public void changed(){}});check(ProfileStore.encode(restarted.config).toString().equals(saved),"new PadView restores geometry visibility behavior feedback");
                String activeBefore=reloaded.active;try{reloaded.create(" ",reloaded.load(activeBefore));throw new AssertionError("invalid name accepted");}catch(IllegalArgumentException expected){}check(reloaded.active.equals(activeBefore),"invalid creation preserves active profile");
                reloaded.rename("Test racing");check(reloaded.load(id).name.equals("Test racing"),"rename");reloaded.setLayoutLabels(id,"Racing","drift, nitro, Drift");ProfileStore labeled=new ProfileStore(testPrefs);check(labeled.category(id).equals("Racing")&&labeled.tags(id).equals(Arrays.asList("drift","nitro")),"layout category and tags persist and normalize");check(labeled.layoutCategories(labeled.load(id).game).contains("Racing")&&labeled.layoutTags(labeled.load(id).game).contains("nitro"),"layout filters expose saved metadata");reloaded.duplicate("Duplicate",id);check(!id.equals(reloaded.active)&&reloaded.category(reloaded.active).equals("Racing")&&reloaded.tags(reloaded.active).equals(Arrays.asList("drift","nitro")),"duplicate copies category and tags");reloaded.reset();check(reloaded.load(reloaded.active).sensitivity==1,"reset behavior");String export=reloaded.exportCurrent();reloaded.importProfile(export);check(reloaded.load(reloaded.active).integrated,"JSON import racing");String deletedLayout=reloaded.active,deletedGame=reloaded.load(deletedLayout).game;reloaded.delete();check(!reloaded.ids().contains(deletedLayout)&&reloaded.load(reloaded.active).game.equals(deletedGame),"delete selects another layout in the same game");check(!reloaded.load("Default").integrated&&reloaded.load("Default").get("RT").visible,"built-in unchanged");
                org.json.JSONObject missing=new org.json.JSONObject("{\"version\":2,\"name\":\"Old custom\"}");check(ProfileStore.decode(missing).controls.size()==ControllerConfig.preset("Default").controls.size(),"missing fields use defaults");org.json.JSONObject malformed=ProfileStore.encode(ControllerConfig.preset("Default"));malformed.put("travel",0);malformed.put("sensitivity",99);check(ProfileStore.decode(malformed).travel>=.04f&&ProfileStore.decode(malformed).sensitivity<=3,"invalid ranges bounded");
                SharedPreferences migration=activity.getSharedPreferences("instrumentation-migration",0);migration.edit().clear().putFloat("LS.x",220).putFloat("LS.y",240).putFloat("LS.size",1.2f).putString("rightStickMode","FLOATING").commit();ProfileStore migrated=new ProfileStore(migration);check(!migrated.builtin(migrated.active)&&migrated.load(migrated.active).rightMode.equals("FLOATING"),"legacy settings migrate to custom copy");check(Math.abs(migrated.load(migrated.active).get("LS").x-.2f)<.001,"legacy position conversion");migration.edit().clear().putString("profiles.v2","{\"future\":{\"version\":99,\"name\":\"Keep me\"}}").putString("active.v2","future").commit();ProfileStore future=new ProfileStore(migration);check(!future.ids().contains("future"),"unsupported layout not activated");future.select("Default");check(migration.getString("profiles.v2","").contains("Keep me"),"unsupported layout retained on subsequent saves");migration.edit().clear().commit();
                configurationAudit(activity,testPrefs);
                v41(context,testPrefs);
                v5Device(activity);
                // Completed touch streams must retain gesture history and explicit toggles.
                view.selectProfile("Default");touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("LS"));touch(MotionEvent.ACTION_UP,new int[]{1},xy("LS"));touch(MotionEvent.ACTION_DOWN,new int[]{2},xy("LS"));check((view.state.buttons&(1<<PadState.L3))!=0,"Android double tap survives new DOWN");view.release();
                view.config.get("AIM").visible=true;view.config.aimMode="TOGGLE";touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("AIM"));touch(MotionEvent.ACTION_UP,new int[]{1},xy("AIM"));touch(MotionEvent.ACTION_DOWN,new int[]{2},xy("A"));check(view.state.lt==1&&(view.state.buttons&1)!=0,"aim toggle survives next gesture");view.release();
                // Profile hierarchy and whole-game JSON survive reload and reset.
                reloaded.createGame("Regression game");String gameLayout=reloaded.active;ControllerConfig gameConfig=reloaded.load(gameLayout);check(gameConfig.game.equals("Regression game"),"new game owns first layout");reloaded.reset();check(reloaded.load(gameLayout).game.equals("Regression game"),"layout reset preserves game");reloaded.create("Layout B",gameConfig);String secondLayout=reloaded.active;reloaded.select(gameLayout);ControllerConfig linked=reloaded.load(gameLayout);linked.holdLayout=secondLayout;linked.aimLayout=secondLayout;linked.get("A").action="LAYOUT";linked.get("A").target=secondLayout;reloaded.save(linked);check(reloaded.layouts("Regression game").size()==2,"two layouts in game");reloaded.duplicateGame("Regression game","Game copy");check(reloaded.layouts("Game copy").size()==2,"duplicate game");ControllerConfig copied=reloaded.load(reloaded.active);check(reloaded.layouts("Game copy").contains(copied.holdLayout)&&!copied.holdLayout.equals(secondLayout)&&copied.aimLayout.equals(copied.holdLayout)&&copied.get("A").target.equals(copied.holdLayout),"game copy remaps internal switching rules");reloaded.renameGame("Game copy","Renamed game");check(reloaded.games().contains("Renamed game"),"rename game");String gameJson=reloaded.exportGame("Regression game");reloaded.importGame(gameJson);check(reloaded.layouts("Regression game 2").size()==2,"whole game import");reloaded.deleteGame("Renamed game");check(!reloaded.games().contains("Renamed game"),"delete game");
                String longName=String.format(java.util.Locale.ROOT,"%060d",0);reloaded.createGame(longName);reloaded.importGame(reloaded.exportGame(longName));check(reloaded.load(reloaded.active).game.length()<=60&&!reloaded.load(reloaded.active).game.equals(longName),"imported duplicate long game name remains distinct");
                // A pointer transition updates surviving axes even without ACTION_MOVE.
                view.selectProfile("Default");float[] moving=xy("LS");touch(MotionEvent.ACTION_DOWN,new int[]{1},moving);moving[0]+=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},join(moving,xy("A")));check(view.state.lx>.99,"pointer down updates existing coordinates");moving[0]-=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{1,2},join(moving,xy("A")));check(Math.abs(view.state.lx)<.01,"pointer up updates surviving coordinates");view.release();
                view.startEditing();float previousX=view.config.get("A").x;view.config.get("A").x=.65f;view.saveLayout();view.history(false);check(Math.abs(view.config.get("A").x-previousX)<.001,"editor undo");view.history(true);check(Math.abs(view.config.get("A").x-.65f)<.001,"editor redo");view.finishEditing();
                view.selectProfile("Default");view.config.get("LAYOUT HOLD").visible=true;view.config.holdLayout="FPS";view.saveLayout();String returnLayout=view.profiles.active;touch(MotionEvent.ACTION_DOWN,new int[]{5},xy("LAYOUT HOLD"));check(view.config.name.equals("FPS")&&view.profiles.active.equals(returnLayout),"temporary layout does not persist selection");touch(MotionEvent.ACTION_UP,new int[]{5},new float[]{500,150});check(view.config.holdLayout.equals("FPS"),"temporary layout release restores origin");neutral();
                view.selectProfile("Default");view.config.triggerMode="DIGITAL";view.config.aimLayout="FPS Precision";view.saveLayout();String aimOrigin=view.profiles.active;touch(MotionEvent.ACTION_DOWN,new int[]{9},xy("LT"));check(view.config.base.equals("FPS Precision")&&view.state.lt==1,"LT enters temporary aim layout with pressure");touch(MotionEvent.ACTION_UP,new int[]{9},new float[]{100,250});check(view.profiles.active.equals(aimOrigin)&&view.config.aimLayout.equals("FPS Precision"),"LT release restores original layout");neutral();
                view.selectProfile("Default");view.config.switchButtons=(1<<PadState.LB)|(1<<PadState.RB);view.saveLayout();String chordOrigin=view.profiles.active;float[] lb=xy("LB"),rightBumper=xy("RB");touch(MotionEvent.ACTION_DOWN,new int[]{1},lb);touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},join(lb,rightBumper));check(!view.profiles.active.equals(chordOrigin),"button combination changes layout");neutral();
                for(String template:ProfileStore.BUILTINS){ControllerConfig preset=ControllerConfig.preset(template);check(ProfileStore.decode(ProfileStore.encode(preset)).base.equals(template),"preset roundtrip "+template);}
                view.selectProfile("Default");touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("A"));check(view.state.buttons!=0,"held before switch");view.selectProfile("Racing");neutral();
                view.testMode=true;touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("LS"));float[] up=xy("LS");up[1]-=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_MOVE,new int[]{1},up);check(view.state.rt>.99,"test overlay accepts live controller input");view.release();neutral();
                BluetoothPad transport=new BluetoothPad(activity,new BluetoothPad.Listener(){public void status(String s){}public void rumble(int a,int b,int ms){}public void disconnected(){view.release();}});
                touch(MotionEvent.ACTION_DOWN,new int[]{3},xy("A"));check(view.state.buttons!=0,"held before disconnect callback");java.lang.reflect.Field callback=BluetoothPad.class.getDeclaredField("callback");callback.setAccessible(true);((android.bluetooth.BluetoothHidDevice.Callback)callback.get(transport)).onConnectionStateChanged(null,android.bluetooth.BluetoothProfile.STATE_DISCONNECTED);neutral();transport.close();
                for(String preset:new String[]{"Default","FPS","Racing","FPS Precision","Racing Unified Trigger","Fighting Game","Accessibility / Large Buttons"}){view.selectProfile(preset);view.layout(0,0,2200,1000);snapshot(activity,preset);}
                for(int width:new int[]{1280,2400}){view.layout(0,0,width,1080);view.selectProfile("Racing");touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("LS"));up=xy("LS");up[1]-=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_MOVE,new int[]{1},up);check(view.state.rt>.99,"range at width "+width);view.release();}
            }catch(Throwable t){failure=t;}finally{if(view!=null)view.release();activity.setContentView(original);testPrefs.edit().clear().commit();}
        });
        // Restoring the original view schedules insets/layout work. Export only after it settles.
        waitForIdleSync();runOnMainSync(()->{try{FrameLayout content=activity.findViewById(android.R.id.content);exportGeometry((PadView)content.getChildAt(0),activity);}catch(Exception ex){if(failure==null)failure=ex;}});
        if(failure!=null)throw new AssertionError(failure);result.putString("stream","\nPASS: "+checks+" Android assertions ("+("settings".equals(arguments.getString("phase"))?"settings, game mode and rendering":"full input, persistence and settings regression")+").\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(t));finish(Activity.RESULT_CANCELED,result);}}
    private Context hapticPersistenceContext(Context base){return new ContextWrapper(base){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("instrumentation-haptics-durable",mode);}};}
    private void closeStartup(Activity activity)throws Exception{
        java.lang.reflect.Field startup;try{startup=MainActivity.class.getDeclaredField("startup");}catch(NoSuchFieldException absent){return;}startup.setAccessible(true);
        ControllerSettings initial=(ControllerSettings)startup.get(activity);
        if(initial!=null){java.lang.reflect.Field sf=ControllerSettings.class.getDeclaredField("screen");sf.setAccessible(true);SettingsScreen screen=(SettingsScreen)sf.get(initial);if(screen!=null)screen.close();}
    }
    private void gyroDevice(Bundle result)throws Exception{
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        runOnMainSync(()->{try{closeStartup(activity);}catch(Exception e){failure=e;}});
        PadView[] test={null};View[] original={null};GyroInput[] sensor={null};
        try{
            runOnMainSync(()->{
                original[0]=((FrameLayout)activity.findViewById(android.R.id.content)).getChildAt(0);
                test[0]=new PadView(activity,new PadView.Actions(){public void connect(){}public void settings(){}public void changed(){}});
                test[0].config=ControllerConfig.preset("Default");test[0].config.gyroEnabled=true;test[0].config.gyroAimOnly=false;
                test[0].input.configure(test[0].config);activity.setContentView(test[0]);
                sensor[0]=new GyroInput(activity,test[0]);test[0].gyro=sensor[0];
            });
            waitForIdleSync();Thread.sleep(300);
            runOnMainSync(()->{check(sensor[0].available(),"hardware gyroscope detected");sensor[0].calibrate();sensor[0].sync(true);});
            Thread.sleep(2200);
            java.lang.reflect.Field samples=GyroInput.class.getDeclaredField("samples");samples.setAccessible(true);
            String[] status={""};
            runOnMainSync(()->{try{
                check(samples.getLong(sensor[0])>30,"real timestamped gyro events");status[0]=sensor[0].status();
                sensor[0].sync(false);test[0].input.update(SystemClock.uptimeMillis());check(test[0].state.rx==0&&test[0].state.ry==0,"sensor stop neutralizes right stick");
                ControllerConfig c=ControllerConfig.preset("Default");c.gyroSensitivity=1.7f;
                org.json.JSONObject old=ProfileStore.encode(c);old.remove("gyroSensitivityX");old.remove("gyroSensitivityY");
                check(ProfileStore.decode(old).gyroGainX()==1.7f&&ProfileStore.decode(old).gyroGainY()==1.7f,"legacy shared gyro gain preserved");
                c.gyroSensitivityX=2.3f;c.gyroSensitivityY=.8f;ControllerConfig saved=ProfileStore.decode(ProfileStore.encode(c));
                check(saved.gyroGainX()==2.3f&&saved.gyroGainY()==.8f,"independent gyro gain JSON roundtrip");
            }catch(Exception e){failure=e;}});
            if(failure!=null)throw new AssertionError(failure);
            ControllerSettings[] calibrationSettings={null};SettingsScreen[] calibrationScreen={null};
            runOnMainSync(()->{try{
                test[0].config.gyroEnabled=false;calibrationSettings[0]=new ControllerSettings(activity,test[0],()->{});java.lang.reflect.Method page=ControllerSettings.class.getDeclaredMethod("gyro");page.setAccessible(true);page.invoke(calibrationSettings[0]);
                java.lang.reflect.Field screenField=ControllerSettings.class.getDeclaredField("screen");screenField.setAccessible(true);calibrationScreen[0]=(SettingsScreen)screenField.get(calibrationSettings[0]);java.lang.reflect.Field window=SettingsScreen.class.getDeclaredField("window");window.setAccessible(true);Dialog dialog=(Dialog)window.get(calibrationScreen[0]);android.widget.Button calibrate=findButton(dialog.getWindow().getDecorView(),"Calibrate gyro");check(calibrate!=null&&calibrate.isEnabled()&&calibrate.isShown(),"calibration available with motion aiming disabled");calibrate.performClick();check(sensor[0].calibrating(),"calibration begins directly in settings");
            }catch(Exception e){failure=e;}});waitForIdleSync();Thread.sleep(2200);
            runOnMainSync(()->{try{check(!sensor[0].calibrating(),"disabled motion calibration completes with real sensor samples in settings");sensor[0].sync(true);java.lang.reflect.Field sampling=GyroInput.class.getDeclaredField("registered");sampling.setAccessible(true);check(!sampling.getBoolean(sensor[0]),"temporary calibration sensor unregisters while motion stays off");test[0].input.update(SystemClock.uptimeMillis());check(!test[0].config.gyroEnabled&&test[0].state.rx==0&&test[0].state.ry==0,"calibration never enables aiming or emits controller axes");sensor[0].calibrate();sensor[0].sync(false);check(!sensor[0].calibrating(),"app pause cancels temporary calibration");calibrationScreen[0].close();}catch(Exception e){failure=e;}});
            if(failure!=null)throw new AssertionError(failure);
            SettingsPreview[] preview={null};boolean[] enabled={true};
            runOnMainSync(()->{preview[0]=new SettingsPreview(activity,"Gyroscope",(key,fallback)->fallback,(key,fallback)->key.equals("enable gyro")?enabled[0]:fallback,(key,fallback)->fallback);activity.setContentView(preview[0]);});
            waitForIdleSync();Thread.sleep(350);
            java.lang.reflect.Field registered=SettingsPreview.class.getDeclaredField("registered"),gyroAt=SettingsPreview.class.getDeclaredField("gyroAt"),gx=SettingsPreview.class.getDeclaredField("gyroX");registered.setAccessible(true);gyroAt.setAccessible(true);gx.setAccessible(true);
            runOnMainSync(()->{try{check(registered.getBoolean(preview[0]),"motion aiming enables actual preview sensor without removed switches");check(gyroAt.getLong(preview[0])>0,"preview receives physical gyro events");enabled[0]=false;preview[0].invalidate();}catch(Exception e){failure=e;}});
            Thread.sleep(150);
            runOnMainSync(()->{try{check(!registered.getBoolean(preview[0])&&gx.getFloat(preview[0])==0,"disabled preview unregisters and clears output");check(test[0].state.rx==0&&test[0].state.ry==0,"preview leaves gameplay RS neutral");}catch(Exception e){failure=e;}});
            if(failure!=null)throw new AssertionError(failure);
            result.putString("stream","\nPASS: "+checks+" gyro device assertions. Physical direction/feel NOT TESTED.\n"+status[0]+"\n");
        }finally{runOnMainSync(()->{if(sensor[0]!=null)sensor[0].sync(false);if(original[0]!=null)activity.setContentView(original[0]);});}
        finish(Activity.RESULT_OK,result);
    }
    private void bluetoothFeedback(Bundle result)throws Exception{
        MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();runOnMainSync(()->{try{closeStartup(activity);}catch(Exception e){failure=e;}});
        java.lang.reflect.Field transportField=MainActivity.class.getDeclaredField("bluetooth");transportField.setAccessible(true);BluetoothPad transport=(BluetoothPad)transportField.get(activity);String target=arguments.getString("bluetoothHost","cachyos-x8664");android.bluetooth.BluetoothDevice peer=null;for(android.bluetooth.BluetoothDevice device:transport.paired())if(target.equals(device.getName()))peer=device;check(peer!=null,"paired test host exists: "+target);android.bluetooth.BluetoothDevice receivingHost=peer;runOnMainSync(()->transport.connect(receivingHost));
        java.lang.reflect.Field latest=PhoneHaptics.class.getDeclaredField("lastHost");latest.setAccessible(true);boolean[] seen=new boolean[4];long started=SystemClock.uptimeMillis(),until=started+11000;
        while(SystemClock.uptimeMillis()<until){runOnMainSync(()->{try{RumbleState host=(RumbleState)latest.get(activity.haptics);if(host.received<started||!host.source.equals("Bluetooth report 2"))return;if(host.heavy>0&&host.light==0)seen[0]=true;if(host.heavy==0&&host.light>0)seen[1]=true;if(host.heavy>0&&host.light>0)seen[2]=true;if(host.heavy==0&&host.light==0&&seen[2])seen[3]=true;}catch(Exception ex){failure=ex;}});Thread.sleep(20);}
        java.lang.reflect.Field starts=PhoneHaptics.class.getDeclaredField("starts");starts.setAccessible(true);check(starts.getLong(activity.haptics)>0,"Bluetooth feedback actuated production mixer");
        if(failure!=null)throw new AssertionError(failure);for(boolean value:seen)check(value,"actual Bluetooth heavy/light/both/stop: "+Arrays.toString(seen));runOnMainSync(()->activity.haptics.stop());
        result.putString("stream","\nPASS: Linux FF → Bluetooth vendor output report 2 → production Android haptics: heavy-only, light-only, both, explicit stop. No physical quality or game compatibility claim.\n");finish(Activity.RESULT_OK,result);
    }
    private void feedback(Bundle result)throws Exception{
        boolean[] seen=new boolean[4];String[] status={"starting"};int[] received={0};PhoneHaptics[] sink={null};
        Context test=new ContextWrapper(getTargetContext()){@Override public SharedPreferences getSharedPreferences(String name,int mode){return super.getSharedPreferences("feedback-test",mode);}};
        runOnMainSync(()->{test.getSharedPreferences("feedback-test",0).edit().clear().commit();sink[0]=new PhoneHaptics(test);sink[0].active(true);});
        java.lang.reflect.Field lastHostState=PhoneHaptics.class.getDeclaredField("lastHost"),localState=PhoneHaptics.class.getDeclaredField("local");lastHostState.setAccessible(true);localState.setAccessible(true);
        java.lang.reflect.Field hostState=PhoneHaptics.class.getDeclaredField("host"),amplitude=PhoneHaptics.class.getDeclaredField("amplitude");hostState.setAccessible(true);amplitude.setAccessible(true);
        runOnMainSync(()->{try{sink[0].host(255,0,180,"Lease test");RumbleState first=(RumbleState)hostState.get(sink[0]);sink[0].local(2);check(hostState.get(sink[0])==first,"local cue preserves host lease and independent channels");sink[0].stopHost();check(lastHostState.get(sink[0])==first&&localState.getFloat(sink[0])>0,"transport stop preserves last actual host telemetry and local source");sink[0].active(false);check(!((RumbleState)hostState.get(sink[0])).active(SystemClock.uptimeMillis()),"focus loss stops host");sink[0].host(255,255,200,"Inactive");check(!((RumbleState)hostState.get(sink[0])).active(SystemClock.uptimeMillis()),"inactive sink rejects host updates");sink[0].active(true);sink[0].host(255,255,80,"Expiry test");}catch(Exception e){throw new RuntimeException(e);}});
        Thread.sleep(180);runOnMainSync(()->{try{check(amplitude.getInt(sink[0])==0,"Android mixer expires and cancels finite host effect");sink[0].stop();}catch(Exception e){throw new RuntimeException(e);}});

        WifiPad pad=new WifiPad(new WifiPad.Listener(){public void status(String s){status[0]=s;}public void stopped(long session){}public void rumble(int low,int high,int ms){synchronized(seen){received[0]++;if(low>0&&high==0)seen[0]=true;if(high>0&&low==0)seen[1]=true;if(low>0&&high>0)seen[2]=true;if(low==0&&high==0&&seen[2])seen[3]=true;}new Handler(Looper.getMainLooper()).post(()->{sink[0].host(low,high,ms,"Linux FF via authenticated Wi-Fi");if(low>0)sink[0].local(1);});}});
        java.net.DatagramSocket relay="true".equals(arguments.getString("tunnel"))?new java.net.DatagramSocket(0,java.net.InetAddress.getByName("127.0.0.1")):null;
        java.net.Socket bridge=relay==null?null:new java.net.Socket("127.0.0.1",39571);
        if(bridge!=null)bridge.setTcpNoDelay(true);
        Thread forward=relay==null?null:new Thread(()->{try{java.io.DataInputStream in=new java.io.DataInputStream(bridge.getInputStream());java.io.DataOutputStream out=new java.io.DataOutputStream(bridge.getOutputStream());while(!relay.isClosed()){byte[] buffer=new byte[256];java.net.DatagramPacket packet=new java.net.DatagramPacket(buffer,buffer.length);relay.receive(packet);out.writeInt(packet.getLength());out.write(buffer,0,packet.getLength());out.flush();int length=in.readInt();if(length>0&&length<=256){byte[] reply=new byte[length];in.readFully(reply);relay.send(new java.net.DatagramPacket(reply,reply.length,packet.getAddress(),packet.getPort()));}}}catch(java.io.IOException ignored){}},"feedback-test-relay");
        if(forward!=null)forward.start();
        try{pad.connect(relay==null?arguments.getString("host"):"127.0.0.1",relay==null?Integer.parseInt(arguments.getString("port")):relay.getLocalPort(),arguments.getString("key"));long until=SystemClock.uptimeMillis()+9000;while(SystemClock.uptimeMillis()<until){pad.send(new PadState().report());Thread.sleep(16);}synchronized(seen){for(boolean value:seen)check(value,"heavy/light/both/stop reached phone mixer: "+Arrays.toString(seen)+" callbacks="+received[0]+" "+status[0]+" invalid="+pad.invalidFeedback+" stale="+pad.staleFeedback+" ack="+pad.lastFeedbackSequence+" sent="+pad.lastSentSequence);}check(received[0]>10,"authenticated feedback delivered");}
        finally{pad.close();if(relay!=null){relay.close();bridge.close();forward.join(1000);}runOnMainSync(()->sink[0].active(false));}
        result.putString("stream","\nPASS: actual Linux FF → PP01 → Android mixer: heavy-only, light-only, both, stop; "+received[0]+" authenticated callbacks. Local cues mixed during host effects. Physical quality not measured.\n");finish(Activity.RESULT_OK,result);
    }
    private void performance(Bundle result)throws Exception{
        ControllerConfig config=ControllerConfig.preset("Default");org.json.JSONObject json=ProfileStore.encode(config);
        for(int i=0;i<100;i++){ProfileStore.encode(config);ProfileStore.decode(json);}
        long start=System.nanoTime();for(int i=0;i<1000;i++)ProfileStore.encode(config);long encode=System.nanoTime()-start;
        start=System.nanoTime();for(int i=0;i<1000;i++)ProfileStore.decode(json);long decode=System.nanoTime()-start;
        ControllerInput input=new ControllerInput(config);input.resize(1100,480);
        start=System.nanoTime();for(int i=0;i<100000;i++)input.update(i);long idle=System.nanoTime()-start;
        for(String id:new String[]{"LS","RS","A","RT","RB"}){ControllerConfig.Control c=config.get(id);input.down(input.pointerCount(),c.x*1100,c.y*480,0);}
        start=System.nanoTime();for(int i=0;i<100000;i++)input.update(i);long active=System.nanoTime()-start;
        result.putString("stream",String.format(java.util.Locale.ROOT,"\nPERFORMANCE: encode %.1f us/layout; decode %.1f us/layout; idle update %.2f us; five-pointer update %.2f us. Warmed loops, not end-to-end latency.\n",encode/1e6,decode/1e6,idle/1e8,active/1e8));finish(Activity.RESULT_OK,result);
    }
    private void v41(Context context,SharedPreferences prefs)throws Exception{
        view.selectProfile("Default");check(view.nextFrameDelay()==100,"idle heartbeat cadence");
        touch(MotionEvent.ACTION_DOWN,new int[]{0},xy("LS"));check(view.nextFrameDelay()==16,"touch restores active cadence");view.release();
        android.view.accessibility.AccessibilityNodeInfo info=view.createAccessibilityNodeInfo();check(info.getActionList().size()>=5,"native accessibility actions exposed");
        check(view.performAccessibilityAction(0x0f010004,null),"accessible release action");neutral();
        view.startEditing();int count=view.config.controls.size();
        view.addControl(view.config.get("A"));String added=view.config.controls.get(count).id;
        check(view.config.controls.size()==count+1&&view.config.get(added).label.equals("A"),"native add instance");neutral();
        view.history(false);check(view.config.get(added)==null,"undo add");view.history(true);check(view.config.get(added)!=null,"redo stable identity");
        view.config.get(added).x=.5f;view.config.get(added).y=.7f;view.config.get(added).w=.11f;view.config.get(added).hitHeight=2;view.saveLayout();
        view.deleteControl("A");check(view.config.get("A")==null&&view.config.get(added)!=null,"delete standard slot");view.history(false);check(view.config.get("A")!=null,"undo delete");view.history(true);check(view.config.get("A")==null,"redo delete");
        ControllerConfig restored=new ProfileStore(prefs).load(view.profiles.active);check(restored.get("A")==null&&restored.get(added).w==.11f&&restored.get(added).hitHeight==2,"instance geometry and deletion persist");
        snapshot(context,"V41_Editor");view.finishEditing();touch(MotionEvent.ACTION_DOWN,new int[]{3},xy(added));check((view.state.buttons&1)!=0,"added native instance emits mapping");touch(MotionEvent.ACTION_UP,new int[]{3},xy(added));neutral();
        org.json.JSONObject legacy=ProfileStore.encode(ControllerConfig.preset("Default"));legacy.put("version",3);ControllerConfig upgraded=ProfileStore.decode(legacy);check(upgraded.get("LS")!=null&&!upgraded.transferSticks,"v3 defaults migrate");
        org.json.JSONObject invalid=ProfileStore.encode(restored);invalid.getJSONArray("controls").put(invalid.getJSONArray("controls").getJSONObject(0));
        try{ProfileStore.decode(invalid);throw new AssertionError("duplicate IDs accepted");}catch(IllegalArgumentException expected){check(true,"duplicate IDs rejected");}
        invalid=ProfileStore.encode(restored);invalid.getJSONArray("controls").getJSONObject(0).put("kind","arbitrary");try{ProfileStore.decode(invalid);throw new AssertionError("invalid kind accepted");}catch(IllegalArgumentException expected){check(true,"invalid kinds rejected");}
        ProfileStore store=new ProfileStore(prefs);store.createEmptyGame("Empty");check(new ProfileStore(prefs).games().contains("Empty"),"empty game persists");store.duplicateGame("Empty","Empty copy");store.renameGame("Empty copy","Renamed empty");store.importGame(store.exportGame("Empty"));check(store.games().contains("Empty 2")&&store.layouts("Empty 2").isEmpty(),"empty game export import");store.deleteGame("Renamed empty");check(!store.games().contains("Renamed empty"),"empty game deletion");
        ControllerConfig one=ControllerConfig.preset("Default");one.game="Empty";store.create("Only",one);store.delete();check(new ProfileStore(prefs).games().contains("Empty"),"last-layout deletion retains game");
        SharedPreferences oldPrefs=getTargetContext().getSharedPreferences("instrumentation-v41-migration",0);
        org.json.JSONObject oldRoot=new org.json.JSONObject();oldRoot.put("legacy",legacy);String legacySnapshot=oldRoot.toString();
        oldPrefs.edit().clear().putString("profiles.v2",legacySnapshot).putString("active.v2","legacy").commit();
        ProfileStore upgradedStore=new ProfileStore(oldPrefs);upgradedStore.select("legacy");
        check(oldPrefs.getString("profiles.v2","").equals(legacySnapshot)&&oldPrefs.getString("active.v2","").equals("legacy"),"V4 rollback snapshot remains untouched");
        check(new ProfileStore(oldPrefs).active.equals("legacy")&&oldPrefs.contains("profiles.v4"),"migration preserves active identity and is restartable");
        final boolean[] fail={false};
        SharedPreferences failing=(SharedPreferences)java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),new Class<?>[]{SharedPreferences.class},(proxy,method,args)->{
            Object result=method.invoke(oldPrefs,args);
            if(!method.getName().equals("edit"))return result;
            SharedPreferences.Editor editor=(SharedPreferences.Editor)result;
            return java.lang.reflect.Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),new Class<?>[]{SharedPreferences.Editor.class},(ep,em,ea)->{
                if(em.getName().equals("commit")&&fail[0])return false;
                Object value=em.invoke(editor,ea);return value==editor?ep:value;
            });
        });
        ProfileStore failingStore=new ProfileStore(failing);fail[0]=true;
        try{failingStore.createEmptyGame("Must roll back");throw new AssertionError("failed commit accepted");}catch(IllegalStateException expected){check(!failingStore.games().contains("Must roll back"),"game registry rolls back failed commit");}
        try{failingStore.create("Failed layout",restored);throw new AssertionError("failed layout accepted");}catch(IllegalStateException expected){check(failingStore.active.equals("legacy")&&failingStore.ids().size()==ProfileStore.BUILTINS.length+1,"layout creation rolls back failed commit");}
        oldPrefs.edit().clear().commit();
        // Unified trigger + both sticks and FPS camera + aim + second-finger fire on real MotionEvents.
        view.selectProfile("Default");view.config.get("LT/RT").visible=true;view.config.get("LT/RT").x=.5f;view.config.get("LT/RT").y=.75f;
        float[] unified=xy("LT/RT"),left=xy("LS"),right=xy("RS");
        touch(MotionEvent.ACTION_DOWN,new int[]{1},left);left[0]+=view.input.radius(view.config.get("LS"));
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},join(left,right));right[0]+=view.input.radius(view.config.get("RS"));
        touch(MotionEvent.ACTION_POINTER_DOWN|(2<<8),new int[]{1,2,3},join(left,right,unified));unified[1]-=view.config.unifiedTravel*view.input.height;
        touch(MotionEvent.ACTION_MOVE,new int[]{1,2,3},join(left,right,unified));check(view.state.lx>.99&&view.state.rx>.99&&view.state.rt>.99&&view.state.lt==0,"native unified trigger with two sticks");
        touch(MotionEvent.ACTION_POINTER_UP|(2<<8),new int[]{1,2,3},join(left,right,unified));check(view.state.rt==0&&view.state.lx>.99,"native unified release preserves sticks");view.release();
        view.selectProfile("FPS Precision");view.config.haptics=0;right=xy("RS");left=xy("LS");float[] aim=xy("AIM");
        touch(MotionEvent.ACTION_DOWN,new int[]{1},left);left[0]+=view.input.radius(view.config.get("LS"))*.5f;
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},join(left,right));
        touch(MotionEvent.ACTION_POINTER_DOWN|(2<<8),new int[]{1,2,3},join(left,right,aim));
        touch(MotionEvent.ACTION_POINTER_DOWN|(3<<8),new int[]{1,2,3,4},join(left,right,aim,right));right[0]+=20;
        touch(MotionEvent.ACTION_MOVE,new int[]{1,2,3,4},join(left,right,aim,right));check(view.state.lx>0&&view.state.rx>0&&view.state.lt==1&&view.state.rt==1,"native FPS simultaneous movement camera aim fire");
        touch(MotionEvent.ACTION_POINTER_UP|(3<<8),new int[]{1,2,3,4},join(left,right,aim,right));check(view.state.rt==0&&view.state.lt==1&&view.input.pointerCount()==3,"native fire releases independently");view.release();neutral();
        // Destination and source opt in, and retain only primary stick owners.
        view.selectProfile("Default");view.config.transferSticks=true;view.config.get("LAYOUT HOLD").visible=true;
        ControllerConfig destination=view.config.copy();destination.name="Transfer target";view.profiles.create(destination.name,destination);String target=view.profiles.active;
        view.selectProfile("Default");view.config.transferSticks=true;view.config.holdLayout=target;view.config.get("LAYOUT HOLD").visible=true;view.saveLayout();
        float[] ls=xy("LS");touch(MotionEvent.ACTION_DOWN,new int[]{1},ls);ls[0]+=view.input.radius(view.config.get("LS"))*.6f;touch(MotionEvent.ACTION_MOVE,new int[]{1},ls);float axis=view.state.lx;float[] hold=xy("LAYOUT HOLD");
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},join(ls,hold));check(Math.abs(view.state.lx-axis)<.001&&view.input.pointerCount()==1,"temporary entry transfers LS");
        touch(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{1,2},join(ls,hold));check(Math.abs(view.state.lx-axis)<.001&&view.config.holdLayout.equals(target),"temporary exit transfers LS");touch(MotionEvent.ACTION_CANCEL,new int[]{1},ls);neutral();
        view.selectProfile("Default");
        // Added standard slots may all be removed, including an actually empty layout.
        ControllerConfig blank=ControllerConfig.preset("Default");blank.controls.clear();check(ProfileStore.decode(ProfileStore.encode(blank)).controls.isEmpty(),"empty v4 layout stays empty");
        org.json.JSONObject encoded=ProfileStore.encode(blank);check(!encoded.has("MAX_CONTROLS"),"static constants are not serialized");
    }
    @SuppressWarnings("unchecked")
    private void deletionAudit(Activity activity)throws Exception{
        SharedPreferences prefs=activity.getSharedPreferences("instrumentation-delete",0);prefs.edit().clear().commit();
        ProfileStore store=new ProfileStore(prefs);ControllerConfig c=ControllerConfig.preset("Default");c.game="Deletion test";
        store.create("First",c);String first=store.active;store.create("Second",c);String second=store.active;
        String prefix=QuickPanelPreferences.prefix(prefs,first),other=QuickPanelPreferences.prefix(prefs,second);
        String ids=new org.json.JSONArray().put(first).put(second).toString();prefs.edit().putString("quickPanel.ids",ids).putString(other+"ids",ids).commit();
        store.delete(first);
        check(store.active.equals(second)&&!store.ids().contains(first),"deleting inactive layout preserves active layout");
        check(!prefs.contains(prefix+"initialized")&&!prefs.getString(other+"ids","").contains(first)&&!prefs.getString("quickPanel.ids","").contains(first),"deletion removes scoped preferences and every quick shortcut");
        store.create("Third",c);String third=store.active;store.delete(third);
        check(store.active.equals(second),"deleting active layout selects remaining layout in same game");
        store.delete(second);check(store.active.equals("Default")&&new ProfileStore(prefs).ids().equals(store.ids()),"last custom deletion falls back to Default and persists");
        boolean protectedTemplate=false;try{store.delete("Default");}catch(IllegalArgumentException expected){protectedTemplate=true;}check(protectedTemplate,"factory layouts are protected from deletion");
        store.editBuiltin(store.load("Default"));prefs.edit().putString("quickPanel.ids","[\"Default\"]").commit();store.delete("Default");
        check(store.builtin("Default")&&prefs.getString("quickPanel.ids","").contains("Default"),"removing built-in customization preserves factory shortcut");
        prefs.edit().clear().commit();
    }
    private void configurationAudit(Activity activity,SharedPreferences prefs)throws Exception{
        deletionAudit(activity);
        view.selectProfile("Default");prefs.edit().putString("quickPanel.ids","[\"Default\",\"FPS\"]").putBoolean("quickPanel.enabled",true).putString("quickPanel.anchor","Left").commit();view.startEditing();
        check(prefs.getString("quickPanel.ids","").contains(view.profiles.active)&&!prefs.getString("quickPanel.ids","").contains("Default"),"shortcuts follow edited built-in copy");
        String panelPrefix=QuickPanelPreferences.prefix(prefs,view.profiles.active);check(prefs.getBoolean(panelPrefix+"enabled",false)&&prefs.getString(panelPrefix+"anchor","").equals("Left"),"edited copy inherits panel settings");
        view.addControl(ControllerConfig.preset("Default").get("LT/RT"));
        String id=view.editorSelection().id,layout=view.profiles.active;
        view.mutate(()->{ControllerConfig.Control c=view.config.get(id);c.x=.82f;c.y=.41f;c.w=.08f;c.h=.28f;});
        ControllerSettings settings=new ControllerSettings(activity,view,()->{});
        settings.properties(id,3);
        java.lang.reflect.Field sf=ControllerSettings.class.getDeclaredField("screen");sf.setAccessible(true);
        java.lang.reflect.Field nf=ControllerSettings.class.getDeclaredField("numbers");nf.setAccessible(true);
        java.lang.reflect.Field tf=ControllerSettings.class.getDeclaredField("switches");tf.setAccessible(true);
        java.lang.reflect.Field cf=ControllerSettings.class.getDeclaredField("choices");cf.setAccessible(true);
        Map<String,android.widget.Switch> toggles=(Map<String,android.widget.Switch>)tf.get(settings);
        toggles.get("Origin follows touchdown").setChecked(false);toggles.get("Invert direction").setChecked(true);
        Map<String,android.widget.Spinner> choices=(Map<String,android.widget.Spinner>)cf.get(settings);
        choices.get("Orientation").setSelection(1);choices.get("Response").setSelection(2);
        Map<String,android.widget.EditText> fields=(Map<String,android.widget.EditText>)nf.get(settings);
        fields.get("Dead zone (0–0.5)").setText("0.21");fields.get("Activation distance / display (0.04–0.5)").setText("0.31");fields.get("Sensitivity (0.25–3)").setText("1.7");
        ((SettingsScreen)sf.get(settings)).close();view.finishEditing();
        ControllerConfig saved=new ProfileStore(prefs).load(layout);
        check(saved.get(id)!=null&&saved.get(id).visible&&saved.get(id).x==.82f,"added unified control persists");
        check(!saved.unifiedFloating&&saved.unifiedInvert&&saved.unifiedOrientation.equals("HORIZONTAL")&&saved.unifiedCurve.equals("AGGRESSIVE")&&Math.abs(saved.unifiedDeadzone-.21f)<.001&&Math.abs(saved.unifiedTravel-.31f)<.001&&Math.abs(saved.unifiedSensitivity-1.7f)<.001,"all unified settings persist after closing editor");
        view.selectProfile("FPS");view.selectProfile(layout);
        check(ProfileStore.encode(view.config).toString().equals(ProfileStore.encode(saved).toString()),"switching away and back preserves complete layout");
        // A still-open settings page must not replace a newly added controller instance.
        settings=new ControllerSettings(activity,view,()->{});settings.properties(id,3);
        view.addControl(ControllerConfig.preset("Default").get("A"));String additional=view.editorSelection().id;
        toggles=(Map<String,android.widget.Switch>)tf.get(settings);toggles.get("Invert direction").setChecked(false);
        ((SettingsScreen)sf.get(settings)).close();
        check(view.config.get(additional)!=null,"settings save preserves separately added controls");
        check(new ProfileStore(prefs).load(layout).get(additional)!=null,"merged controls survive store reload");
        settings=new ControllerSettings(activity,view,()->{});settings.properties(id,3);
        view.mutate(()->{view.config.gyroSensitivity=1.4f;view.config.get(id).x=.73f;});
        toggles=(Map<String,android.widget.Switch>)tf.get(settings);toggles.get("Invert direction").setChecked(true);
        ((SettingsScreen)sf.get(settings)).close();
        check(view.config.gyroSensitivity==1.4f&&view.config.get(id).x==.73f,"unified settings preserve newer gyro and geometry changes");
        settings=new ControllerSettings(activity,view,()->{});settings.properties(id,3);view.selectProfile("FPS");
        toggles=(Map<String,android.widget.Switch>)tf.get(settings);toggles.get("Invert direction").setChecked(false);((SettingsScreen)sf.get(settings)).close();
        check(view.profiles.active.equals("FPS")&&view.profiles.builtin(view.profiles.active),"stale settings cannot overwrite a different layout");
        view.selectProfile(layout);
        ControllerConfig copy=view.config.copy(),roundTrip=ProfileStore.decode(ProfileStore.encode(copy));
        check(ProfileStore.encode(copy).toString().equals(ProfileStore.encode(view.config).toString()),"configuration copy preserves all serialized fields");
        check(ProfileStore.encode(roundTrip).toString().equals(ProfileStore.encode(copy).toString()),"all configuration and control fields survive JSON roundtrip");
        view.profiles.importProfile(ProfileStore.encode(copy).toString());ControllerConfig imported=view.profiles.load(view.profiles.active);imported.name=copy.name;
        check(ProfileStore.encode(imported).toString().equals(ProfileStore.encode(copy).toString()),"export import preserves complete configuration except unique name");
        view.selectProfile("Default");view.requestEditing();
        java.lang.reflect.Field prompt=PadView.class.getDeclaredField("layoutEditDialog");prompt.setAccessible(true);
        AlertDialog dialog=(AlertDialog)prompt.get(view);dialog.dismiss();
        check(view.profiles.builtin("Default")&&!view.editing,"cancel leaves built-in untouched");
        view.requestEditing();dialog=(AlertDialog)prompt.get(view);view.resolveLayoutEdit("Default",false);
        check(view.editing&&view.profiles.active.equals("Default")&&!view.profiles.builtin("Default"),"explicit edit keeps template identity");
        view.addControl(ControllerConfig.preset("Default").get("LT/RT"));String inline=view.editorSelection().id;view.finishEditing();
        view.selectProfile("FPS");view.selectProfile("Default");check(view.config.get(inline)!=null,"in-place template edits survive layout switching");
        check(Collections.frequency(view.profiles.ids(),"Default")==1,"customized template appears once in chooser");
        check(new ProfileStore(prefs).load("Default").get(inline)!=null,"customized template survives store reload");
        view.profiles.delete();view.selectProfile("Default");check(view.profiles.builtin("Default"),"factory template remains recoverable");
        settings=new ControllerSettings(activity,view,()->{});settings.properties("LT/RT",3);toggles=(Map<String,android.widget.Switch>)tf.get(settings);toggles.get("Invert direction").setChecked(true);
        check(((AlertDialog)prompt.get(view)).isShowing()&&view.profiles.builtin("Default"),"settings changes also request built-in choice");
        view.resolveLayoutEdit("Default",true);((SettingsScreen)sf.get(settings)).close();
        check(!view.profiles.builtin(view.profiles.active)&&view.config.unifiedInvert&&view.profiles.load("Default").unifiedInvert==false,"settings choice saves pending edit only to selected copy");
        view.selectProfile("Default");
        settings=new ControllerSettings(activity,view,()->{});settings.properties("LT/RT",3);fields=(Map<String,android.widget.EditText>)nf.get(settings);fields.get("Sensitivity (0.25–3)").setText("2.1");settings.setup();
        view.resolveLayoutEdit("Default",false);check(view.profiles.active.equals("Default")&&Math.abs(view.config.unifiedSensitivity-2.1f)<.001,"pending numeric edit survives navigation and in-place choice");((SettingsScreen)sf.get(settings)).close();
        view.profiles.delete();view.selectProfile("Default");
    }
    private void v5Device(Activity activity)throws Exception{
        view.selectProfile("Two-Thumb FPS / GTA On Foot");view.config.haptics=0;
        check(view.gameplayLock.locked,"V5 preset starts locked");
        ControllerConfig restored=ProfileStore.decode(ProfileStore.encode(view.config));check(restored.sameThumbFire&&restored.gameplayLock&&restored.fireGapMs==280,"V5 fields survive schema-v4 roundtrip");ControllerConfig legacyMacros=ControllerConfig.preset("Default");legacyMacros.rtDoubleTapNitro=true;legacyMacros.ltSteering=true;ControllerConfig migratedMacros=ProfileStore.decode(ProfileStore.encode(legacyMacros));check(!migratedMacros.rtDoubleTapNitro&&!migratedMacros.ltSteering&&migratedMacros.macro("builtin:double_tap_nitro")!=null&&migratedMacros.macro("builtin:steering_lt")!=null,"legacy trigger options migrate to editable built-in macros");check(migratedMacros.macroAssignments.stream().anyMatch(a->a.controlId.equals("RT")&&a.trigger.equals("DOUBLE_TAP"))&&migratedMacros.macroAssignments.stream().anyMatch(a->a.controlId.equals("LT")&&a.trigger.equals("HOLD")),"built-in macros retain trigger assignments");
        float[] aim=xy("AIM"),left=xy("LS"),right=xy("RS");touch(MotionEvent.ACTION_DOWN,new int[]{1},aim);touch(MotionEvent.ACTION_UP,new int[]{1},aim);check(view.state.lt==1,"native sticky aim");
        touch(MotionEvent.ACTION_DOWN,new int[]{2},left);left[0]+=view.input.radius(view.config.get("LS"));touch(MotionEvent.ACTION_MOVE,new int[]{2},left);
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{2,3},join(left,right));touch(MotionEvent.ACTION_POINTER_UP|(1<<8),new int[]{2,3},join(left,right));
        touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{2,8},join(left,right));right[0]+=35;touch(MotionEvent.ACTION_MOVE,new int[]{2,8},join(left,right));check(view.state.rt==1&&view.state.rx>0&&view.state.lx>.99&&view.state.lt==1,"native two-thumb walking camera aim fire");
        right[0]=view.getWidth()+50;touch(MotionEvent.ACTION_MOVE,new int[]{2,8},join(left,right));check(view.state.rt==0&&view.input.pointerCount()==2,"native fire exit retains camera");touch(MotionEvent.ACTION_CANCEL,new int[]{2,8},join(left,right));neutral();
        // Hidden administrative region cannot steal a gameplay control placed under it.
        ControllerConfig.Control button=view.config.get("A");button.x=.93f;button.y=.025f;button.w=.12f;button.h=.10f;float[] at=xy("A");touch(MotionEvent.ACTION_DOWN,new int[]{3},at);check(view.state.buttons!=0,"hidden game toolbar lets top-edge controller buttons receive input");touch(MotionEvent.ACTION_UP,new int[]{3},at);view.release();view.selectProfile("Two-Thumb FPS / GTA On Foot");view.unlock();
        view.connected=true;view.startGameMode();float[] corner=xy("LS");corner[0]+=(.5f-view.config.get("LS").x)*view.input.width;corner[1]-=view.config.get("LS").y*view.input.height-12*activity.getResources().getDisplayMetrics().density;int previousPauses=pauses;
        touch(MotionEvent.ACTION_DOWN,new int[]{1},corner);check(view.gameplayLock.holding(),"visible edge handle begins deliberate pause");touch(MotionEvent.ACTION_POINTER_DOWN|(1<<8),new int[]{1,2},join(corner,xy("A")));view.refresh(SystemClock.uptimeMillis()+1600);check(pauses==previousPauses&&view.gameplayLock.locked,"second finger cancels pause");touch(MotionEvent.ACTION_CANCEL,new int[]{1,2},join(corner,xy("A")));
        touch(MotionEvent.ACTION_DOWN,new int[]{1},corner);view.refresh(SystemClock.uptimeMillis()+1500);check(pauses==previousPauses&&!view.gameplayLock.locked&&connections==0,"hold exits game mode directly without a menu");neutral();touch(MotionEvent.ACTION_UP,new int[]{1},corner);
        // A tap never opens controls; the overlay handle stays reachable with a top-edge control.
        view.startGameMode();previousPauses=pauses;touch(MotionEvent.ACTION_DOWN,new int[]{1},corner);touch(MotionEvent.ACTION_UP,new int[]{1},corner);view.refresh(SystemClock.uptimeMillis()+1600);check(pauses==previousPauses,"quick handle tap cannot pause");
        view.config.get("A").x=.5f;view.config.get("A").y=0;view.config.get("A").h=.2f;
        touch(MotionEvent.ACTION_DOWN,new int[]{1},corner);view.refresh(SystemClock.uptimeMillis()+1500);check(!view.gameplayLock.locked&&pauses==previousPauses,"exit handle reachable over arbitrary edge layout");touch(MotionEvent.ACTION_UP,new int[]{1},corner);view.release();
        java.lang.reflect.Field activityPad=MainActivity.class.getDeclaredField("pad");activityPad.setAccessible(true);Object savedPad=activityPad.get(activity);activityPad.set(activity,view);
        try{java.lang.reflect.Method gameMenu=MainActivity.class.getDeclaredMethod("gameMenu");gameMenu.setAccessible(true);
            view.startGameMode();view.state.lt=1;gameMenu.invoke(activity);check(!view.gameplayLock.locked&&!view.settingsOpen&&view.state.lt==0,"Back exits game mode and neutralizes input without a popup");
            view.selectProfile("Default");view.startGameMode();String active=view.profiles.active;view.state.rt=1;check(view.performAccessibilityAction(0x0f010009,null),"next layout is accessible in game mode");check(!view.profiles.active.equals(active)&&view.gameplayLock.locked,"toolbar next changes layout while staying in game mode");neutral();view.toolbarCycle(-1);check(view.profiles.active.equals(active),"toolbar previous returns to previous layout");view.unlock();view.toolbarCycle(1);check(!view.gameplayLock.locked,"toolbar switching preserves unlocked mode");
        }finally{activityPad.set(activity,savedPad);}
        view.saveLayout();
        ControllerSettings settings=new ControllerSettings(activity,view,()->{});settings.show();check(view.settingsOpen,"settings blocks gameplay");touch(MotionEvent.ACTION_DOWN,new int[]{9},xy("LS"));neutral();
        java.lang.reflect.Method category=ControllerSettings.class.getDeclaredMethod("category",int.class);category.setAccessible(true);
        java.lang.reflect.Field sf=ControllerSettings.class.getDeclaredField("screen");sf.setAccessible(true);SettingsScreen screen=(SettingsScreen)sf.get(settings);
        java.lang.reflect.Field wf=SettingsScreen.class.getDeclaredField("window");wf.setAccessible(true);Dialog dialog=(Dialog)wf.get(screen);
        java.lang.reflect.Field nf=ControllerSettings.class.getDeclaredField("numbers");nf.setAccessible(true);
        java.lang.reflect.Field navField=SettingsScreen.class.getDeclaredField("nav");navField.setAccessible(true);
        check(((android.view.ViewGroup)navField.get(screen)).getChildCount()==5,"settings has five primary destinations");category.invoke(settings,13);check(findButton(dialog.getWindow().getDecorView(),"Change layout")==null,"controls omits duplicate layout switcher");
        settings.quickSettings();settings.switcher();
        java.lang.reflect.Method previewRoute=ControllerSettings.class.getDeclaredMethod("preview",String.class);previewRoute.setAccessible(true);previewRoute.invoke(settings,"Default");
        findButton(dialog.getWindow().getDecorView(),"‹ Back").performClick();
        java.lang.reflect.Field headingField=SettingsScreen.class.getDeclaredField("heading");headingField.setAccessible(true);
        check(((android.widget.TextView)headingField.get(screen)).getText().toString().startsWith("Choose a layout"),"preview Back returns to layout chooser");
        findButton(dialog.getWindow().getDecorView(),"‹ Back").performClick();
        check(((android.widget.TextView)headingField.get(screen)).getText().toString().equals("Quick settings"),"nested Back returns to original quick settings");
        settings.switcher();previewRoute.invoke(settings,"Default");dialog.onBackPressed();check(((android.widget.TextView)headingField.get(screen)).getText().toString().startsWith("Choose a layout"),"Android Back follows the same page history");
        category.invoke(settings,3);
        check(findButton(dialog.getWindow().getDecorView(),"How this works")==null,"no page-wide help button");
        android.widget.Button info=findButton(dialog.getWindow().getDecorView(),"ⓘ");
        check(info!=null&&info.getContentDescription().toString().startsWith("About "),"setting help has accessible name");
        if(Build.VERSION.SDK_INT>=26)check(info.getTooltipText()!=null,"setting help supports pointer hover and long press");
        String helpProfile=ProfileStore.encode(view.config).toString();info.performClick();
        java.lang.reflect.Field helpField=SettingsScreen.class.getDeclaredField("helpDialog");helpField.setAccessible(true);
        Dialog help=(Dialog)helpField.get(screen);check(help.isShowing(),"tapping info opens contextual explanation");
        check(helpProfile.equals(ProfileStore.encode(view.config).toString()),"reading help does not change settings");neutral();
        category.invoke(settings,0);check(!help.isShowing(),"leaving page dismisses its help");
        String presetId=view.profiles.active;category.invoke(settings,3);category.invoke(settings,0);check(view.profiles.active.equals(presetId),"opening and leaving form does not copy preset");
        category.invoke(settings,3);@SuppressWarnings("unchecked") Map<String,android.widget.EditText> fields=(Map<String,android.widget.EditText>)nf.get(settings);android.widget.EditText dead=fields.get("Stick dead zone (0–0.4)");float before=view.config.deadzone;
        dead.setText("0.2");dead.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);check(Math.abs(view.config.deadzone-.2f)<.001,"numeric Done saves immediately");
        String customId=view.profiles.active;check(!view.profiles.builtin(customId),"first change creates recoverable custom copy");
        dead.setText("0.19");category.invoke(settings,0);check(Math.abs(view.config.deadzone-.19f)<.001,"navigation commits valid edit");check(view.profiles.active.equals(customId),"later edits reuse same custom profile");
        check(Math.abs(new ProfileStore(activity.getSharedPreferences("instrumentation-layouts",0)).load(customId).deadzone-.19f)<.001,"automatic save persists");
        category.invoke(settings,3);fields=(Map<String,android.widget.EditText>)nf.get(settings);dead=fields.get("Stick dead zone (0–0.4)");dead.setText("99");dead.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);check(Math.abs(view.config.deadzone-.19f)<.001&&dead.getError()!=null,"invalid numeric input preserves saved value");dead.setText("0.17");dead.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);check(Math.abs(view.config.deadzone-.17f)<.001,"invalid edit recovers");findButton(dialog.getWindow().getDecorView(),"Undo changes on this page").performClick();check(Math.abs(view.config.deadzone-.19f)<.001,"undo restores page entry values durably");
        check(findButton(dialog.getWindow().getDecorView(),"Apply")==null&&findButton(dialog.getWindow().getDecorView(),"Cancel")==null,"ordinary settings have no draft footer");
        category.invoke(settings,9);java.lang.reflect.Field toggles=ControllerSettings.class.getDeclaredField("switches");toggles.setAccessible(true);Map<String,android.widget.Switch> switches=(Map<String,android.widget.Switch>)toggles.get(settings);switches.get("Prefer game mode when returning to controller").setChecked(false);check(!view.config.gameplayLock,"toggle saves immediately");switches.get("Prefer game mode when returning to controller").setChecked(true);
        category.invoke(settings,3);fields=(Map<String,android.widget.EditText>)nf.get(settings);android.widget.SeekBar slider=(android.widget.SeekBar)fields.get("Stick dead zone (0–0.4)").getTag();check(slider.getContentDescription().equals("Deadzone"),"slider accessible name");
        for(int section:new int[]{0,13,3,4,5,7,8,9,10,11,2,12}){category.invoke(settings,section);View root=dialog.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));root.layout(0,0,2400,1080);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(2400,1080,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getCacheDir(),"ux-settings-"+section+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();check(root.getWidth()==2400,"native settings render category "+section);}
        if(Build.VERSION.SDK_INT>=30){java.lang.reflect.Field rootField=SettingsScreen.class.getDeclaredField("root");rootField.setAccessible(true);View safeRoot=(View)rootField.get(screen);safeRoot.dispatchApplyWindowInsets(new WindowInsets.Builder().setInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout(),android.graphics.Insets.of(80,20,60,40)).build());check(safeRoot.getPaddingLeft()>=80&&safeRoot.getPaddingRight()>=60&&safeRoot.getPaddingBottom()>=40,"settings respects cutout and navigation insets");}
        java.lang.reflect.Method triggerPage=ControllerSettings.class.getDeclaredMethod("independentTriggers");triggerPage.setAccessible(true);triggerPage.invoke(settings);java.lang.reflect.Field vf=ControllerSettings.class.getDeclaredField("visualization");vf.setAccessible(true);check(((View)vf.get(settings)).getVisibility()==View.VISIBLE,"trigger preview visible without expansion");check(findButton(dialog.getWindow().getDecorView(),"16%")!=null,"trigger travel has a human percentage");check(findButton(dialog.getWindow().getDecorView(),"Try these settings   +")==null,"no hidden generic preview");capture(dialog.getWindow().getDecorView(),activity,"ux-trigger-controls");
        view.connected=false;settings.setup();check(findButton(dialog.getWindow().getDecorView(),"Connect computer")!=null&&findButton(dialog.getWindow().getDecorView(),"Start game mode")==null,"offline readiness offers connection only");view.connected=true;settings.setup();check(findButton(dialog.getWindow().getDecorView(),"Start game mode")!=null,"connected readiness offers game mode");view.connected=false;settings.setup();capture(dialog.getWindow().getDecorView(),activity,"ux-setup");
        check(findButton(dialog.getWindow().getDecorView(),"Edit layout")!=null&&findButton(dialog.getWindow().getDecorView(),"Change layout")!=null,"overview exposes direct layout actions");
        view.config.cameraX=.7f;view.config.cameraY=1.3f;view.saveLayout();category.invoke(settings,5);check(view.config.cameraX==.7f&&view.config.cameraY==1.3f,"opening simplified camera page preserves unequal axes");
        fields=(Map<String,android.widget.EditText>)nf.get(settings);android.widget.EditText camera=fields.get("Camera sensitivity (0.1–5)");camera.setText("1.2");camera.onEditorAction(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);check(Math.abs(view.config.cameraX-1.2f)<.001&&Math.abs(view.config.cameraY-1.2f)<.001,"simple sensitivity updates both camera axes");
        View advancedRow=(View)fields.get("Camera X sensitivity (0.1–5)").getParent().getParent();check(((View)advancedRow.getParent()).getVisibility()==View.GONE,"camera tuning collapsed initially");
        findButton(dialog.getWindow().getDecorView(),"Advanced   +").performClick();switches=(Map<String,android.widget.Switch>)toggles.get(settings);switches.get("Enable same-thumb camera fire").setChecked(false);View timingRow=(View)fields.get("First tap duration (80–250 ms)").getParent().getParent();check(((View)timingRow.getParent()).getVisibility()==View.GONE,"disabled fire hides timing without dropping values");switches.get("Enable same-thumb camera fire").setChecked(true);check(((View)timingRow.getParent()).getVisibility()==View.VISIBLE,"enabled fire reveals timing");
        settings.devicePicker(new String[0],new Runnable[0],false,()->{},()->{});capture(dialog.getWindow().getDecorView(),activity,"ux-picker-empty");
        screen.close();ControllerSettings branch=new ControllerSettings(activity,view,()->{});branch.show();SettingsScreen branchScreen=(SettingsScreen)sf.get(branch);Dialog branchDialog=(Dialog)wf.get(branchScreen);android.view.ViewGroup branchNav=(android.view.ViewGroup)navField.get(branchScreen);
        branchNav.getChildAt(2).performClick();findButton(branchDialog.getWindow().getDecorView(),"Vibration   ›").performClick();branchNav.getChildAt(4).performClick();findButton(branchDialog.getWindow().getDecorView(),"Layout options   ›").performClick();branchDialog.onBackPressed();check(((android.widget.TextView)headingField.get(branchScreen)).getText().toString().equals("More"),"Back returns to current section parent");branchDialog.onBackPressed();check(!branchScreen.showing()&&!view.settingsOpen,"Back at section root closes settings without replaying other sections");
        branch.show();branchScreen=(SettingsScreen)sf.get(branch);branchDialog=(Dialog)wf.get(branchScreen);View close=findDescription(branchDialog.getWindow().getDecorView(),"Close settings");check(close!=null,"settings exposes an explicit close button");close.performClick();check(!branchScreen.showing(),"close button dismisses settings directly");
        view.selectProfile("Default");String originalPreset=ProfileStore.encode(view.config).toString();view.unlock();android.graphics.RectF bounds=view.controllerBounds();float[] editAt={bounds.left+bounds.width()*.85f,bounds.top+10};view.requestEditing();java.lang.reflect.Field editPrompt=PadView.class.getDeclaredField("layoutEditDialog");editPrompt.setAccessible(true);AlertDialog choice=(AlertDialog)editPrompt.get(view);check(choice!=null&&choice.isShowing()&&!view.editing,"built-in editor asks before changing or copying");view.resolveLayoutEdit(view.profiles.active,true);check(view.editing&&!view.profiles.builtin(view.profiles.active),"direct editor automatically creates custom copy");check(ProfileStore.encode(view.profiles.load("Default")).toString().equals(originalPreset),"editor keeps built-in untouched");
        View editorRoot=(View)view.getParent();capture(editorRoot,activity,"ux-editor-canvas");check(findButton(editorRoot,"Save")!=null&&findButton(editorRoot,"＋")!=null&&findButton(editorRoot,"Test")!=null,"editor has dedicated accessible toolbar");
        float[] controlPoint=xy("A");touch(MotionEvent.ACTION_DOWN,new int[]{3},controlPoint);touch(MotionEvent.ACTION_UP,new int[]{3},controlPoint);check(view.editorSelection()!=null&&!view.settingsOpen&&findButton(editorRoot,"Gesture action…")!=null,"selection opens contextual panel without Settings");neutral();capture(editorRoot,activity,"ux-editor-selection");
        ControllerSettings propertySheet=new ControllerSettings(activity,view,()->{});propertySheet.properties(view.editorSelection().id,0);SettingsScreen propertyScreen=(SettingsScreen)sf.get(propertySheet);Dialog propertyDialog=(Dialog)wf.get(propertyScreen);check(findButton(propertyDialog.getWindow().getDecorView(),"‹ Back")!=null,"property sheet has Back navigation");findButton(propertyDialog.getWindow().getDecorView(),"‹ Back").performClick();check(view.editing&&!view.settingsOpen,"closing properties preserves editor mode");
        LayoutPreview sandboxPreview=new LayoutPreview(activity,view.config,true);sandboxPreview.layout(0,0,1000,500);ControllerConfig.Control previewA=view.config.get("A");MotionEvent previewDown=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,previewA.x*1000,previewA.y*500,0);sandboxPreview.onTouchEvent(previewDown);previewDown.recycle();java.lang.reflect.Field sandboxField=LayoutPreview.class.getDeclaredField("sandbox");sandboxField.setAccessible(true);ControllerInput isolated=(ControllerInput)sandboxField.get(sandboxPreview);check((isolated.state.buttons&1)!=0&&view.state.buttons==0,"editor test preview uses isolated input state");
        float oldX=view.config.get("A").x;view.mutate(()->view.config.get("A").x=.6f);view.history(false);check(Math.abs(view.config.get("A").x-oldX)<.001,"dedicated editor undo persists original position");view.history(true);check(Math.abs(view.config.get("A").x-.6f)<.001,"dedicated editor redo persists moved position");view.addRacingControl();check(view.config.integrated&&view.editorSelection().kind.equals("stick")&&view.editorSelection().bit==0,"editor adds steering and pedals to the left stick");view.mutate(()->view.config.integrated=false);check(!view.config.integrated,"editor can restore a normal steering stick");view.finishEditing();check(!view.editing&&findButton((View)view.getParent(),"Save")==null,"closing editor removes its chrome");
        view.connected=true;view.startGameMode();snapshot(activity,"game-mode");check(view.performAccessibilityAction(0x0f010001,null),"game mode exposes settings accessibility action");check(!view.settingsOpen&&view.gameplayLock.locked,"return re-locks and closes settings");view.release();neutral();
        android.view.ContextThemeWrapper large=new android.view.ContextThemeWrapper(activity,android.R.style.Theme_Material_NoActionBar);android.content.res.Configuration configuration=new android.content.res.Configuration(activity.getResources().getConfiguration());configuration.fontScale=1.5f;large.applyOverrideConfiguration(configuration);
        ControllerSettings accessible=new ControllerSettings(large,view,()->{});accessible.show();category.invoke(accessible,3);SettingsScreen largeScreen=(SettingsScreen)sf.get(accessible);Dialog largeDialog=(Dialog)wf.get(largeScreen);View root=largeDialog.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(1280,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(720,View.MeasureSpec.EXACTLY));root.layout(0,0,1280,720);android.widget.Button reset=findButton(root,"Reset this section…");check(reset!=null&&root.getHeight()==720,"narrow large-font scroll contains contextual reset");check(findButton(root,"Apply")==null,"large font has no fixed footer");
        android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(1280,720,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getCacheDir(),"ux-settings-large-font.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();largeScreen.close();PhoneHaptics hapticDraft=new PhoneHaptics(hapticPersistenceContext(activity));hapticDraft.enabled=false;hapticDraft.hostEnabled=false;hapticDraft.localEnabled=true;hapticDraft.quiet=true;hapticDraft.texture=true;hapticDraft.strength=.37f;hapticDraft.heavyWeight=.22f;hapticDraft.lightWeight=.91f;hapticDraft.save();check(Math.abs(new PhoneHaptics(hapticPersistenceContext(activity)).strength-.37f)<.001,"haptic settings persist through a new sink instance");view.selectProfile("Default");appearanceDevice(activity);
    }
    private void capture(View root,Context context,String name)throws Exception{root.measure(View.MeasureSpec.makeMeasureSpec(2400,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));root.layout(0,0,2400,1080);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(2400,1080,android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(context.getCacheDir(),name+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private View findDescription(View root,String description){if(description.equals(String.valueOf(root.getContentDescription())))return root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){View found=findDescription(group.getChildAt(i),description);if(found!=null)return found;}}return null;}
    private android.widget.Button findButton(View root,String text){if(root instanceof android.widget.Button&&text.equals(((android.widget.Button)root).getText().toString()))return (android.widget.Button)root;if(root instanceof android.view.ViewGroup){android.view.ViewGroup group=(android.view.ViewGroup)root;for(int i=0;i<group.getChildCount();i++){android.widget.Button found=findButton(group.getChildAt(i),text);if(found!=null)return found;}}return null;}
    private void chordsPhase(Bundle result)throws Exception{
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        SharedPreferences prefs=activity.getSharedPreferences("instrumentation-chords",0);
        View[] original={null};Dialog[] mapper={null};String[] customId={null};
        try{
            uiCheck(()->{
                closeStartup(activity);prefs.edit().clear().commit();
                original[0]=((FrameLayout)activity.findViewById(android.R.id.content)).getChildAt(0);
                Context context=new ContextWrapper(activity){@Override public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}};
                view=new PadView(context,new PadView.Actions(){public void settings(){}public void connect(){}public void changed(){}});
                activity.setContentView(view);view.layout(0,0,2200,1000);
                view.profiles.create("Chord test",ControllerConfig.preset("Default"));view.selectProfile(view.profiles.active);view.startEditing();
                mapper[0]=AutomationEditor.show(view,"A");
            });workspaceIdle();
            uiCheck(()->{View root=mapper[0].getWindow().getDecorView();check(findButton(root,"Combination")==null,"separate combination action removed");tapDiagram(root,"KEY_CTRL");tapDiagram(root,"KEY_C");});workspaceIdle();
            uiCheck(()->{
                View root=mapper[0].getWindow().getDecorView();int before=view.config.controls.size();findButton(root,"New custom button").performClick();
                check(view.config.controls.size()==before+1,"mapper creates a custom button");
                ControllerConfig.Control custom=view.config.controls.get(view.config.controls.size()-1);customId[0]=custom.id;
                check(custom.buttons==0&&custom.binding.equals("KEY_CTRL+KEY_C"),"Keyboard selection assigns a simultaneous key chord");
                ControllerConfig reopened=new ProfileStore(prefs).load(view.profiles.active);
                check(reopened.get(customId[0]).binding.equals("KEY_CTRL+KEY_C")&&reopened.get(customId[0]).buttons==0,"custom chord persists after reload");
                capture(mapper[0].getWindow().getDecorView(),activity,"keyboard-chord-custom-button");
            });
            result.putString("stream","\nPASS: "+checks+" real Android combination/custom-button UI assertions.\n");
        }finally{runOnMainSync(()->{if(mapper[0]!=null)mapper[0].dismiss();if(view!=null)view.release();if(original[0]!=null)activity.setContentView(original[0]);prefs.edit().clear().commit();});}
        finish(Activity.RESULT_OK,result);
    }
    private void macroMigrationPhase(Bundle result){try{ControllerConfig legacy=ControllerConfig.preset("Default");legacy.rtDoubleTapNitro=true;legacy.ltSteering=true;ControllerConfig config=ProfileStore.decode(ProfileStore.encode(legacy));check(!config.rtDoubleTapNitro&&!config.ltSteering,"legacy flags cleared");check(config.macro("builtin:double_tap_nitro")!=null&&config.macro("builtin:steering_lt")!=null,"built-in definitions restored");check(config.macroAssignments.stream().anyMatch(a->a.controlId.equals("RT")&&a.trigger.equals("DOUBLE_TAP"))&&config.macroAssignments.stream().anyMatch(a->a.controlId.equals("LT")&&a.trigger.equals("HOLD")),"legacy assignments migrated");config.macro("builtin:steering_lt").program="steer RIGHT";ControllerConfig edited=ProfileStore.decode(ProfileStore.encode(config));check(edited.macro("builtin:steering_lt").program.equals("steer RIGHT"),"built-in edits persist");result.putString("stream","\nPASS: "+checks+" built-in macro migration and persistence assertions.\n");finish(Activity.RESULT_OK,result);}catch(Throwable failure){result.putString("stream","\nFAIL: "+android.util.Log.getStackTraceString(failure));finish(Activity.RESULT_CANCELED,result);}}
    private void automationPhase(Bundle result)throws Exception{
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        final Dialog[] dialog={null};final View[] original={null};SharedPreferences prefs=activity.getSharedPreferences("instrumentation-workspace",0);
        try{
            runOnMainSync(()->{try{
                prefs.edit().clear().commit();Context context=new ContextWrapper(activity){@Override public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}};
                FrameLayout content=activity.findViewById(android.R.id.content);original[0]=content.getChildAt(0);FrameLayout host=new FrameLayout(activity);
                view=new PadView(context,new PadView.Actions(){public void settings(){}public void connect(){}public void changed(){}});host.addView(view);activity.setContentView(host);
                ControllerConfig config=ControllerConfig.preset("Default");config.get("A").source="KEY_CTRL+KEY_A";config.get("A").binding="KEY_SPACE";config.get("B").program="press A+KEY_W\nwait 100\nrelease A+KEY_W";config.get("B").macroTriggers="X,Y";
                view.profiles.create("Workspace test",config);view.selectProfile(view.profiles.active);view.startEditing();
                check(findButton(host,"Mapper")!=null&&findButton(host,"Macros")!=null&&findButton(host,"Save")!=null,"compact editor toolbar");
                dialog[0]=AutomationEditor.show(view,"A");
            }catch(Throwable e){failure=e;}});workspaceIdle();
            runOnMainSync(()->{try{
                View root=dialog[0].getWindow().getDecorView();tapDiagram(root,"KEY_B");findButton(root,"A").performClick();
                check(view.config.get("A").binding.equals("KEY_B")&&AutomationEditor.source(view.config.get("A"),"").equals("KEY_CTRL+KEY_A"),"tap directly assigns output and preserves original hardware chord");
            }catch(Throwable e){failure=e;}});workspaceIdle();
            runOnMainSync(()->{try{View root=dialog[0].getWindow().getDecorView();
                tapDiagram(root,"KEY_W");findButton(root,"LS ↑").performClick();check(view.config.get("LS").mapUp.equals("KEY_W"),"tap assigns directional output without second chooser");
                findButton(root,"Pin").performClick();findButton(root,"Panel ›").performClick();check(findButton(root,"Pin")==null&&findButton(root,"Unpin")==null,"panel collapses");findButton(root,"‹ Panel").performClick();check(findButton(root,"Unpin")!=null,"pin survives collapse and reopen");
            }catch(Throwable e){failure=e;}});workspaceIdle();
            // Use real Android drag dispatch in both directions, not direct calls to binding code.
            workspaceDrag(dialog[0],"LS ↓","KEY_S",true);workspaceIdle();
            runOnMainSync(()->check(view.config.get("LS").mapDown.equals("KEY_S"),"reverse drag maps virtual to physical"));
            workspaceDrag(dialog[0],"LS →","KEY_D",false);workspaceIdle();
            runOnMainSync(()->{try{
                check(view.config.get("LS").mapRight.equals("KEY_D"),"physical drag maps to virtual");
                ControllerConfig reopened=new ProfileStore(prefs).load(view.profiles.active);check(reopened.get("LS").mapUp.equals("KEY_W")&&reopened.get("LS").mapDown.equals("KEY_S")&&reopened.get("A").binding.equals("KEY_B")&&AutomationEditor.source(reopened.get("A"),"").equals("KEY_CTRL+KEY_A"),"mapper fields persist and reopen");
                ControllerConfig imported=ProfileStore.decode(ProfileStore.encode(reopened));check(imported.get("B").macroTriggers.equals("X,Y")&&imported.get("B").program.equals(reopened.get("B").program),"existing macros and trigger links survive mapping edits");
                View root=dialog[0].getWindow().getDecorView();findButton(root,"Mouse").performClick();capture(root,activity,"redesign-mouse-before");tapDiagram(root,"MOUSE_MOVE");findButton(root,"LS").performClick();
                check(DesktopBindings.contains(view.config.get("LS"),"","MOUSE_MOVE"),"Pointer assigns all four directions in one step");
                capture(root,activity,"redesign-pointer-assigned");tapDiagram(root,"MOUSE_LEFT");findButton(root,"LT").performClick();
                check(view.config.get("LT").binding.equals("MOUSE_LEFT"),"mouse click assigns output without second selection");
                check(new ProfileStore(prefs).load(view.profiles.active).get("LS").mapLeft.equals("MOUSE_X=-500"),"pointer mapping persists");
                ControllerConfig legacy=reopened.copy();ControllerConfig.Control broken=legacy.get("LS");
                broken.binding="";broken.mapUp="MOUSE_Y=-500+MOUSE_X=500+MOUSE_Y=500+MOUSE_X=-500";broken.mapDown=broken.mapLeft=broken.mapRight="";broken.mappings.clear();
                String[] legacyTokens={"MOUSE_Y_NEG","MOUSE_Y_POS","MOUSE_X_NEG","MOUSE_X_POS"},legacyDirections={"up","down","left","right"};
                for(int i=0;i<4;i++){ControllerConfig.Mapping mapping=new ControllerConfig.Mapping();mapping.source=legacyTokens[i];mapping.direction=legacyDirections[i];broken.mappings.add(mapping);}
                ControllerConfig repaired=ProfileStore.decode(ProfileStore.encode(legacy));
                check(DesktopBindings.contains(repaired.get("LS"),"","MOUSE_MOVE"),"saved cancelling pointer configuration repairs on load");
                check(repaired.get("LS").mappings.size()==4&&legacy.get("LS").mapDown.isEmpty(),"repair preserves sources and does not modify serialized original");

                ControllerConfig.Control rs=view.config.get("RS");ControllerConfig.Mapping right=new ControllerConfig.Mapping(),left=new ControllerConfig.Mapping();right.source="MOUSE_X_POS";right.direction="right";left.source="MOUSE_X_NEG";left.direction="left";
                view.mutate(()->{view.config.get("RS").mappings.add(right);view.config.get("RS").mappings.add(left);});
                view.mutate(()->view.config.get("RS").mapping(left.id).sensitivity=.4f);
                ControllerConfig persisted=new ProfileStore(prefs).load(view.profiles.active);check(persisted.get("RS").mapping(right.id).sensitivity==1&&persisted.get("RS").mapping(left.id).sensitivity==.4f,"opposite mapping edit persists independently");
                view.mutate(()->view.config.get("RS").mappings.removeIf(m->m.id.equals(left.id)));persisted=new ProfileStore(prefs).load(view.profiles.active);check(persisted.get("RS").mapping(right.id)!=null&&persisted.get("RS").mapping(left.id)==null,"delete persists only selected mapping");
                view.history(false);check(view.config.get("RS").mapping(left.id)!=null,"mapping delete undo");view.history(true);check(view.config.get("RS").mapping(left.id)==null,"mapping delete redo");
                ControllerConfig.Macro extra=new ControllerConfig.Macro();extra.program="press Y\nwait 100";view.mutate(()->{view.config.macros.add(extra);view.config.assign(extra.id,"A","PRESS");view.config.assign("legacy:B","A","TAP");});
                persisted=ProfileStore.decode(ProfileStore.encode(view.config));check(persisted.macros.size()==2&&persisted.macroAssignments.stream().filter(link->link.controlId.equals("A")).count()==2,"multiple macro assignments survive export/import");
                view.mutate(()->view.config.deleteMacro(extra.id));view.mutate(()->view.config.macroAssignments.removeIf(link->link.controlId.equals("A")));check(view.config.macro("legacy:B")!=null,"unassign preserves reusable macro");
                capture(dialog[0].getWindow().getDecorView(),activity,"redesign-keyboard");
                findButton(dialog[0].getWindow().getDecorView(),"Mouse").performClick();capture(dialog[0].getWindow().getDecorView(),activity,"redesign-mouse");
                check(findButton(dialog[0].getWindow().getDecorView(),"Controllers")==null,"controller tab hidden without removing backend");
                dialog[0].dismiss();dialog[0]=MacroEditor.show(view,"B",()->{});findButton(dialog[0].getWindow().getDecorView(),"Test").performClick();findButton(dialog[0].getWindow().getDecorView(),"Step").performClick();check(view.state.buttons==0,"macro preview emits no live controller output");findButton(dialog[0].getWindow().getDecorView(),"Back to actions").performClick();
                findButton(dialog[0].getWindow().getDecorView(),"◷  Delay").performClick();findButton(dialog[0].getWindow().getDecorView(),"Apply").performClick();
                check(findButton(dialog[0].getWindow().getDecorView(),"↶").isEnabled(),"node insertion enables undo");findButton(dialog[0].getWindow().getDecorView(),"↶").performClick();check(findButton(dialog[0].getWindow().getDecorView(),"↷").isEnabled(),"node undo enables redo");findButton(dialog[0].getWindow().getDecorView(),"↷").performClick();findButton(dialog[0].getWindow().getDecorView(),"↶").performClick();
                capture(dialog[0].getWindow().getDecorView(),activity,"redesign-macro");findButton(dialog[0].getWindow().getDecorView(),"Save").performClick();
                check(view.config.macros.size()==1&&view.config.macroAssignments.size()==3,"visual macro retains reusable definition and assignments");
                check(!dialog[0].isShowing(),"macro save closes workspace");view.settingsOpen=false;
                view.multiSelect=true;view.editorSelectionIds.clear();touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("A"));touch(MotionEvent.ACTION_UP,new int[]{1},xy("A"));touch(MotionEvent.ACTION_DOWN,new int[]{1},xy("B"));touch(MotionEvent.ACTION_UP,new int[]{1},xy("B"));check(view.selectedControls().size()==2,"canvas taps select multiple controls");
                float beforeAlign=view.config.get("A").x;view.alignSelection("left");check(Math.abs(view.config.get("A").x-view.config.get("A").w/2-view.config.get("B").x+view.config.get("B").w/2)<.001,"multi-selection aligns edges");view.history(false);check(Math.abs(view.config.get("A").x-beforeAlign)<.001,"alignment undo restores coordinates");view.history(true);check(Math.abs(view.config.get("A").x-view.config.get("A").w/2-view.config.get("B").x+view.config.get("B").w/2)<.001,"alignment redo restores operation");view.multiSelect=false;
                view.finishEditing();view.settingsOpen=false;
                ControllerConfig.Mapping key=new ControllerConfig.Mapping();key.source="KEY_1";view.config.get("B").mappings.add(key);check(HardwareInput.key(view,new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_1)),"mapped keyboard triggers macro");check((view.state.buttons&1)!=0,"saved macro executes controller output");HardwareInput.key(view,new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_1));view.release();check(view.state.buttons==0,"macro interruption releases controller");
            }catch(Throwable e){failure=e;}});workspaceIdle();
        }finally{runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();if(view!=null){view.release();view.finishEditing();}if(original[0]!=null)activity.setContentView(original[0]);prefs.edit().clear().commit();});}
        if(failure!=null)throw new AssertionError(failure);result.putString("stream","\nPASS: "+checks+" Android visual workspace assertions.\n");finish(Activity.RESULT_OK,result);
    }
    private void workspaceIdle()throws Exception{waitForIdleSync();Thread.sleep(180);if(failure!=null)throw new AssertionError(failure);}
    private DeviceDiagram deviceDiagram(View root){if(root instanceof DeviceDiagram)return (DeviceDiagram)root;if(root instanceof ViewGroup)for(int i=0;i<((ViewGroup)root).getChildCount();i++){DeviceDiagram d=deviceDiagram(((ViewGroup)root).getChildAt(i));if(d!=null)return d;}return null;}
    private float[] diagramPoint(View root,String token)throws Exception{
        DeviceDiagram diagram=deviceDiagram(root);java.lang.reflect.Field parts=DeviceDiagram.class.getDeclaredField("parts");parts.setAccessible(true);
        for(Object part:(java.util.List<?>)parts.get(diagram)){java.lang.reflect.Field t=part.getClass().getDeclaredField("token"),b=part.getClass().getDeclaredField("box");t.setAccessible(true);b.setAccessible(true);if(!token.equals(t.get(part)))continue;android.graphics.RectF box=(android.graphics.RectF)b.get(part);float[] out=new float[2];String[] names={"scale","ox","oy"};float[] values=new float[3];for(int i=0;i<3;i++){java.lang.reflect.Field f=DeviceDiagram.class.getDeclaredField(names[i]);f.setAccessible(true);values[i]=f.getFloat(diagram);}out[0]=values[1]+box.centerX()*values[0];out[1]=values[2]+box.centerY()*values[0];return out;}throw new AssertionError("No diagram input "+token);
    }
    private void tapDiagram(View root,String token)throws Exception{DeviceDiagram d=deviceDiagram(root);float[] xy=diagramPoint(root,token);long now=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(now,now,MotionEvent.ACTION_DOWN,xy[0],xy[1],0),up=MotionEvent.obtain(now,now+20,MotionEvent.ACTION_UP,xy[0],xy[1],0);d.dispatchTouchEvent(down);d.dispatchTouchEvent(up);down.recycle();up.recycle();}
    private void workspaceDrag(Dialog dialog,String control,String token,boolean reverse)throws Exception{
        final float[][] points=new float[2][];
        runOnMainSync(()->{try{View root=dialog.getWindow().getDecorView();View target=findButton(root,control);if(target==null)throw new AssertionError("Missing target "+control);target.requestRectangleOnScreen(new android.graphics.Rect(0,0,target.getWidth(),target.getHeight()),true);}catch(Throwable t){failure=t;}});workspaceIdle();
        runOnMainSync(()->{try{View root=dialog.getWindow().getDecorView();View target=findButton(root,control);int[] where=new int[2];target.getLocationOnScreen(where);points[0]=new float[]{where[0]+target.getWidth()/2f,where[1]+target.getHeight()/2f};float[] physical=diagramPoint(root,token);deviceDiagram(root).getLocationOnScreen(where);points[1]=new float[]{physical[0]+where[0],physical[1]+where[1]};}catch(Throwable t){failure=t;}});workspaceIdle();
        float[] from=points[reverse?0:1],to=points[reverse?1:0];long start=SystemClock.uptimeMillis();MotionEvent down=MotionEvent.obtain(start,start,MotionEvent.ACTION_DOWN,from[0],from[1],0);getUiAutomation().injectInputEvent(down,true);down.recycle();Thread.sleep(650);
        for(int i=1;i<=12;i++){MotionEvent move=MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_MOVE,from[0]+(to[0]-from[0])*i/12,from[1]+(to[1]-from[1])*i/12,0);getUiAutomation().injectInputEvent(move,true);move.recycle();Thread.sleep(25);}MotionEvent up=MotionEvent.obtain(start,SystemClock.uptimeMillis(),MotionEvent.ACTION_UP,to[0],to[1],0);getUiAutomation().injectInputEvent(up,true);up.recycle();
    }
    private byte[] touchDesktopReport(){
        ControllerConfig config=ControllerConfig.preset("Custom Blank Layout");config.controls.clear();
        ControllerConfig.Control surface=new ControllerConfig.Control("surface","trackpad",0,.5f,.5f,.5f,.6f);
        ControllerConfig.Control chord=new ControllerConfig.Control("chord","button",0,.9f,.8f,.1f,.1f);
        chord.buttons=0;chord.binding="KEY_A+KEY_CTRL+MOUSE_LEFT+MOUSE_RIGHT+MOUSE_MIDDLE+MOUSE_BACK+MOUSE_FORWARD";
        config.controls.add(surface);config.controls.add(chord);
        ControllerInput input=new ControllerInput(config);
        check(input.down(1,550,240,100),"touch pointer captured");input.move(1,567,221,110);
        check(input.down(2,600,250,120),"second touch scroll captured");input.move(1,567,141,130);
        check(input.down(3,990,384,140),"simultaneous keyboard and mouse buttons captured");input.update(150);
        byte[] frame=input.automation.desktop();input.release();
        check(java.nio.ByteBuffer.wrap(frame).getShort(33)==17&&java.nio.ByteBuffer.wrap(frame).getShort(35)==-19&&java.nio.ByteBuffer.wrap(frame).getShort(37)==2,"touch processing generated exact relative movement and wheel");
        check(frame[32]==31&&frame[0]==16&&frame[28]==1,"touch processing generated simultaneous buttons and keys");
        return frame;
    }
    private void nativeDesktop(Bundle result)throws Exception {
        java.io.File ready=new java.io.File(getTargetContext().getCacheDir(),"native-desktop-ready");
        ready.delete();
        java.io.File waiting=new java.io.File(getTargetContext().getCacheDir(),"native-desktop-waiting");waiting.delete();
        MainActivity activity=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        waitForIdleSync();
        runOnMainSync(()->{try{closeStartup(activity);}catch(Exception e){failure=e;}});
        if(failure!=null)throw new AssertionError(failure);
        java.lang.reflect.Field field=MainActivity.class.getDeclaredField("bluetooth");
        field.setAccessible(true);
        BluetoothPad transport=(BluetoothPad)field.get(activity);
        String name=arguments.getString("bluetoothHost","cachyos-x8664");
        android.bluetooth.BluetoothDevice peer=null;
        for(android.bluetooth.BluetoothDevice candidate:transport.paired())if(name.equals(candidate.getName()))peer=candidate;
        check(peer!=null,"paired Bluetooth host exists");
        android.bluetooth.BluetoothDevice host=peer;
        runOnMainSync(()->transport.connect(host));
        try {
            long until=SystemClock.uptimeMillis()+20000;
            while(!transport.connected()&&SystemClock.uptimeMillis()<until)Thread.sleep(50);
            check(transport.connected(),"native HID connection established before capture");
            waiting.createNewFile();until=SystemClock.uptimeMillis()+12000;
            while(!ready.exists()&&SystemClock.uptimeMillis()<until)Thread.sleep(50);
            check(transport.connected()&&ready.exists(),"host grabbed native input interfaces before test");
            byte[] desktop=touchDesktopReport();
            long before=transport.desktopReports;
            for(int repeat=0;repeat<3;repeat++){
                byte[] source=mappedSourceReport();
                runOnMainSync(()->{transport.sendSource(source);transport.sendDesktop(desktop);});Thread.sleep(150);
                runOnMainSync(()->transport.sendDesktop(new byte[Automation.DESKTOP_SIZE]));Thread.sleep(150);
            }
            check(transport.desktopReports>=before+6,"Android accepted keyboard and mouse HID reports");
            result.putString("stream","\nPASS: "+checks+" native desktop HID generation assertions; host must verify evdev events independently.\n");
            finish(Activity.RESULT_OK,result);
        } finally {
            runOnMainSync(transport::neutral);
            ready.delete();
        }
    }
    private byte[] mappedSourceReport(){
        ControllerConfig config=ControllerConfig.preset("Custom Blank Layout");config.controls.clear();
        ControllerConfig.Control b=new ControllerConfig.Control("mapped-b","button",PadState.B,.2f,.5f,.12f,.2f);b.binding="MOUSE_LEFT";
        ControllerConfig.Control lt=new ControllerConfig.Control("mapped-lt","trigger",0,.5f,.5f,.12f,.2f);lt.mode="DIGITAL";lt.binding="KEY_A";
        ControllerConfig.Control rt=new ControllerConfig.Control("mapped-rt","trigger",1,.8f,.5f,.12f,.2f);rt.mode="DIGITAL";rt.binding="KEY_CTRL";
        config.controls.add(b);config.controls.add(lt);config.controls.add(rt);
        ControllerInput input=new ControllerInput(config);
        check(input.down(1,220,240,100)&&input.down(2,550,240,100)&&input.down(3,880,240,100),"mapped B/LT/RT source touches");input.update(120);
        check(input.state.buttons==0&&input.state.lt==0&&input.state.rt==0,"source metadata does not inject controller output");
        check(input.sourceState.buttons==(1<<PadState.B)&&input.sourceState.lt==1&&input.sourceState.rt==1,"source metadata retains mapped B/LT/RT");
        return input.sourceState.report();
    }
    private void desktopTransport(Bundle result)throws Exception {
        String host=arguments.getString("host"), key=arguments.getString("key");
        int port=Integer.parseInt(arguments.getString("port","39570"));
        check(host!=null&&key!=null,"explicit isolated companion required");
        for(int attempt=0;attempt<2;attempt++){
            java.util.concurrent.CountDownLatch ready=new java.util.concurrent.CountDownLatch(1);
            final String[] status={"not started"};
            try(WifiPad transport=new WifiPad(new WifiPad.Listener(){
                public void status(String text){status[0]=text;if(text.contains("authenticated"))ready.countDown();}
                public void rumble(int low,int high,int ms){}
            })){
                transport.connect(host,port,key);
                check(ready.await(8,java.util.concurrent.TimeUnit.SECONDS),"real phone UDP authentication: "+status[0]);
                byte[] desktop=touchDesktopReport();
                transport.sendDesktop(desktop);
                transport.sendSource(mappedSourceReport());
                PadState controller=new PadState();
                controller.buttons=1<<PadState.B;controller.hat=1;
                controller.lx=12000f/32767;controller.ly=-15000f/32767;
                controller.rx=6000f/32767;controller.ry=-8000f/32767;
                controller.lt=.5f;controller.rt=1;
                transport.send(controller.report());
                Thread.sleep(250);
                // First cycle explicitly releases; second loses the transport while
                // held, so the receiver must release every input on timeout.
                if(attempt==0){
                    transport.sendDesktop(new byte[Automation.DESKTOP_SIZE]);
                    transport.send(new PadState().report());
                }
                Thread.sleep(250);
                check(transport.desktopGenerated>=(attempt==0?2:1)&&transport.desktopSerialized>0&&transport.desktopSendAttempts>0&&transport.desktopAcknowledged>0,"desktop generation serialization send and acknowledgement");
            }
            Thread.sleep(700);
        }
        result.putString("stream","\nPASS: "+checks+" real phone desktop transport and reconnect assertions.\n");
        finish(Activity.RESULT_OK,result);
    }
    private void nearbyPhase(Bundle result)throws Exception{
        String host=arguments.getString("host");if(host==null)throw new IllegalArgumentException("Explicit test companion host required");
        final CompanionLink.Computer[] found={null};final String[] key={null};java.util.concurrent.CountDownLatch discovered=new java.util.concurrent.CountDownLatch(1),paired=new java.util.concurrent.CountDownLatch(1);
        CompanionLink.Listener listener=new CompanionLink.Listener(){
            public void computer(CompanionLink.Computer computer){if(computer.host.equals(host)){found[0]=computer;discovered.countDown();}}
            public void status(String message){android.util.Log.i("PhonePadPairingTest",message);}
            public void paired(CompanionLink.Computer computer,String token){key[0]=token;paired.countDown();}
        };
        try(CompanionLink discovery=new CompanionLink(listener)){discovery.scan("",39570);check(discovered.await(6,java.util.concurrent.TimeUnit.SECONDS),"real LAN companion discovery");}
        check(found[0].name.length()>0&&found[0].id.length()==64,"discovered name and identity");
        try(CompanionLink pairing=new CompanionLink(listener)){pairing.pair(found[0],"PhonePad device verification");check(paired.await(65,java.util.concurrent.TimeUnit.SECONDS),"approved encrypted key exchange with Python companion");}
        check(key[0]!=null&&key[0].length()==32,"128-bit key provisioned without entry");
        SharedPreferences prefs=getTargetContext().getSharedPreferences("instrumentation-pairing",0);try{
            prefs.edit().putString("key."+found[0].id,key[0]).commit();check(prefs.getString("key."+found[0].id,"").equals(key[0]),"key saved by computer identity");
            for(int attempt=0;attempt<2;attempt++){
                final java.util.concurrent.CountDownLatch connected=new java.util.concurrent.CountDownLatch(1);
                try(WifiPad transport=new WifiPad(new WifiPad.Listener(){public void status(String text){if(text.contains("authenticated"))connected.countDown();}public void rumble(int a,int b,int ms){}})){
                    transport.connect(found[0].host,found[0].port,prefs.getString("key."+found[0].id,""));check(connected.await(5,java.util.concurrent.TimeUnit.SECONDS),attempt==0?"provisioned key authenticates PP01":"saved key reconnects without approval");transport.neutral();
                }
                Thread.sleep(650);
            }
        }finally{prefs.edit().clear().commit();}
        result.putString("stream","\nPASS: "+checks+" real LAN discovery / encrypted pairing / reconnect assertions.\n");finish(Activity.RESULT_OK,result);
    }
    private void appearanceDevice(Activity activity)throws Exception{
        Context context=view.getContext();String before=ProfileStore.encode(view.config).toString();SharedPreferences prefs=context.getSharedPreferences("appearance",0);
        ControllerSettings settings=new ControllerSettings(context,view,()->{});java.lang.reflect.Method appearance=ControllerSettings.class.getDeclaredMethod("appearance");appearance.setAccessible(true);
        java.lang.reflect.Field sf=ControllerSettings.class.getDeclaredField("screen");sf.setAccessible(true);java.lang.reflect.Field wf=SettingsScreen.class.getDeclaredField("window");wf.setAccessible(true);
        try{
            appearance.invoke(settings);SettingsScreen screen=(SettingsScreen)sf.get(settings);Dialog dialog=(Dialog)wf.get(screen);
            findButton(dialog.getWindow().getDecorView(),"○  Light").performClick();screen=(SettingsScreen)sf.get(settings);dialog=(Dialog)wf.get(screen);
            check(new AppAppearance(context).light,"Light theme persists and applies");findButton(dialog.getWindow().getDecorView(),"○  Violet").performClick();screen=(SettingsScreen)sf.get(settings);dialog=(Dialog)wf.get(screen);
            check(prefs.getString("accent","").equals("Violet")&&SettingsScreen.ACCENT==new AppAppearance(context).accent,"accent applies to settings and persists");capture(dialog.getWindow().getDecorView(),activity,"ux-appearance-light");
            check(ProfileStore.encode(view.config).toString().equals(before),"appearance leaves layout and input configuration unchanged");
            screen.close();view.unlock();view.config.labels="PLAYSTATION";snapshot(activity,"symbols-playstation-light");
            ControllerConfig.Control home=view.config.get("HOME");check(ControllerSymbols.standard(home),"default home uses vector glyph");ControllerConfig.Control custom=home.copy();custom.label="My action";check(!ControllerSymbols.standard(custom),"custom label takes precedence over platform glyph");custom=home.copy();custom.buttons=1<<PadState.A;check(!ControllerSymbols.standard(custom),"remapped home retains explicit label");
            view.config.labels="XBOX";snapshot(activity,"symbols-xbox-light");prefs.edit().putString("theme","Dark").putString("accent","Blue").commit();view.loadSettings();check(!new AppAppearance(context).light,"Dark theme reload");snapshot(activity,"symbols-xbox-dark");view.config.labels="PLAYSTATION";snapshot(activity,"symbols-playstation-dark");
        }finally{SettingsScreen screen=(SettingsScreen)sf.get(settings);if(screen!=null)screen.close();prefs.edit().remove("theme").remove("accent").commit();view.config=ProfileStore.decode(new org.json.JSONObject(before));view.input.configure(view.config);view.loadSettings();AppAppearance.apply(activity);}
    }
    private interface UiCheck {void run()throws Exception;}
    private void uiCheck(UiCheck check)throws Exception{runOnMainSync(()->{try{check.run();}catch(Throwable t){failure=t;}});waitForIdleSync();if(failure!=null)throw new AssertionError(failure);}
    private void quickPanelPhase(Bundle result)throws Exception{
        Activity activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();
        View[] original={null};SharedPreferences prefs=activity.getSharedPreferences("instrumentation-quick-panel",0);
        try{
            uiCheck(()->{closeStartup(activity);original[0]=((FrameLayout)activity.findViewById(android.R.id.content)).getChildAt(0);prefs.edit().clear().commit();Context context=new ContextWrapper(activity){@Override public SharedPreferences getSharedPreferences(String name,int mode){return prefs;}};view=new PadView(context,new PadView.Actions(){public void connect(){}public void settings(){}public void changed(){}});activity.setContentView(view);view.layout(0,0,2200,1000);});
            quickPanelDevice(activity);result.putString("stream","\nPASS: "+checks+" quick-panel Android assertions.\n");
        }finally{runOnMainSync(()->{if(view!=null)view.release();if(original[0]!=null)activity.setContentView(original[0]);prefs.edit().clear().commit();});}
        finish(Activity.RESULT_OK,result);
    }
    private void quickPanelDevice(Activity activity)throws Exception{
        uiCheck(()->{
        SharedPreferences prefs=view.getContext().getSharedPreferences("pad",0);
        prefs.edit().putBoolean("quickPanel.enabled",true).putString("quickPanel.ids","[\"Default\",\"FPS\"]").putString("quickPanel.anchor","Bottom").apply();
        view.settingsOpen=false;view.editing=false;view.testMode=false;view.unlock();
        ControllerSettings entry=new ControllerSettings(activity,view,()->{});entry.setup();java.lang.reflect.Field sf=ControllerSettings.class.getDeclaredField("screen");sf.setAccessible(true);java.lang.reflect.Field wf=SettingsScreen.class.getDeclaredField("window");wf.setAccessible(true);((Dialog)wf.get(sf.get(entry))).onBackPressed();check(!view.gameplayLock.locked&&!view.settingsOpen,"Back from Setup leaves panel editable outside game mode");
        QuickLayoutPanel panel=view.quickLayouts();panel.setEnabled(true);prefs.edit().putString(panel.preferenceKey("ids"),"[\"Default\",\"FPS\"]").apply();panel.refresh();
        check(panel.getVisibility()==View.VISIBLE&&findButton(panel,"+")!=null,"quick panel has add button outside gameplay");
        check(findButton(panel,"1")!=null&&findButton(panel,"2")!=null&&findButton(panel,"FPS")==null,"numbered shortcuts are the default");
        check(panel.getGravity()==Gravity.CENTER&&((android.widget.Button)findDescription(panel,"Switch to FPS")).getGravity()==Gravity.CENTER,"panel and shortcut contents are centered");
        String saved=prefs.getString(panel.preferenceKey("ids"),"");findButton(panel,"+").performClick();
        java.lang.reflect.Field popup=QuickLayoutPanel.class.getDeclaredField("dialog");popup.setAccessible(true);android.app.AlertDialog chooser=(android.app.AlertDialog)popup.get(panel);
        check(chooser.isShowing(),"panel plus opens layout selection");chooser.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();check(saved.equals(prefs.getString(panel.preferenceKey("ids"),"")),"cancel preserves quick layouts");
        findButton(panel,"+").performClick();chooser=(android.app.AlertDialog)popup.get(panel);int racing=view.profiles.ids().indexOf("Racing");chooser.getListView().performItemClick(null,racing,racing);chooser.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();
        });
        uiCheck(()->{
        SharedPreferences prefs=view.getContext().getSharedPreferences("pad",0);QuickLayoutPanel panel=view.quickLayouts();java.lang.reflect.Field popup=QuickLayoutPanel.class.getDeclaredField("dialog");popup.setAccessible(true);
        check(prefs.getString(panel.preferenceKey("ids"),"").contains("Racing"),"selected quick layout persists");
        view.state.lt=1;findDescription(panel,"Switch to FPS").performClick();check(view.profiles.active.equals("FPS")&&!view.gameplayLock.locked,"quick layout selects profile without entering game mode");neutral();
        panel.measure(View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));panel.layout(0,0,panel.getMeasuredWidth(),panel.getMeasuredHeight());panel.sync();
        View grip=findButton(panel,"⠿");long now=SystemClock.uptimeMillis();
        for(int action:new int[]{MotionEvent.ACTION_DOWN,MotionEvent.ACTION_MOVE,MotionEvent.ACTION_UP}){MotionEvent event=MotionEvent.obtain(now,now+20,action,action==MotionEvent.ACTION_DOWN?20:180,action==MotionEvent.ACTION_DOWN?20:100,0);grip.dispatchTouchEvent(event);event.recycle();}
        check(prefs.getString(panel.preferenceKey("anchor"),"").equals("Free")&&prefs.contains(panel.preferenceKey("x")),"drag saves free panel position");
        });
        uiCheck(()->{
        SharedPreferences prefs=view.getContext().getSharedPreferences("pad",0);QuickLayoutPanel panel=view.quickLayouts();java.lang.reflect.Field popup=QuickLayoutPanel.class.getDeclaredField("dialog");popup.setAccessible(true);
        capture((View)view.getParent(),activity,"ux-quick-panel-free");
        panel.choosePosition();android.app.AlertDialog position=(android.app.AlertDialog)popup.get(panel);position.getListView().performItemClick(null,3,3);check(prefs.getString(panel.preferenceKey("anchor"),"").equals("Left"),"panel docks to selected edge");QuickLayoutPanel reload=new QuickLayoutPanel(view);java.lang.reflect.Field anchorField=QuickLayoutPanel.class.getDeclaredField("anchor"),xField=QuickLayoutPanel.class.getDeclaredField("x");anchorField.setAccessible(true);xField.setAccessible(true);check(anchorField.get(reload).equals("Left")&&Math.abs(xField.getFloat(reload)-prefs.getFloat(panel.preferenceKey("x"),-1))<.001,"new panel restores saved position and attachment");reload.dispose();
        panel.measure(View.MeasureSpec.makeMeasureSpec(2200,View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(1000,View.MeasureSpec.AT_MOST));panel.layout(0,0,panel.getMeasuredWidth(),panel.getMeasuredHeight());panel.sync();check(Math.abs(panel.getX()-view.controllerBounds().left)<1,"docked panel respects safe controller edge");
        });
        uiCheck(()->{
        SharedPreferences prefs=view.getContext().getSharedPreferences("pad",0);QuickLayoutPanel panel=view.quickLayouts();java.lang.reflect.Field popup=QuickLayoutPanel.class.getDeclaredField("dialog");popup.setAccessible(true);
        capture((View)view.getParent(),activity,"ux-quick-panel-docked");
        view.startGameMode();panel.refresh();check(panel.getVisibility()==View.VISIBLE&&findButton(panel,"+")==null&&findButton(panel,"⠿")!=null,"game mode shows shortcuts and configuration grip but hides add");view.gameplayLock.down(5,0,0,SystemClock.uptimeMillis());MotionEvent panelDown=MotionEvent.obtain(1,1,MotionEvent.ACTION_DOWN,10,10,0);panel.onInterceptTouchEvent(panelDown);panelDown.recycle();check(!view.gameplayLock.holding(),"panel touch cancels menu hold owned by another finger");
        panel.configure();android.app.AlertDialog configuration=(android.app.AlertDialog)popup.get(panel);check(configuration.isShowing(),"panel can be configured during gameplay");
        findButton(configuration.getWindow().getDecorView(),"Horizontal").performClick();check(panel.getOrientation()==android.widget.LinearLayout.HORIZONTAL&&prefs.getString(panel.preferenceKey("direction"),"").equals("Horizontal"),"direction updates immediately independent of dock edge");
        android.widget.SeekBar size=(android.widget.SeekBar)findDescription(configuration.getWindow().getDecorView(),"Quick panel size");Bundle progress=new Bundle();progress.putFloat(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE,0);size.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId(),progress);check(Math.abs(prefs.getFloat(panel.preferenceKey("size"),0)-.75f)<.001,"size slider updates persisted panel size live");
        android.widget.Switch names=(android.widget.Switch)findDescription(configuration.getWindow().getDecorView(),"Show full layout names");names.setChecked(true);check(findButton(panel,"FPS")!=null&&prefs.getBoolean(panel.preferenceKey("names"),false),"full names update immediately for this layout");
        QuickLayoutPanel reload=new QuickLayoutPanel(view);reload.sync();check(reload.getOrientation()==android.widget.LinearLayout.HORIZONTAL&&reload.getChildAt(0).getLayoutParams().height==Math.round(36*activity.getResources().getDisplayMetrics().density),"new panel restores size and direction");reload.dispose();
        });
        uiCheck(()->{
        SharedPreferences prefs=view.getContext().getSharedPreferences("pad",0);QuickLayoutPanel panel=view.quickLayouts();java.lang.reflect.Field popup=QuickLayoutPanel.class.getDeclaredField("dialog");popup.setAccessible(true);android.app.AlertDialog configuration=(android.app.AlertDialog)popup.get(panel);
        capture(configuration.getWindow().getDecorView(),activity,"ux-panel-configuration");configuration.dismiss();
        panel.chooseLayouts();check(!((android.app.AlertDialog)popup.get(panel)).isShowing(),"game mode rejects panel editing");
        view.state.rt=1;findDescription(panel,"Switch to Default").performClick();check(view.gameplayLock.locked&&view.profiles.active.equals("Default"),"gameplay quick switch preserves game mode");neutral();
        String defaultPrefix=panel.preferenceKey("");check(!prefs.getBoolean(panel.preferenceKey("names"),false)&&Math.abs(prefs.getFloat(panel.preferenceKey("size"),0)-1)<.001,"switching layouts restores independent label and size defaults");
        check(prefs.getString(panel.preferenceKey("anchor"),"").equals("Bottom"),"new layout inherits original global placement rather than another layout's changes");
        view.selectProfile("FPS");panel.refresh();check(findButton(panel,"FPS")!=null&&prefs.getString(panel.preferenceKey("anchor"),"").equals("Left")&&Math.abs(prefs.getFloat(panel.preferenceKey("size"),0)-.75f)<.001,"returning restores that layout's names, attachment and size");
        panel.setEnabled(false);check(panel.getVisibility()==View.GONE,"visibility can be disabled for one layout");view.selectProfile("Default");panel.refresh();check(panel.getVisibility()==View.VISIBLE&&panel.preferenceKey("").equals(defaultPrefix),"another layout keeps its panel enabled");
        view.settingsOpen=true;panel.sync();check(panel.getVisibility()==View.GONE,"settings hide quick panel");view.settingsOpen=false;
        prefs.edit().putString(panel.preferenceKey("ids"),"[\"deleted-layout\"]").apply();panel.refresh();check(panel.getVisibility()==View.VISIBLE&&findButton(panel,"▦")!=null,"empty gameplay panel offers a layout chooser");
        prefs.edit().putBoolean(panel.preferenceKey("enabled"),false).remove(panel.preferenceKey("ids")).remove(panel.preferenceKey("anchor")).remove(panel.preferenceKey("x")).remove(panel.preferenceKey("y")).apply();panel.refresh();
        view.config.holdLayout="FPS";java.lang.reflect.Method temporary=PadView.class.getDeclaredMethod("temporaryLayout",int.class);temporary.setAccessible(true);temporary.invoke(view,70);panel.refresh();check(view.currentLayoutId().equals("FPS")&&panel.getVisibility()==View.GONE,"temporary layouts use their own panel settings");view.release();panel.refresh();check(view.currentLayoutId().equals("Default"),"releasing temporary layout restores the base panel scope");
        view.profiles.create("Panel scope rename",view.config);view.selectProfile(view.profiles.active);panel.refresh();String stable=panel.preferenceKey("");panel.setEnabled(true);view.profiles.rename("Panel scope renamed");view.selectProfile(view.profiles.active);panel.refresh();check(panel.preferenceKey("").equals(stable)&&panel.enabled(),"renaming a layout keeps its panel settings by stable ID");
        });
    }
    private void exportGeometry(PadView pad,Activity activity)throws Exception{org.json.JSONObject geometry=new org.json.JSONObject();geometry.put("config",ProfileStore.encode(pad.config));geometry.put("rotation",pad.getDisplay().getRotation());geometry.put("activeLayout",pad.profiles.active);SharedPreferences savedPrefs=activity.getSharedPreferences("pad",0);org.json.JSONObject stored=new org.json.JSONObject(savedPrefs.getString("profiles.v4",savedPrefs.getString("profiles.v2","{}")));geometry.put("storedLayout",stored.optJSONObject(pad.profiles.active));geometry.put("width",pad.input.width);geometry.put("height",pad.input.height);geometry.put("viewWidth",pad.getWidth());geometry.put("viewHeight",pad.getHeight());for(String key:new String[]{"left","top"}){java.lang.reflect.Field f=PadView.class.getDeclaredField(key);f.setAccessible(true);geometry.put(key,f.getFloat(pad));}try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getCacheDir(),"live-layout.json"))){out.write(geometry.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));}}
    private void snapshot(Context activity,String name)throws Exception{android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(view.getWidth(),view.getHeight(),android.graphics.Bitmap.Config.ARGB_8888);view.draw(new android.graphics.Canvas(image));try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(activity.getCacheDir(),"review-"+name.replaceAll("[^A-Za-z0-9]","_")+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    private void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
    private void neutral(){check(Arrays.equals(view.state.report(),new PadState().report()),"neutral report");}
    private float[] xy(String id){try{java.lang.reflect.Field l=PadView.class.getDeclaredField("left"),t=PadView.class.getDeclaredField("top");l.setAccessible(true);t.setAccessible(true);ControllerConfig.Control c=view.config.get(id);return new float[]{l.getFloat(view)+c.x*view.input.width,t.getFloat(view)+c.y*view.input.height};}catch(Exception e){throw new RuntimeException(e);}}
    private static float[] join(float[]... points){float[] result=new float[points.length*2];for(int i=0;i<points.length;i++){result[2*i]=points[i][0];result[2*i+1]=points[i][1];}return result;}
    private void touch(int action,int[] ids,float[] xy){MotionEvent.PointerProperties[] props=new MotionEvent.PointerProperties[ids.length];MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[ids.length];for(int i=0;i<ids.length;i++){props[i]=new MotionEvent.PointerProperties();props[i].id=ids[i];props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;coords[i]=new MotionEvent.PointerCoords();coords[i].x=xy[2*i];coords[i].y=xy[2*i+1];coords[i].pressure=1;coords[i].size=1;}long now=SystemClock.uptimeMillis();MotionEvent e=MotionEvent.obtain(now,now,action,ids.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);view.dispatchTouchEvent(e);e.recycle();}
}
