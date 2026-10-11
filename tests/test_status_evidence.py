import sys
import time
import threading
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from service import CompanionService


class StatusEvidenceTests(unittest.TestCase):
    def setUp(self):self.service=CompanionService(dict(backend='dry-run'))

    def test_stopped_receivers_do_not_mean_native_hid_disconnected(self):
        s=self.service
        s.bt_info.update(powered=True,input_devices=[dict(path='/dev/input/test',name='Phone')])
        s.native_counters.update(received_reports=57,decoded_reports=57)
        s._publish();state=s.snapshot()
        self.assertEqual(state['state'],'Stopped')
        self.assertTrue(state['connection']['hid_connected'])
        self.assertEqual(state['connection']['os_evidence'],'HID devices present')
        self.assertEqual(state['native_counters']['kernel_input_reports'],0)
        self.assertEqual(state['observation_source'],'None')
        self.assertFalse(state['feedback']['available'])
        s.native_counters['kernel_input_reports']=1;s._publish()
        self.assertEqual(s.snapshot()['connection']['os_evidence'],'Linux events observed')

    def test_audio_connection_is_not_phonepad_hid(self):
        self.service.bt_info.update(powered=True,devices=[dict(connected=True,paired=True,hid_capable=False,name='Audio')])
        self.service._publish()
        self.assertFalse(self.service.snapshot()['connection']['connected'])

    def test_readonly_evdev_preferred_without_opening_or_injecting_raw_hid(self):
        s=self.service
        info=dict(powered=True,hid=[dict(path='/dev/hidraw-test')],input_devices=[dict(path='/dev/input/test')],devices=[])
        def monitor(devices):s.native_input_monitor=Mock()
        with patch.object(s,'_monitor_kernel_input',side_effect=monitor),patch('service.os.open') as opened:
            s._update_bluetooth(info)
        opened.assert_not_called()
        self.assertIsNone(s.router)
        s._publish()
        self.assertEqual(s.snapshot()['observation_source'],'Linux evdev')
        self.assertEqual(s.snapshot()['state'],'Stopped')

    def test_rumble_requires_actual_writable_output_path(self):
        s=self.service;s.bt_info['hid']=[dict(path='/dev/hidraw-test',feedback_output=True)]
        with patch('service.os.access',return_value=False):
            self.assertFalse(s._feedback_status()['available'])
            with self.assertRaises(ValueError):s._handle('rumble',None)
        with patch('service.os.access',return_value=True):
            self.assertEqual(s._feedback_status()['route'],'bluetooth')
            with patch('service.os.open',return_value=42),patch('service.os.write',return_value=4) as write,patch('service.os.close'):
                s._handle('rumble',None)
                write.assert_called_once_with(42,bytes([2,100,100,25]))

    def test_writable_hid_without_feedback_descriptor_is_not_supported(self):
        self.service.bt_info['hid']=[dict(path='/dev/hidraw-test')]
        with patch('service.os.access',return_value=True):
            self.assertFalse(self.service._feedback_status()['available'])

    def test_reconnect_feedback_is_scoped_to_requested_device_and_cleared_on_error(self):
        s=self.service
        s.scanning=True;s.reconnecting_address='AA:BB:CC:DD:EE:FF'
        s._publish()
        self.assertEqual(s.snapshot()['reconnecting_address'],'AA:BB:CC:DD:EE:FF')
        s._handle('bluetooth_error','connection timed out')
        s._publish()
        self.assertEqual(s.snapshot()['reconnecting_address'],'')

    def test_pairing_error_offers_retry_for_the_same_device(self):
        s=self.service
        s.scanning=True;s.pairing_address='AA:BB:CC:DD:EE:FF'
        s._handle('bluetooth_error','Authentication failed')
        s._publish()
        status=s.snapshot()
        self.assertEqual(status['bluetooth_retry_address'],'AA:BB:CC:DD:EE:FF')
        self.assertEqual(status['bluetooth_retry_action'],'pair')
        self.assertEqual(status['pairing_address'],'')

    def test_bluetooth_pair_runs_off_thread_and_refreshes_device_state(self):
        s=self.service;called=threading.Event()
        info=dict(powered=True,adapter='powered',devices=[],hid=[],input_devices=[],error='')
        with patch('service.pair_bluetooth',side_effect=lambda address:called.set()),patch.object(s,'probe',return_value=info):
            s._pair_bluetooth('AA:BB:CC:DD:EE:FF')
            self.assertTrue(called.wait(1))
            self.assertEqual(s.pairing_address,'AA:BB:CC:DD:EE:FF')
            for _ in range(100):
                try:name,value=s.commands.get_nowait();break
                except Exception:time.sleep(.01)
            else:self.fail('Bluetooth pairing worker did not report completion')
        s._handle(name,value);s._publish()
        self.assertFalse(s.snapshot()['scanning'])
        self.assertEqual(s.snapshot()['pairing_address'],'')
        self.assertFalse(s.snapshot()['bluetooth_retry_address'])
