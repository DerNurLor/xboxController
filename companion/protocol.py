"""PhonePad v1 protocol. Only authenticated, ordered, complete state reaches a backend."""
import hashlib
import hmac
import secrets
import struct
from dataclasses import dataclass
from collections import Counter
from desktop import decode_desktop, NEUTRAL_DESKTOP

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
    desktop_seq: int = 0
    desktop_body: bytes = NEUTRAL_DESKTOP
    desktop_last: float = 0
    desktop_neutral: bool = True
    source_seq: int = 0
    source_body: bytes = NEUTRAL
    source_last: float = 0
    source_neutral: bool = True


class Protocol:
    def __init__(self, key, apply, rumble=lambda: bytes(3), stop_feedback=lambda: None, desktop=lambda body: None, game_state=lambda: b'{}', source=None):
        if len(key) != 16:
            raise ValueError('A 128-bit pairing key is required')
        self.key, self.apply, self.rumble = key, apply, rumble
        self.desktop, self.game_state = desktop, game_state
        self.stop_feedback = stop_feedback
        self.source = source
        self.session = None
        self.last_event = {}
        self.current_event = None
        self.counters = Counter(received_packets=0, invalid_packets=0, decoding_errors=0,
                                dropped_events=0, backend_failures=0, connection_interruptions=0,
                                decoded_events=0, routed_events=0, backend_writes=0)

    def _deliver(self, target, body, channel, sequence, now):
        self.last_event = {'channel':channel, 'sequence':sequence,
                           'received_monotonic':now, 'stage':'routed'}
        self.current_event = self.last_event
        self.counters['routed_events'] += 1
        try:
            target(body)
        except Exception:
            self.counters['backend_failures'] += 1
            self.last_event['stage'] = 'backend_failed'
            raise
        finally:
            self.current_event = None
        self.counters['backend_writes'] += 1
        self.last_event['stage'] = 'backend_written'

    def expire(self, now):
        s = self.session
        if s and now - s.source_last > TIMEOUT and not s.source_neutral:
            if self.source:self.source(NEUTRAL)
            s.source_neutral=True
        if s and now - s.desktop_last > TIMEOUT and not s.desktop_neutral:
            self.desktop(NEUTRAL_DESKTOP);s.desktop_neutral=True
        if s and now - s.last > TIMEOUT and not s.neutralized:
            self.apply(NEUTRAL)
            s.neutralized = True
        if s and now - s.last > TIMEOUT:
            if self.source:self.source(NEUTRAL)
            self.stop_feedback()
            self.desktop(NEUTRAL_DESKTOP)
            self.session = None
            self.counters['connection_interruptions'] += 1

    def receive(self, raw, address, now):
        self.counters['received_packets'] += 1
        reply = self._receive(raw, address, now)
        if reply is None:
            self.counters['invalid_packets'] += 1
            self.counters['dropped_events'] += 1
        return reply

    def _receive(self, raw, address, now):
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
                self.desktop(NEUTRAL_DESKTOP)
                self.stop_feedback()
                if self.source:self.source(NEUTRAL)
                nonce = secrets.token_bytes(16)
                s = self.session = Session(address, client, nonce, sign(self.key, client + nonce), now)
            return packet(2, client, 0, s.nonce, self.key)
        s = self.session
        if not s or s.address != address:
            return None
        source = unpack(raw, s.key, 7, STATE.size) if self.source else None
        if source:
            nonce, seq, body = source
            if nonce != s.nonce or seq <= 0 or seq >= 2**63 or seq < s.source_seq:return None
            if seq == s.source_seq:
                return packet(8,s.nonce,seq,b'',s.key) if body == s.source_body else None
            try:values=decode(body)
            except ValueError:
                self.counters['decoding_errors'] += 1
                return None
            self.source(body)
            s.source_seq,s.source_body=seq,body
            s.source_last,s.source_neutral=now,body==NEUTRAL
            self.counters['source_reports'] += 1
            return packet(8,s.nonce,seq,b'',s.key)
        desktop = unpack(raw, s.key, 5, len(NEUTRAL_DESKTOP))
        if desktop:
            nonce, seq, body = desktop
            if nonce != s.nonce or seq < s.desktop_seq or seq == 0 or seq >= 2**63:return None
            if seq == s.desktop_seq:
                return packet(6,s.nonce,seq,self.game_state(),s.key) if body == s.desktop_body else None
            try:decode_desktop(body)
            except ValueError:
                self.counters['decoding_errors'] += 1
                return None
            self.counters['decoded_events'] += 1
            self._deliver(self.desktop, body, 'desktop', seq, now)
            s.desktop_seq,s.desktop_body,s.desktop_last,s.desktop_neutral=seq,body,now,body[:33]==NEUTRAL_DESKTOP[:33]
            return packet(6,s.nonce,seq,self.game_state(),s.key)
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
            self.counters['decoding_errors'] += 1
            return None
        self.counters['decoded_events'] += 1
        self._deliver(self.apply, body, 'controller', seq, now)
        s.seq, s.last, s.neutralized, s.body = seq, now, body == NEUTRAL, body
        return packet(4, s.nonce, seq, self.rumble(), s.key)
