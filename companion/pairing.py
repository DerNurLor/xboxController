"""LAN discovery and explicitly approved, encrypted first pairing (PPPAIR1).

P-256 ECDH + HKDF-SHA256 + AES-GCM. Compare the eight-character check on
both screens before approving; discovery names are not authenticated identities.
The existing PP01 input/feedback protocol is unchanged.
"""
import hashlib
import json
import secrets
import threading
from pathlib import Path

PREFIX = b'PPPAIR1 '


def encode(message):
    return PREFIX + json.dumps(message, separators=(',', ':')).encode('utf-8')


def clean_name(value):
    return ''.join(c for c in str(value) if c.isprintable())[:64] or 'Computer'


class PairingService:
    def __init__(self, key, identity_file, name, port, now, notify=print):
        from cryptography.hazmat.primitives import serialization
        from cryptography.hazmat.primitives.asymmetric import ec
        self.serialization, self.ec = serialization, ec
        path = Path(identity_file)
        path.parent.mkdir(parents=True, exist_ok=True)
        try:
            self.identity = serialization.load_pem_private_key(path.read_bytes(), password=None)
        except FileNotFoundError:
            self.identity = ec.generate_private_key(ec.SECP256R1())
            data = self.identity.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption())
            import os
            fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'wb') as output:
                output.write(data)
        if not isinstance(self.identity, ec.EllipticCurvePrivateKey) or self.identity.curve.name != 'secp256r1':
            raise ValueError('Pairing identity must be P-256')
        self.public = self.identity.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
        self.id = hashlib.sha256(self.public).hexdigest()
        self.key, self.name, self.port, self.notify = key, clean_name(name), port, notify
        self.until, self.pending = now + 120, None
        self.qr_token, self.qr_code, self.qr_until, self.qr_used = secrets.token_hex(16), ''.join(secrets.choice('23456789ABCDEFGHJKLMNPQRSTUVWXYZ') for _ in range(8)), now + 300, False
        self.lock = threading.Lock()
        self.last_discovery = {}

    def command(self, command, now):
        with self.lock:
            if command.strip().lower() == 'pair':
                self.until = now + 120
                self.qr_token = secrets.token_hex(16)
                self.qr_code = ''.join(secrets.choice('23456789ABCDEFGHJKLMNPQRSTUVWXYZ') for _ in range(8))
                self.qr_until, self.qr_used = now + 300, False
                self.pending = None
                self.notify('Pairing open for 2 minutes. Select this computer on your phone.')
            elif command.strip().lower() in ('y', 'yes', 'n', 'no'):
                p = self.pending
                if p and p['expires'] > now and p['state'] == 'pending':
                    p['state'] = 'approved' if command.strip().lower() in ('y', 'yes') else 'denied'
                    self.notify('Phone approved.' if p['state'] == 'approved' else 'Pairing declined.')

    def receive(self, raw, address, now, busy=False):
        if not raw.startswith(PREFIX) or len(raw) > 1536:
            return None
        try:
            message = json.loads(raw[len(PREFIX):])
            if not isinstance(message, dict):
                return None
            nonce = message.get('nonce', '')
            if not isinstance(nonce, str) or len(nonce) != 32 or len(bytes.fromhex(nonce)) != 16:
                return None
            with self.lock:
                return self._receive(message, nonce, address, now, busy)
        except (ValueError, TypeError, KeyError, OverflowError):
            return None

    def _receive(self, message, nonce, address, now, busy):
        if message.get('op') == 'discover':
            if now - self.last_discovery.get(address[0], -10) < .5:
                return None
            if len(self.last_discovery) >= 64:
                self.last_discovery.clear()
            self.last_discovery[address[0]] = now
            return encode(dict(op='computer', nonce=nonce, name=self.name, id=self.id, public=self.public.hex(), port=self.port, pairable=now < self.until and not busy))
        if message.get('op') != 'pair':
            return None
        p = self.pending
        if p and p['expires'] <= now:
            self.pending = p = None
        if p and p['address'] == address and p['nonce'] == nonce and p['client'] == message.get('public'):
            result = dict(op=p['state'], nonce=nonce, public=self.public.hex(), server=p['server'])
            if p['state'] == 'approved':
                result.update(iv=p['iv'], sealed=p['sealed'])
            return encode(result)
        qr_token = message.get('qr', '')
        qr_pairing = isinstance(qr_token, str) and not self.qr_used and now < self.qr_until and (secrets.compare_digest(qr_token, self.qr_token) or secrets.compare_digest(qr_token, self.qr_code))
        if qr_token and not qr_pairing:
            return encode(dict(op='unavailable', nonce=nonce))
        if busy or (now >= self.until and not qr_pairing) or p:
            return encode(dict(op='unavailable', nonce=nonce))
        from cryptography.hazmat.primitives import hashes
        from cryptography.hazmat.primitives.kdf.hkdf import HKDF
        from cryptography.hazmat.primitives.ciphers.aead import AESGCM
        client_raw = bytes.fromhex(message['public'])
        if len(client_raw) != 91:
            return None
        client = self.serialization.load_der_public_key(client_raw)
        if not isinstance(client, self.ec.EllipticCurvePublicKey) or client.curve.name != 'secp256r1':
            return None
        server_nonce = secrets.token_bytes(16)
        salt = bytes.fromhex(nonce) + server_nonce
        shared = self.identity.exchange(self.ec.ECDH(), client)
        key = HKDF(algorithm=hashes.SHA256(), length=32, salt=salt, info=b'PhonePad pairing v1').derive(shared)
        transcript = client_raw + self.public + salt
        check = hashlib.sha256(key + transcript).hexdigest()[:8].upper()
        iv = secrets.token_bytes(12)
        sealed = AESGCM(key).encrypt(iv, self.key, transcript)
        state = 'approved' if qr_pairing else 'pending'
        self.pending = dict(address=address, nonce=nonce, client=message['public'], server=server_nonce.hex(), expires=now + 60, state=state, iv=iv.hex(), sealed=sealed.hex())
        if qr_pairing:
            self.qr_used = True
            self.notify(f"Connected {clean_name(message.get('name', 'Phone'))} ({address[0]}) using the one-time code.")
        else:
            self.notify(f"Pair {clean_name(message.get('name', 'Phone'))} ({address[0]})? Check {check} matches the phone, then type y + Enter to approve (n declines). Expires in 60 seconds.")
        response = dict(op=state, nonce=nonce, public=self.public.hex(), server=server_nonce.hex())
        if state == 'approved':
            response.update(iv=iv.hex(), sealed=sealed.hex())
        return encode(response)
