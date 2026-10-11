package dev.phonepad;

import android.view.*;
import java.util.*;

/** Android device input is translated to named sources; layout mappings decide its output. */
final class HardwareInput {
    static final Map<Integer,String> NAMES=new HashMap<>();
    private static final Map<Integer,float[]> mousePositions=new HashMap<>();
    static void reset(){mousePositions.clear();}
    static {
        for(int key=KeyEvent.KEYCODE_A;key<=KeyEvent.KEYCODE_Z;key++)NAMES.put(key,"KEY_"+(char)('A'+key-KeyEvent.KEYCODE_A));
        for(int key=KeyEvent.KEYCODE_0;key<=KeyEvent.KEYCODE_9;key++)NAMES.put(key,"KEY_"+(key-KeyEvent.KEYCODE_0));
        for(int i=0;i<12;i++)NAMES.put(KeyEvent.KEYCODE_F1+i,"KEY_F"+(i+1));
        int[] codes={KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_ESCAPE,KeyEvent.KEYCODE_DEL,KeyEvent.KEYCODE_TAB,KeyEvent.KEYCODE_SPACE,KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_SHIFT_LEFT,KeyEvent.KEYCODE_ALT_LEFT,KeyEvent.KEYCODE_META_LEFT,KeyEvent.KEYCODE_CTRL_RIGHT,KeyEvent.KEYCODE_SHIFT_RIGHT,KeyEvent.KEYCODE_ALT_RIGHT,KeyEvent.KEYCODE_META_RIGHT,KeyEvent.KEYCODE_DPAD_UP,KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_BUTTON_A,KeyEvent.KEYCODE_BUTTON_B,KeyEvent.KEYCODE_BUTTON_X,KeyEvent.KEYCODE_BUTTON_Y,KeyEvent.KEYCODE_BUTTON_L1,KeyEvent.KEYCODE_BUTTON_R1,KeyEvent.KEYCODE_BUTTON_L2,KeyEvent.KEYCODE_BUTTON_R2,KeyEvent.KEYCODE_BUTTON_THUMBL,KeyEvent.KEYCODE_BUTTON_THUMBR,KeyEvent.KEYCODE_BUTTON_START,KeyEvent.KEYCODE_BUTTON_SELECT};
        String[] names={"KEY_ENTER","KEY_ESC","KEY_BACKSPACE","KEY_TAB","KEY_SPACE","KEY_CTRL","KEY_SHIFT","KEY_ALT","KEY_META","KEY_RIGHTCTRL","KEY_RIGHTSHIFT","KEY_RIGHTALT","KEY_RIGHTMETA","KEY_UP","KEY_DOWN","KEY_LEFT","KEY_RIGHT","PAD_A","PAD_B","PAD_X","PAD_Y","PAD_LB","PAD_RB","PAD_LT","PAD_RT","PAD_L3","PAD_R3","PAD_MENU","PAD_VIEW"};for(int i=0;i<codes.length;i++)NAMES.put(codes[i],names[i]);
        String[] extras={"MINUS","EQUALS","LEFT_BRACKET","RIGHT_BRACKET","BACKSLASH","SEMICOLON","APOSTROPHE","GRAVE","COMMA","PERIOD","SLASH","CAPS_LOCK","SCROLL_LOCK","BREAK","INSERT","MOVE_HOME","PAGE_UP","FORWARD_DEL","MOVE_END","PAGE_DOWN","NUM_LOCK","NUMPAD_DIVIDE","NUMPAD_MULTIPLY","NUMPAD_SUBTRACT","NUMPAD_ADD","NUMPAD_ENTER","NUMPAD_1","NUMPAD_2","NUMPAD_3","NUMPAD_4","NUMPAD_5","NUMPAD_6","NUMPAD_7","NUMPAD_8","NUMPAD_9","NUMPAD_0","NUMPAD_DOT"};
        String[] output={"MINUS","EQUAL","LEFTBRACE","RIGHTBRACE","BACKSLASH","SEMICOLON","APOSTROPHE","GRAVE","COMMA","DOT","SLASH","CAPSLOCK","SCROLLLOCK","PAUSE","INSERT","HOME","PAGEUP","DELETE","END","PAGEDOWN","NUMLOCK","KPSLASH","KPASTERISK","KPMINUS","KPPLUS","KPENTER","KP1","KP2","KP3","KP4","KP5","KP6","KP7","KP8","KP9","KP0","KPDOT"};for(int i=0;i<extras.length;i++)NAMES.put(KeyEvent.keyCodeFromString("KEYCODE_"+extras[i]),"KEY_"+output[i]);
    }
    static {NAMES.put(KeyEvent.KEYCODE_SYSRQ,"KEY_PRINTSCREEN");NAMES.put(KeyEvent.KEYCODE_BUTTON_MODE,"PAD_HOME");}
    static String name(KeyEvent e){return NAMES.get(e.getKeyCode());}
    static List<String> sources(){List<String> names=new ArrayList<>(new TreeSet<>(NAMES.values()));names.addAll(Arrays.asList("MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD","AXIS_LX_NEG","AXIS_LX_POS","AXIS_LY_NEG","AXIS_LY_POS","AXIS_RX_NEG","AXIS_RX_POS","AXIS_RY_NEG","AXIS_RY_POS","AXIS_LT_POS","AXIS_RT_POS","AXIS_HATX_NEG","AXIS_HATX_POS","AXIS_HATY_NEG","AXIS_HATY_POS","MOUSE_X_NEG","MOUSE_X_POS","MOUSE_Y_NEG","MOUSE_Y_POS","WHEEL_UP","WHEEL_DOWN"));return names;}
    private static List<String> sources(ControllerConfig.Control c){List<String> out=new ArrayList<>();if(c.independentMappings){for(ControllerConfig.Mapping m:c.mappings)out.add(m.source);}else out.addAll(Arrays.asList(c.source,c.sourceUp,c.sourceDown,c.sourceLeft,c.sourceRight));return out;}
    private static boolean accepts(PadView pad,String name){if(name==null)return false;for(ControllerConfig.Control c:pad.config.controls)for(String expression:sources(c))if(Arrays.asList(expression.split("[+|]")).contains(name))return true;return false;}
    static boolean key(PadView pad,KeyEvent e){
        if(pad==null||pad.editing||pad.settingsOpen)return false;
        String name=name(e),hat=null;
        if(e.isFromSource(InputDevice.SOURCE_GAMEPAD)||e.isFromSource(InputDevice.SOURCE_JOYSTICK)){
            switch(e.getKeyCode()){case KeyEvent.KEYCODE_DPAD_UP:hat="AXIS_HATY_NEG";break;case KeyEvent.KEYCODE_DPAD_DOWN:hat="AXIS_HATY_POS";break;case KeyEvent.KEYCODE_DPAD_LEFT:hat="AXIS_HATX_NEG";break;case KeyEvent.KEYCODE_DPAD_RIGHT:hat="AXIS_HATX_POS";break;}
        }
        boolean normal=accepts(pad,name),direction=accepts(pad,hat);if(!normal&&!direction)return false;
        if(e.getAction()==KeyEvent.ACTION_DOWN||e.getAction()==KeyEvent.ACTION_UP){float amount=e.getAction()==KeyEvent.ACTION_DOWN?1:0;if(normal)pad.input.automation.hardwareForDevice(name,amount,e.getDeviceId());if(direction)pad.input.automation.hardwareForDevice(hat,amount,e.getDeviceId());pad.hardwareChanged();return true;}return false;
    }
    private static boolean transientAxis(PadView pad,String negative,String positive,float value,int device,long now){boolean used=false;for(String name:new String[]{negative,positive})if(accepts(pad,name)){used=true;float amount=Math.max(0,Math.min(1,name.equals(negative)?-value:value));if(amount>0)pad.input.automation.transientInput(name,amount,device,now+60);}return used;}
    static boolean motion(PadView pad,MotionEvent event){if(pad==null||pad.editing||pad.settingsOpen)return false;boolean handled=false;
        if(event.isFromSource(InputDevice.SOURCE_JOYSTICK)){int[] axes={MotionEvent.AXIS_X,MotionEvent.AXIS_Y,MotionEvent.AXIS_Z,MotionEvent.AXIS_RZ,MotionEvent.AXIS_LTRIGGER,MotionEvent.AXIS_RTRIGGER,MotionEvent.AXIS_HAT_X,MotionEvent.AXIS_HAT_Y};String[] names={"LX","LY","RX","RY","LT","RT","HATX","HATY"};for(int i=0;i<axes.length;i++){float value=event.getAxisValue(axes[i]);for(int sign:new int[]{-1,1}){String key="AXIS_"+names[i]+(sign<0?"_NEG":"_POS");if(accepts(pad,key)){pad.input.automation.hardwareForDevice(key,Math.max(0,sign*value),event.getDeviceId());handled=true;}}}}
        if(event.isFromSource(InputDevice.SOURCE_MOUSE)||event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)){
            int device=event.getDeviceId();long now=android.os.SystemClock.uptimeMillis();
            boolean trackpad=false;for(ControllerConfig.Control c:pad.config.controls)if(c.kind.equals("trackpad")&&pad.input.visible(c)&&pad.input.automation.matches(c.condition))trackpad=true;
            int[] bits={MotionEvent.BUTTON_PRIMARY,MotionEvent.BUTTON_SECONDARY,MotionEvent.BUTTON_TERTIARY,MotionEvent.BUTTON_BACK,MotionEvent.BUTTON_FORWARD};
            String[] names={"MOUSE_LEFT","MOUSE_RIGHT","MOUSE_MIDDLE","MOUSE_BACK","MOUSE_FORWARD"};
            for(int i=0;i<bits.length;i++){boolean mapped=accepts(pad,names[i]);float amount=(event.getButtonState()&bits[i])!=0?1:0;if(mapped)pad.input.automation.hardwareForDevice(names[i],amount,device);pad.input.automation.hardwareForDevice("PASSTHROUGH_"+names[i],trackpad&&!mapped?amount:0,device);handled|=mapped||trackpad;}
            float dx=0,dy=0,scroll=0;
            if(event.getActionMasked()==MotionEvent.ACTION_SCROLL)scroll=event.getAxisValue(MotionEvent.AXIS_VSCROLL);
            if(event.getActionMasked()==MotionEvent.ACTION_MOVE||event.getActionMasked()==MotionEvent.ACTION_HOVER_MOVE){float x=event.getX(),y=event.getY();if(event.isFromSource(InputDevice.SOURCE_MOUSE_RELATIVE)){dx=x;dy=y;}else{float[] previous=mousePositions.put(device,new float[]{x,y});if(previous!=null){dx=x-previous[0];dy=y-previous[1];}}}
            boolean mx=transientAxis(pad,"MOUSE_X_NEG","MOUSE_X_POS",dx/20,device,now),my=transientAxis(pad,"MOUSE_Y_NEG","MOUSE_Y_POS",dy/20,device,now),mw=transientAxis(pad,"WHEEL_DOWN","WHEEL_UP",scroll,device,now);
            if(trackpad)pad.input.automation.motion(mx?0:dx,my?0:dy,mw?0:scroll);
            handled|=trackpad||mx||my||mw;
            if(handled&&pad.gameplayLock.locked&&!pad.hasPointerCapture()){pad.requestFocus();pad.requestPointerCapture();}
        }
        if(event.getActionMasked()==MotionEvent.ACTION_HOVER_EXIT)mousePositions.remove(event.getDeviceId());
        if(handled)pad.hardwareChanged();return handled;
    }
}
