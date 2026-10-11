package dev.phonepad;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public final class HidDesktopReportsTest {
    private static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);}
    private static boolean contains(byte[] data,int... wanted){outer:for(int i=0;i<=data.length-wanted.length;i++){for(int n=0;n<wanted.length;n++)if((data[i+n]&255)!=wanted[n])continue outer;return true;}return false;}
    public static void main(String[] args){
        byte[] state=new byte[Automation.DESKTOP_SIZE];
        state[44/8]|=1<<(44%8);state[224/8]|=1<<(224%8);state[32]=(byte)0xe1;
        ByteBuffer.wrap(state).order(ByteOrder.BIG_ENDIAN).position(33).putShort((short)0x1234).putShort((short)-2).putShort((short)300);
        byte[] keyboard=HidDesktopReports.keyboard(state),mouse=HidDesktopReports.mouse(state);
        check(keyboard.length==29,"NKRO report size");
        check((keyboard[44/8]&(1<<(44%8)))!=0&&(keyboard[224/8]&(1<<(224%8)))!=0,"Space and modifier usages");
        check(mouse.length==7&&(mouse[0]&255)==1,"mouse button mask");
        ByteBuffer axes=ByteBuffer.wrap(mouse).order(ByteOrder.LITTLE_ENDIAN);axes.get();
        check(axes.getShort()==0x1234&&axes.getShort()==-2&&axes.getShort()==300,"mouse relative axes and wheel");
        check(Arrays.equals(HidDesktopReports.keyboard(new byte[Automation.DESKTOP_SIZE]),new byte[29]),"keyboard neutral report");
        check(Arrays.equals(HidDesktopReports.mouse(new byte[Automation.DESKTOP_SIZE]),new byte[7]),"mouse neutral report");
        byte[] descriptor=HidDescriptor.BYTES;
        check(descriptor.length<2048,"Android SDP descriptor limit");
        check(contains(descriptor,0x85,0x01)&&contains(descriptor,0x85,0x02)&&contains(descriptor,0x85,0x03)&&contains(descriptor,0x85,0x04),"all report IDs advertised");
        check(contains(descriptor,0x19,0x00,0x29,0xe7,0x75,0x01,0x96,0xe8,0x00),"keyboard descriptor matches 232-bit report");
        check(contains(descriptor,0x09,0x30,0x09,0x31,0x09,0x38,0x16,0x00,0x80,0x26,0xff,0x7f,0x75,0x10,0x95,0x03),"mouse descriptor matches signed 16-bit report");
        System.out.println("Bluetooth desktop HID tests passed");
    }
}
