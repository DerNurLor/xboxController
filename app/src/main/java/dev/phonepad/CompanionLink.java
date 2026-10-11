package dev.phonepad;

import org.json.JSONObject;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Bounded LAN discovery and approved encrypted provisioning; never sends input. */
public final class CompanionLink implements AutoCloseable {
    static final String PREFIX="PPPAIR1 ";
    public static final int PORT=39570;
    public interface Listener {void computer(Computer c);void status(String text);default void paired(Computer c,String key){} }
    public static final class Computer {
        public final String name,host,id,publicKey;public final int port;public final boolean pairable;
        Computer(String name,String host,int port,String id,String publicKey,boolean pairable){this.name=name;this.host=host;this.port=port;this.id=id;this.publicKey=publicKey;this.pairable=pairable;}
    }
    private final Listener listener;private volatile boolean closed;private volatile DatagramSocket socket;
    public CompanionLink(Listener listener){this.listener=listener;}
    private void start(Runnable run){new Thread(run,"PhonePad nearby").start();}
    public void scan(String savedHost,int savedPort){start(()->{
        try(DatagramSocket s=new DatagramSocket()){
            socket=s;if(closed)return;s.setBroadcast(true);s.setSoTimeout(180);
            String nonce=random(16);Set<InetSocketAddress> destinations=new LinkedHashSet<>();
            destinations.add(new InetSocketAddress("255.255.255.255",PORT));
            Enumeration<NetworkInterface> interfaces=NetworkInterface.getNetworkInterfaces();
            while(interfaces!=null&&interfaces.hasMoreElements()){NetworkInterface network=interfaces.nextElement();if(!network.isUp()||network.isLoopback())continue;for(InterfaceAddress address:network.getInterfaceAddresses())if(address.getBroadcast()!=null){
                destinations.add(new InetSocketAddress(address.getBroadcast(),PORT));
                // A bounded local sweep also works when host firewalls drop broadcast discovery.
                byte[] ip=address.getAddress().getAddress();int prefix=address.getNetworkPrefixLength();
                if(ip.length==4&&address.getAddress().isSiteLocalAddress()&&prefix>=16&&prefix<=30){
                    int bits=Math.max(24,prefix),count=1<<(32-bits),base=(ip[3]&255)&~(count-1);
                    for(int n=1;n<count-1;n++){byte[] candidate=ip.clone();candidate[3]=(byte)(base+n);if(!Arrays.equals(candidate,ip))destinations.add(new InetSocketAddress(InetAddress.getByAddress(candidate),PORT));}
                }
            }}
            if(!savedHost.isEmpty())try{destinations.add(new InetSocketAddress(InetAddress.getByName(savedHost),savedPort));}catch(Exception ignored){}
            JSONObject request=new JSONObject().put("op","discover").put("nonce",nonce);
            long end=System.nanoTime()+4_000_000_000L,last=0;Set<String> seen=new HashSet<>();
            while(!closed&&System.nanoTime()<end){
                if(System.nanoTime()-last>900_000_000L){last=System.nanoTime();for(InetSocketAddress destination:destinations)try{send(s,request,destination);}catch(Exception ignored){}}
                DatagramPacket packet=receive(s);if(packet==null)continue;
                try{JSONObject value=parse(packet);if(!nonce.equals(value.optString("nonce"))||!"computer".equals(value.optString("op")))continue;
                    String pub=value.getString("public"),id=value.getString("id");byte[] encoded=hex(pub,91);
                    if(!id.equals(hex(MessageDigest.getInstance("SHA-256").digest(encoded))))continue;
                    int port=value.getInt("port");if(port!=packet.getPort()||port<1||port>65535)continue;
                    String host=packet.getAddress().getHostAddress();String tag=id+host+port;if(seen.size()>=32||!seen.add(tag))continue;
                    listener.computer(new Computer(clean(value.optString("name","Computer")),host,port,id,pub,value.optBoolean("pairable")));
                }catch(Exception ignored){}
            }
            if(!closed)listener.status(seen.isEmpty()?"No companions found. Open PhonePad companion on your PC and use the same Wi-Fi.":"Choose your computer.");
        }catch(Exception e){if(!closed)listener.status("Search unavailable. Check Wi-Fi or use Manual connection.");}
    });}
    public void pair(Computer computer,String phoneName){pair(computer,phoneName,"");}
    public void pairQr(Computer computer,String phoneName,String token){hex(token,16);pair(computer,phoneName,token);}
    public void pairCode(Computer computer,String phoneName,String code){if(!code.matches("[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}"))throw new IllegalArgumentException("Enter the 8-character code shown by the companion.");pair(computer,phoneName,code);}
    private void pair(Computer computer,String phoneName,String qrToken){start(()->{
        try(DatagramSocket s=new DatagramSocket()){
            socket=s;if(closed)return;s.connect(InetAddress.getByName(computer.host),computer.port);s.setSoTimeout(500);
            KeyPairGenerator generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));KeyPair client=generator.generateKeyPair();
            byte[] clientPublic=client.getPublic().getEncoded(),serverPublic=hex(computer.publicKey,91);String nonce=random(16);
            JSONObject request=new JSONObject().put("op","pair").put("nonce",nonce).put("public",hex(clientPublic)).put("name",clean(phoneName));if(!qrToken.isEmpty())request.put("qr",qrToken);
            long end=System.nanoTime()+60_000_000_000L,last=0;String announced="";
            while(!closed&&System.nanoTime()<end){
                if(System.nanoTime()-last>500_000_000L){send(s,request,new InetSocketAddress(computer.host,computer.port));last=System.nanoTime();}
                DatagramPacket packet=receive(s);if(packet==null)continue;
                JSONObject value;try{value=parse(packet);}catch(Exception ignored){continue;}
                if(!nonce.equals(value.optString("nonce")))continue;String op=value.optString("op");
                if(op.equals("unavailable"))throw new IllegalStateException(qrToken.isEmpty()?"On your PC, type pair in the companion to allow pairing. Finish any active session first.":"This code expired or the companion is busy. Restart the companion to show a new code.");
                if(op.equals("denied"))throw new IllegalStateException("Pairing declined on the computer.");
                if(!op.equals("pending")&&!op.equals("approved"))continue;
                if(!computer.publicKey.equals(value.optString("public")))throw new GeneralSecurityException("Computer identity changed. Search again.");
                byte[] serverNonce=hex(value.getString("server"),16),salt=join(hex(nonce,16),serverNonce);
                KeyAgreement agreement=KeyAgreement.getInstance("ECDH");agreement.init(client.getPrivate());agreement.doPhase(KeyFactory.getInstance("EC").generatePublic(new X509EncodedKeySpec(serverPublic)),true);
                byte[] key=derive(agreement.generateSecret(),salt),transcript=join(join(clientPublic,serverPublic),salt);
                String check=hex(MessageDigest.getInstance("SHA-256").digest(join(key,transcript))).substring(0,8).toUpperCase(Locale.ROOT);
                if(qrToken.isEmpty()&&!announced.equals(check)){announced=check;listener.status("Check "+check+" matches on "+computer.name+", then approve on that computer.");}
                if(op.equals("approved")){
                    Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,new SecretKeySpec(key,"AES"),new GCMParameterSpec(128,hex(value.getString("iv"),12)));cipher.updateAAD(transcript);
                    byte[] token=cipher.doFinal(hex(value.getString("sealed"),32));if(token.length!=16)throw new GeneralSecurityException("Invalid pairing response");
                    if(!closed)listener.paired(computer,hex(token));return;
                }
            }
            if(!closed)listener.status(qrToken.isEmpty()?"Pairing timed out. Select the computer to try again.":"Connection timed out. Check the code and try again.");
        }catch(Exception e){if(!closed)listener.status(e instanceof IllegalStateException?e.getMessage():"Could not pair securely. Search again and retry.");}
    });}
    static byte[] derive(byte[] shared,byte[] salt)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(salt,"HmacSHA256"));byte[] prk=mac.doFinal(shared);mac.init(new SecretKeySpec(prk,"HmacSHA256"));return mac.doFinal(join("PhonePad pairing v1".getBytes(StandardCharsets.UTF_8),new byte[]{1}));}
    private static void send(DatagramSocket s,JSONObject value,SocketAddress to)throws Exception{byte[] raw=(PREFIX+value).getBytes(StandardCharsets.UTF_8);s.send(new DatagramPacket(raw,raw.length,to));}
    private static DatagramPacket receive(DatagramSocket s)throws Exception{DatagramPacket p=new DatagramPacket(new byte[1537],1537);try{s.receive(p);return p.getLength()>1536?null:p;}catch(SocketTimeoutException e){return null;}}
    private static JSONObject parse(DatagramPacket p)throws Exception{String raw=new String(p.getData(),0,p.getLength(),StandardCharsets.UTF_8);if(!raw.startsWith(PREFIX))throw new IllegalArgumentException();return new JSONObject(raw.substring(PREFIX.length()));}
    static String clean(String name){return name.replaceAll("[\\p{Cntrl}\\p{Cf}]","").substring(0,Math.min(64,name.replaceAll("[\\p{Cntrl}\\p{Cf}]","").length()));}
    static byte[] join(byte[] a,byte[] b){byte[] r=Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(Locale.ROOT,"%02x",b&255));return s.toString();}
    static byte[] hex(String text,int size){if(text.length()!=size*2||!text.matches("[0-9a-fA-F]+"))throw new IllegalArgumentException();byte[] out=new byte[size];for(int i=0;i<size;i++)out[i]=(byte)Integer.parseInt(text.substring(i*2,i*2+2),16);return out;}
    static String random(int size){byte[] b=new byte[size];new SecureRandom().nextBytes(b);return hex(b);}
    @Override public void close(){closed=true;DatagramSocket s=socket;if(s!=null)s.close();}
}
