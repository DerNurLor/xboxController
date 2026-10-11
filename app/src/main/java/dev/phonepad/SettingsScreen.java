package dev.phonepad;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Shared landscape workspace. Navigation and content scroll independently; no fixed form footer. */
public final class SettingsScreen {
    static int BACKGROUND=0xff101820, SURFACE=0xff1b2934, TEXT=0xffe5e9ec, MUTED=0xff9ca7af, ACCENT=0xff8ed9c5;
    private final Context context;
    private final Runnable finished;
    private boolean closed;
    private final Dialog window;
    private final LinearLayout content,root,nav;
    private final TextView heading,status;
    private final Button back;
    private final ScrollView navigation;
    private int selection;
    public int selection(){return selection;}
    private Runnable navigateBack;
    private Runnable beforeLeave=()->{};
    private android.app.AlertDialog helpDialog;
    public SettingsScreen(Context context,String[] categories,IntConsumer select,Runnable close){
        this(context,categories,select,close,false);
    }
    public SettingsScreen(Context context,String[] categories,IntConsumer select,Runnable close,boolean editor){
        this.context=context;AppAppearance appearance=AppAppearance.apply(context);finished=close;window=new Dialog(context,appearance.light?R.style.AppThemeLight:R.style.AppTheme){@Override public void onBackPressed(){if(navigateBack!=null)navigateBack.run();else super.onBackPressed();}};
        root=new LinearLayout(context);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(BACKGROUND);
        LinearLayout header=new LinearLayout(context);header.setGravity(Gravity.CENTER_VERTICAL);
        back=button("‹ Setup",this::close);header.addView(back,new LinearLayout.LayoutParams(dp(108),dp(44)));
        heading=label("Settings",20);header.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        status=label("",12);status.setTextColor(MUTED);header.addView(status);Button closeButton=button("×",this::close);closeButton.setContentDescription("Close settings");closeButton.setTooltipText("Close settings");closeButton.setGravity(Gravity.CENTER);header.addView(closeButton,new LinearLayout.LayoutParams(dp(48),dp(48)));root.addView(header);
        LinearLayout body=new LinearLayout(context);nav=new LinearLayout(context);nav.setOrientation(LinearLayout.VERTICAL);
        for(int i=0;i<categories.length;i++){final int n=i;Button item=button((new String[]{"⌂","⊕","▷","⌁","⋯"})[i%5]+"  "+categories[i],()->select.accept(n));item.setTextSize(14);item.setContentDescription(categories[i]);nav.addView(item,new LinearLayout.LayoutParams(-1,-2));}
        navigation=new ScrollView(context);navigation.addView(nav);body.addView(navigation,new LinearLayout.LayoutParams(0,-1,0.25f));
        content=new LinearLayout(context);content.setOrientation(LinearLayout.VERTICAL);LinearLayout.LayoutParams contentSpace=new LinearLayout.LayoutParams(0,-1,0.75f);contentSpace.leftMargin=dp(10);body.addView(content,contentSpace);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        int margin=dp(2);root.setPadding(margin,margin,margin,margin);
        root.setOnApplyWindowInsetsListener((v,insets)->{int l,r,t,b;if(android.os.Build.VERSION.SDK_INT>=30){android.graphics.Insets i=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());l=i.left;r=i.right;t=i.top;b=i.bottom;}else{l=insets.getSystemWindowInsetLeft();r=insets.getSystemWindowInsetRight();t=insets.getSystemWindowInsetTop();b=insets.getSystemWindowInsetBottom();}root.setPadding(margin+l,margin+t,margin+r,margin+b);return insets;});
        window.setOnKeyListener((d,key,event)->{if(key==KeyEvent.KEYCODE_BACK&&navigateBack!=null){if(event.getAction()==KeyEvent.ACTION_UP)navigateBack.run();return true;}return false;});window.setContentView(root);window.setOnDismissListener(d->{leave();finish();});window.show();window.getWindow().setLayout(-1,-1);if(android.os.Build.VERSION.SDK_INT>=30){window.getWindow().setDecorFitsSystemWindows(false);WindowInsetsController controller=window.getWindow().getInsetsController();if(controller!=null){controller.hide(WindowInsets.Type.systemBars());controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}if(editor){navigation.setVisibility(View.GONE);status.setVisibility(View.GONE);back("‹ Editor",this::close);window.getWindow().setGravity(Gravity.END);window.getWindow().setLayout(Math.min(dp(360),context.getResources().getDisplayMetrics().widthPixels),-1);window.getWindow().setDimAmount(0.15f);}
        window.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
    }
    int dp(float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    public TextView label(String title,int size){TextView t=new TextView(context);t.setText(title);t.setTextSize(size);t.setTextColor(TEXT);return t;}
    public boolean showing(){return window.isShowing();}
    private void finish(){if(!closed){closed=true;finished.run();}}
    public void navigation(Runnable action){navigateBack=action;back("‹ Back",action);}
    public void back(String title,Runnable action){back.setText(navigateBack==null?title:"‹ Back");back.setContentDescription("Back");back.setOnClickListener(v->{if(navigateBack!=null)navigateBack.run();else action.run();});}
    public void close(){leave();window.dismiss();finish();}
    public void leave(){if(helpDialog!=null){helpDialog.dismiss();helpDialog=null;}Runnable pending=beforeLeave;beforeLeave=()->{};pending.run();}
    public void onLeave(Runnable action){beforeLeave=action;}
    public void status(String message){status.setText(message);}
    public void selected(int index){selection=index;for(int i=0;i<nav.getChildCount();i++){View v=nav.getChildAt(i);v.setSelected(i==index);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(i==index?SURFACE:BACKGROUND);bg.setCornerRadius(dp(12));if(i==index){LayerDrawable selected=new LayerDrawable(new android.graphics.drawable.Drawable[]{bg,new ColorDrawable(ACCENT)});selected.setLayerWidth(1,dp(3));selected.setLayerInset(1,0,dp(12),0,dp(12));v.setBackground(selected);}else v.setBackground(bg);((Button)v).setTypeface(null,i==index?android.graphics.Typeface.BOLD:android.graphics.Typeface.NORMAL);((Button)v).setTextColor(i==index?ACCENT:MUTED);}}
    public Button button(String title,Runnable action){Button b=new Button(context);b.setText(title);b.setTextSize(15);b.setTextColor(TEXT);b.setAllCaps(false);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(12),dp(8),dp(12),dp(8));b.setMinHeight(dp(48));b.setMinimumHeight(dp(48));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x335c8f80),new ColorDrawable(android.graphics.Color.TRANSPARENT),null));b.setOnClickListener(v->action.run());return b;}
    public Button info(String title,String explanation){
        Button info=button("ⓘ",()->{
            if(helpDialog!=null)helpDialog.dismiss();
            helpDialog=new android.app.AlertDialog.Builder(context).setTitle(title).setMessage(explanation).setPositiveButton("Got it",null).create();
            helpDialog.show();helpDialog.getWindow().setLayout(Math.min(dp(360),context.getResources().getDisplayMetrics().widthPixels-dp(32)),-2);
        });
        info.setGravity(Gravity.CENTER);info.setPadding(0,0,0,0);info.setTextColor(ACCENT);
        info.setContentDescription("About "+title);
        if(android.os.Build.VERSION.SDK_INT>=26)info.setTooltipText(explanation);
        return info;
    }
    /** Brief feedback only; obey Android's reduced/disabled animation setting. */
    public void reveal(View view){
        view.animate().cancel();view.setAlpha(1);view.setTranslationY(0);
        if(android.os.Build.VERSION.SDK_INT>=26&&android.animation.ValueAnimator.areAnimatorsEnabled()){
            view.setAlpha(.85f);view.setTranslationY(dp(6));
            view.animate().alpha(1).translationY(0).setDuration(160).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
        }
    }
    public LinearLayout panel(String title){
        LinearLayout p=new LinearLayout(context);p.setOrientation(LinearLayout.VERTICAL);p.setPadding(dp(8),dp(6),dp(8),dp(6));
        GradientDrawable bg=new GradientDrawable();bg.setColor(SURFACE);bg.setCornerRadius(dp(16));bg.setStroke(dp(1),new AppAppearance(context).border);p.setBackground(bg);
        TextView label=label(title,12);label.setTextColor(ACCENT);p.addView(label);return p;
    }
    /** Stack when either region would become too narrow; controls retain their own scrolling. */
    public LinearLayout columns(View primary,View secondary){
        LinearLayout columns=new LinearLayout(context){@Override protected void onMeasure(int w,int h){
            boolean wide=View.MeasureSpec.getSize(w)>=dp(600);setOrientation(wide?HORIZONTAL:VERTICAL);
            for(int i=0;i<getChildCount();i++){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)getChildAt(i).getLayoutParams();lp.width=wide?0:-1;lp.weight=wide?(i==0?1.15f:1):0;lp.height=-2;lp.setMargins(dp(2),dp(2),dp(2),dp(2));}
            super.onMeasure(w,h);
        }};columns.setGravity(Gravity.TOP);columns.addView(primary,new LinearLayout.LayoutParams(0,-2,1));columns.addView(secondary,new LinearLayout.LayoutParams(0,-2,1));return columns;
    }
    /** Real layout gaps between rows, including nested expanded sections. */
    public void spaceRows(View view){
        android.content.res.ColorStateList tint=new android.content.res.ColorStateList(new int[][]{new int[]{android.R.attr.state_enabled,android.R.attr.state_checked},new int[]{android.R.attr.state_enabled},new int[]{}},new int[]{ACCENT,MUTED,MUTED});
        if(view instanceof Switch){((Switch)view).setThumbTintList(tint);((Switch)view).setTrackTintList(tint);}
        if(view instanceof SeekBar){((SeekBar)view).setProgressTintList(ColorStateList.valueOf(ACCENT));((SeekBar)view).setThumbTintList(ColorStateList.valueOf(ACCENT));}
        if(!(view instanceof ViewGroup))return;
        ViewGroup group=(ViewGroup)view;
        if(group instanceof LinearLayout&&((LinearLayout)group).getOrientation()==LinearLayout.VERTICAL){
            for(int i=0;i<group.getChildCount();i++){View child=group.getChildAt(i);if(child.getLayoutParams() instanceof LinearLayout.LayoutParams){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)child.getLayoutParams();lp.bottomMargin=Math.max(lp.bottomMargin,dp(7));child.setLayoutParams(lp);}}
        }
        for(int i=0;i<group.getChildCount();i++)spaceRows(group.getChildAt(i));
    }
    public void page(String title,View body,Runnable apply,Runnable reset,Runnable cancel){
        content.animate().cancel();
        heading.setText(title.equals("Gyroscope")?"Motion aiming":title.equals("FPS & Two-Thumb")?"Camera & aiming":title);status("");content.removeAllViews();ScrollView scroll=new ScrollView(context);scroll.setFillViewport(false);scroll.addView(body);content.addView(scroll,new LinearLayout.LayoutParams(-1,-1));
        // Explicit submission is reserved for naming, import and connection transactions.
        if(apply!=null&&body instanceof LinearLayout){Button submit=button(title.equals("Wi-Fi companion")?"Connect":"Save",apply);submit.setTextColor(ACCENT);((LinearLayout)body).addView(submit);}
        spaceRows(body);reveal(content);
    }
}
