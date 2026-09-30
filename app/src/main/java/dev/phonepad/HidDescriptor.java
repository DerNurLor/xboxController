package dev.phonepad;

public final class HidDescriptor {
    private HidDescriptor() {}
    // Report 1: 16 buttons, hat+padding, X/Y/Rx/Ry signed 16-bit, Z/Rz unsigned 8-bit.
    // Report 2: vendor output, two motor amplitudes and duration in 10 ms units.
    // Generic hosts do not automatically send vendor rumble; a companion can.
    public static final byte[] BYTES=hex(
        "05 01 09 05 A1 01 85 01 " +
        "05 09 19 01 29 10 15 00 25 01 75 01 95 10 81 02 " +
        "05 01 09 39 15 00 25 07 35 00 46 3B 01 65 14 75 04 95 01 81 42 " +
        "65 00 75 04 95 01 81 03 " +
        "09 30 09 31 09 33 09 34 16 01 80 26 FF 7F 36 00 00 46 00 00 75 10 95 04 81 02 " +
        "09 32 09 35 15 00 26 FF 00 75 08 95 02 81 02 " +
        "85 02 06 00 FF 09 01 15 00 26 FF 00 75 08 95 03 91 02 C0");
    private static byte[] hex(String h) {
        String[] a=h.split(" "); byte[] b=new byte[a.length];
        for(int i=0;i<a.length;i++) b[i]=(byte)Integer.parseInt(a[i],16);
        return b;
    }
}
