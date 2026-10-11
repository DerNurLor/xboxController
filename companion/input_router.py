"""Merge simultaneous PhonePad transports into one set of virtual input devices."""
import struct
from protocol import NEUTRAL, STATE, decode
from desktop import NEUTRAL_DESKTOP, decode_desktop

HATS=((0,-1),(1,-1),(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,0))


class InputRouter:
    def __init__(self, gamepad, desktop, observer=None):
        self.gamepad, self.desktop, self.observer = gamepad, desktop, observer
        self.pads = {'wifi': NEUTRAL, 'bluetooth': NEUTRAL}
        self.desktops = {'wifi': NEUTRAL_DESKTOP, 'bluetooth': NEUTRAL_DESKTOP}
        self.last_observed = {}
        self.player_gamepads = {}

    def _observe(self, source, kind, state):
        if self.observer and (self.last_observed.get((source, kind)) != state or
                              (kind == 'mouse' and any(state[1:]))):
            self.last_observed[source, kind] = state
            self.observer(source, kind, state)

    def test_snapshot(self):
        self.last_observed.clear()
        for source, body in self.pads.items():
            values = decode(body)
            if body != NEUTRAL: self._observe(source, 'controller', values)
        for source, body in self.desktops.items():
            keys, buttons, x, y, wheel = decode_desktop(body)
            if keys: self._observe(source, 'keyboard', tuple(sorted(keys)))
            if buttons: self._observe(source, 'mouse', (buttons, 0, 0, 0))

    def gamepad_state(self, source, body):
        values = decode(body)
        self.pads[source] = body
        if source in self.player_gamepads:
            self.player_gamepads[source].apply(body)
            self._observe(source, 'controller', values)
            return
        buttons = hat = 0
        axes = [0, 0, 0, 0]
        triggers = [0, 0]
        vectors = []
        for name, report in self.pads.items():
            if name in self.player_gamepads:continue
            b, h, *rest = decode(report)
            buttons |= b
            vectors.append(HATS[h])
            for i, value in enumerate(rest[:4]):
                if abs(value) > abs(axes[i]): axes[i] = value
            for i, value in enumerate(rest[4:]): triggers[i] = max(triggers[i], value)
        x = max(-1, min(1, sum(v[0] for v in vectors)))
        y = max(-1, min(1, sum(v[1] for v in vectors)))
        hat = next((i for i, vector in enumerate(HATS) if vector == (x, y)), 8)
        merged = STATE.pack(buttons, hat, *axes, *triggers)
        self.gamepad.apply(merged)
        self._observe(source, 'controller', values)

    def desktop_state(self, source, body):
        keys, buttons, x, y, wheel = decode_desktop(body)
        self.desktops[source] = body
        all_keys, all_buttons = set(), 0
        for report in self.desktops.values():
            other_keys, other_buttons, _, _, _ = decode_desktop(report)
            all_keys.update(other_keys)
            all_buttons |= other_buttons
        merged = bytearray(NEUTRAL_DESKTOP)
        for key in all_keys: merged[key // 8] |= 1 << (key % 8)
        merged[32] = all_buttons
        # Relative movement belongs to this update only; replaying saved deltas
        # while combining another transport would move the pointer repeatedly.
        struct.pack_into('!hhh', merged, 33, x, y, wheel)
        self.desktop.apply(bytes(merged))
        self._observe(source, 'keyboard', tuple(sorted(keys)))
        self._observe(source, 'mouse', (buttons, x, y, wheel))

    def neutral(self, source):
        self.gamepad_state(source, NEUTRAL)
        self.desktop_state(source, NEUTRAL_DESKTOP)

    def remove_player(self, source):
        self.neutral(source)
        self.player_gamepads.pop(source,None)
        self.pads.pop(source,None);self.desktops.pop(source,None)
        for key in list(self.last_observed):
            if key[0]==source:self.last_observed.pop(key)
