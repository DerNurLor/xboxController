import sys
import unittest
from pathlib import Path
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from source_monitor import SourceMonitor
from protocol import STATE, NEUTRAL
from bluetooth_input import BluetoothInput


class SourceMonitorTests(unittest.TestCase):
    def test_source_only_never_calls_input_backend_and_expires(self):
        writes=[];seen=[]
        decoder=BluetoothInput(writes.append,writes.append)
        report=bytes([5])+STATE.pack(2,8,0,0,0,0,255,255)
        self.assertTrue(decoder.receive(report,1));self.assertEqual(writes,[])
        monitor=SourceMonitor(lambda source,body:seen.append((source,body)))
        with patch('source_monitor.os.open',return_value=42) as opened,patch('source_monitor.os.close'):
            monitor.update([dict(path='/dev/hidraw0',source_input=True)])
            self.assertEqual(opened.call_args.args[1],__import__('os').O_RDONLY|__import__('os').O_NONBLOCK)
            self.assertFalse(monitor.accept('/dev/hidraw0',bytes([1])+report[1:],1))
            self.assertTrue(monitor.accept('/dev/hidraw0',report,1))
            with patch('source_monitor.os.read',side_effect=BlockingIOError):monitor.poll(1.6)
            self.assertEqual(seen[-1][1],NEUTRAL)
            monitor.close()

    def test_old_descriptor_does_not_open_extra_reader(self):
        monitor=SourceMonitor(lambda *args:None)
        with patch('source_monitor.os.open') as opened:
            monitor.update([dict(path='/dev/hidraw0')])
            opened.assert_not_called()
