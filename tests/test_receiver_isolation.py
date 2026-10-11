import socket
import sys
import tempfile
import time
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from desktop import NEUTRAL_DESKTOP
from protocol import packet, sign, unpack
from service import CompanionService


class ReceiverIsolationTests(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as free:
            free.bind(('127.0.0.1', 0))
            port = free.getsockname()[1]
        self.service = CompanionService(dict(backend='dry-run', bind='127.0.0.1', port=port,
                                             bluetooth=True), self.directory.name)
        self.refresh = patch.object(self.service, '_refresh').start()
        self.service._start()

    def tearDown(self):
        self.service._stop()
        patch.stopall()
        self.directory.cleanup()

    def authenticate(self):
        key = bytes.fromhex((Path(self.directory.name) / 'pairing.key').read_text().strip())
        peer = ('127.0.0.1', 12345)
        reply = self.service.protocol.receive(packet(1, bytes(16), 0, b'', key), peer, time.monotonic())
        nonce = unpack(reply, key, 2, 16)[2]
        session = sign(key, bytes(16) + nonce)
        body = bytearray(NEUTRAL_DESKTOP)
        body[0] = 16
        body[32] = 1
        self.service.protocol.receive(packet(5, nonce, 1, body, session), peer, time.monotonic())
        return peer, nonce, session

    def test_monitor_start_stop_and_preferences_preserve_authenticated_wifi(self):
        peer, nonce, session_key = self.authenticate()
        service = self.service
        original = (service.sock, service.protocol, service.router, service.desktop)
        session = service.protocol.session
        service._handle('receiver', ('bluetooth', False))
        service._handle('receiver', ('bluetooth', True))
        service._configure({**service.config, 'bluetooth': False})
        service._configure({**service.config, 'bluetooth': True})
        service._configure({**service.config, 'trace_feedback': True, 'custom': 'preserved'})
        self.assertEqual(original, (service.sock, service.protocol, service.router, service.desktop))
        self.assertIs(service.protocol.session, session)
        self.assertEqual(service.desktop.keys, {4})
        self.assertEqual(service.desktop.buttons, 1)
        self.assertIsNotNone(service.protocol.receive(
            packet(5, nonce, 2, NEUTRAL_DESKTOP, session_key), peer, time.monotonic()))
        self.assertEqual(service.desktop.keys, set())
        self.assertEqual(service.desktop.buttons, 0)

    def test_native_neutral_does_not_hide_wifi_held_keys_or_replay_movement(self):
        service = self.service
        service._observe('wifi', 'keyboard', [4,224])
        service._observe('wifi', 'mouse', [1,17,-19,2])
        service._observe('bluetooth', 'keyboard', [5])
        self.assertEqual(service.live['keyboard'], [4,5,224])
        service._observe('bluetooth', 'keyboard', [])
        service._observe('bluetooth', 'mouse', [0,0,0,0])
        self.assertEqual(service.live['keyboard'], [4,224])
        self.assertEqual(service.live['mouse'], [1,0,0,0])
        service._observe('wifi', 'controller', [1,8,100,0,0,0,0,0])
        service._observe('bluetooth', 'controller', [0,8,0,0,0,0,0,0])
        self.assertEqual(service.live['controller'], [1,8,100,0,0,0,0,0])

    def test_trace_correlates_received_packets_but_not_watchdog_releases(self):
        self.service.trace = True
        self.authenticate()
        events = [event for event in self.service.history if event['kind']=='keyboard' and event['state']]
        self.assertEqual(events[-1]['sequence'], 1)
        self.assertEqual(events[-1]['channel'], 'desktop')
        self.assertEqual(self.service.protocol.last_event['stage'], 'backend_written')
        self.service.protocol.expire(time.monotonic()+1)
        releases = [event for event in self.service.history if event['kind']=='keyboard' and not event['state']]
        self.assertNotIn('sequence', releases[-1])
        self.assertIsNone(self.service.protocol.current_event)

    def test_wifi_stop_releases_inputs_without_stopping_monitor(self):
        self.authenticate()
        desktop = self.service.desktop
        self.service._handle('receiver', ('wifi', False))
        self.service._publish()
        snapshot = self.service.snapshot()
        self.assertEqual(snapshot['receivers']['wifi'], 'Stopped')
        self.assertEqual(snapshot['receivers']['bluetooth'], 'Waiting for device')
        self.assertTrue(snapshot['active'])
        self.assertFalse(snapshot['requested']['wifi'])
        self.assertEqual(desktop.keys, set())
        self.assertEqual(desktop.buttons, 0)
        # Unrelated settings do not resume a deliberately stopped receiver.
        self.service._configure({**self.service.config, 'trace_feedback': True})
        self.assertIsNone(self.service.sock)
        self.service._handle('receiver', ('wifi', True))
        self.assertIsNotNone(self.service.sock)
        self.assertEqual(self.service.states['wifi'], 'Running')

    def test_stopping_managed_receivers_preserves_passive_native_hold(self):
        self.service._observe('bluetooth','keyboard',[62])
        self.service._observe('bluetooth','mouse',[1,0,0,0])
        self.service._stop()
        self.service._publish()
        state=self.service.snapshot()
        self.assertEqual(state['state'],'Stopped')
        self.assertEqual(state['live']['keyboard'],[62])
        self.assertEqual(state['live']['mouse'][0],1)

    def test_wifi_setting_changes_restart_only_wifi(self):
        original = self.service.sock
        refreshes = self.refresh.call_count
        self.service._configure({**self.service.config, 'name': 'Changed host'})
        self.assertIsNot(self.service.sock, original)
        self.assertEqual(original.fileno(), -1)
        self.assertEqual(self.refresh.call_count, refreshes)
        self.assertTrue(self.service.requested['bluetooth'])

    def test_failed_receiver_retry_does_not_reinitialize_other_receiver(self):
        self.service._handle('receiver', ('wifi', False))
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as conflict:
            conflict.bind(('127.0.0.1', self.service.config['port']))
            self.service._handle('receiver', ('wifi', True))
            self.service._publish()
            self.assertEqual(self.service.snapshot()['state'], 'Partially running')
        self.service._handle('receiver', ('wifi', True))
        self.assertEqual(self.service.states['wifi'], 'Running')
        self.service._handle('receiver', ('bluetooth', False))
        self.service._handle('receiver', ('wifi', False))
        self.service._publish()
        self.assertEqual(self.service.snapshot()['state'], 'Stopped')


if __name__ == '__main__':
    unittest.main()
