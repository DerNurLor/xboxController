package dev.phonepad;

import java.nio.*;
import java.util.*;

public final class CoreTest {
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception{
        PadState p=new PadState();check(p.report().length==13,"HID payload length");check(p.report()[2]==8,"neutral hat");
        p.buttons=(1<<PadState.A)|(1<<PadState.R3);p.lx=-1;p.ly=1;p.rx=.5f;p.rt=1;
        ByteBuffer b=ByteBuffer.wrap(p.report()).order(ByteOrder.LITTLE_ENDIAN);
        check((b.getShort()&65535)==16385,"simultaneous buttons");b.get();
        check(b.getShort()==-32767&&b.getShort()==32767&&b.getShort()==16384,"axis endpoints");
        check((p.report()[12]&255)==255,"full trigger");
        float[] s=PadState.stick(.01f,.01f,.08f,1);check(s[0]==0&&s[1]==0,"radial dead zone");
        s=PadState.stick(1,1,.08f,1);check(Math.abs(Math.hypot(s[0],s[1])-1)<.00001,"circular saturation");
        p.clear();check(Arrays.equals(p.report(),new PadState().report()),"release all");
        byte[] key=WifiPad.hex("000102030405060708090a0b0c0d0e0f"),nonce=new byte[16];
        byte[] packet=WifiPad.packet(3,nonce,1,p.report(),key);
        check(WifiPad.valid(packet,3,13,key),"signed packet");
        packet[30]^=1;check(!WifiPad.valid(packet,3,13,key),"tamper rejection");
        check(!WifiPad.valid(new byte[0],3,13,key),"short frame rejection");
        WifiPad wifi=new WifiPad(new WifiPad.Listener(){public void status(String s){}public void rumble(int l,int h,int ms){}});
        p.buttons=1;wifi.send(p.report());p.clear();wifi.send(p.report());
        long now=System.nanoTime()/1_000_000;
        check(wifi.nextReport(now)[0]==1&&wifi.nextReport(now)[0]==0,"preserve sub-frame taps");
        p.buttons=1;wifi.send(p.report());wifi.neutral();check(wifi.nextReport(now)[0]==0,"urgent neutral clears queued presses");
        wifi.send(p.report());check(wifi.nextReport(now+1000)[0]==0,"stalled UI neutralizes Wi-Fi output");
        byte[] vector=WifiPad.packet(1,nonce,0,new byte[0],key);
        StringBuilder out=new StringBuilder();for(byte v:vector)out.append(String.format("%02x",v&255));
        System.out.println("HELLO_VECTOR="+out);System.out.println("Core tests passed");
    }
}
