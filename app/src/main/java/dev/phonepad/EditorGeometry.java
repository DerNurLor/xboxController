package dev.phonepad;
import java.util.*;

/** Pure normalized geometry shared by the canvas and regression tests. */
final class EditorGeometry {
    static void transform(List<ControllerConfig.Control> controls,String operation){
        if(controls.size()<2)return;
        float l=1,r=0,t=1,b=0;for(ControllerConfig.Control c:controls){l=Math.min(l,c.x-c.w/2);r=Math.max(r,c.x+c.w/2);t=Math.min(t,c.y-c.h/2);b=Math.max(b,c.y+c.h/2);}
        if(operation.equals("distributeX")||operation.equals("distributeY")){
            if(controls.size()<3)return;boolean horizontal=operation.equals("distributeX");List<ControllerConfig.Control> sorted=new ArrayList<>(controls);sorted.sort(Comparator.comparingDouble(c->horizontal?c.x:c.y));
            float total=0;for(ControllerConfig.Control c:sorted)total+=horizontal?c.w:c.h;float gap=((horizontal?r-l:b-t)-total)/(sorted.size()-1),position=horizontal?l:t;
            for(ControllerConfig.Control c:sorted){float size=horizontal?c.w:c.h;if(horizontal)c.x=position+size/2;else c.y=position+size/2;position+=size+gap;}return;
        }
        float width=controls.get(0).w,height=controls.get(0).h;
        for(ControllerConfig.Control c:controls){switch(operation){case "left":c.x=l+c.w/2;break;case "right":c.x=r-c.w/2;break;case "top":c.y=t+c.h/2;break;case "bottom":c.y=b-c.h/2;break;case "centerX":c.x=(l+r)/2;break;case "centerY":c.y=(t+b)/2;break;case "width":c.w=width;break;case "height":c.h=height;break;}clamp(c);}
    }
    static void clamp(ControllerConfig.Control c){c.x=PadState.clamp(c.x,c.w/2,1-c.w/2);c.y=PadState.clamp(c.y,Math.max(.1f,c.h/2),1-c.h/2);}
    static float[] snap(ControllerConfig.Control moving,List<ControllerConfig.Control> controls,Set<String> selected,float thresholdX,float thresholdY){
        float[] result={Float.NaN,Float.NaN};float bx=thresholdX,by=thresholdY,dx=0,dy=0;
        List<float[]> targets=new ArrayList<>();targets.add(new float[]{0,.5f,1,0,.5f,1});for(ControllerConfig.Control c:controls)if(c.visible&&!selected.contains(c.id))targets.add(new float[]{c.x-c.w/2,c.x,c.x+c.w/2,c.y-c.h/2,c.y,c.y+c.h/2});
        for(float[] target:targets)for(int i=0;i<3;i++)for(int j=0;j<3;j++){
            float x=target[i]-(moving.x+(j-1)*moving.w/2),y=target[i+3]-(moving.y+(j-1)*moving.h/2);
            if(Math.abs(x)<bx){bx=Math.abs(x);dx=x;result[0]=target[i];}if(Math.abs(y)<by){by=Math.abs(y);dy=y;result[1]=target[i+3];}
        }
        moving.x+=dx;moving.y+=dy;clamp(moving);return result;
    }
    private EditorGeometry(){}
}
