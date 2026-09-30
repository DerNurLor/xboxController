package dev.phonepad;

import java.net.*;
import java.nio.*;
import java.security.*;
import java.util.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Authenticated UDP with fresh server sessions, replay protection and full-state frames. */
public final class WifiPad implements AutoCloseable {
    public interface Listener { void status(String s); void rumble(int low,int high,int ms); }
    private final Listener listener;
    private volatile byte[] latest=new PadState().report();
    private volatile boolean running,connected;
    private volatile long generation,latestAt,neutralEpoch;
    private final ArrayDeque<byte[]> edges=new ArrayDeque<>();
    private DatagramSocket socket;
    private Thread worker;
    public volatile long reports;
    public WifiPad(Listener l) {listener=l;}
    public boolean connected() {return connected;}
    public synchronized void send(byte[] r) {
        boolean edge=r[0]!=latest[0]||r[1]!=latest[1]||r[2]!=latest[2]||
            ((r[11]==0)!=(latest[11]==0))||((r[12]==0)!=(latest[12]==0));
        latest=r.clone();latestAt=System.nanoTime()/1_000_000;
        if(edge){if(edges.size()>=32){edges.clear();edges.add(new PadState().report());}edges.add(latest);}
    }
    public synchronized void neutral(){edges.clear();latest=new PadState().report();latestAt=System.nanoTime()/1_000_000;neutralEpoch++;}
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
            socket=s; s.connect(address,port);s.setSoTimeout(15);
            byte[] client=new byte[16],session=null,key=null,pending=null;new SecureRandom().nextBytes(client);
            long seq=0,received=0,lastAck=0,lastHello=0,pendingEpoch=neutralEpoch;
            listener.status("Wi-Fi · authenticating with "+host);
            while(running && owner==generation && !Thread.currentThread().isInterrupted()) {
                long now=System.nanoTime()/1_000_000;
                if(session!=null && now-lastAck>1000) {
                    session=null;key=null;pending=null;connected=false;new SecureRandom().nextBytes(client);lastHello=0;neutral();
                    listener.rumble(0,0,0);listener.status("Wi-Fi host timed out · reconnecting");
                }
                if(session==null) {
                    if(now-lastHello>400) {transmit(s,packet(1,client,0,new byte[0],psk));lastHello=now;}
                } else {
                    if(pending==null || pendingEpoch!=neutralEpoch || now-latestAt>300){
                        pendingEpoch=neutralEpoch;pending=packet(3,session,++seq,nextReport(now),key);
                    }
                    transmit(s,pending);reports++;
                }
                byte[] buffer=new byte[128];DatagramPacket reply=new DatagramPacket(buffer,buffer.length);
                try {s.receive(reply);}catch(SocketTimeoutException ignored){continue;}
                byte[] raw=Arrays.copyOf(buffer,reply.getLength());
                if(session==null && valid(raw,2,16,psk) && Arrays.equals(client,Arrays.copyOfRange(raw,5,21))) {
                    session=Arrays.copyOfRange(raw,29,45);key=mac(psk,concat(client,session));seq=received=0;
                    lastAck=now;connected=true;listener.status("Wi-Fi · "+host+" · authenticated");
                } else if(session!=null && valid(raw,4,3,key) && Arrays.equals(session,Arrays.copyOfRange(raw,5,21))) {
                    long n=ByteBuffer.wrap(raw,21,8).getLong();if(n<=received || n!=seq)continue;
                    received=n;lastAck=now;pending=null;listener.rumble(raw[29]&255,raw[30]&255,(raw[31]&255)*10);
                    // Pace to about 60 Hz even when the host answers immediately.
                    long elapsed=System.nanoTime()/1_000_000-now;if(elapsed<16)Thread.sleep(16-elapsed);
                }
            }
            if(session!=null) transmit(s,packet(3,session,++seq,new PadState().report(),key));
        } catch(Exception e) {
            if(running && owner==generation)listener.status("Wi-Fi: "+e.getClass().getSimpleName()+" · "+e.getMessage());
        } finally {if(owner==generation){connected=false;listener.rumble(0,0,0);}}
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
        socket=null;connected=false;
    }
}
