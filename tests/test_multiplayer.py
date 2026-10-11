import sys
import unittest
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from protocol import Protocol, packet, unpack, sign, STATE, NEUTRAL
from multiplayer import MultiplayerProtocol, player_source
from input_router import InputRouter
from desktop import NEUTRAL_DESKTOP, decode_desktop


class Sink:
    def __init__(self):self.writes=[]
    def apply(self,body):self.writes.append(body)


class MultiplayerTests(unittest.TestCase):
    def setUp(self):
        self.key=bytes(range(16));self.pads={1:Sink()};self.desktop=Sink();self.source=[]
        self.router=InputRouter(self.pads[1],self.desktop)
        def create(slot):
            source=player_source(slot)
            if slot!=1:
                self.pads[slot]=Sink();self.router.player_gamepads[source]=self.pads[slot]
            return Protocol(self.key,lambda b:self.router.gamepad_state(source,b),
                lambda:bytes([slot,0,0]),desktop=lambda b:self.router.desktop_state(source,b),
                source=lambda b:self.source.append((slot,b)))
        self.protocol=MultiplayerProtocol(self.key,create,lambda slot:self.router.remove_player(player_source(slot)))

    def connect(self,client,now=0):
        address=('127.0.0.1',40000+client);identity=bytes([client])*16
        reply=self.protocol.receive(packet(1,identity,0,b'',self.key),address,now)
        self.assertIsNotNone(reply)
        nonce=unpack(reply,self.key,2,16)[2]
        return address,nonce,sign(self.key,identity+nonce)

    def send(self,client,kind,seq,body,now):
        address,nonce,key=client
        return self.protocol.receive(packet(kind,nonce,seq,body,key),address,now)

    def test_independent_controllers_shared_keys_and_disconnect(self):
        first=self.connect(1);second=self.connect(2)
        a=STATE.pack(1,8,1000,0,0,0,0,0);b=STATE.pack(2,8,-2000,0,0,0,0,255)
        self.send(first,3,1,a,.1);self.send(second,3,1,b,.2)
        self.assertEqual(self.pads[1].writes[-1],a);self.assertEqual(self.pads[2].writes[-1],b)
        chord=bytearray(NEUTRAL_DESKTOP);chord[28]=1;chord[32]=1
        self.send(first,5,1,chord,.1);self.send(second,5,1,chord,.2)
        self.protocol.expire(.65)
        self.assertEqual(self.pads[1].writes[-1],NEUTRAL)
        self.assertEqual(self.pads[2].writes[-1],b)
        self.assertEqual(decode_desktop(self.desktop.writes[-1])[:2],({224},1))
        self.protocol.expire(.8)
        self.assertEqual(self.pads[2].writes[-1],NEUTRAL)
        self.assertEqual(self.desktop.writes[-1],NEUTRAL_DESKTOP)
        self.assertEqual(self.protocol.counters['connection_interruptions'],2)
        self.assertIsNone(self.protocol.session)
        self.connect(3,1)
        self.assertEqual(self.protocol.snapshot()[0]['slot'],1)

    def test_source_report_never_injects_or_renews_input_lease(self):
        first=self.connect(1)
        before=len(self.pads[1].writes)
        source=STATE.pack(2,8,0,0,0,0,255,255)
        reply=self.send(first,7,1,source,.1)
        self.assertIsNotNone(unpack(reply,first[2],8,0))
        self.assertEqual(len(self.pads[1].writes),before)
        self.assertEqual(self.source[-1],(1,source))
        self.send(first,7,1,source,.2)
        self.assertEqual(self.protocol.counters['source_reports'],1)
        self.assertIsNone(self.send(first,7,1,NEUTRAL,.3))
        self.protocol.expire(.6)
        self.assertIsNone(self.protocol.session)
        self.assertEqual(self.source[-1],(1,NEUTRAL))

    def test_source_timeout_does_not_disconnect_active_output(self):
        client=self.connect(1)
        self.send(client,7,1,STATE.pack(2,8,0,0,0,0,255,255),.1)
        self.send(client,3,1,NEUTRAL,.4)
        self.protocol.expire(.65)
        self.assertIsNotNone(self.protocol.session)
        self.assertEqual(self.source[-1],(1,NEUTRAL))

    def test_cleanup_attempts_every_player_after_failure(self):
        self.connect(1);self.connect(2)
        released=[]
        def release(slot):
            released.append(slot)
            if slot==1:raise OSError("test cleanup failure")
        self.protocol.release=release
        with self.assertRaisesRegex(RuntimeError,"test cleanup failure"):self.protocol.close()
        self.assertEqual(released,[1,2]);self.assertEqual(self.protocol.players,{})

    def test_capacity_authentication_and_feedback_isolation(self):
        clients=[self.connect(i) for i in range(1,5)]
        self.assertTrue(self.protocol.full)
        self.assertIsNone(self.protocol.receive(packet(1,bytes([5])*16,0,b'',self.key),('127.0.0.1',40005),.1))
        self.assertIsNone(self.protocol.receive(b'invalid',('127.0.0.1',40006),.1))
        for slot,client in enumerate(clients,1):
            reply=self.send(client,3,1,NEUTRAL,.1)
            self.assertEqual(unpack(reply,client[2],4,3)[2],bytes([slot,0,0]))
        self.protocol.close()
        self.assertEqual(self.protocol.snapshot(),[])
