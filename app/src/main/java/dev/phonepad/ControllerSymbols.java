package dev.phonepad;

import android.graphics.*;

/** Crisp platform-style glyphs. Only presentation changes; custom labels win. */
public final class ControllerSymbols {
    static boolean standard(ControllerConfig.Control c){return (c.label.isEmpty()||c.label.equals(c.id))&&c.kind.equals("button")&&c.action.equals("BUTTONS")&&(c.buttons<0||c.buttons==(1<<c.bit));}
    static String label(ControllerConfig.Control c,boolean ps){
        if(!c.label.isEmpty()&&!c.label.equals(c.id)||!ps)return c.title();
        switch(c.id){case "A":return "Cross";case "B":return "Circle";case "X":return "Square";case "Y":return "Triangle";case "HOME":return "PlayStation";case "VIEW":return "Create";case "MENU":return "Options";case "LT":return "L2";case "RT":return "R2";case "LB":return "L1";case "RB":return "R1";default:return c.title();}
    }
    static int faceColor(String id,boolean ps,boolean light){
        switch(id){case "A":return ps?(light?0xff175caa:0xff91b9f7):(light?0xff277414:0xffb2f96c);case "B":return light?0xffb42a44:0xffff8b90;case "X":return ps?(light?0xffa13682:0xffeda5dc):(light?0xff175caa:0xff86baff);case "Y":return ps?(light?0xff137962:0xff8be1c1):(light?0xff806000:0xffead080);default:return light?0xff192b38:0xffe5e9ec;}
    }
    static boolean draw(Canvas canvas,Paint paint,ControllerConfig.Control c,boolean ps,float x,float y,float size,int color){
        if(!standard(c)||!(c.id.equals("HOME")||c.id.equals("VIEW")||c.id.equals("MENU")||c.id.equals("SHARE")||ps&&c.id.matches("[ABXY]")))return false;
        int save=canvas.save();canvas.translate(x,y);canvas.scale(size,size);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(.12f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(color);
        switch(c.id){
            case "A":canvas.drawLine(-.7f,-.7f,.7f,.7f,paint);canvas.drawLine(-.7f,.7f,.7f,-.7f,paint);break;
            case "B":canvas.drawCircle(0,0,.8f,paint);break;
            case "X":canvas.drawRect(-.7f,-.7f,.7f,.7f,paint);break;
            case "Y":{Path p=new Path();p.moveTo(0,-.85f);p.lineTo(.85f,.65f);p.lineTo(-.85f,.65f);p.close();canvas.drawPath(p,paint);break;}
            case "HOME":
                if(!ps){canvas.drawCircle(0,0,.92f,paint);Path p=new Path();p.moveTo(-.62f,-.57f);p.cubicTo(-.2f,-.52f,.4f,.18f,.65f,.63f);p.moveTo(.62f,-.57f);p.cubicTo(.2f,-.52f,-.4f,.18f,-.65f,.63f);canvas.drawPath(p,paint);}
                else{Path p=new Path();p.moveTo(-.2f,.8f);p.lineTo(-.2f,-.85f);p.cubicTo(.8f,-.7f,.8f,.15f,.12f,.12f);canvas.drawPath(p,paint);canvas.drawArc(-.88f,.15f,.88f,.73f,165,280,false,paint);}
                break;
            case "VIEW":
                if(ps){canvas.drawLine(-.6f,.4f,-.6f,-.3f,paint);canvas.drawLine(0,.3f,0,-.7f,paint);canvas.drawLine(.6f,.4f,.6f,-.3f,paint);}
                else{canvas.drawRect(-.8f,-.6f,.35f,.25f,paint);canvas.drawRect(-.35f,-.25f,.8f,.6f,paint);}break;
            case "MENU":for(int i=-1;i<=1;i++)canvas.drawLine(-.7f,i*.45f,.7f,i*.45f,paint);break;
            case "SHARE":canvas.drawLine(0,.4f,0,-.7f,paint);canvas.drawLine(-.35f,-.3f,0,-.7f,paint);canvas.drawLine(.35f,-.3f,0,-.7f,paint);Path p=new Path();p.moveTo(-.7f,0);p.lineTo(-.7f,.7f);p.lineTo(.7f,.7f);p.lineTo(.7f,0);canvas.drawPath(p,paint);break;
        }
        paint.setStrokeCap(Paint.Cap.BUTT);paint.setStrokeJoin(Paint.Join.MITER);paint.setStyle(Paint.Style.FILL);canvas.restoreToCount(save);return true;
    }
}
