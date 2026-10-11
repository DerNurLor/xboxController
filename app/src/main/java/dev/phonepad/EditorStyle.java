package dev.phonepad;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.*;
import android.widget.*;

/** Shared, compact chrome for the editing workspaces. */
final class EditorStyle {
    static final int BG=0xff08131b, PANEL=0xff101f29, SURFACE=0xff172c39, LINE=0xff294653;
    static final int TEXT=0xffe9f2f5, MUTED=0xff95afbd, TEAL=0xff78d5c1, BLUE=0xff245b86, WARN=0xffffcc80;
    static int dp(Context c,float value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static GradientDrawable background(Context c,int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,7));d.setStroke(dp(c,1),LINE);return d;}
    static LinearLayout column(Context c){LinearLayout v=new LinearLayout(c);v.setOrientation(1);return v;}
    static TextView text(Context c,String text,int size,int color){TextView v=new TextView(c);v.setText(text);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(dp(c,8),dp(c,4),dp(c,8),dp(c,4));return v;}
    static Button button(Context c,String label,boolean active,Runnable click){Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(12);b.setTextColor(active?BG:TEXT);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(dp(c,10),0,dp(c,10),0);b.setBackground(background(c,active?TEAL:SURFACE));b.setOnClickListener(v->click.run());b.setContentDescription(label);return b;}
    static void add(LinearLayout row,View v,int width,int height){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width<0?width:dp(row.getContext(),width),dp(row.getContext(),height));p.setMargins(dp(row.getContext(),2),dp(row.getContext(),2),dp(row.getContext(),2),dp(row.getContext(),2));row.addView(v,p);}
    static void fullscreen(Dialog d,View root){
        d.show();Window w=d.getWindow();if(w==null)return;w.setBackgroundDrawableResource(android.R.color.transparent);w.setLayout(-1,-1);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        if(Build.VERSION.SDK_INT>=30){w.setDecorFitsSystemWindows(false);WindowInsetsController bars=w.getInsetsController();if(bars!=null){bars.hide(WindowInsets.Type.systemBars());bars.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());v.setPadding(i.left,i.top,i.right,i.bottom);return insets;});root.requestApplyInsets();}
        else {root.setFitsSystemWindows(true);w.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);}
    }
    private EditorStyle(){}
}
