package dev.phonepad;

import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated UDP with fresh server sessions, replay protection and full-state frames. */
public final class WifiPad implements AutoCloseable {
    public interface Listener { void status(String s); void rumble(int low,int high,int ms); default void rumble(int low,int high,int ms,long session){rumble(low,high,ms);} default void stopped(long session){rumble(0,0,0,session);} default void gameState(String json,long session){} }
    private final Listener listener;
    private volatile byte[] latest=new PadState().report();
    private volatile boolean running,connected;
    private volatile long generation,latestAt,neutralEpoch;
    private final ArrayDeque<byte[]> edges=new ArrayDeque<>();
    private final ArrayDeque<byte[]> desktopEdges=new ArrayDeque<>();
    private byte[] latestDesktop=new byte[Automation.DESKTOP_SIZE];
 private volatile long desktopAt,lastDesktopAck;
    public boolean desktopConnected(){return connected&&System.nanoTime()/1_000_000-lastDesktopAck<1000;}
    public volatile long desktopGenerated,desktopSerialized,desktopSendAttempts,desktopAcknowledged;
    public synchronized void sendDesktop(byte[] frame){
        desktopGenerated++;
        boolean edge=false;for(int i=0;i<33;i++)if(frame[i]!=latestDesktop[i])edge=true;
        byte[] next=frame.clone();ByteBuffer old=ByteBuffer.wrap(latestDesktop),sum=ByteBuffer.wrap(next);
        for(int i=33;i<39;i+=2)sum.putShort(i,(short)Math.max(-32767,Math.min(32767,old.getShort(i)+sum.getShort(i))));
        latestDesktop=next;desktopAt=System.nanoTime()/1_000_000;
        if(edge){if(desktopEdges.size()>=32){desktopEdges.clear();desktopEdges.add(new byte[Automation.DESKTOP_SIZE]);}desktopEdges.add(latestDesktop.clone());Arrays.fill(latestDesktop,33,39,(byte)0);}
    }
    private synchronized byte[] nextDesktop(long now){if(now-desktopAt>300){desktopEdges.clear();return new byte[Automation.DESKTOP_SIZE];}if(!desktopEdges.isEmpty())return desktopEdges.removeFirst();byte[] result=latestDesktop.clone();Arrays.fill(latestDesktop,33,39,(byte)0);return result;}
    private byte[] latestSource=new PadState().report();
    private final ArrayDeque<byte[]> sourceEdges=new ArrayDeque<>();
    private volatile long sourceAt;
    public synchronized void sendSource(byte[] report){
        if(!Arrays.equals(report,latestSource)){
            if(sourceEdges.size()>=32){sourceEdges.clear();sourceEdges.add(new PadState().report());}
            sourceEdges.add(report.clone());
        }
        latestSource=report.clone();sourceAt=System.nanoTime()/1_000_000;
    }
    private synchronized byte[] nextSource(long now){
        if(now-sourceAt>300){sourceEdges.clear();return new PadState().report();}
        return sourceEdges.isEmpty()?latestSource.clone():sourceEdges.removeFirst();
    }
    private DatagramSocket socket;
    private Thread worker;
    public volatile long reports,invalidFeedback,staleFeedback,lastFeedbackSequence,lastSentSequence;
    public WifiPad(Listener l) {listener=l;}
    public boolean connected() {return connected;}
    public long sessionGeneration(){return generation;}
    public synchronized void send(byte[] r) {
        boolean edge=r[0]!=latest[0]||r[1]!=latest[1]||r[2]!=latest[2]||
            ((r[11]==0)!=(latest[11]==0))||((r[12]==0)!=(latest[12]==0));
        latest=r.clone();latestAt=System.nanoTime()/1_000_000;
        if(edge){if(edges.size()>=32){edges.clear();edges.add(new PadState().report());}edges.add(latest);}
    }
    public synchronized void neutral(){replace(new PadState().report());}
    /** Layout transitions discard old button edges while retaining validated stick axes. */
    public synchronized void replace(byte[] report){sourceEdges.clear();latestSource=new PadState().report();sourceAt=0;desktopEdges.clear();latestDesktop=new byte[Automation.DESKTOP_SIZE];desktopAt=System.nanoTime()/1_000_000;edges.clear();latest=report.clone();latestAt=System.nanoTime()/1_000_000;neutralEpoch++;}
    synchronized byte[] nextReport(long now){
        if(now-latestAt>300){edges.clear();return new PadState().report();}
        return edges.isEmpty()?latest:edges.removeFirst();
    }
    public void connect(String host,int port,String token) {
        byte[] key=hex(token); if(key.length!=16)throw new IllegalArgumentException("Pairing key must contain 32 hexadecimal characters");
        if(port<1||port>65535)throw new IllegalArgumentException("Port must be 1–65535");
        close();neutral();running=true;long owner=++generation;
        worker=new Thread(()->run(host,port,key,owner),"PhonePad-WiFi");worker.start();
    }
    private void run(String host,int port,byte[] psk,long owner) {
        try(DatagramSocket s=new DatagramSocket()) {
            InetAddress address=InetAddress.getByName(host);
            if(owner!=generation || !running)return;
            socket=s; s.connect(address,port);s.setSoTimeout(4);
            byte[] client=new byte[16],session=null,key=null,pending=null,pendingDesktop=null,pendingSource=null;boolean sourceSupported=false;long sourceSeq=0,sourceSentAt=0,sourceEpoch=neutralEpoch;new SecureRandom().nextBytes(client);
            long seq=0,received=0,lastAck=0,lastHello=0,pendingEpoch=neutralEpoch,desktopSeq=0,desktopReceived=0,desktopEpoch=neutralEpoch,desktopSentAt=0,lastTransmit=0;
            listener.status("Wi-Fi · authenticating with "+host);
            while(running && owner==generation && !Thread.currentThread().isInterrupted()) {
                long now=System.nanoTime()/1_000_000;
                if(session!=null && now-lastAck>1000) {
                    session=null;key=null;pending=null;pendingDesktop=null;pendingSource=null;sourceSupported=false;lastDesktopAck=0;connected=false;new SecureRandom().nextBytes(client);lastHello=0;neutral();
                    listener.stopped(owner);listener.status("Wi-Fi host timed out · reconnecting");
                }
                if(session==null) {
                    if(now-lastHello>400) {transmit(s,packet(1,client,0,new byte[0],psk));lastHello=now;}
                } else if(now-lastTransmit>=16) {
                    lastTransmit=now;
                    if(pending==null || pendingEpoch!=neutralEpoch || now-latestAt>300){
                        pendingEpoch=neutralEpoch;pending=packet(3,session,++seq,nextReport(now),key);lastSentSequence=seq;
                    }
                    transmit(s,pending);reports++;
                    if(pendingDesktop==null||desktopEpoch!=neutralEpoch||now-desktopSentAt>300){if(pendingDesktop!=null&&now-desktopSentAt>300)synchronized(this){desktopEdges.clear();Arrays.fill(latestDesktop,33,39,(byte)0);}desktopEpoch=neutralEpoch;desktopSentAt=now;pendingDesktop=packet(5,session,++desktopSeq,nextDesktop(now),key);desktopSerialized++;}
                    desktopSendAttempts++;transmit(s,pendingDesktop);
                    if(sourceSupported){
                        if(pendingSource==null||sourceEpoch!=neutralEpoch||now-sourceSentAt>300){sourceEpoch=neutralEpoch;sourceSentAt=now;pendingSource=packet(7,session,++sourceSeq,nextSource(now),key);}
                        transmit(s,pendingSource);
                    }
                }
                byte[] buffer=new byte[1085];DatagramPacket reply=new DatagramPacket(buffer,buffer.length);
                try {s.receive(reply);}catch(SocketTimeoutException ignored){continue;}
                byte[] raw=Arrays.copyOf(buffer,reply.getLength());
                if(session==null && valid(raw,2,16,psk) && Arrays.equals(client,Arrays.copyOfRange(raw,5,21))) {
                    session=Arrays.copyOfRange(raw,29,45);key=mac(psk,concat(client,session));seq=received=desktopSeq=desktopReceived=0;pendingDesktop=null;sourceSeq=0;pendingSource=null;sourceSupported=false;
                    lastAck=now;connected=true;listener.status("Wi-Fi · "+host+" · authenticated");
                } else if(session!=null&&raw.length>=63&&raw.length<=961&&raw[4]==6){
                    if(!valid(raw,6,raw.length-61,key)||!Arrays.equals(session,Arrays.copyOfRange(raw,5,21)))continue;
                    long n=ByteBuffer.wrap(raw,21,8).getLong();if(n<=desktopReceived||n!=desktopSeq)continue;
                    desktopReceived=n;desktopAcknowledged++;pendingDesktop=null;lastDesktopAck=now;String gameJson=new String(raw,29,raw.length-61,java.nio.charset.StandardCharsets.UTF_8);sourceSupported=gameJson.contains("\"_phonepad_source\":1");listener.gameState(gameJson,owner);
                } else if(session!=null && valid(raw,8,0,key)) {
                    if(Arrays.equals(session,Arrays.copyOfRange(raw,5,21))&&ByteBuffer.wrap(raw,21,8).getLong()==sourceSeq)pendingSource=null;
                } else if(session!=null && raw.length>4 && raw[4]==4) {
                    if(!valid(raw,4,3,key)){invalidFeedback++;continue;}
                    if(!Arrays.equals(session,Arrays.copyOfRange(raw,5,21))){staleFeedback++;continue;}
                    long n=ByteBuffer.wrap(raw,21,8).getLong();lastFeedbackSequence=n;if(n<=received || n!=seq){staleFeedback++;continue;}
                    received=n;lastAck=now;pending=null;listener.rumble(raw[29]&255,raw[30]&255,(raw[31]&255)*10,owner);
                    // Both independently acknowledged channels share the 60 Hz send clock.
                }
            }
            if(session!=null){if(sourceSupported)transmit(s,packet(7,session,++sourceSeq,new PadState().report(),key));transmit(s,packet(5,session,++desktopSeq,new byte[Automation.DESKTOP_SIZE],key));transmit(s,packet(3,session,++seq,new PadState().report(),key));}
        } catch(Exception e) {
            if(running && owner==generation)listener.status("Wi-Fi: "+e.getClass().getSimpleName()+" · "+e.getMessage());
        } finally {if(owner==generation){connected=false;listener.stopped(owner);}}
    }
    private static void transmit(DatagramSocket s,byte[] b)throws Exception {s.send(new DatagramPacket(b,b.length));}
    static byte[] packet(int type,byte[] session,long seq,byte[] body,byte[] key)throws Exception {
        byte[] data=ByteBuffer.allocate(29+body.length).put(new byte[]{'P','P','0','1'}).put((byte)type).put(session).putLong(seq).put(body).array();
        return concat(data,mac(key,data));
    }
    static boolean valid(byte[] raw,int type,int body,byte[] key)throws Exception {
        if(raw.length!=61+body || raw[0]!='P'||raw[1]!='P'||raw[2]!='0'||raw[3]!='1'||raw[4]!=type)return false;
        return MessageDigest.isEqual(Arrays.copyOfRange(raw,raw.length-32,raw.length),mac(key,Arrays.copyOf(raw,raw.length-32)));
    }
    static byte[] mac(byte[] key,byte[] data)throws Exception {Mac m=Mac.getInstance("HmacSHA256");m.init(new SecretKeySpec(key,"HmacSHA256"));return m.doFinal(data);}
    static byte[] concat(byte[] a,byte[] b){byte[] r=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
    static byte[] hex(String text){
        String s=text.trim();if(!s.matches("[0-9a-fA-F]{32}"))throw new IllegalArgumentException("Enter the companion’s 32-character pairing key");
        byte[] b=new byte[s.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(s.substring(i*2,i*2+2),16);return b;
    }
    @Override public void close() {
        running=false;generation++;
        if(worker!=null) {
            try{worker.join(180);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            if(worker.isAlive()) {if(socket!=null)socket.close();worker.interrupt();}
            worker=null;
        }
        socket=null;connected=false;listener.stopped(generation);
    }
}
