"""Exclusive grab prevents native and companion mouse reports being doubled."""
import sys
import types
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from bluetooth_input_grab import BluetoothInputGrab


class BluetoothInputGrabTests(unittest.TestCase):
    def test_grabs_only_interfaces_for_selected_phone(self):
        devices = {
            '/dev/input/event20': SimpleNamespace(info=SimpleNamespace(bustype=5, vendor=0x038f, product=0x1200), uniq='aa:bb', grabbed=False, closed=False),
            '/dev/input/event21': SimpleNamespace(info=SimpleNamespace(bustype=5, vendor=0x038f, product=0x1200), uniq='aa:bb', grabbed=False, closed=False),
            '/dev/input/event22': SimpleNamespace(info=SimpleNamespace(bustype=5, vendor=0x038f, product=0x1200), uniq='cc:dd', grabbed=False, closed=False),
        }
        for item in devices.values():
            item.grab=lambda item=item: setattr(item, 'grabbed', True)
            item.ungrab=lambda item=item: setattr(item, 'grabbed', False)
            item.close=lambda item=item: setattr(item, 'closed', True)
        fake_evdev=types.SimpleNamespace(InputDevice=devices.__getitem__, list_devices=lambda:list(devices))
        with patch.dict(sys.modules, {'evdev':fake_evdev}):
            grab=BluetoothInputGrab('AA:BB')
            self.assertEqual(len(grab.devices), 2)
            grab.close()
        self.assertTrue(devices['/dev/input/event20'].closed)
        self.assertTrue(devices['/dev/input/event21'].closed)
        self.assertTrue(devices['/dev/input/event22'].closed)

    def test_partial_failure_releases_every_successful_grab(self):
        first=SimpleNamespace(info=SimpleNamespace(bustype=5,vendor=0x038f,product=0x1200),uniq='aa',grabbed=False)
        first.grab=lambda:setattr(first,'grabbed',True)
        first.ungrab=lambda:setattr(first,'grabbed',False)
        first.close=lambda:None
        class Refuse:
            info=first.info
            uniq='aa'
            def grab(self): raise PermissionError('denied')
            def close(self): pass
        rows={'/dev/input/event1':first,'/dev/input/event2':Refuse()}
        fake_evdev=types.SimpleNamespace(InputDevice=rows.__getitem__,list_devices=lambda:list(rows))
        with patch.dict(sys.modules,{'evdev':fake_evdev}):
            with self.assertRaisesRegex(RuntimeError,'exclusive control'):
                BluetoothInputGrab('aa')
        self.assertFalse(first.grabbed)


if __name__ == '__main__': unittest.main()
