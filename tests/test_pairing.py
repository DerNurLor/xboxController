import hashlib
import json
from pathlib import Path
import secrets
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'companion'))
from pairing import PairingService, encode, PREFIX
from protocol import Protocol, packet, unpack
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF
from cryptography.exceptions import InvalidTag


class PairingTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.identity = Path(self.temp.name)/'identity.pem'
        self.key = secrets.token_bytes(16)
        self.messages = []
        self.server = PairingService(self.key, self.identity, 'Desktop\nName', 39570, 0, self.messages.append)
        self.client = ec.generate_private_key(ec.SECP256R1())
        self.public = self.client.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.nonce = secrets.token_hex(16)
        self.request = dict(op='pair', nonce=self.nonce, public=self.public.hex(), name='Phone')
        self.addr = ('192.168.1.68', 12345)

    def receive(self, message=None, now=1, address=None, busy=False):
        raw = self.server.receive(encode(message or self.request), address or self.addr, now, busy)
        return json.loads(raw[len(PREFIX):]) if raw else None

    def material(self, reply):
        public = bytes.fromhex(reply['public'])
        shared = self.client.exchange(ec.ECDH(), serialization.load_der_public_key(public))
        salt = bytes.fromhex(self.nonce + reply['server'])
        key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=b'PhonePad pairing v1').derive(shared)
        return key, self.public + public + salt

    def test_encrypted_approval_then_existing_protocol(self):
        first = self.receive()
        self.assertEqual(first['op'], 'pending')
        self.assertNotIn('sealed', first)
        self.assertNotIn(self.key.hex(), json.dumps(first))
        key, transcript = self.material(first)
        self.assertIn(hashlib.sha256(key + transcript).hexdigest()[:8].upper(), self.messages[-1])
        self.server.command('y', 2)
        approved = self.receive(now=3)
        recovered = AESGCM(key).decrypt(bytes.fromhex(approved['iv']), bytes.fromhex(approved['sealed']), transcript)
        self.assertEqual(recovered, self.key)
        protocol = Protocol(self.key, lambda _: None)
        client_nonce = secrets.token_bytes(16)
        self.assertIsNotNone(unpack(protocol.receive(packet(1, client_nonce, 0, b'', recovered), self.addr, 4), recovered, 2, 16))
        with self.assertRaises(InvalidTag):
            AESGCM(key).decrypt(bytes.fromhex(approved['iv']), bytes.fromhex(approved['sealed']), transcript + b'x')

    def test_discovery_is_named_bounded_and_has_no_key(self):
        reply = self.receive(dict(op='discover', nonce=self.nonce))
        self.assertEqual(reply['name'], 'DesktopName')
        self.assertEqual(reply['id'], self.server.id)
        self.assertNotIn(self.key.hex(), json.dumps(reply))
        self.assertIsNone(self.receive(dict(op='discover', nonce=self.nonce), now=1.1))
        self.assertFalse(self.receive(dict(op='discover', nonce=self.nonce), now=122)['pairable'])

    def test_identity_persists_and_closed_window_requires_reopen(self):
        again = PairingService(self.key, self.identity, 'Desktop', 39570, 0)
        self.assertEqual(again.id, self.server.id)
        self.assertEqual(self.receive(now=121)['op'], 'unavailable')
        self.server.command('pair', 122)
        self.assertEqual(self.receive(now=123)['op'], 'pending')

    def test_qr_pairing_auto_approves_once_and_seals_the_saved_key(self):
        request = dict(self.request, qr=self.server.qr_token)
        reply = self.receive(request, now=1)
        self.assertEqual(reply['op'], 'approved')
        key, transcript = self.material(reply)
        self.assertEqual(AESGCM(key).decrypt(bytes.fromhex(reply['iv']), bytes.fromhex(reply['sealed']), transcript), self.key)
        second_client = ec.generate_private_key(ec.SECP256R1())
        second = dict(request, nonce=secrets.token_hex(16), public=second_client.public_key().public_bytes(
            serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo).hex())
        self.assertEqual(self.receive(second, now=2, address=('192.168.1.70', 2222))['op'], 'unavailable')
        self.assertIn('one-time code', self.messages[-1])

    def test_short_code_pairing_auto_approves_once(self):
        self.assertRegex(self.server.qr_code, r'^[23456789ABCDEFGHJKLMNPQRSTUVWXYZ]{8}$')
        reply = self.receive(dict(self.request, qr=self.server.qr_code), now=1)
        self.assertEqual(reply['op'], 'approved')
        key, transcript = self.material(reply)
        self.assertEqual(AESGCM(key).decrypt(bytes.fromhex(reply['iv']), bytes.fromhex(reply['sealed']), transcript), self.key)
        self.assertIn('one-time code', self.messages[-1])

    def test_decline_expiry_busy_and_no_displacement(self):
        self.assertEqual(self.receive(busy=True)['op'], 'unavailable')
        first = self.receive()
        self.assertEqual(self.receive(now=2), first)
        self.assertEqual(len(self.messages), 1)
        self.assertEqual(self.receive(address=('192.168.1.70', 2222))['op'], 'unavailable')
        self.assertEqual(self.receive(dict(self.request, nonce=secrets.token_hex(16)))['op'], 'unavailable')
        self.server.command('n', 3)
        self.assertEqual(self.receive(now=4)['op'], 'denied')
        self.server.command('y', 5)
        self.assertEqual(self.receive(now=6)['op'], 'denied')
        self.server.command('y', 70)
        self.assertEqual(self.receive(now=71)['op'], 'pending')

    def test_malformed_traffic_does_not_prompt_or_disclose(self):
        for raw in [b'PPPAIR1 {', encode([]), encode(dict(op='pair', nonce='bad')), encode(dict(self.request, public='aa')), encode(dict(self.request, public=7)), b'PPPAIR1 '+b'x'*1600]:
            self.assertIsNone(self.server.receive(raw, self.addr, 1))
        self.assertEqual(self.messages, [])


if __name__ == '__main__':
    unittest.main()
