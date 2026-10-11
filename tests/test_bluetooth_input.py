"""Bluetooth composite HID routing into PC gamepad and desktop outputs."""
import struct
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from bluetooth_input import BluetoothInput
from desktop import NEUTRAL_DESKTOP
from protocol import NEUTRAL
from hid_devices import enumerate_phonepad_hid


class Sink:
    def __init__(self): self.states = []
    def apply(self, state): self.states.append(bytes(state))


class BluetoothInputTests(unittest.TestCase):
    def setUp(self):
        self.pad, self.desktop = Sink(), Sink()
        self.router = BluetoothInput(self.pad, self.desktop)

    def keyboard(self, *usages):
        bits = bytearray(29)
        for usage in usages: bits[usage // 8] |= 1 << (usage % 8)
        return bytes([3]) + bits

    def mouse(self, buttons=0, x=0, y=0, wheel=0):
        return bytes([4, buttons]) + struct.pack('<hhh', x, y, wheel)

    def test_keyboard_and_mouse_reports_preserve_each_others_held_state(self):
        self.assertTrue(self.router.receive(self.keyboard(30), 1.0))  # A
        self.assertTrue(self.router.receive(self.mouse(1, -17, 9, 1), 1.01))
        state = self.desktop.states[-1]
        self.assertEqual(state[3], 1 << 6)  # HID usage 30 (key 1), LSB-first bitmap
        self.assertEqual(state[32], 1)
        self.assertEqual(struct.unpack('!hhh', state[33:39]), (-17, 9, 1))
        self.assertTrue(self.router.receive(self.keyboard(), 1.02))
        state = self.desktop.states[-1]
        self.assertEqual(state[3], 0)
        self.assertEqual(state[32], 1)  # keyboard release does not release mouse button

    def test_mouse_motion_is_signed_little_endian_and_does_not_stick(self):
        self.router.receive(self.mouse(0, -32767, 123, -1), 2.0)
        self.assertEqual(struct.unpack('!hhh', self.desktop.states[-1][33:39]), (-32767, 123, -1))
        self.router.receive(self.mouse(), 2.01)
        self.assertEqual(self.desktop.states[-1][33:39], bytes(6))

    def test_independent_gamepad_and_desktop_timeout(self):
        gamepad = bytes([1]) + bytes(13)
        self.assertTrue(self.router.receive(gamepad, 3.0))
        self.router.receive(self.keyboard(44), 3.01)  # Space
        self.router.expire(3.6)
        self.assertEqual(self.pad.states[-1], NEUTRAL)
        self.assertEqual(self.desktop.states[-1], NEUTRAL_DESKTOP)

    def test_malformed_reports_are_ignored(self):
        self.assertFalse(self.router.receive(bytes([4, 0]) + bytes(4), 4.0))
        self.assertFalse(self.router.receive(bytes([4, 0x80]) + bytes(6), 4.0))
        self.assertFalse(self.router.receive(bytes([3]) + bytes(28), 4.0))
        self.assertFalse(self.pad.states)
        self.assertFalse(self.desktop.states)

    def test_stale_relative_motion_does_not_release_held_keyboard(self):
        self.router.receive(self.keyboard(44), 5.0)
        self.router.receive(self.mouse(0, 22, 0), 5.1)
        self.router.expire(5.4)
        self.assertTrue(self.router.desktop_active)
        self.assertEqual(self.desktop.states[-1][5], 1 << 4)  # Space stays held

    def test_device_discovery_uses_hidraw_and_finds_linux_uhid_phone(self):
        class Hidraw:
            def enumerate(self):
                return [
                    {'path': b'/dev/hidraw1', 'vendor_id': 0x038f, 'product_id': 0x1200,
                     'product_string': 'Redmi PhonePad', 'usage_page': 1, 'usage': usage}
                    for usage in (5, 6, 2)
                ]
        found = enumerate_phonepad_hid(Hidraw(), '/nonexistent')
        self.assertEqual(len(found), 1)
        self.assertEqual(found[0]['path'], '/dev/hidraw1')
        self.assertEqual(found[0]['product'], 'Redmi PhonePad')

    def test_sysfs_fallback_finds_uhid_when_hidapi_omits_it(self):
        class EmptyHid:
            def enumerate(self): return []
        with tempfile.TemporaryDirectory() as directory:
            node = Path(directory) / 'hidraw7' / 'device'
            node.mkdir(parents=True)
            (node / 'uevent').write_text('HID_ID=0005:0000038F:00001200\nHID_NAME=PhonePad over Bluetooth\n')
            found = enumerate_phonepad_hid(EmptyHid(), directory)
        self.assertEqual(found[0]['path'], '/dev/hidraw7')
        self.assertEqual(found[0]['usage'], 5)


if __name__ == '__main__': unittest.main()
