#!/usr/bin/env python3
"""Real Linux FF through authenticated UDP to the attached Android test mixer."""
import argparse,secrets,socket,subprocess,sys,time,struct
from pathlib import Path
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'companion'))
from backends import LinuxPad
from protocol import Protocol
args=argparse.ArgumentParser(description=__doc__);args.add_argument('--host',required=True,help='LAN address of this PC reachable from phone');args.add_argument('--usb',action='store_true',help='Relay unchanged UDP packets over temporary adb reverse TCP; does not verify LAN');opt=args.parse_args()
key=secrets.token_bytes(16);backend=LinuxPad();protocol=Protocol(key,backend.apply,backend.rumble,backend.stop_feedback)
player=None;android=None;bridge=None;buffer=b"";frames={}
try:
    with socket.socket(socket.AF_INET,socket.SOCK_STREAM if opt.usb else socket.SOCK_DGRAM) as sock:
        sock.bind((opt.host,0 if opt.usb else 39570));sock.settimeout(.005)
        if opt.usb:
            sock.listen(1)
            subprocess.run(['adb','reverse','tcp:39571',f'tcp:{sock.getsockname()[1]}'],check=True,stdout=subprocess.DEVNULL)
        android=subprocess.Popen(['adb','shell','am','instrument','-w','-e','phase','feedback','-e','tunnel',str(opt.usb).lower(),'-e','host',opt.host,'-e','port',str(sock.getsockname()[1]),'-e','key',key.hex(),'dev.phonepad.tests/dev.phonepad.DeviceTests'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
        until=time.monotonic()+30
        code='''
import sys,time
from evdev import InputDevice,ecodes as e,ff
d=InputDevice(sys.argv[1])
try:
 for low,high in [(65535,0),(0,65535),(65535,65535)]:
  effect=ff.Effect(e.FF_RUMBLE,-1,0,ff.Trigger(0,0),ff.Replay(400,0),ff.EffectType(ff_rumble_effect=ff.Rumble(low,high)))
  ident=d.upload_effect(effect);d.write(e.EV_FF,ident,1);time.sleep(.3);d.write(e.EV_FF,ident,0);time.sleep(.2);d.erase_effect(ident)
 # Natural expiry without explicit stop.
 effect=ff.Effect(e.FF_RUMBLE,-1,0,ff.Trigger(0,0),ff.Replay(150,0),ff.EffectType(ff_rumble_effect=ff.Rumble(40000,20000)))
 ident=d.upload_effect(effect);d.write(e.EV_FF,ident,1);time.sleep(.4);d.erase_effect(ident)
finally:d.close()
'''
        while android.poll() is None and time.monotonic()<until:
            backend.poll();protocol.expire(time.monotonic())
            try:
                if opt.usb:
                    if bridge is None:bridge,address=sock.accept();bridge.settimeout(.005);bridge.setsockopt(socket.IPPROTO_TCP,socket.TCP_NODELAY,1)
                    data=bridge.recv(512)
                    if not data:android.wait(timeout=15);break
                    buffer+=data
                    if len(buffer)<4:continue
                    length=struct.unpack('!I',buffer[:4])[0]
                    if len(buffer)<4+length:continue
                    raw,buffer=buffer[4:4+length],buffer[4+length:]
                else:raw,address=sock.recvfrom(256)
            except socket.timeout:continue
            reply=protocol.receive(raw,address,time.monotonic())
            marker=(len(raw),raw[4] if len(raw)>4 else -1,reply[4] if reply else 0);frames[marker]=frames.get(marker,0)+1
            if opt.usb:bridge.sendall(struct.pack("!I",len(reply or b""))+(reply or b""))
            elif reply:sock.sendto(reply,address)
            if protocol.session and protocol.session.seq>5 and player is None:player=subprocess.Popen([sys.executable,'-c',code,backend.ui.device.path])
        if android.poll() is None:raise RuntimeError('Android feedback test timed out')
        output=android.communicate(timeout=15)[0];(root/'build/phone-feedback.txt').write_text(output);print('Transport under test:', 'authenticated packets over USB relay (LAN unverified)' if opt.usb else 'Wi-Fi LAN');print(output);print('Frames (length,kind,reply):',frames);print('Host session:',bool(protocol.session),'FF producer:',None if player is None else player.poll())
        assert android.returncode==0 and 'PASS:' in output and 'FAIL:' not in output,'Phone feedback test failed'
        assert player is not None and player.wait(timeout=2)==0,'FF producer failed'
finally:
    for process in (player,android):
        if process is not None and process.poll() is None:process.terminate();process.wait(timeout=5)
    if bridge is not None:bridge.close()
    if opt.usb:subprocess.run(['adb','reverse','--remove','tcp:39571'],check=False)
    backend.close()
