package dev.phonepad;

import android.content.SharedPreferences;
import org.json.*;
import java.lang.reflect.Field;
import java.util.*;

/** Versioned, bounded JSON; built-ins are generated and never written over. */
public final class ProfileStore {
    public static final String[] BUILTINS={"Two-Thumb FPS / GTA On Foot","Default","FPS","Racing","Classic Xbox","Compact Xbox","Minimal Controller","FPS Standard","FPS Touchpad","FPS Precision","Third-Person Action","GTA Walking","GTA Driving","GTA Combat","Racing Standard","Racing One-Stick","Racing Unified Trigger","Platformer","Fighting Game","Retro Controller","Twin-Stick Shooter","Flight Controller","Tank / Vehicle","One-Handed Left","One-Handed Right","Accessibility / Large Buttons","Custom Blank Layout"};
    private final SharedPreferences prefs;
    private JSONObject custom=new JSONObject();
    private JSONObject layoutMetadata=new JSONObject();
    private final Set<String> unavailable=new HashSet<>();
    private String committedCustom="{}",committedMetadata="{}",committedActive="Default",committedGames="[]";
    private final Set<String> gameNames=new LinkedHashSet<>();
    public String active="Default";
    public ProfileStore(SharedPreferences prefs){
        this.prefs=prefs;
        try{custom=new JSONObject(prefs.getString("profiles.v4",prefs.getString("profiles.v2","{}")));}catch(JSONException ignored){}
        try{layoutMetadata=new JSONObject(prefs.getString("layout-meta.v1","{}"));}catch(JSONException ignored){}
        List<String> bad=new ArrayList<>();Iterator<String> keys=custom.keys();while(keys.hasNext()){String key=keys.next();try{JSONObject item=custom.optJSONObject(key);if(item==null)throw new IllegalArgumentException();decode(item);}catch(IllegalArgumentException ex){bad.add(key);}}unavailable.addAll(bad);
        active=prefs.getString("active.v4",prefs.getString("active.v2","Default"));
        if(!builtin(active)&&(!custom.has(active)||unavailable.contains(active)))active="Default";
        try{JSONArray names=new JSONArray(prefs.getString("games.v1","[]"));for(int i=0;i<names.length();i++)gameNames.add(checkedName(names.getString(i)));}catch(JSONException|IllegalArgumentException ignored){}
        for(String id:ids())gameNames.add(gameOf(id));
        committedGames=new JSONArray(gameNames).toString();
        committedCustom=custom.toString();committedMetadata=layoutMetadata.toString();committedActive=active;
        if(!prefs.contains("active.v4")&&!prefs.contains("active.v2"))migrate();
    }
    public boolean builtin(String id){return Arrays.asList(BUILTINS).contains(id)&&(!custom.has(id)||unavailable.contains(id));}
    public List<String> ids(){List<String> ids=new ArrayList<>(Arrays.asList(BUILTINS));Iterator<String> it=custom.keys();while(it.hasNext()){String id=it.next();if(!unavailable.contains(id)&&!ids.contains(id))ids.add(id);}return ids;}
    public String name(String id){return builtin(id)?id:custom.optJSONObject(id).optString("name","Custom");}
    public String category(String id){JSONObject item=layoutMetadata.optJSONObject(id);String value=item==null?"":item.optString("category","").trim();if(!value.isEmpty())return value;if(template(id))return defaultCategory(id);ControllerConfig c=load(id);return template(c.base)?defaultCategory(c.base):"Uncategorized";}
    public List<String> tags(String id){List<String> result=new ArrayList<>();JSONObject item=layoutMetadata.optJSONObject(id);JSONArray values=item==null?null:item.optJSONArray("tags");if(values!=null)for(int i=0;i<values.length();i++){String tag=values.optString(i,"").trim();if(!tag.isEmpty()&&!result.contains(tag))result.add(tag);}return result;}
    public String tagsText(String id){return String.join(", ",tags(id));}
    public List<String> layoutCategories(String game){Set<String> values=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(String id:layouts(game))values.add(category(id));return new ArrayList<>(values);}
    public List<String> layoutTags(String game){Set<String> values=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);for(String id:layouts(game))values.addAll(tags(id));return new ArrayList<>(values);}
    public void setLayoutLabels(String id,String category,String tags){if(builtin(id))throw new IllegalArgumentException("Duplicate this built-in layout before adding your own labels");if(!custom.has(id)||unavailable.contains(id))throw new IllegalArgumentException("Unknown layout");category=checkedLabel(category,"Category",32);if(tags.length()>240)throw new IllegalArgumentException("Use up to 240 characters for tags");List<String> normalized=new ArrayList<>();for(String raw:tags.split(",")){String tag=raw.trim().toLowerCase(Locale.ROOT);if(tag.isEmpty())continue;tag=checkedLabel(tag,"Tag",24);if(!normalized.contains(tag)){if(normalized.size()>=12)throw new IllegalArgumentException("Use at most 12 tags per layout");normalized.add(tag);}}try{JSONObject item=new JSONObject();item.put("category",category);item.put("tags",new JSONArray(normalized));layoutMetadata.put(id,item);persist();}catch(JSONException impossible){throw new IllegalStateException(impossible);}}
    public ControllerConfig load(String id){ControllerConfig c=builtin(id)?ControllerConfig.preset(id):decode(custom.optJSONObject(id));return c;}
    public ControllerConfig select(String id){if(!builtin(id)&&(!custom.has(id)||unavailable.contains(id)))throw new IllegalArgumentException("Unknown profile");active=id;persist();return load(id);}
    public void save(ControllerConfig config){
        String copiedFrom=builtin(active)?active:null;
        if(builtin(active)){config.name=uniqueName(config.name+" copy",config.game);active=UUID.randomUUID().toString();}
        try{custom.put(active,encode(config));gameNames.add(config.game);persist(copiedFrom);}catch(JSONException e){throw new IllegalStateException(e);}
    }
    /** Explicitly customize a template under its existing identity; factory defaults remain resettable. */
    public void editBuiltin(ControllerConfig config){
        if(!builtin(active))throw new IllegalStateException("This layout is already editable");
        try{custom.put(active,encode(config));unavailable.remove(active);gameNames.add(config.game);persist();}
        catch(JSONException impossible){throw new IllegalStateException(impossible);}
    }
    public void create(String name,ControllerConfig source){source=source.copy();source.name=uniqueName(name,source.game);String previous=active,created=UUID.randomUUID().toString();active=created;try{save(source);}catch(RuntimeException e){custom.remove(created);active=previous;throw e;}}
    public void duplicate(String name,String sourceId){ControllerConfig source=load(sourceId);String category=category(sourceId),tags=tagsText(sourceId);create(name,source);setLayoutLabels(active,category,tags);}
    public void rename(String name){if(builtin(active))throw new IllegalArgumentException("Duplicate a built-in to rename it");ControllerConfig c=load(active);c.name=checkedName(name);save(c);}
    public boolean template(String id){return Arrays.asList(BUILTINS).contains(id);}
    private String defaultCategory(String name){String value=name.toLowerCase(Locale.ROOT);if(value.contains("racing")||value.contains("driving")||value.contains("vehicle")||value.contains("flight")||value.contains("tank"))return "Racing & vehicles";if(value.contains("fight"))return "Fighting";if(value.contains("platform"))return "Platformer";if(value.contains("fps")||value.contains("shooter")||value.contains("gta")||value.contains("third-person")||value.contains("two-thumb"))return "Action & shooter";return "General";}
    private String checkedLabel(String raw,String kind,int max){String value=raw==null?"":raw.trim();if(value.isEmpty()&&kind.equals("Category"))return "Uncategorized";if(value.isEmpty()||value.length()>max)throw new IllegalArgumentException(kind+" must be 1–"+max+" characters");return value;}
    public void delete(){delete(active);}
    public void delete(String id){
        if(builtin(id)||!custom.has(id))throw new IllegalArgumentException("Built-ins cannot be deleted");
        String game=gameOf(id);custom.remove(id);layoutMetadata.remove(id);
        if(!template(id)&&active.equals(id)){List<String> remaining=layouts(game);active=remaining.isEmpty()?"Default":remaining.get(0);}
        persist(null,template(id)?null:id);
    }
    public ControllerConfig reset(){ControllerConfig old=load(active),fresh=ControllerConfig.preset(old.base);if(!builtin(active)){fresh.name=old.name;fresh.game=old.game;save(fresh);}return fresh;}
    public String exportCurrent(){return encode(load(active)).toString();}
    public void importProfile(String text){if(text.length()>200000)throw new IllegalArgumentException("Layout is too large");try{JSONObject json=new JSONObject(text);if((json.optInt("version",0)<2||json.optInt("version",0)>4))throw new IllegalArgumentException("Unsupported layout version");ControllerConfig p=decode(json);create(p.name,p);}catch(JSONException e){throw new IllegalArgumentException("Invalid layout JSON");}}
    private String gameOf(String id){
        if(builtin(id))return "General";
        String game=custom.optJSONObject(id).optString("game","General");
        if(game.trim().isEmpty())return "General";
        return game.length()>60?game.substring(0,60):game;
    }
    public List<String> games(){Set<String> names=new LinkedHashSet<>();names.add("General");names.addAll(gameNames);for(String id:ids())names.add(gameOf(id));return new ArrayList<>(names);}
    public List<String> layouts(String game){List<String> result=new ArrayList<>();for(String id:ids())if(gameOf(id).equals(game))result.add(id);return result;}
    public void createEmptyGame(String name){name=checkedName(name);if(games().contains(name))throw new IllegalArgumentException("A game profile already has this name");gameNames.add(name);persist();}
    public void createGame(String name){name=checkedName(name);if(games().contains(name))throw new IllegalArgumentException("A game profile already has this name");ControllerConfig c=ControllerConfig.preset("Custom Blank Layout");c.game=name;create("Layout A",c);}
    public void renameGame(String game,String name){
        name=checkedName(name);if(game.equals("General"))throw new IllegalArgumentException("General contains built-in templates; duplicate it instead");
        if(games().contains(name)&&!name.equals(game))throw new IllegalArgumentException("A game profile already has this name");
        try{for(String id:ids()){if(builtin(id))continue;ControllerConfig c=load(id);boolean changed=false;if(c.game.equals(game)){c.game=name;changed=true;}for(ControllerConfig.Control control:c.controls)if(control.action.equals("PROFILE")&&control.target.equals(game)){control.target=name;changed=true;}if(changed)custom.put(id,encode(c));}gameNames.remove(game);gameNames.add(name);persist();}catch(JSONException e){throw new IllegalStateException(e);}
    }
    public void duplicateGame(String game,String name){
        name=checkedName(name);if(games().contains(name))throw new IllegalArgumentException("A game profile already has this name");
        List<String> originals=layouts(game);if(originals.isEmpty()){createEmptyGame(name);return;}List<ControllerConfig> copies=new ArrayList<>();for(String id:originals){ControllerConfig c=load(id);c.game=name;copies.add(c);}
        insertGroup(copies,originals,game);
    }
    public void deleteGame(String game){if(game.equals("General"))throw new IllegalArgumentException("General contains built-in templates");for(String id:layouts(game)){custom.remove(id);layoutMetadata.remove(id);}gameNames.remove(game);if(!builtin(active)&&!custom.has(active))active="Default";persist();}
    public String exportGame(String game){try{JSONObject root=new JSONObject();root.put("gameVersion",1);root.put("name",game);JSONArray a=new JSONArray();for(String id:layouts(game)){JSONObject layout=encode(load(id));layout.put("layoutId",id);a.put(layout);}root.put("layouts",a);return root.toString();}catch(JSONException e){throw new IllegalStateException(e);}}
    public void importGame(String text){
        if(text.length()>1000000)throw new IllegalArgumentException("Game profile is too large");
        try{JSONObject root=new JSONObject(text);if(root.getInt("gameVersion")!=1)throw new IllegalArgumentException("Unsupported game profile version");String name=checkedName(root.getString("name"));String candidate=name;int n=2;while(games().contains(candidate))candidate=suffixed(name,n++);JSONArray a=root.getJSONArray("layouts");if(a.length()>100)throw new IllegalArgumentException("Use at most 100 layouts");if(a.length()==0){createEmptyGame(candidate);return;}List<ControllerConfig> decoded=new ArrayList<>();List<String> originals=new ArrayList<>();for(int i=0;i<a.length();i++){ControllerConfig c=decode(a.getJSONObject(i));c.game=candidate;checkedName(c.name);decoded.add(c);originals.add(a.getJSONObject(i).optString("layoutId",""));}insertGroup(decoded,originals,name);}catch(JSONException e){throw new IllegalArgumentException("Invalid game profile JSON");}
    }
    private void insertGroup(List<ControllerConfig> configs,List<String> originals,String sourceGame){
        Map<String,String> ids=new HashMap<>();List<String> fresh=new ArrayList<>();for(String id:originals){String next=UUID.randomUUID().toString();fresh.add(next);if(!id.isEmpty())ids.put(id,next);}
        try{for(int i=0;i<configs.size();i++){ControllerConfig c=configs.get(i);if(!c.holdLayout.isEmpty())c.holdLayout=ids.getOrDefault(c.holdLayout,"");if(!c.aimLayout.isEmpty())c.aimLayout=ids.getOrDefault(c.aimLayout,"");for(ControllerConfig.Control control:c.controls){if(control.action.equals("LAYOUT"))control.target=ids.getOrDefault(control.target,"");if(control.action.equals("PROFILE")&&control.target.equals(sourceGame))control.target=c.game;}custom.put(fresh.get(i),encode(c));gameNames.add(c.game);}active=fresh.get(0);persist();}catch(JSONException e){throw new IllegalStateException(e);}
    }
    private String suffixed(String base,int n){String suffix=" "+n;return base.substring(0,Math.min(base.length(),60-suffix.length()))+suffix;}
    private String checkedName(String s){s=s.trim();if(s.isEmpty()||s.length()>60)throw new IllegalArgumentException("Use a name of 1–60 characters");return s;}
    private String uniqueName(String s,String game){s=checkedName(s);Set<String> names=new HashSet<>();for(String id:layouts(game))names.add(name(id));String out=s;int n=2;while(names.contains(out))out=suffixed(s,n++);return out;}
    private void persist(){persist(null);}
    private void persist(String copiedFrom){persist(copiedFrom,null);}
    private void persist(String copiedFrom,String deleted){
        String serialized=custom.toString(),metadata=layoutMetadata.toString(),groups=new JSONArray(gameNames).toString();
        // Expand into new keys; V4 preferences remain an untouched rollback snapshot.
        SharedPreferences.Editor edit=prefs.edit().putString("profiles.v4",serialized).putString("layout-meta.v1",metadata).putString("active.v4",active).putString("games.v1",groups);
        if(copiedFrom!=null)QuickPanelPreferences.followCopy(prefs,edit,copiedFrom,active);
        if(deleted!=null)QuickPanelPreferences.removeLayout(prefs,edit,deleted);
        if(!edit.commit()){
            try{custom=new JSONObject(committedCustom);layoutMetadata=new JSONObject(committedMetadata);gameNames.clear();JSONArray old=new JSONArray(committedGames);for(int i=0;i<old.length();i++)gameNames.add(old.getString(i));}
            catch(JSONException impossible){throw new IllegalStateException(impossible);}
            active=committedActive;throw new IllegalStateException("Could not save layout");
        }
        committedCustom=serialized;committedMetadata=metadata;committedActive=active;committedGames=groups;
    }
    /** Apply only a settings page's edits, preserving newer controls and unrelated settings. */
    static ControllerConfig mergeChanges(ControllerConfig before,ControllerConfig draft,ControllerConfig current){
        ControllerConfig merged=current.copy();mergeFields(before,draft,merged);
        for(ControllerConfig.Control original:before.controls){ControllerConfig.Control changed=draft.get(original.id),live=merged.get(original.id);
            if(changed==null)merged.deleteControl(original.id);else if(live!=null)mergeFields(original,changed,live);
        }
        for(ControllerConfig.Control added:draft.controls)if(before.get(added.id)==null&&merged.get(added.id)==null)merged.controls.add(added.copy());
        return merged;
    }
    private static void mergeFields(Object before,Object draft,Object live){
        try{for(Field field:fields(draft))if(!java.lang.reflect.Modifier.isFinal(field.getModifiers())&&!java.util.Objects.equals(field.get(before),field.get(draft)))field.set(live,field.get(draft));}
        catch(IllegalAccessException impossible){throw new IllegalStateException(impossible);}
    }
    public static JSONObject encode(ControllerConfig c){
        c=c.copy();c.migrateMacros();
        JSONObject root=new JSONObject();try{root.put("version",4);writeFields(root,c);JSONArray controls=new JSONArray();for(ControllerConfig.Control control:c.controls){JSONObject item=new JSONObject();writeFields(item,control);item.put("mappings",encodeObjects(control.mappings));controls.put(item);}root.put("controls",controls);root.put("macros",encodeObjects(c.macros));root.put("macroAssignments",encodeObjects(c.macroAssignments));}catch(Exception e){throw new IllegalArgumentException("Invalid profile",e);}return root;
    }
    private static final Field[] CONFIG_FIELDS=scalarFields(ControllerConfig.class),CONTROL_FIELDS=scalarFields(ControllerConfig.Control.class);
    private static Field[] scalarFields(Class<?> type){
        List<Field> fields=new ArrayList<>();
        for(Field field:type.getFields()){
            Class<?> t=field.getType();
            if(!java.lang.reflect.Modifier.isStatic(field.getModifiers())&&(t==String.class||t==float.class||t==boolean.class||t==int.class))fields.add(field);
        }
        return fields.toArray(new Field[0]);
    }
    private static Field[] fields(Object object){return object instanceof ControllerConfig?CONFIG_FIELDS:object instanceof ControllerConfig.Control?CONTROL_FIELDS:scalarFields(object.getClass());}
    private static JSONArray encodeObjects(java.util.List<?> objects)throws Exception{JSONArray array=new JSONArray();for(Object object:objects){JSONObject item=new JSONObject();writeFields(item,object);array.put(item);}return array;}
    private static <T> void readObjects(JSONArray array,java.util.List<T> out,java.util.function.Supplier<T> create)throws Exception{
        if(array==null)return;if(array.length()>16384)throw new IllegalArgumentException("Too many editor items");
        Set<String> ids=new HashSet<>();for(int i=0;i<array.length();i++){T item=create.get();readFields(array.getJSONObject(i),item);String id=(String)item.getClass().getField("id").get(item);if(id.isEmpty()||id.length()>150||!ids.add(id))throw new IllegalArgumentException("Invalid editor identity");out.add(item);}
    }
    private static void writeFields(JSONObject json,Object object)throws Exception{
        for(Field field:fields(object))json.put(field.getName(),field.get(object));
    }
    private static void readFields(JSONObject json,Object object)throws Exception{
        for(Field field:fields(object)){
            if(java.lang.reflect.Modifier.isFinal(field.getModifiers())||!json.has(field.getName()))continue;
            String key=field.getName();Class<?> type=field.getType();
            if(type==String.class)field.set(object,json.getString(key));
            else if(type==float.class)field.setFloat(object,(float)json.getDouble(key));
            else if(type==int.class)field.setInt(object,json.getInt(key));
            else if(type==boolean.class)field.setBoolean(object,json.getBoolean(key));
        }
    }
    public static ControllerConfig decode(JSONObject j){
        ControllerConfig c=ControllerConfig.preset("Default");if(j==null)return c;
        int version=j.optInt("version",2);if(version<2||version>4)throw new IllegalArgumentException("Unsupported layout version");
        try{
            readFields(j,c);JSONArray controls=j.optJSONArray("controls");
            if(version==4){
                if(controls==null||controls.length()>ControllerConfig.MAX_CONTROLS)throw new IllegalArgumentException("Invalid control list");
                ControllerConfig defaults=c.copy();c.controls.clear();Set<String> ids=new HashSet<>();
                for(int i=0;i<controls.length();i++){
                    JSONObject item=controls.getJSONObject(i);String id=item.getString("id"),kind=item.getString("kind");int bit=item.getInt("bit");
                    ControllerConfig.Control standard=defaults.get(id);
                    if(standard!=null&&(!standard.kind.equals(kind)||standard.bit!=bit))throw new IllegalArgumentException("Changed standard control type");
                    if(id.trim().isEmpty()||id.length()>100||!ids.add(id)||!ControllerConfig.validControl(kind,bit))throw new IllegalArgumentException("Invalid or duplicate control identity");
                    ControllerConfig.Control control=new ControllerConfig.Control(id,kind,bit,.5f,.5f,.08f,.15f);
                    readFields(item,control);readObjects(item.optJSONArray("mappings"),control.mappings,ControllerConfig.Mapping::new);c.controls.add(control);
                }
            }else if(controls!=null){
                // v2/v3 overlay fixed slots so missing legacy fields keep their defaults.
                for(int i=0;i<controls.length();i++){JSONObject item=controls.getJSONObject(i);ControllerConfig.Control control=c.get(item.optString("id"));if(control!=null)readFields(item,control);}
            }
            readObjects(j.optJSONArray("macros"),c.macros,ControllerConfig.Macro::new);
            readObjects(j.optJSONArray("macroAssignments"),c.macroAssignments,ControllerConfig.MacroAssignment::new);
            for(ControllerConfig.Macro m:c.macros){Automation.compile(m.program);Automation.validateCondition(m.condition);if(m.name.length()>80)m.name=m.name.substring(0,80);}
            for(ControllerConfig.MacroAssignment a:c.macroAssignments){if(c.macro(a.macroId)==null||c.get(a.controlId)==null)throw new IllegalArgumentException("Missing macro reference");a.trigger=choice(a.trigger,"PRESS","HOLD","TOGGLE","TAP","DOUBLE_TAP","LONG_PRESS");}
            for(ControllerConfig.Control control:c.controls)for(ControllerConfig.Mapping m:control.mappings){Automation.validateSource(m.source);Automation.validateBinding(m.output);m.direction=choice(m.direction,"","up","down","left","right");m.deadzone=bounded(m.deadzone,0,.4f,0);m.sensitivity=bounded(m.sensitivity,.1f,3,1);m.response=bounded(m.response,.25f,3,1);}
        }catch(Exception e){throw new IllegalArgumentException("Malformed layout",e);}
        c.fireTapMs=bounded(c.fireTapMs,80,250,180);c.fireGapMs=bounded(c.fireGapMs,120,400,280);c.fireSlop=bounded(c.fireSlop,.005f,.05f,.025f);
        c.base=choice(c.base,BUILTINS);c.labels=choice(c.labels,"XBOX","PLAYSTATION");c.leftMode=choice(c.leftMode,"FIXED","FLOATING");c.rightMode=choice(c.rightMode,"FIXED","FLOATING","TOUCHPAD");c.triggerMode=choice(c.triggerMode,"EDGE","VERTICAL","DIGITAL");c.curve=choice(c.curve,"LINEAR","SOFT","AGGRESSIVE");c.clickGesture=choice(c.clickGesture,"DOUBLE_TAP","LONG_PRESS","SECOND_FINGER","OFF");
        c.deadzone=bounded(c.deadzone,0,.4f,.08f);c.sensitivity=bounded(c.sensitivity,.25f,3,1);c.triggerDeadzone=bounded(c.triggerDeadzone,0,.5f,.12f);c.triggerSensitivity=bounded(c.triggerSensitivity,.25f,3,1);c.travel=bounded(c.travel,.04f,.5f,.16f);c.gridSize=bounded(c.gridSize,.01f,.1f,.025f);c.haptics=Math.max(0,Math.min(3,c.haptics));
        c.quickStyle=choice(c.quickStyle,"LIST","RADIAL");c.switchButtons=Math.max(0,Math.min(65535,c.switchButtons));
        c.unifiedOrientation=choice(c.unifiedOrientation,"VERTICAL","HORIZONTAL");c.unifiedCurve=choice(c.unifiedCurve,"LINEAR","SOFT","AGGRESSIVE");c.aimMode=choice(c.aimMode,"HOLD","TOGGLE");
        c.unifiedDeadzone=bounded(c.unifiedDeadzone,0,.5f,.08f);c.unifiedTravel=bounded(c.unifiedTravel,.04f,.5f,.18f);c.unifiedSensitivity=bounded(c.unifiedSensitivity,.25f,3,1);
        c.gyroSensitivityX=c.gyroSensitivityX==0?0:bounded(c.gyroSensitivityX,.05f,5,0);c.gyroSensitivityY=c.gyroSensitivityY==0?0:bounded(c.gyroSensitivityY,.05f,5,0);
        c.gyroSensitivity=bounded(c.gyroSensitivity,.05f,5,.6f);c.gyroSmoothing=bounded(c.gyroSmoothing,0,.95f,.35f);c.gyroDeadzone=bounded(c.gyroDeadzone,0,.2f,.015f);
        c.cameraX=bounded(c.cameraX,.1f,5,1);c.cameraY=bounded(c.cameraY,.1f,5,1);c.cameraCurve=bounded(c.cameraCurve,.25f,3,1);c.cameraSmoothing=bounded(c.cameraSmoothing,0,.95f,0);c.aimSensitivity=bounded(c.aimSensitivity,.1f,1,.4f);
        if(c.game.trim().isEmpty())c.game="General";if(c.game.length()>60)c.game=c.game.substring(0,60);
        if(c.name.length()>60)c.name=c.name.substring(0,60);
        Set<String> controlIds=new HashSet<>();for(ControllerConfig.Control t:c.controls)controlIds.add(t.id);
        for(ControllerConfig.Control t:c.controls){if(!t.macroTriggers.isEmpty()){List<String> valid=new ArrayList<>();for(String id:t.macroTriggers.split(","))if(controlIds.contains(id)&&!id.equals(t.id)&&!valid.contains(id))valid.add(id);t.macroTriggers=String.join(",",valid);if(t.macroTriggers.length()>8192)t.macroTriggers="";}Automation.validate(t);t.macroMode=choice(t.macroMode,"PRESS","HOLD","TOGGLE");if(t.label.length()>60)t.label=t.label.substring(0,60);t.w=bounded(t.w,.025f,.5f,.08f);t.h=bounded(t.h,.04f,.7f,.15f);t.x=bounded(t.x,t.w/2,1-t.w/2,.5f);t.y=bounded(t.y,Math.max(.1f,t.h/2),1-t.h/2,.5f);t.hitbox=bounded(t.hitbox,1,2,1.15f);t.mode=t.mode.isEmpty()?"":t.kind.equals("stick")?choice(t.mode,t.bit==0?new String[]{"FIXED","FLOATING"}:new String[]{"FIXED","FLOATING","TOUCHPAD"}):t.kind.equals("trigger")?choice(t.mode,"EDGE","VERTICAL","DIGITAL"):"";
            t.gesture=choice(t.gesture,"HOLD","TAP","DOUBLE_TAP","LONG_PRESS","SWIPE","SECOND_FINGER","TOGGLE");t.action=choice(t.action,"BUTTONS","NEXT","PREVIOUS","LAYOUT","PROFILE","MOVEMENT_HOLD","MODIFIER");if(t.gesture.equals("TOGGLE")&&!t.action.equals("BUTTONS"))t.gesture="TAP";
            t.modifiedButtons=Math.max(-1,Math.min(65535,t.modifiedButtons));t.hitWidth=bounded(t.hitWidth,.5f,3,1);t.hitHeight=bounded(t.hitHeight,.5f,3,1);
            t.opacity=bounded(t.opacity,.1f,1,1);
            t.deadzone=t.deadzone<0?-1:bounded(t.deadzone,0,.4f,.08f);t.sensitivity=t.sensitivity<0?-1:bounded(t.sensitivity,.1f,5,1);t.response=bounded(t.response,.25f,3,1);t.buttons=Math.max(-1,Math.min(65535,t.buttons));t.outputLT=bounded(t.outputLT,0,1,0);t.outputRT=bounded(t.outputRT,0,1,0);}
        for(ControllerConfig.Control control:c.controls)DesktopBindings.repairCancelledPointer(control);
        c.migrateMacros();c.ensureBuiltinMacros();return c;
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
