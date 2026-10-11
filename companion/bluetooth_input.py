"""Decode PhonePad's composite Bluetooth HID input reports for the PC backends."""
import struct

from desktop import NEUTRAL_DESKTOP, decode_desktop
from protocol import NEUTRAL, decode


class BluetoothInput:
    """Route gamepad, keyboard, and mouse reports into full-state backends."""

    def __init__(self, gamepad, desktop):
        self.gamepad = gamepad if callable(gamepad) else gamepad.apply
        self.desktop = desktop if callable(desktop) else desktop.apply
        self.desktop_state = bytearray(NEUTRAL_DESKTOP)
        self.pad_active = False
        self.desktop_active = False
        self.pad_at = self.desktop_at = 0.0
        self.keyboard_at = self.mouse_at = 0.0

    def receive(self, report, now):
        if not report:
            return False
        report_id = report[0]
        if report_id == 5:
            # Companion's source-only reader handles this report; never inject it.
            try:decode(report[1:])
            except ValueError:return False
            return True
        if report_id == 1:
            if len(report) != 14:
                return False
            body = bytes(report[1:])
            try:
                decode(body)
            except ValueError:
                return False
            self.gamepad(body)
            self.pad_at = now
            self.pad_active = body != NEUTRAL
            return True
        if report_id == 3:
            if len(report) != 30:
                return False
            candidate = bytearray(NEUTRAL_DESKTOP)
            candidate[:29] = report[1:]
            try:
                decode_desktop(candidate)
            except ValueError:
                return False
            self.desktop_state[:29] = report[1:]
            self.desktop_state[29:32] = bytes(3)
            self.desktop_state[33:39] = bytes(6)
            self.keyboard_at = now
        elif report_id == 4:
            if len(report) != 8 or report[1] & ~0x1f:
                return False
            self.desktop_state[32] = report[1]
            x, y, wheel = struct.unpack('<hhh', report[2:])
            self.desktop_state[33:39] = struct.pack('!hhh', x, y, wheel)
            self.mouse_at = now
        else:
            return False
        self.desktop(bytes(self.desktop_state))
        self.desktop_state[33:39] = bytes(6)
        self.desktop_at = now
        self.desktop_active = bytes(self.desktop_state[:33]) != NEUTRAL_DESKTOP[:33]
        return True

    def expire(self, now, timeout=0.5):
        if self.pad_active and now - self.pad_at > timeout:
            self.gamepad(NEUTRAL)
            self.pad_active = False
        before = bytes(self.desktop_state)
        if now - self.keyboard_at > timeout:
            self.desktop_state[:32] = bytes(32)
        if now - self.mouse_at > timeout:
            self.desktop_state[32] = 0
        if before != bytes(self.desktop_state):
            self.desktop(bytes(self.desktop_state))
        self.desktop_active = any(self.desktop_state[:33])

    def neutral(self):
        self.gamepad(NEUTRAL)
        self.desktop_state[:] = NEUTRAL_DESKTOP
        self.desktop(NEUTRAL_DESKTOP)
        self.pad_active = self.desktop_active = False
