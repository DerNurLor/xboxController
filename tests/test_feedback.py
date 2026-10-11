"""FF lifecycle regression without a kernel device or optional evdev dependency."""
import sys
import unittest
from pathlib import Path
from types import SimpleNamespace as N
from unittest.mock import patch
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from backends import LinuxPad
from protocol import Protocol, packet


class FeedbackTests(unittest.TestCase):
    def setUp(self):
        self.pad=LinuxPad.__new__(LinuxPad)
        self.pad.effects={1:(65535,32768,1,0)}
        self.pad.active={1:(10,1)}
        self.pad.gain=65535
        self.pad.e=N(EV_UINPUT=0x101,UI_FF_UPLOAD=1,UI_FF_ERASE=2,EV_FF=21,FF_GAIN=96,FF_RUMBLE=80)
        self.pad.ui=N(read=lambda:[],end_upload=lambda request:None,end_erase=lambda request:None)

    def rumble(self,now):
        with patch('backends.time.monotonic',return_value=now):return self.pad.rumble()

    def test_channels_gain_and_finite_lease(self):
        self.assertEqual(self.rumble(10),bytes([255,128,25]))
        self.pad.gain=0;self.assertEqual(self.rumble(10)[:2],bytes(2))
        self.assertEqual(self.rumble(11),bytes(3))

    def test_playing_upload_update_stop_erase(self):
        e=self.pad.e
        upload=N(effect=N(type=e.FF_RUMBLE,id=1,u=N(ff_rumble_effect=N(strong_magnitude=0,weak_magnitude=65535)),ff_replay=N(length=500,delay=0)),retval=None)
        self.pad._begin_ff=lambda ident,operation:upload
        self.pad.ui.read=lambda:[N(type=e.EV_UINPUT,code=e.UI_FF_UPLOAD,value=9)]
        self.pad.poll();self.assertEqual(upload.retval,0)
        self.assertEqual(self.rumble(10.1)[:2],bytes([0,255]))
        self.assertEqual(self.rumble(10.5),bytes(3))
        self.pad.ui.read=lambda:[N(type=e.EV_FF,code=1,value=1)]
        with patch('backends.time.monotonic',return_value=20):self.pad.poll()
        self.assertEqual(self.rumble(20)[:2],bytes([0,255]))
        self.pad.ui.read=lambda:[N(type=e.EV_FF,code=1,value=0)]
        self.pad.poll();self.assertEqual(self.rumble(20),bytes(3))
        self.pad.active[1]=(20,1)
        erase=N(effect_id=1,retval=None);self.pad._begin_ff=lambda ident,operation:erase
        self.pad.ui.read=lambda:[N(type=e.EV_UINPUT,code=e.UI_FF_ERASE,value=10)]
        self.pad.poll();self.assertEqual(erase.retval,0);self.assertFalse(self.pad.effects);self.assertFalse(self.pad.active)

    def test_repeat_delay_and_infinite(self):
        self.pad.effects[1]=(65535,0,.25,.25);self.pad.active[1]=(10,2)
        for now in (10,10.6,11):self.assertEqual(self.rumble(now),bytes(3))
        self.pad.active[1]=(10,2)
        for now in (10.3,10.8):self.assertEqual(self.rumble(now)[0],255)
        self.pad.effects[1]=(0,65535,0,0);self.pad.active[1]=(10,1)
        self.assertEqual(self.rumble(100),bytes([0,255,25]))
        self.pad.stop_feedback();self.assertEqual(self.rumble(100),bytes(3))

    def test_session_loss_clears_even_neutral_input_rumble(self):
        key=bytes(16);protocol=Protocol(key,lambda body:None,self.pad.rumble,self.pad.stop_feedback)
        protocol.receive(packet(1,bytes(16),0,b'',key),('local',1),10)
        self.assertFalse(self.pad.active)
        self.pad.active[1]=(10,1)
        protocol.expire(10.6);self.assertFalse(self.pad.active)
