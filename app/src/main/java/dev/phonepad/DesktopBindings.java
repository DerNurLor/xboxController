package dev.phonepad;

import java.util.*;

/** Desktop actions selected in the mapper are outputs, not physical input sources. */
public final class DesktopBindings {
    private DesktopBindings() {}
    public static String output(String token) {
        if(token.contains("+")){
            Set<String> chord=new LinkedHashSet<>();
            for(String part:token.split("\\+",-1))chord.add(output(part));
            return String.join("+",chord);
        }
        if(token.matches("(?:MOUSE_[XY]|WHEEL)=-?[0-9]+(?:\\.[0-9]+)?")){
            String[] parts=token.split("=",2);float value=Float.parseFloat(parts[1]);
            if(Float.isFinite(value)&&Math.abs(value)<=(parts[0].equals("WHEEL")?30:2000))return token;
        }
        switch(token) {
            case "MOUSE_X_NEG": return "MOUSE_X=-500";
            case "MOUSE_X_POS": return "MOUSE_X=500";
            case "MOUSE_Y_NEG": return "MOUSE_Y=-500";
            case "MOUSE_Y_POS": return "MOUSE_Y=500";
            case "WHEEL_UP": return "WHEEL=5";
            case "WHEEL_DOWN": return "WHEEL=-5";
            default:
                if(Automation.KEYS.containsKey(token)||Arrays.asList("MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD").contains(token))return token;
                throw new IllegalArgumentException("Unsupported desktop action: "+token);
        }
    }
    public static String binding(ControllerConfig.Control c,String direction) {
        switch(direction){case "up":return c.mapUp;case "down":return c.mapDown;case "left":return c.mapLeft;case "right":return c.mapRight;default:return c.binding;}
    }
    private static void set(ControllerConfig.Control c,String direction,String value) {
        switch(direction){case "up":c.mapUp=value;break;case "down":c.mapDown=value;break;case "left":c.mapLeft=value;break;case "right":c.mapRight=value;break;default:c.binding=value;}
    }
    public static boolean pointerControl(ControllerConfig.Control c) {
        return c.kind.equals("stick")||c.kind.equals("trackpad")||c.kind.equals("dpad");
    }
    public static void assign(ControllerConfig.Control c,String direction,String token) {
        if(token.equals("MOUSE_MOVE")){
            if(!pointerControl(c))throw new IllegalArgumentException("Choose a stick, trackpad or D-pad for pointer movement");
            // A whole-control binding would bypass directional processing.
            c.binding="";
            c.mapUp=output("MOUSE_Y_NEG");c.mapDown=output("MOUSE_Y_POS");
            c.mapLeft=output("MOUSE_X_NEG");c.mapRight=output("MOUSE_X_POS");
        }else {String value=output(token);if(!direction.isEmpty()&&pointerControl(c))c.binding="";set(c,direction,value);}
    }
    public static boolean contains(ControllerConfig.Control c,String direction,String token) {
        if(token.equals("MOUSE_MOVE"))return pointerControl(c)&&c.binding.isEmpty()&&
            c.mapUp.equals(output("MOUSE_Y_NEG"))&&c.mapDown.equals(output("MOUSE_Y_POS"))&&
            c.mapLeft.equals(output("MOUSE_X_NEG"))&&c.mapRight.equals(output("MOUSE_X_POS"));
        return Arrays.asList(binding(c,direction).split("\\+")).containsAll(Arrays.asList(output(token).split("\\+")));
    }
    public static void remove(ControllerConfig.Control c,String direction,String token) {
        if(token.equals("MOUSE_MOVE")){
            if(contains(c,direction,token)){c.mapUp="";c.mapDown="";c.mapLeft="";c.mapRight="";}
            return;
        }
        List<String> remaining=new ArrayList<>(Arrays.asList(binding(c,direction).split("\\+")));
        remaining.removeAll(Arrays.asList(output(token).split("\\+")));set(c,direction,String.join("+",remaining));
    }
    /** Repair only the old mapper's all-directions-in-one-output cancellation artifact. */
    public static boolean repairCancelledPointer(ControllerConfig.Control c) {
        if(!c.kind.equals("stick")||!c.binding.isEmpty()||!c.mapDown.isEmpty()||!c.mapLeft.isEmpty()||!c.mapRight.isEmpty())return false;
        String[] tokens={"MOUSE_Y_NEG","MOUSE_Y_POS","MOUSE_X_NEG","MOUSE_X_POS"};
        String[] directions={"up","down","left","right"};
        Set<String> expected=new HashSet<>();for(String token:tokens)expected.add(output(token));
        String[] existing=c.mapUp.split("\\+");
        if(existing.length!=4||!new HashSet<>(Arrays.asList(existing)).equals(expected))return false;
        for(int i=0;i<tokens.length;i++){
            boolean found=false;
            for(ControllerConfig.Mapping m:c.mappings)
                if(m.source.equals(tokens[i])&&m.direction.equals(directions[i])&&m.output.isEmpty())found=true;
            if(!found)return false;
        }
        assign(c,"","MOUSE_MOVE");return true;
    }
}
