package dev.phonepad;

import android.content.SharedPreferences;
import org.json.*;
import java.lang.reflect.Field;
import java.util.*;

/** Versioned, bounded JSON; built-ins are generated and never written over. */
public final class ProfileStore {
    public static final String[] BUILTINS={"Default","FPS","Racing","Classic Xbox","Compact Xbox","Minimal Controller","FPS Standard","FPS Touchpad","FPS Precision","Third-Person Action","GTA Walking","GTA Driving","GTA Combat","Racing Standard","Racing One-Stick","Racing Unified Trigger","Platformer","Fighting Game","Retro Controller","Twin-Stick Shooter","Flight Controller","Tank / Vehicle","One-Handed Left","One-Handed Right","Accessibility / Large Buttons","Custom Blank Layout"};
    private final SharedPreferences prefs;
    private JSONObject custom=new JSONObject();
    private final Set<String> unavailable=new HashSet<>();
    private String committedCustom="{}",committedActive="Default";
    public String active="Default";
    public ProfileStore(SharedPreferences prefs){
        this.prefs=prefs;
        try{custom=new JSONObject(prefs.getString("profiles.v2","{}"));}catch(JSONException ignored){}
        List<String> bad=new ArrayList<>();Iterator<String> keys=custom.keys();while(keys.hasNext()){String key=keys.next();try{JSONObject item=custom.optJSONObject(key);if(item==null)throw new IllegalArgumentException();decode(item);}catch(IllegalArgumentException ex){bad.add(key);}}unavailable.addAll(bad);
        active=prefs.getString("active.v2","Default");
        if(!builtin(active)&&(!custom.has(active)||unavailable.contains(active)))active="Default";
        committedCustom=custom.toString();committedActive=active;
        if(!prefs.contains("active.v2"))migrate();
    }
    public boolean builtin(String id){return Arrays.asList(BUILTINS).contains(id);}
    public List<String> ids(){List<String> ids=new ArrayList<>(Arrays.asList(BUILTINS));Iterator<String> it=custom.keys();while(it.hasNext()){String id=it.next();if(!unavailable.contains(id))ids.add(id);}return ids;}
    public String name(String id){return builtin(id)?id:custom.optJSONObject(id).optString("name","Custom");}
    public ControllerConfig load(String id){ControllerConfig c=builtin(id)?ControllerConfig.preset(id):decode(custom.optJSONObject(id));return c;}
    public ControllerConfig select(String id){if(!builtin(id)&&(!custom.has(id)||unavailable.contains(id)))throw new IllegalArgumentException("Unknown profile");active=id;persist();return load(id);}
    public void save(ControllerConfig config){
        if(builtin(active)){config.name=uniqueName(config.name+" copy",config.game);active=UUID.randomUUID().toString();}
        try{custom.put(active,encode(config));persist();}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public void create(String name,ControllerConfig source){source=source.copy();source.name=uniqueName(name,source.game);String previous=active,created=UUID.randomUUID().toString();active=created;try{save(source);}catch(RuntimeException e){custom.remove(created);active=previous;throw e;}}
    public void rename(String name){if(builtin(active))throw new IllegalArgumentException("Duplicate a built-in to rename it");ControllerConfig c=load(active);c.name=checkedName(name);save(c);}
    public void delete(){if(builtin(active))throw new IllegalArgumentException("Built-ins cannot be deleted");custom.remove(active);active="Default";persist();}
    public ControllerConfig reset(){ControllerConfig old=load(active),fresh=ControllerConfig.preset(old.base);if(!builtin(active)){fresh.name=old.name;fresh.game=old.game;save(fresh);}return fresh;}
    public String exportCurrent(){return encode(load(active)).toString();}
    public void importProfile(String text){if(text.length()>200000)throw new IllegalArgumentException("Layout is too large");try{JSONObject json=new JSONObject(text);if((json.optInt("version",0)!=2&&json.optInt("version",0)!=3))throw new IllegalArgumentException("Unsupported layout version");ControllerConfig p=decode(json);create(p.name,p);}catch(JSONException e){throw new IllegalArgumentException("Invalid layout JSON");}}
    public List<String> games(){Set<String> names=new LinkedHashSet<>();names.add("General");for(String id:ids())names.add(load(id).game);return new ArrayList<>(names);}
    public List<String> layouts(String game){List<String> result=new ArrayList<>();for(String id:ids())if(load(id).game.equals(game))result.add(id);return result;}
    public void createGame(String name){name=checkedName(name);if(games().contains(name))throw new IllegalArgumentException("A game profile already has this name");ControllerConfig c=ControllerConfig.preset("Custom Blank Layout");c.game=name;create("Layout A",c);}
    public void renameGame(String game,String name){
        name=checkedName(name);if(game.equals("General"))throw new IllegalArgumentException("General contains built-in templates; duplicate it instead");
        if(games().contains(name)&&!name.equals(game))throw new IllegalArgumentException("A game profile already has this name");
        try{for(String id:ids()){if(builtin(id))continue;ControllerConfig c=load(id);boolean changed=false;if(c.game.equals(game)){c.game=name;changed=true;}for(ControllerConfig.Control control:c.controls)if(control.action.equals("PROFILE")&&control.target.equals(game)){control.target=name;changed=true;}if(changed)custom.put(id,encode(c));}persist();}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public void duplicateGame(String game,String name){
        name=checkedName(name);if(games().contains(name))throw new IllegalArgumentException("A game profile already has this name");
        List<String> originals=layouts(game);List<ControllerConfig> copies=new ArrayList<>();for(String id:originals){ControllerConfig c=load(id);c.game=name;copies.add(c);}
        insertGroup(copies,originals,game);
    }
    public void deleteGame(String game){if(game.equals("General"))throw new IllegalArgumentException("General contains built-in templates");for(String id:layouts(game))custom.remove(id);if(!builtin(active)&&!custom.has(active))active="Default";persist();}
    public String exportGame(String game){try{JSONObject root=new JSONObject();root.put("gameVersion",1);root.put("name",game);JSONArray a=new JSONArray();for(String id:layouts(game)){JSONObject layout=encode(load(id));layout.put("layoutId",id);a.put(layout);}root.put("layouts",a);return root.toString();}catch(JSONException e){throw new IllegalStateException(e);}}
    public void importGame(String text){
        if(text.length()>1000000)throw new IllegalArgumentException("Game profile is too large");
        try{JSONObject root=new JSONObject(text);if(root.getInt("gameVersion")!=1)throw new IllegalArgumentException("Unsupported game profile version");String name=checkedName(root.getString("name"));String candidate=name;int n=2;while(games().contains(candidate))candidate=suffixed(name,n++);JSONArray a=root.getJSONArray("layouts");if(a.length()==0||a.length()>100)throw new IllegalArgumentException("Use 1–100 layouts");List<ControllerConfig> decoded=new ArrayList<>();List<String> originals=new ArrayList<>();for(int i=0;i<a.length();i++){ControllerConfig c=decode(a.getJSONObject(i));c.game=candidate;checkedName(c.name);decoded.add(c);originals.add(a.getJSONObject(i).optString("layoutId",""));}insertGroup(decoded,originals,name);}catch(JSONException e){throw new IllegalArgumentException("Invalid game profile JSON");}
    }
    private void insertGroup(List<ControllerConfig> configs,List<String> originals,String sourceGame){
        Map<String,String> ids=new HashMap<>();List<String> fresh=new ArrayList<>();for(String id:originals){String next=UUID.randomUUID().toString();fresh.add(next);if(!id.isEmpty())ids.put(id,next);}
        try{for(int i=0;i<configs.size();i++){ControllerConfig c=configs.get(i);if(!c.holdLayout.isEmpty())c.holdLayout=ids.getOrDefault(c.holdLayout,"");if(!c.aimLayout.isEmpty())c.aimLayout=ids.getOrDefault(c.aimLayout,"");for(ControllerConfig.Control control:c.controls){if(control.action.equals("LAYOUT"))control.target=ids.getOrDefault(control.target,"");if(control.action.equals("PROFILE")&&control.target.equals(sourceGame))control.target=c.game;}custom.put(fresh.get(i),encode(c));}active=fresh.get(0);persist();}catch(JSONException e){throw new IllegalStateException(e);}
    }
    private String suffixed(String base,int n){String suffix=" "+n;return base.substring(0,Math.min(base.length(),60-suffix.length()))+suffix;}
    private String checkedName(String s){s=s.trim();if(s.isEmpty()||s.length()>60)throw new IllegalArgumentException("Use a name of 1–60 characters");return s;}
    private String uniqueName(String s,String game){s=checkedName(s);Set<String> names=new HashSet<>();for(String id:layouts(game))names.add(name(id));String out=s;int n=2;while(names.contains(out))out=suffixed(s,n++);return out;}
    private void persist(){String serialized=custom.toString();if(!prefs.edit().putString("profiles.v2",serialized).putString("active.v2",active).commit()){try{custom=new JSONObject(committedCustom);}catch(JSONException impossible){throw new IllegalStateException(impossible);}active=committedActive;throw new IllegalStateException("Could not save layout");}committedCustom=serialized;committedActive=active;}
    public static JSONObject encode(ControllerConfig c){
        JSONObject root=new JSONObject();try{root.put("version",3);writeFields(root,c);JSONArray controls=new JSONArray();for(ControllerConfig.Control control:c.controls){JSONObject item=new JSONObject();writeFields(item,control);controls.put(item);}root.put("controls",controls);}catch(Exception e){throw new IllegalArgumentException("Invalid profile",e);}return root;
    }
    private static void writeFields(JSONObject j,Object obj)throws Exception {for(Field f:obj.getClass().getFields()){Class<?> type=f.getType();if(type==String.class||type==float.class||type==boolean.class||type==int.class)j.put(f.getName(),f.get(obj));}}
    private static void readFields(JSONObject j,Object obj)throws Exception {for(Field f:obj.getClass().getFields()){if(java.lang.reflect.Modifier.isFinal(f.getModifiers())||!j.has(f.getName()))continue;String k=f.getName();Class<?> t=f.getType();if(t==String.class)f.set(obj,j.getString(k));else if(t==float.class)f.setFloat(obj,(float)j.getDouble(k));else if(t==int.class)f.setInt(obj,j.getInt(k));else if(t==boolean.class)f.setBoolean(obj,j.getBoolean(k));}}
    public static ControllerConfig decode(JSONObject j){
        ControllerConfig c=ControllerConfig.preset("Default");if(j==null)return c;if(j.optInt("version",2)>3)throw new IllegalArgumentException("Unsupported layout version");
        try{readFields(j,c);JSONArray controls=j.optJSONArray("controls");if(controls!=null){for(int i=0;i<controls.length();i++){JSONObject item=controls.getJSONObject(i);ControllerConfig.Control control=c.get(item.optString("id"));if(control!=null)readFields(item,control);}}}catch(Exception e){throw new IllegalArgumentException("Malformed layout",e);}
        c.base=choice(c.base,BUILTINS);c.labels=choice(c.labels,"XBOX","PLAYSTATION");c.leftMode=choice(c.leftMode,"FIXED","FLOATING");c.rightMode=choice(c.rightMode,"FIXED","FLOATING","TOUCHPAD");c.triggerMode=choice(c.triggerMode,"EDGE","VERTICAL","DIGITAL");c.curve=choice(c.curve,"LINEAR","SOFT","AGGRESSIVE");c.clickGesture=choice(c.clickGesture,"DOUBLE_TAP","LONG_PRESS","SECOND_FINGER","OFF");
        c.deadzone=bounded(c.deadzone,0,.4f,.08f);c.sensitivity=bounded(c.sensitivity,.25f,3,1);c.triggerDeadzone=bounded(c.triggerDeadzone,0,.5f,.12f);c.triggerSensitivity=bounded(c.triggerSensitivity,.25f,3,1);c.travel=bounded(c.travel,.04f,.5f,.16f);c.gridSize=bounded(c.gridSize,.01f,.1f,.025f);c.haptics=Math.max(0,Math.min(3,c.haptics));
        c.quickStyle=choice(c.quickStyle,"LIST","RADIAL");c.switchButtons=Math.max(0,Math.min(65535,c.switchButtons));
        c.unifiedOrientation=choice(c.unifiedOrientation,"VERTICAL","HORIZONTAL");c.unifiedCurve=choice(c.unifiedCurve,"LINEAR","SOFT","AGGRESSIVE");c.aimMode=choice(c.aimMode,"HOLD","TOGGLE");
        c.unifiedDeadzone=bounded(c.unifiedDeadzone,0,.5f,.08f);c.unifiedTravel=bounded(c.unifiedTravel,.04f,.5f,.18f);c.unifiedSensitivity=bounded(c.unifiedSensitivity,.25f,3,1);
        c.gyroSensitivity=bounded(c.gyroSensitivity,.05f,5,.6f);c.gyroSmoothing=bounded(c.gyroSmoothing,0,.95f,.35f);c.gyroDeadzone=bounded(c.gyroDeadzone,0,.2f,.015f);
        c.cameraX=bounded(c.cameraX,.1f,5,1);c.cameraY=bounded(c.cameraY,.1f,5,1);c.cameraCurve=bounded(c.cameraCurve,.25f,3,1);c.cameraSmoothing=bounded(c.cameraSmoothing,0,.95f,0);c.aimSensitivity=bounded(c.aimSensitivity,.1f,1,.4f);
        if(c.game.trim().isEmpty())c.game="General";if(c.game.length()>60)c.game=c.game.substring(0,60);
        if(c.name.length()>60)c.name=c.name.substring(0,60);
        for(ControllerConfig.Control t:c.controls){t.w=bounded(t.w,.025f,.5f,.08f);t.h=bounded(t.h,.04f,.7f,.15f);t.x=bounded(t.x,t.w/2,1-t.w/2,.5f);t.y=bounded(t.y,Math.max(.1f,t.h/2),1-t.h/2,.5f);t.hitbox=bounded(t.hitbox,1,2,1.15f);t.mode=t.mode.isEmpty()?"":t.kind.equals("stick")?choice(t.mode,t.bit==0?new String[]{"FIXED","FLOATING"}:new String[]{"FIXED","FLOATING","TOUCHPAD"}):t.kind.equals("trigger")?choice(t.mode,"EDGE","VERTICAL","DIGITAL"):"";
            t.gesture=choice(t.gesture,"HOLD","TAP","DOUBLE_TAP","LONG_PRESS","SWIPE","SECOND_FINGER","TOGGLE");t.action=choice(t.action,"BUTTONS","NEXT","PREVIOUS","LAYOUT","PROFILE","MOVEMENT_HOLD","MODIFIER");if(t.gesture.equals("TOGGLE")&&!t.action.equals("BUTTONS"))t.gesture="TAP";
            t.modifiedButtons=Math.max(-1,Math.min(65535,t.modifiedButtons));t.hitWidth=bounded(t.hitWidth,.5f,3,1);t.hitHeight=bounded(t.hitHeight,.5f,3,1);
            t.opacity=bounded(t.opacity,.1f,1,1);
            t.deadzone=t.deadzone<0?-1:bounded(t.deadzone,0,.4f,.08f);t.sensitivity=t.sensitivity<0?-1:bounded(t.sensitivity,.1f,5,1);t.response=bounded(t.response,.25f,3,1);t.buttons=Math.max(-1,Math.min(65535,t.buttons));t.outputLT=bounded(t.outputLT,0,1,0);t.outputRT=bounded(t.outputRT,0,1,0);}
        return c;
    }
    private static String choice(String value,String... values){return Arrays.asList(values).contains(value)?value:values[0];}
    private static float bounded(float n,float lo,float hi,float fallback){return Float.isFinite(n)?Math.max(lo,Math.min(hi,n)):fallback;}
    private void migrate(){
        if(prefs.contains("LS.x")||prefs.contains("triggerMode")||prefs.contains("deadzone")){
            ControllerConfig c=ControllerConfig.preset("Default");c.name="Previous layout";
            c.deadzone=prefs.getFloat("deadzone",.08f);c.sensitivity=prefs.getFloat("sensitivity",1);c.rightMode=prefs.getString("rightStickMode","FIXED");c.triggerMode=prefs.getString("triggerMode","EDGE");c.haptics=prefs.getBoolean("haptics",true)?1:0;c.labels=prefs.getString("layoutMode","XBOX");
            for(ControllerConfig.Control t:c.controls){if(prefs.contains(t.id+".x"))t.x=prefs.getFloat(t.id+".x",t.x*1100)/1100;if(prefs.contains(t.id+".y"))t.y=prefs.getFloat(t.id+".y",t.y*480)/480;float size=prefs.getFloat(t.id+".size",1);t.w*=size;t.h*=size;}
            create(c.name,decode(encode(c)));
        }else persist();
    }
}
