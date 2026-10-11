"""Independent authenticated PP01 sessions; wire format remains unchanged."""
from collections import Counter
from protocol import Protocol, unpack


def player_source(slot):
    return 'wifi' if slot == 1 else 'wifi:' + str(slot)


class MultiplayerProtocol:
    def __init__(self, key, create, release, limit=4):
        self.key, self.create, self.release, self.limit = key, create, release, limit
        self.players = {}
        self.counters = Counter(received_packets=0, invalid_packets=0, dropped_events=0,
                                decoding_errors=0, backend_failures=0, connection_interruptions=0,
                                decoded_events=0, routed_events=0, backend_writes=0)
        self.last_event = {}
        self.current = None

    @property
    def session(self):
        return next((p.session for p in self.players.values() if p.session), None)

    @property
    def current_event(self):
        return self.current.current_event if self.current else None

    @property
    def full(self):
        return len(self.players) >= self.limit

    def _count(self, protocol, before):
        self.counters.update({key: value-before.get(key,0) for key,value in protocol.counters.items()})
        if protocol.last_event:self.last_event=dict(protocol.last_event)

    def expire(self, now):
        for slot, protocol in list(self.players.items()):
            before=dict(protocol.counters)
            try:protocol.expire(now)
            finally:self._count(protocol,before)
            if protocol.session is None:
                self.release(slot)
                del self.players[slot]

    def receive(self, raw, address, now):
        self.expire(now)
        slot=next((slot for slot,p in self.players.items() if p.session.address==address),None)
        if slot is None:
            hello=unpack(raw,self.key,1,0)
            if not hello or hello[1]!=0 or self.full:
                self.counters.update(received_packets=1,invalid_packets=1,dropped_events=1)
                return None
            slot=next(slot for slot in range(1,self.limit+1) if slot not in self.players)
            protocol=self.create(slot)
            self.players[slot]=protocol
        protocol=self.players[slot];before=dict(protocol.counters);self.current=protocol
        try:return protocol.receive(raw,address,now)
        finally:
            self.current=None
            self._count(protocol,before)

    def close(self):
        errors = []
        for slot in list(self.players):
            try:self.release(slot)
            except Exception as exc:errors.append(exc)
            finally:del self.players[slot]
        if errors:raise RuntimeError("Player cleanup failed: " + "; ".join(map(str,errors))) from errors[0]

    def snapshot(self):
        return [dict(slot=slot,source=player_source(slot),address=list(p.session.address))
                for slot,p in sorted(self.players.items()) if p.session]
