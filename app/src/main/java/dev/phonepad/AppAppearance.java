package dev.phonepad;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

/** Device-wide visual preferences; exported controller layouts and input are unchanged. */
public final class AppAppearance {
    public final boolean light;
    public final int background,surface,control,border,text,muted,accent,pressed;
    public AppAppearance(Context context){
        SharedPreferences prefs=context.getSharedPreferences("appearance",0);
        String theme=prefs.getString("theme","Dark"),color=prefs.getString("accent","Mint");
        light=theme.equals("Light")||theme.equals("System")&&(context.getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)!=Configuration.UI_MODE_NIGHT_YES;
        background=light?0xffeef2f5:0xff101820;surface=light?0xfffafcfd:0xff1b2934;
        control=light?0xffdce5ec:0xff243743;border=light?0xffa6b6c3:0xff405665;
        text=light?0xff192b38:0xffe5e9ec;muted=light?0xff4f6270:0xff9ca7af;
        accent=color.equals("Blue")?(light?0xff175eaf:0xff96c5ff):color.equals("Violet")?(light?0xff7545a5:0xffd7afff):color.equals("Amber")?(light?0xff865600:0xffffd17d):(light?0xff176b57:0xff8ed9c5);
        pressed=light?0xffc3dacf:0xff334a46;
    }
    public static AppAppearance apply(Context context){AppAppearance a=new AppAppearance(context);context.setTheme(a.light?R.style.AppThemeLight:R.style.AppTheme);SettingsScreen.BACKGROUND=a.background;SettingsScreen.SURFACE=a.surface;SettingsScreen.TEXT=a.text;SettingsScreen.MUTED=a.muted;SettingsScreen.ACCENT=a.accent;return a;}
}
