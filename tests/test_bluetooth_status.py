import sys
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import Mock

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from bluetooth_status import pair


class BluetoothPairingTests(unittest.TestCase):
    def test_pair_uses_selected_address(self):
        run = Mock(return_value=SimpleNamespace(returncode=0, stdout='Pairing successful', stderr=''))
        self.assertEqual(pair('AA:BB:CC:DD:EE:FF', run), 'Pairing successful')
        run.assert_called_once_with(
            ['bluetoothctl', 'pair', 'AA:BB:CC:DD:EE:FF'],
            capture_output=True, text=True, timeout=25)

    def test_pair_rejects_unvalidated_address_before_calling_system(self):
        run = Mock()
        with self.assertRaisesRegex(ValueError, 'scan results'):
            pair('not-an-address', run)
        run.assert_not_called()

    def test_pair_surfaces_bluetoothctl_error(self):
        run = Mock(return_value=SimpleNamespace(returncode=1, stdout='', stderr='Authentication failed'))
        with self.assertRaisesRegex(RuntimeError, 'Authentication failed'):
            pair('AA:BB:CC:DD:EE:FF', run)
