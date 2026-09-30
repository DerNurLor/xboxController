"""PhonePad v1 protocol. Only authenticated, ordered, complete state reaches a backend."""
import hashlib
import hmac
import secrets
import struct
from dataclasses import dataclass

HEADER = struct.Struct('!4sB16sQ')
STATE = struct.Struct('<HBhhhhBB')
NEUTRAL = STATE.pack(0, 8, 0, 0, 0, 0, 0, 0)
TIMEOUT = 0.5


def sign(key, data):
    return hmac.digest(key, data, hashlib.sha256)


def packet(kind, session, seq, body, key):
    data = HEADER.pack(b'PP01', kind, session, seq) + body
    return data + sign(key, data)


def unpack(raw, key, kind, size):
    if len(raw) != HEADER.size + size + 32:
        return None
    magic, actual, session, seq = HEADER.unpack_from(raw)
    if magic != b'PP01' or actual != kind or not hmac.compare_digest(sign(key, raw[:-32]), raw[-32:]):
        return None
    return session, seq, raw[HEADER.size:-32]


def decode(body):
    if len(body) != STATE.size:
        raise ValueError('Invalid state length')
    values = STATE.unpack(body)
    if values[1] > 8 or any(v == -32768 for v in values[2:6]):
        raise ValueError('Invalid controller range')
    return values


@dataclass
class Session:
    address: tuple
    client: bytes
    nonce: bytes
    key: bytes
    last: float
    seq: int = 0
    body: bytes = NEUTRAL
    neutralized: bool = True


class Protocol:
    def __init__(self, key, apply, rumble=lambda: bytes(3)):
        if len(key) != 16:
            raise ValueError('A 128-bit pairing key is required')
        self.key, self.apply, self.rumble = key, apply, rumble
        self.session = None

    def expire(self, now):
        s = self.session
        if s and now - s.last > TIMEOUT and not s.neutralized:
            self.apply(NEUTRAL)
            s.neutralized = True
        if s and now - s.last > TIMEOUT:
            self.session = None

    def receive(self, raw, address, now):
        self.expire(now)
        hello = unpack(raw, self.key, 1, 0)
        if hello:
            client, seq, _ = hello
            if seq != 0:
                return None
            s = self.session
            if s and (s.address != address or s.client != client) and now - s.last < 1:
                return None  # A different session cannot displace an active player.
            if not s or s.address != address or s.client != client:
                self.apply(NEUTRAL)
                nonce = secrets.token_bytes(16)
                s = self.session = Session(address, client, nonce, sign(self.key, client + nonce), now)
            return packet(2, client, 0, s.nonce, self.key)
        s = self.session
        if not s or s.address != address:
            return None
        state = unpack(raw, s.key, 3, STATE.size)
        if not state:
            return None
        nonce, seq, body = state
        if nonce != s.nonce or seq < s.seq or seq == 0 or seq >= 2**63:
            return None
        if seq == s.seq:
            # Lost acknowledgements can be recovered without replaying input or
            # extending the watchdog lease. Different data at the same sequence is invalid.
            return packet(4, s.nonce, seq, self.rumble(), s.key) if body == s.body else None
        try:
            decode(body)
        except ValueError:
            return None
        self.apply(body)
        s.seq, s.last, s.neutralized, s.body = seq, now, body == NEUTRAL, body
        return packet(4, s.nonce, seq, self.rumble(), s.key)
