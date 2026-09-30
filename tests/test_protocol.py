import sys
from pathlib import Path
import unittest

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from protocol import Protocol, packet, unpack, sign, NEUTRAL, STATE, decode


class ProtocolTests(unittest.TestCase):
    def setUp(self):
        self.key=bytes(range(16));self.client=bytes(16);self.applied=[];self.addr=('127.0.0.1',12345)
        self.p=Protocol(self.key,self.applied.append)

    def handshake(self):
        raw=packet(1,self.client,0,b'',self.key)
        reply=self.p.receive(raw,self.addr,0)
        self.nonce=unpack(reply,self.key,2,16)[2]
        self.session_key=sign(self.key,self.client+self.nonce)
        return raw

    def state(self,seq=1,body=NEUTRAL):
        return packet(3,self.nonce,seq,body,self.session_key)

    def test_authentication_and_replay(self):
        self.handshake();body=STATE.pack(1,3,-32767,32767,0,0,128,255)
        ack=self.p.receive(self.state(body=body),self.addr,.01)
        self.assertEqual(self.applied[-1],body);self.assertIsNotNone(unpack(ack,self.session_key,4,3))
        count=len(self.applied)
        self.assertIsNotNone(self.p.receive(self.state(body=body),self.addr,.02))
        corrupt=bytearray(self.state(2));corrupt[30]^=1
        self.assertIsNone(self.p.receive(corrupt,self.addr,.03))
        self.assertEqual(len(self.applied),count)

    def test_deadman_neutralizes_once(self):
        self.handshake();self.p.receive(self.state(body=STATE.pack(1,8,1,2,3,4,0,0)),self.addr,.01)
        self.p.expire(.6);self.assertEqual(self.applied[-1],NEUTRAL)
        count=len(self.applied);self.p.expire(.7);self.assertEqual(len(self.applied),count)
        self.assertIsNone(self.p.receive(self.state(2),self.addr,.71))

    def test_reject_other_address_and_session(self):
        self.handshake();self.assertIsNone(self.p.receive(self.state(),('127.0.0.2',12345),.01))
        old=self.state();self.p.expire(6);self.handshake()
        self.assertIsNone(self.p.receive(old,self.addr,.01))

    def test_duplicate_hello_does_not_reset_sequence(self):
        hello=self.handshake();self.p.receive(self.state(),self.addr,.01)
        self.p.receive(hello,self.addr,.02)
        count=len(self.applied)
        self.assertIsNotNone(self.p.receive(self.state(),self.addr,.03))
        self.assertEqual(len(self.applied),count)

    def test_duplicate_does_not_extend_watchdog(self):
        self.handshake();body=STATE.pack(1,8,0,0,0,0,0,0)
        self.p.receive(self.state(body=body),self.addr,.01)
        self.p.receive(self.state(body=body),self.addr,.4)
        self.p.expire(.52)
        self.assertIsNone(self.p.session)
        self.assertEqual(self.applied[-1],NEUTRAL)

    def test_same_sequence_with_different_body_is_rejected(self):
        self.handshake();self.p.receive(self.state(),self.addr,.01)
        self.assertIsNone(self.p.receive(self.state(body=STATE.pack(1,8,0,0,0,0,0,0)),self.addr,.02))

    def test_malformed_and_invalid_ranges(self):
        self.handshake()
        for n in range(100):self.assertIsNone(self.p.receive(bytes(n),self.addr,.01))
        self.assertIsNone(self.p.receive(self.state(body=STATE.pack(0,9,0,0,0,0,0,0)),self.addr,.02))
        with self.assertRaises(ValueError):decode(bytes(12))

    def test_active_session_cannot_be_displaced(self):
        self.handshake()
        hello=packet(1,b'x'*16,0,b'',self.key)
        self.assertIsNone(self.p.receive(hello,('other',1),.2))

    def test_java_vector(self):
        path=Path(__file__).resolve().parents[1]/'build/core-tests.txt'
        if not path.exists():self.skipTest('Run scripts/test.py for cross-language vector')
        vector=next(line.split('=',1)[1] for line in path.read_text().splitlines() if line.startswith('HELLO_VECTOR='))
        self.assertEqual(packet(1,self.client,0,b'',self.key).hex(),vector)


if __name__=='__main__':unittest.main()
