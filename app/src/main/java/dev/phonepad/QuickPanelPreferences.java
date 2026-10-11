package dev.phonepad;

import android.content.SharedPreferences;
import android.util.Base64;
import java.nio.charset.StandardCharsets;

/** Independent device-local panel settings, keyed by stable layout ID, not its name. */
final class QuickPanelPreferences {
    private static String keyPrefix(String id){return "quickPanel.layout."+Base64.encodeToString(id.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP)+".";}
    /** An edited built-in becomes a custom layout; shortcuts should follow that edit. */
    static void followCopy(SharedPreferences prefs,SharedPreferences.Editor edit,String source,String target){
        String from=keyPrefix(source),to=keyPrefix(target);
        String[] keys={"enabled","names","ids","anchor","direction","size","x","y"};
        java.util.Map<String,?> values=prefs.getAll();
        for(String key:keys){Object value=values.get(from+key);if(value==null)value=values.get("quickPanel."+key);
            if(value instanceof Boolean)edit.putBoolean(to+key,(Boolean)value);
            else if(value instanceof Float)edit.putFloat(to+key,(Float)value);
            else if(value instanceof String)edit.putString(to+key,key.equals("ids")?replaceId((String)value,source,target):(String)value);
        }
        edit.putBoolean(to+"initialized",true);
        for(java.util.Map.Entry<String,?> entry:values.entrySet())if(entry.getKey().startsWith("quickPanel.")&&entry.getKey().endsWith("ids")&&entry.getValue() instanceof String)
            edit.putString(entry.getKey(),replaceId((String)entry.getValue(),source,target));
    }
    private static String replaceId(String json,String source,String target){
        try{org.json.JSONArray old=new org.json.JSONArray(json),next=new org.json.JSONArray();java.util.Set<String> seen=new java.util.HashSet<>();
            for(int i=0;i<old.length();i++){String id=old.getString(i);if(id.equals(source))id=target;if(id!=null&&seen.add(id))next.put(id);}return next.toString();
        }catch(org.json.JSONException invalid){return json;}
    }
    static void removeLayout(SharedPreferences prefs,SharedPreferences.Editor edit,String id){
        String prefix=keyPrefix(id);
        for(java.util.Map.Entry<String,?> entry:prefs.getAll().entrySet()){
            String key=entry.getKey();
            if(key.startsWith(prefix))edit.remove(key);
            else if(key.startsWith("quickPanel.")&&key.endsWith("ids")&&entry.getValue() instanceof String)
                edit.putString(key,replaceId((String)entry.getValue(),id,null));
        }
    }
    static String prefix(SharedPreferences prefs,String layoutId){
        String prefix="quickPanel.layout."+Base64.encodeToString(layoutId.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP)+".";
        if(!prefs.getBoolean(prefix+"initialized",false)){
            // Snapshot the previous global settings once. Editing one layout never changes
            // defaults for another, including layouts that have not been visited yet.
            SharedPreferences.Editor edit=prefs.edit();
            edit.putBoolean(prefix+"enabled",prefs.getBoolean("quickPanel.enabled",false));
            edit.putBoolean(prefix+"names",prefs.getBoolean("quickPanel.names",false));
            edit.putString(prefix+"ids",prefs.getString("quickPanel.ids","[]"));
            edit.putString(prefix+"anchor",prefs.getString("quickPanel.anchor","Bottom"));
            edit.putString(prefix+"direction",prefs.getString("quickPanel.direction","Auto"));
            edit.putFloat(prefix+"size",prefs.getFloat("quickPanel.size",1));
            edit.putFloat(prefix+"x",prefs.getFloat("quickPanel.x",.5f));
            edit.putFloat(prefix+"y",prefs.getFloat("quickPanel.y",1));
            edit.putBoolean(prefix+"initialized",true).apply();
        }
        return prefix;
    }
}
