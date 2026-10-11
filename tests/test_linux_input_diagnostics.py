import errno
import sys
import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from linux_input_diagnostics import CapturedInputs, describe_device, permission_snapshot, required_capabilities
from evdev import ecodes as e


class LinuxInputDiagnosticsTests(unittest.TestCase):
    def make_device(self, root, event, phys, missing=None):
        path = Path(root) / event / 'device'
        (path / 'capabilities').mkdir(parents=True)
        (path / 'phys').write_text(phys)
        (path / 'name').write_text('PhonePad Mouse')
        caps = required_capabilities('mouse')
        if missing is not None:
            caps[e.EV_REL].remove(missing)
        for name, values in [('ev', {e.EV_SYN, *caps}), ('key', caps[e.EV_KEY]), ('rel', caps[e.EV_REL])]:
            # Sysfs emits native-word groups, most significant first.
            import struct
            width = struct.calcsize('L') * 8
            value = sum(1 << n for n in values)
            words = []
            while value:
                words.insert(0, format(value & ((1 << width) - 1), 'x'))
                value >>= width
            (path / 'capabilities' / name).write_text(' '.join(words) or '0')

    def test_identity_and_capabilities_survive_permission_denial(self):
        with tempfile.TemporaryDirectory() as root:
            self.make_device(root, 'event1', 'phonepad/other/mouse')
            self.make_device(root, 'event2', 'phonepad/test/mouse')
            ui = SimpleNamespace(name='PhonePad Mouse', phys='phonepad/test/mouse')
            with patch('evdev.InputDevice', side_effect=PermissionError(errno.EACCES, 'denied')):
                result = describe_device(ui, 'mouse', root)
            self.assertEqual(result['path'], '/dev/input/event2')
            self.assertTrue(result['observed'])
            self.assertFalse(result['ready'])
            self.assertEqual(result['status'], 'Permission required')
            self.assertEqual(result['missing'], [])
            self.assertIn(e.REL_WHEEL, result['capabilities']['EV_REL'])

    def test_missing_wheel_not_reported_ready(self):
        with tempfile.TemporaryDirectory() as root:
            self.make_device(root, 'event3', 'phonepad/test/mouse', e.REL_WHEEL)
            with patch('evdev.InputDevice') as device:
                result = describe_device(SimpleNamespace(name='PhonePad Mouse', phys='phonepad/test/mouse'), 'mouse', root)
            device.return_value.close.assert_called_once()
            self.assertEqual(result['status'], 'Capabilities missing')
            self.assertFalse(result['ready'])

    def test_capture_failure_releases_every_previous_device(self):
        first, second = Mock(), Mock()
        second.grab.side_effect = OSError('busy')
        details = [dict(ready=True, path=str(n)) for n in range(2)]
        with self.assertRaises(OSError):
            CapturedInputs(details, Mock(side_effect=[first, second]))
        first.ungrab.assert_called_once()
        first.close.assert_called_once()
        second.close.assert_called_once()

    def test_unready_device_never_opened(self):
        factory = Mock()
        with self.assertRaises(RuntimeError):
            CapturedInputs([dict(ready=False, name='Mouse', status='Permission required')], factory)
        factory.assert_not_called()
        with self.assertRaises(ValueError):
            CapturedInputs([], factory)

    def test_uhid_is_optional(self):
        with tempfile.TemporaryDirectory() as root:
            snapshot = permission_snapshot(root, root)
        self.assertTrue(snapshot['nodes'][0]['required'])
        self.assertFalse(snapshot['nodes'][1]['required'])


if __name__ == '__main__':
    unittest.main()
