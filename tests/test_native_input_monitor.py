import sys
import unittest
from pathlib import Path
from types import SimpleNamespace

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from evdev import ecodes as e
from native_input_monitor import NativeInputMonitor


class FakeDevice:
    def __init__(self, path, kind):
        self.path = path
        self.name = kind
        self.events = []
        self.closed = False
        self.active = []
        if kind == 'keyboard':
            self.caps = {e.EV_KEY: [e.KEY_A, e.KEY_LEFTCTRL]}
        elif kind == 'mouse':
            self.caps = {e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE, e.BTN_SIDE, e.BTN_EXTRA],
                         e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL]}
        else:
            self.caps = {e.EV_KEY: [e.BTN_A, e.BTN_B, e.BTN_TL, e.BTN_TR], e.EV_ABS: [e.ABS_X, e.ABS_Y, e.ABS_Z,
                e.ABS_RX, e.ABS_RY, e.ABS_RZ, e.ABS_HAT0X, e.ABS_HAT0Y]}
        self.abs = {code: SimpleNamespace(value=0, minimum=-32767, maximum=32767)
                    for code in self.caps.get(e.EV_ABS, [])}
        for code in (e.ABS_Z, e.ABS_RZ):
            if code in self.abs:
                self.abs[code] = SimpleNamespace(value=0, minimum=0, maximum=255)
        for code in (e.ABS_HAT0X, e.ABS_HAT0Y):
            if code in self.abs:
                self.abs[code] = SimpleNamespace(value=0, minimum=-1, maximum=1)

    def capabilities(self, absinfo=False): return self.caps
    def active_keys(self): return self.active
    def absinfo(self, code): return self.abs[code]
    def read(self):
        result, self.events = self.events, []
        if not result: raise BlockingIOError()
        return result
    def close(self): self.closed = True


def ev(kind, code, value): return SimpleNamespace(type=kind, code=code, value=value)


class NativeInputMonitorTests(unittest.TestCase):
    def setUp(self):
        self.devices = {}
        fake = SimpleNamespace(ecodes=e,
            InputDevice=lambda path: self.devices.setdefault(path, FakeDevice(path, path)))
        self.events = []
        self.monitor = NativeInputMonitor(
            [{'path': name} for name in ('keyboard', 'mouse', 'controller')],
            lambda source, kind, state: self.events.append((source, kind, state)), fake)

    def tearDown(self): self.monitor.close()

    def test_keyboard_press_release_and_modifiers_map_to_hid_usages(self):
        keyboard = self.devices['keyboard']
        keyboard.events = [ev(e.EV_KEY, e.KEY_A, 1), ev(e.EV_KEY, e.KEY_LEFTCTRL, 1),
                           ev(e.EV_SYN, e.SYN_REPORT, 0), ev(e.EV_KEY, e.KEY_A, 0),
                           ev(e.EV_KEY, e.KEY_LEFTCTRL, 0), ev(e.EV_SYN, e.SYN_REPORT, 0)]
        self.monitor.poll()
        states = [state for _, kind, state in self.events if kind == 'keyboard']
        self.assertEqual(states, [[4, 224], []])

    def test_mouse_motion_click_and_wheel_are_reported_together(self):
        mouse = self.devices['mouse']
        mouse.events = [ev(e.EV_REL, e.REL_X, 17), ev(e.EV_REL, e.REL_Y, -9),
                        ev(e.EV_REL, e.REL_WHEEL, 1), ev(e.EV_KEY, e.BTN_LEFT, 1),
                        ev(e.EV_SYN, e.SYN_REPORT, 0), ev(e.EV_KEY, e.BTN_LEFT, 0),
                        ev(e.EV_SYN, e.SYN_REPORT, 0)]
        self.monitor.poll()
        states = [state for _, kind, state in self.events if kind == 'mouse']
        self.assertEqual(states, [[1, 17, -9, 1], [0, 0, 0, 0]])

    def test_controller_button_stick_and_dpad_are_normalized(self):
        controller = self.devices['controller']
        controller.events = [ev(e.EV_KEY, e.BTN_A, 1), ev(e.EV_ABS, e.ABS_X, 16384),
                             ev(e.EV_ABS, e.ABS_HAT0Y, -1), ev(e.EV_SYN, e.SYN_REPORT, 0)]
        self.monitor.poll()
        state = [state for _, kind, state in self.events if kind == 'controller'][-1]
        self.assertEqual(state, [1, 0, 16384, 0, 0, 0, 0, 0])

    def test_controller_b_and_trigger_press_release_are_observable(self):
        controller = self.devices['controller']
        controller.events = [ev(e.EV_KEY, e.BTN_B, 1), ev(e.EV_ABS, e.ABS_Z, 255),
                             ev(e.EV_ABS, e.ABS_RZ, 128), ev(e.EV_SYN, e.SYN_REPORT, 0),
                             ev(e.EV_KEY, e.BTN_B, 0), ev(e.EV_ABS, e.ABS_Z, 0),
                             ev(e.EV_ABS, e.ABS_RZ, 0), ev(e.EV_SYN, e.SYN_REPORT, 0)]
        self.monitor.poll()
        states = [state for _, kind, state in self.events if kind == 'controller']
        self.assertEqual([state[0] for state in states], [1 << 1, 0])
        self.assertEqual(states[0][6:8], [255, 128])
        self.assertEqual(states[1][6:8], [0, 0])

    def test_native_phonepad_share_button_code_319_is_visible_and_releases(self):
        controller = self.devices['controller']
        controller.events = [ev(e.EV_KEY, 319, 1), ev(e.EV_SYN, e.SYN_REPORT, 0),
                             ev(e.EV_KEY, 319, 0), ev(e.EV_SYN, e.SYN_REPORT, 0)]
        self.monitor.poll()
        states = [state for _, kind, state in self.events if kind == 'controller']
        self.assertEqual([state[0] for state in states], [1 << 15, 0])

    def test_keypad_digits_match_usb_hid_order(self):
        self.assertEqual([self.monitor._keymap(e)[getattr(e, 'KEY_KP'+str(i))]
                          for i in range(10)], [98,89,90,91,92,93,94,95,96,97])

    def test_dropped_frames_resync_held_keys_at_next_report(self):
        keyboard=self.devices['keyboard'];keyboard.active=[e.KEY_B]
        keyboard.events=[ev(e.EV_KEY,e.KEY_A,1), ev(e.EV_SYN,e.SYN_DROPPED,0),
                         ev(e.EV_KEY,e.KEY_LEFTCTRL,1),ev(e.EV_SYN,e.SYN_REPORT,0)]
        self.monitor.poll()
        self.assertEqual(self.events[-1],('bluetooth','keyboard',[5]))

    def test_partial_device_disconnect_releases_displayed_keys(self):
        keyboard=self.devices['keyboard']
        keyboard.events=[ev(e.EV_KEY,e.KEY_A,1),ev(e.EV_SYN,e.SYN_REPORT,0)]
        self.monitor.poll()
        def gone():raise OSError('disconnected')
        keyboard.read=gone
        self.monitor.poll()
        self.assertEqual(self.events[-1],('bluetooth','keyboard',[]))
        self.assertEqual(len(self.monitor.devices),2)


if __name__ == '__main__': unittest.main()
