import json
import struct
import socket
import subprocess
import threading
import sys
import tempfile
import time
import unittest
from types import SimpleNamespace
import urllib.request
import urllib.error
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from desktop import NEUTRAL_DESKTOP, decode_desktop, Desktop
from protocol import Protocol, packet, unpack, sign, NEUTRAL
from game_events import GameStates, GameEventServer

class AutomationTests(unittest.TestCase):
    def setUp(self):
        self.key=bytes(range(16));self.addr=('127.0.0.1',1234);self.applied=[];self.pad=[]
        self.protocol=Protocol(self.key,self.pad.append,desktop=self.applied.append,game_state=lambda:b'{"vehicle":"false"}')
        reply=self.protocol.receive(packet(1,bytes(16),0,b'',self.key),self.addr,0)
        self.nonce=unpack(reply,self.key,2,16)[2];self.session_key=sign(self.key,bytes(16)+self.nonce)
    def frame(self,seq,body):return packet(5,self.nonce,seq,body,self.session_key)
    def test_desktop_retransmission_does_not_repeat_motion(self):
        body=bytearray(NEUTRAL_DESKTOP);body[26//8]|=1<<(26%8);body[32]=1;body[33:]=struct.pack('!hhh',30,-15,1)
        data=self.frame(1,body);reply=self.protocol.receive(data,self.addr,.01)
        self.assertEqual(unpack(reply,self.session_key,6,len(b'{"vehicle":"false"}'))[2],b'{"vehicle":"false"}')
        count=len(self.applied);self.protocol.receive(data,self.addr,.02);self.assertEqual(len(self.applied),count)
        self.assertIsNone(self.protocol.receive(self.frame(1,NEUTRAL_DESKTOP),self.addr,.03))
        self.assertEqual(decode_desktop(self.applied[-1]),({26},1,30,-15,1))
    def test_desktop_lease_expires_while_controller_still_active(self):
        body=bytearray(NEUTRAL_DESKTOP);body[0]=16;self.protocol.receive(self.frame(1,body),self.addr,.01)
        self.protocol.receive(packet(3,self.nonce,1,NEUTRAL,self.session_key),self.addr,.3)
        self.protocol.receive(self.frame(1,body),self.addr,.4)
        self.protocol.expire(.52);self.assertEqual(self.applied[-1],NEUTRAL_DESKTOP);self.assertIsNotNone(self.protocol.session)
    def test_desktop_rejects_tamper_unknown_usage_and_old_session(self):
        data=bytearray(self.frame(1,NEUTRAL_DESKTOP));data[-1]^=1;self.assertIsNone(self.protocol.receive(data,self.addr,.01))
        data=bytearray(NEUTRAL_DESKTOP);data[0]=1;self.assertIsNone(self.protocol.receive(self.frame(1,data),self.addr,.02))
        self.assertIsNone(self.protocol.receive(self.frame(1,NEUTRAL_DESKTOP),('127.0.0.2',1234),.03))
        self.protocol.expire(2);self.assertIsNone(self.protocol.receive(self.frame(1,NEUTRAL_DESKTOP),self.addr,2.1))
    def test_game_states_expire_delete_and_bound(self):
        states=GameStates();states.publish({'state':{'vehicle':True},'ttl_ms':100},1)
        self.assertEqual(json.loads(states.snapshot(1.05)),{'vehicle':'true'});self.assertEqual(states.snapshot(1.11),b'{}')
        states.publish({'state':{'vehicle':False}},2);states.publish({'state':{'vehicle':None}},2.1);self.assertEqual(states.snapshot(2.2),b'{}')
        for data in ({'state':{'input.LT':True}},{'state':{'vehicle':[]}}, {'state':{},'ttl_ms':0}, {'state':{str(i):i for i in range(17)}}):
            with self.assertRaises(ValueError):states.publish(data)
    def test_local_plugin_http_authorization_and_cleanup(self):
        with tempfile.TemporaryDirectory() as directory:
            server=GameEventServer(Path(directory)/'events.json')
            try:
                connection=json.loads(server.path.read_text());url=connection['url'];body=b'{"state":{"vehicle":false},"ttl_ms":500}'
                with self.assertRaises(urllib.error.HTTPError) as error:urllib.request.urlopen(urllib.request.Request(url,body,method='POST'))
                self.assertEqual(error.exception.code,401);error.exception.close()
                request=urllib.request.Request(url,body,{'Authorization':'Bearer '+connection['token'],'Content-Type':'application/json'},method='POST')
                with urllib.request.urlopen(request,timeout=2) as response:self.assertEqual(response.status,204)
                self.assertEqual(json.loads(server.states.snapshot()),{'vehicle':'false'})
            finally:server.close()
            self.assertFalse(server.path.exists())
    def test_java_wifi_two_channels_with_lost_ack(self):
        sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM);sock.bind(('127.0.0.1',0));sock.settimeout(.05)
        outputs=[];pads=[];protocol=Protocol(self.key,pads.append,desktop=outputs.append,game_state=lambda:b'{"vehicle":"false"}')
        stopped=threading.Event();dropped=False
        def serve():
            nonlocal dropped
            while not stopped.is_set():
                try:data,address=sock.recvfrom(1537)
                except socket.timeout:continue
                reply=protocol.receive(data,address,time.monotonic())
                if reply:
                    if data[4]==5 and not dropped:dropped=True;continue
                    sock.sendto(reply,address)
        thread=threading.Thread(target=serve);thread.start()
        try:
            root=Path(__file__).resolve().parents[1]
            result=subprocess.run(['java','-cp',str(root/'build/tests'),'dev.phonepad.WifiAutomationTest',str(sock.getsockname()[1])],capture_output=True,text=True,timeout=8)
            self.assertEqual(result.returncode,0,result.stderr)
            self.assertGreater(len(outputs),30)
            self.assertEqual(sum(decode_desktop(frame)[2] for frame in outputs),37)
            self.assertTrue(any(44 in decode_desktop(frame)[0] for frame in outputs))
            self.assertEqual(outputs[-1],NEUTRAL_DESKTOP)
            self.assertTrue(any(frame!=NEUTRAL for frame in pads))
            self.assertEqual(pads[-1],NEUTRAL)
        finally:stopped.set();thread.join(2);sock.close()
    def test_linux_edges_motion_and_partial_failure_release(self):
        desktop=Desktop('dry-run');desktop.backend='linux'
        desktop.e=SimpleNamespace(EV_KEY=1,EV_REL=2,REL_X=0,REL_Y=1,REL_WHEEL=8)
        desktop.codes={4:30,49:43,50:43};desktop.mouse_codes=(272,273,274,275,276)
        events=[]
        desktop.ui=SimpleNamespace(write=lambda *event:events.append(event),syn=lambda:None,close=lambda:None)
        desktop.mouse_ui=desktop.ui
        body=bytearray(NEUTRAL_DESKTOP);body[0]=16;body[32]=1;body[33:]=struct.pack('!hhh',12,-7,1)
        desktop.apply(body)
        self.assertIn((1,30,1),events);self.assertIn((1,272,1),events);self.assertIn((2,0,12),events)
        events.clear();desktop.apply(NEUTRAL_DESKTOP)
        self.assertEqual(set(events),{(1,30,0),(1,272,0)})
        def partial(*event):raise OSError('partial write')
        desktop.ui.write=partial
        with self.assertRaises(OSError):desktop.apply(body)
        self.assertEqual(desktop.keys,{4});self.assertEqual(desktop.buttons,1)
        desktop.ui.write=lambda *event:events.append(event);events.clear();desktop.close()
        self.assertEqual(set(events),{(1,30,0),(1,272,0)})
    def test_linux_space_and_mouse_output(self):
        desktop=Desktop('dry-run');desktop.backend='linux'
        desktop.e=SimpleNamespace(EV_KEY=1,EV_REL=2,REL_X=0,REL_Y=1,REL_WHEEL=8)
        desktop.codes={44:57};desktop.mouse_codes=(272,273,274,275,276)
        events=[]
        desktop.ui=SimpleNamespace(write=lambda *event:events.append(event),syn=lambda:None,close=lambda:None)
        desktop.mouse_ui=desktop.ui
        body=bytearray(NEUTRAL_DESKTOP);body[44//8]|=1<<(44%8);body[32]=1
        desktop.apply(body)
        self.assertIn((1,57,1),events);self.assertIn((1,272,1),events)
        events.clear();desktop.apply(NEUTRAL_DESKTOP)
        self.assertIn((1,57,0),events);self.assertIn((1,272,0),events)
    def test_desktop_dry_run_and_reset(self):
        desktop=Desktop('dry-run');body=bytearray(NEUTRAL_DESKTOP);body[28]=1;body[32]=31;desktop.apply(body)
        self.assertEqual(desktop.keys,{224});self.assertEqual(desktop.buttons,31);desktop.close();self.assertFalse(desktop.keys);self.assertEqual(desktop.buttons,0)

if __name__=='__main__':unittest.main()
