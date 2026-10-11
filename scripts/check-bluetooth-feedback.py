#!/usr/bin/env python3
"""Bounded real FF → Bluetooth vendor report 2 → production phone mixer check."""
from pathlib import Path
import argparse,os,stat,subprocess,sys,time
from contextlib import closing
root=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(root/'companion'))
from backends import LinuxPad
parser=argparse.ArgumentParser(description=__doc__);parser.add_argument("--host-name",default="cachyos-x8664")
parser.add_argument('--pulse-ms',type=int,default=400,help='Duration of each of three FF pulses (100–1500 ms)')
args=parser.parse_args()
if not 100<=args.pulse_ms<=1500:parser.error('--pulse-ms must be 100–1500')
backend=LinuxPad();android=player=None;fd=None;frames=0;observed=set();capabilities={};write_failed=False
code='''
import sys,time
from evdev import InputDevice,ecodes as e,ff
d=InputDevice(sys.argv[1])
pulse_ms=int(sys.argv[2])
try:
 for low,high in [(65535,0),(0,65535),(65535,65535)]:
  effect=ff.Effect(e.FF_RUMBLE,-1,0,ff.Trigger(0,0),ff.Replay(pulse_ms+200,0),ff.EffectType(ff_rumble_effect=ff.Rumble(low,high)))
  ident=d.upload_effect(effect);d.write(e.EV_FF,ident,1);time.sleep(pulse_ms/1000);d.write(e.EV_FF,ident,0);time.sleep(.3);d.erase_effect(ident)
finally:d.close()
'''
try:
    subprocess.run(['adb','shell','am','start','-n','dev.phonepad/.MainActivity'],check=True,stdout=subprocess.DEVNULL)
    android=subprocess.Popen(['adb','shell','am','instrument','-w','-e','phase','bluetooth-feedback','-e','bluetoothHost',args.host_name,'dev.phonepad.tests/dev.phonepad.DeviceTests'],stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True)
    deadline=time.monotonic()+30;started=time.monotonic();foreground=False
    while android.poll() is None and time.monotonic()<deadline:
        backend.poll()
        if not foreground and time.monotonic()-started>1:
            subprocess.run(["adb","shell","am","start","-n","dev.phonepad/.MainActivity"],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL);foreground=True
        if time.monotonic()-started>3 and not capabilities:
            from evdev import InputDevice,list_devices,ecodes as e
            for path in list_devices():
                with closing(InputDevice(path)) as device:
                    if device.name in ('Redmi Note 12 Pro+ 5G','PhonePad','PhonePad Bluetooth Gamepad'):
                        caps=device.capabilities();capabilities={'path':path,'name':device.name,'EV_FF':e.EV_FF in caps,'FF_RUMBLE':e.FF_RUMBLE in caps.get(e.EV_FF,[])}
                        import json
                        (root/'build/native-bluetooth-ff.json').write_text(json.dumps(capabilities,indent=2)+'\n')
                        print('Measured native Bluetooth capabilities:',capabilities,flush=True)
        if fd is None and time.monotonic()-started>3:
            for node in Path('/sys/class/hidraw').glob('*/device/uevent'):
                identity=node.read_text()
                if 'HID_NAME=Redmi Note 12 Pro+ 5G' not in identity and 'HID_NAME=PhonePad' not in identity:continue
                descriptor=(node.parent/'report_descriptor').read_bytes()
                if bytes.fromhex('85 02 06 00 ff 09 01 15 00 26 ff 00 75 08 95 03 91 02') not in descriptor:
                    observed.add('Descriptor mismatch: '+descriptor[-30:].hex());continue
                try:fd=os.open('/dev/'+node.parent.parent.name,os.O_RDWR|os.O_NONBLOCK)
                except OSError as error:observed.add(str(error));continue
                if not stat.S_ISCHR(os.fstat(fd).st_mode):
                    observed.add('Not a character device: /dev/'+node.parent.parent.name)
                    os.close(fd);fd=None;continue
                print('Starting three host FF pulses at',time.strftime('%H:%M:%S'),'duration',args.pulse_ms,'ms each',flush=True)
                player=subprocess.Popen([sys.executable,'-c',code,backend.ui.device.path,str(args.pulse_ms)]);break
        if fd is not None and not write_failed:
            try:os.write(fd,bytes([2])+backend.rumble());frames+=1
            except OSError as error:observed.add('Output interrupted: '+str(error));write_failed=True
        time.sleep(.016)
    if android.poll() is None:
        subprocess.run(["adb","shell","am","force-stop","dev.phonepad"],check=True)
        out=android.communicate(timeout=5)[0];(root/"build/bluetooth-feedback.txt").write_text(out)
        raise RuntimeError("Bluetooth feedback instrumentation did not complete; log saved to build/bluetooth-feedback.txt")
    out=android.communicate()[0];(root/'build/bluetooth-feedback.txt').write_text(out);print(out);print('Discovery:',sorted(observed));print('Output writes:',frames,'FF producer:',None if player is None else player.poll())
    assert android.returncode==0 and 'PASS:' in out and 'FAIL:' not in out,'Bluetooth feedback failed'
    assert player is not None and player.wait(timeout=2)==0,'FF producer failed'
finally:
    if fd is not None:
        try:os.write(fd,bytes([2,0,0,0]))
        except OSError:pass # Device may disappear when instrumentation exits; phone leases expire.
        finally:os.close(fd)
    for process in (player,android):
        if process is not None and process.poll() is None:process.terminate();process.wait(timeout=5)
    backend.close()
