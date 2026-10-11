"""Native HID evidence must exclude audio and virtual input lookalikes."""
import sys
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from hid_devices import enumerate_phonepad_inputs
from bluetooth_status import inspect_bluetooth


class NativeIdentityTests(unittest.TestCase):
    def test_same_ids_on_avrcp_and_uinput_are_not_native_hid(self):
        with tempfile.TemporaryDirectory() as root:
            root = Path(root)
            inputs = root / 'class/input'
            inputs.mkdir(parents=True)
            for index, kind in enumerate(('avrcp', 'uinput', 'hid')):
                parent = root / 'devices' / kind
                device = parent / 'input/input0'
                (device / 'id').mkdir(parents=True)
                for field, value in (('bustype', '0005'), ('vendor', '038f'), ('product', '1200')):
                    (device / 'id' / field).write_text(value)
                (device / 'name').write_text('PhonePad ' + kind)
                event = device / ('event' + str(index))
                event.mkdir()
                (event / 'device').symlink_to(device)
                (inputs / event.name).symlink_to(event)
                if kind == 'hid':
                    (parent / 'uevent').write_text('HID_ID=0005:0000038F:00001200\nHID_UNIQ=aa:bb\n')
            self.assertEqual(enumerate_phonepad_inputs(inputs), [
                {'path': '/dev/input/event2', 'name': 'PhonePad hid', 'serial_number': 'aa:bb'}])

    def test_input_evidence_survives_bluez_and_hidraw_enumeration_failure(self):
        native = [{'path': '/dev/input/event2', 'name': 'PhonePad', 'serial_number': 'aa'}]
        with patch('bluetooth_status.enumerate_phonepad_hid', side_effect=OSError('denied')), \
                patch('bluetooth_status.enumerate_phonepad_inputs', return_value=native):
            def unavailable(*args, **kwargs):
                raise FileNotFoundError('bluetoothctl')
            state = inspect_bluetooth(unavailable)
        self.assertIsNone(state['powered'])
        self.assertEqual(state['input_devices'], native)
        self.assertIn('denied', state['error'])


if __name__ == '__main__':
    unittest.main()
