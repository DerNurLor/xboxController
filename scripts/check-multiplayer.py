#!/usr/bin/env python3
"""Capture two authenticated Wi-Fi clients at separate Linux controllers.

Uses loopback clients, not two physical phones. Grabs only this test's devices.
"""
import errno
import secrets
import select
import socket
import sys
import tempfile
import time
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from evdev import ecodes as e
from linux_input_diagnostics import CapturedInputs
from protocol import packet, unpack, sign, STATE, NEUTRAL
from desktop import NEUTRAL_DESKTOP
from service import CompanionService


def main():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as free:
        free.bind(('127.0.0.1', 0)); port=free.getsockname()[1]
    with tempfile.TemporaryDirectory() as root:
        service=CompanionService(dict(backend='linux',bind='127.0.0.1',port=port,
                                      bluetooth=False,no_discovery=True),root)
        clients=[]; capture=None
        try:
            service.command('start')
            deadline=time.monotonic()+5
            while service.snapshot()['state']!='Running' and time.monotonic()<deadline:time.sleep(.02)
            assert service.snapshot()['state']=='Running',service.snapshot()['errors']
            key=bytes.fromhex((Path(root)/'pairing.key').read_text().strip())
            for slot in (1,2):
                sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);sock.settimeout(2)
                sock.connect(('127.0.0.1',port));identity=secrets.token_bytes(16)
                sock.send(packet(1,identity,0,b'',key));nonce=unpack(sock.recv(4096),key,2,16)[2]
                clients.append((sock,nonce,sign(key,identity+nonce)))
            capture=CapturedInputs(service.device_info)
            controllers=[d for d in capture.devices if e.EV_ABS in d.capabilities()]
            assert len(controllers)==2,'Expected two controller event nodes'
            records=[]; removed=set()
            def send(index,kind,seq,body):
                sock,nonce,key=clients[index];sock.send(packet(kind,nonce,seq,body,key));sock.recv(4096)
            def collect(duration):
                deadline=time.monotonic()+duration
                while time.monotonic()<deadline:
                    ready,_,_=select.select([d for d in capture.devices if d.path not in removed],[],[],max(0,deadline-time.monotonic()))
                    for device in ready:
                        try:records.extend((device.path,event.type,event.code,event.value) for event in device.read())
                        except OSError as exc:
                            if exc.errno!=errno.ENODEV:raise
                            removed.add(device.path)
            chord=bytearray(NEUTRAL_DESKTOP);chord[28]=1;chord[32]=1
            send(0,3,1,STATE.pack(1,8,12000,0,0,0,0,0))
            send(1,3,1,STATE.pack(2,8,-12000,0,0,0,0,255))
            send(0,5,1,chord);send(1,5,1,chord);collect(.03)
            first=next(d.path for d in controllers if d.absinfo(e.ABS_X).value==12000)
            second=next(d.path for d in controllers if d.absinfo(e.ABS_X).value==-12000)
            assert first!=second
            assert (first,e.EV_KEY,e.BTN_A,1) in records
            assert (second,e.EV_KEY,e.BTN_B,1) in records
            # First phone releases; the other still owns the shared Ctrl/button.
            send(0,5,2,NEUTRAL_DESKTOP);send(0,3,2,NEUTRAL);collect(.03)
            assert not any(t==e.EV_KEY and c in (e.KEY_LEFTCTRL,e.BTN_LEFT) and v==0 for _,t,c,v in records)
            assert next(d for d in controllers if d.path==second).absinfo(e.ABS_X).value==-12000
            # Both disappear: the watchdog releases both controller and desktop.
            collect(.65)
            for code in (e.KEY_LEFTCTRL,e.BTN_LEFT):
                assert [v for _,t,c,v in records if t==e.EV_KEY and c==code]==[1,0],(code,records)
            assert second in removed or (second,e.EV_KEY,e.BTN_B,0) in records
            print('PASS: two authenticated clients, two distinct Linux controllers, shared mouse/keyboard ownership, independent releases and disconnect cleanup.')
        finally:
            try:service.close()
            finally:
                if capture:capture.close()
                for sock,_,_ in clients:sock.close()

if __name__=='__main__':main()
