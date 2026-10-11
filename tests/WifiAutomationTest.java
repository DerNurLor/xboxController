package dev.phonepad;

public final class WifiAutomationTest {
    public static void main(String[] args)throws Exception{
        final int[] snapshots={0};
        WifiPad wifi=new WifiPad(new WifiPad.Listener(){
            public void status(String message){}
            public void rumble(int low,int high,int ms){}
            public void gameState(String json,long session){if(json.equals("{\"vehicle\":\"false\"}"))snapshots[0]++;}
        });
        try{
            wifi.connect("127.0.0.1",Integer.parseInt(args[0]),"000102030405060708090a0b0c0d0e0f");
            long start=System.nanoTime();boolean moved=false;
            while((System.nanoTime()-start)/1_000_000<1800){
                long elapsed=(System.nanoTime()-start)/1_000_000;
                PadState pad=new PadState();byte[] desktop=new byte[Automation.DESKTOP_SIZE];
            if(elapsed>400&&elapsed<1000){pad.buttons=1;desktop[44/8]|=1<<(44%8);desktop[32]=1;if(!moved){desktop[34]=37;moved=true;}}
                wifi.send(pad.report());wifi.sendDesktop(desktop);Thread.sleep(16);
            }
            if(!wifi.connected()||!wifi.desktopConnected()||snapshots[0]<30)throw new AssertionError("Both channels must stay live: "+snapshots[0]);
            wifi.neutral();Thread.sleep(70);
        }finally{wifi.close();}
    }
}
