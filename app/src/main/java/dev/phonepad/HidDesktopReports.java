package dev.phonepad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/** Translates the transport-neutral desktop state into the Bluetooth HID reports. */
public final class HidDesktopReports {
    public static final int KEYBOARD_REPORT_ID=3,MOUSE_REPORT_ID=4;
    private static final int KEYBOARD_USAGES=232;
    private HidDesktopReports() {}

    public static byte[] keyboard(byte[] state) {
        if(state==null||state.length!=Automation.DESKTOP_SIZE)throw new IllegalArgumentException("Invalid desktop state");
        return Arrays.copyOf(state,KEYBOARD_USAGES/8);
    }

    /** Five button bits followed by little-endian signed X, Y and wheel values. */
    public static byte[] mouse(byte[] state) {
        if(state==null||state.length!=Automation.DESKTOP_SIZE)throw new IllegalArgumentException("Invalid desktop state");
        byte[] report=new byte[7];report[0]=(byte)(state[32]&0x1f);
        ByteBuffer source=ByteBuffer.wrap(state).order(ByteOrder.BIG_ENDIAN);
        ByteBuffer target=ByteBuffer.wrap(report).order(ByteOrder.LITTLE_ENDIAN);
        target.position(1);for(int offset=33;offset<39;offset+=2)target.putShort(source.getShort(offset));
        return report;
    }
}
