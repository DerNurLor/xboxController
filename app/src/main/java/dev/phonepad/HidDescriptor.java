package dev.phonepad;

/** Composite Bluetooth HID descriptor: gamepad, NKRO keyboard and relative mouse. */
public final class HidDescriptor {
    private HidDescriptor() {}

    public static final byte[] BYTES=hex(
        // Report 1: existing 13-byte gamepad report.
        "05 01 09 05 A1 01 85 01 " +
        "05 09 19 01 29 10 15 00 25 01 75 01 95 10 81 02 " +
        "05 01 09 39 15 00 25 07 35 00 46 3B 01 65 14 75 04 95 01 81 42 " +
        "65 00 75 04 95 01 81 03 " +
        "09 30 09 31 09 33 09 34 16 01 80 26 FF 7F 36 00 00 46 00 00 75 10 95 04 81 02 " +
        "09 32 09 35 15 00 26 FF 00 75 08 95 02 81 02 " +
        "85 02 06 00 FF 09 01 15 00 26 FF 00 75 08 95 03 91 02 C0 " +
        // Report 3: one bit for each keyboard usage from 0x00 through 0xE7.
        "05 01 09 06 A1 01 85 03 " +
        "05 07 15 00 25 01 19 00 29 E7 75 01 96 E8 00 81 02 C0 " +
        // Report 4: five mouse buttons and signed 16-bit X, Y and wheel deltas.
        "05 01 09 02 A1 01 85 04 09 01 A1 00 " +
        "05 09 19 01 29 05 15 00 25 01 75 01 95 05 81 02 " +
        "75 03 95 01 81 03 " +
        "05 01 09 30 09 31 09 38 16 00 80 26 FF 7F 75 10 95 03 81 06 " +
        "C0 C0 " +
        // Report 5: source controls for Companion visualization; vendor-defined, no OS input.
        "06 00 FF 09 02 A1 01 85 05 15 00 26 FF 00 75 08 95 0D 09 02 81 02 C0");

    private static byte[] hex(String value) {
        String[] parts=value.split(" ");byte[] bytes=new byte[parts.length];
        for(int i=0;i<parts.length;i++)bytes[i]=(byte)Integer.parseInt(parts[i],16);
        return bytes;
    }
}
