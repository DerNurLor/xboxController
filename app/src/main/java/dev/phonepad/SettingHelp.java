package dev.phonepad;

import java.util.Locale;
import java.util.regex.*;

/** Explanations are presentation only; persisted schema keys remain unchanged. */
public final class SettingHelp {
    public static float[] range(String title,float initial){
        Matcher m=Pattern.compile("\\(([0-9.]+)–([0-9.]+)").matcher(title);
        if(m.find())return new float[]{Float.parseFloat(m.group(1)),Float.parseFloat(m.group(2))};
        String t=title.toLowerCase(Locale.ROOT);
        if(t.contains("-1 uses"))return new float[]{-1,t.contains("dead")?.4f:5};
        if(t.contains("grid spacing"))return new float[]{1,10};
        if(t.contains("width (%)"))return new float[]{2.5f,50};
        if(t.contains("height (%)"))return new float[]{4,70};
        if(t.contains("%"))return new float[]{0,100};
        return new float[]{0,Math.max(1,initial*2)};
    }
    public static float defaultValue(String title,float fallback){
        String t=title.toLowerCase(Locale.ROOT);
        if(t.contains("-1 uses"))return -1;
        if(t.contains("camera smoothing"))return 0;
        if(t.contains("filtering"))return .35f;
        if(t.contains("rest dead"))return .015f;
        if(t.contains("trigger activation dead"))return .12f;
        if(t.contains("dead zone"))return .08f;
        if(t.contains("grid spacing"))return 2.5f;
        if(t.contains("overall touch"))return 1.15f;
        if(t.contains("travel fraction"))return .16f;
        if(t.contains("activation distance"))return .18f;
        if(t.contains("tap duration"))return 180;
        if(t.contains("tap interval"))return 280;
        if(t.contains("tap movement"))return 2.5f;
        if(t.contains("overall intensity"))return .7f;
        if(t.equals("sensitivity (0.05–5)"))return .6f;
        if(t.contains("sensitivity")||t.contains("exponent")||t.contains("opacity")||t.contains("multiplier")||t.contains("mix weight"))return 1;
        if(t.contains("pressure"))return 0;
        return fallback;
    }
    public static boolean defaultToggle(String title,boolean fallback){String t=title.toLowerCase(Locale.ROOT);if(t.contains("invert")||t.contains("reverse")||t.contains("enable")||t.contains("second finger")||t.contains("quiet")||t.contains("texture")||t.contains("snap")||t.contains("preserve")||t.contains("keep compatible")||t.contains("switch with"))return false;if(t.contains("haptics master")||t.contains("host game")||t.contains("local touch")||t.contains("feedback")||t.contains("grid")||t.contains("origin follows")||t.contains("only while"))return true;return fallback;}
    public static int defaultChoice(String title,int fallback){if(title.equals("Left stick")||title.equals("Local cue strength"))return 1;if(title.equals("Right stick")||title.contains("gesture")||title.contains("Mode")||title.contains("Orientation")||title.contains("Response")||title.contains("Aim activation"))return 0;return fallback;}
    public static String description(String title){
        String t=title.toLowerCase(Locale.ROOT);
        if(t.contains("quick switch layout"))return "Keep chosen layouts in a movable panel. Add layouts with + and move it only outside game mode.";
        if(t.contains("only while lt"))return "Hold LT while moving the phone to use gyro. Turn off to use gyro without holding LT.";
        if(t.contains("rs sensitivity while lt"))return "Speed of right-stick and gyro aiming while LT is held. 100% keeps the normal speed; lower values slow it down.";
        if(t.contains("gyro"))return "Move the phone to aim, alongside the right stick. Calibrate if the camera moves while the phone is still.";
        if(t.contains("tap duration"))return "How short the first tap must be to start tap-and-hold firing.";
        if(t.contains("tap interval"))return "How long you can wait between the first tap and the second touch.";
        if(t.contains("tap movement"))return "How far your finger can move between taps. Lower values help prevent accidental firing.";
        if(t.contains("same-thumb"))return "Tap, then hold nearby to fire while moving the camera. Lift your thumb to stop.";
        if(t.contains("gameplay lock")||t.contains("prefer game mode"))return "Keep the controller in game mode. Hold the edge handle or use Android Back to exit directly.";
        if(t.contains("dead zone"))return "Ignore small movements around the center. Increase to reduce drift; decrease for finer control.";
        if(t.contains("smoothing")||t.contains("filtering"))return "Smooth shaky movement. Higher values feel steadier but respond more slowly.";
        if(t.contains("exponent")||t.equals("response")||t.contains("curve"))return "Change how quickly output grows. Values above 1 give finer control near the center.";
        if(t.contains("sensitivity")||t.contains("gain"))return "Higher values turn the same movement into a stronger response.";
        if(t.contains("travel")||t.contains("activation distance"))return "How far to slide for full trigger pressure. A shorter distance reaches full pressure sooner.";
        if(t.contains("preserve ls"))return "Keep forward and backward stick movement while also using the stick for gas and brake.";
        if(t.contains("steering +"))return "Steer left or right; move up for gas and down for brake. Center the stick to release.";
        if(t.contains("unified"))return "Use one slider for both triggers: up or left for RT, down or right for LT.";
        if(t.contains("fullscreen camera"))return "Swipe empty screen space to move the camera. Buttons and sticks stay on top. Turn off to restore the original camera area.";
        if(t.contains("origin")||t.equals("left stick")||t.equals("right stick")||t.equals("mode"))return "Fixed uses the drawn center. Floating starts where you touch. Camera follows finger movement.";
        if(t.contains("invert")||t.contains("reverse"))return "Reverse this direction without changing other controls.";
        if(t.contains("aim activation"))return "Hold keeps aim active while touching AIM. Toggle keeps it active until you tap AIM again.";
        if(t.contains("second finger"))return "Touch the camera area with a second finger to fire. Lift it to stop.";
        if(t.contains("host game"))return "Play vibration sent by the game through a supported computer connection.";
        if(t.contains("local touch")||t.contains("touch feedback")||t.contains("local cue"))return "Feel a short vibration when you use a control. This is separate from game vibration.";
        if(t.contains("mix weight"))return "Adjust how much this game vibration channel contributes to the phone's vibration.";
        if(t.contains("intensity"))return "Set vibration strength. Zero is silent.";
        if(t.contains("texture"))return "Use short pulses for a textured vibration instead of a steady buzz.";
        if(t.contains("quiet"))return "Reduce vibration to 40% of your selected strength.";
        if(t.contains("haptics master"))return "Turn all phone vibration on or off.";
        if(t.contains("gesture"))return "Choose how to activate this control, such as a hold, double tap, or long press.";
        if(t.contains("hold destination")||t.contains("while lt"))return "Use another layout while this control is held, then return when released.";
        if(t.contains("compatible"))return "Keep a held stick across compatible layouts. Both layouts must enable this option.";
        if(t.contains("pressure"))return "Set trigger pressure from released (0) to fully pressed (1).";
        if(t.contains("touch multiplier"))return "Enlarge the invisible touch target without changing the control's appearance.";
        if(t.contains("width")||t.contains("height")||t.contains("center"))return "Set this control's size or position within the usable screen area.";
        if(t.contains("opacity"))return "Make the control more or less visible. Its touch area stays the same.";
        if(t.contains("grid")||t.contains("snap"))return "Use a grid to line up controls in the layout editor.";
        if(t.contains("label"))return "Change button names without changing what they send to the game.";
        if(t.contains("show")||t.contains("visible")||t.contains("hide"))return "Choose whether this control is available in the current layout.";
        if(t.contains("destination"))return "Choose the layout or game profile this action opens.";
        if(t.equals("action"))return "Choose what this control does when activated.";
        if(t.contains("ip address"))return "Enter the computer's local IP address shown by the companion.";
        if(t.contains("port"))return "Use the same port as the companion. The default is 39570.";
        if(t.contains("pairing key"))return "Enter the 32-character key from the companion. It is not saved on the phone.";
        if(t.contains("name"))return "Choose a name so you can find this layout or game profile later.";
        if(t.contains("json"))return "Paste a layout or game profile previously exported by PhonePad.";
        return "Adjust this option for the current layout. Changes save automatically.";
    }
}
