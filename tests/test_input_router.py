import sys
import unittest
from pathlib import Path
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from input_router import InputRouter
from protocol import NEUTRAL, STATE
from desktop import NEUTRAL_DESKTOP, decode_desktop


class InputRouterTests(unittest.TestCase):
    class Sink:
        def __init__(self): self.values=[]
        def apply(self, value): self.values.append(value)

    def setUp(self):
        self.pads=self.Sink(); self.desktops=self.Sink()
        self.router=InputRouter(self.pads,self.desktops)

    def test_monitor_snapshot_never_recounts_old_movement(self):
        events=[]
        self.router.observer=lambda *event: events.append(event)
        frame=bytearray(NEUTRAL_DESKTOP);frame[32]=1
        frame[33:39]=bytes.fromhex('0005fffd0001')
        self.router.desktop_state('wifi',bytes(frame))
        events.clear();before=len(self.desktops.values)
        self.router.test_snapshot()
        self.assertIn(('wifi','mouse',(1,0,0,0)),events)
        self.assertEqual(len(self.desktops.values),before)

    def test_both_transports_contribute_and_release_independently(self):
        wifi=STATE.pack(1,8,0,0,0,0,0,0)
        bluetooth=STATE.pack(2,8,0,0,0,0,0,0)
        self.router.gamepad_state('wifi',wifi)
        self.router.gamepad_state('bluetooth',bluetooth)
        self.assertEqual(3,STATE.unpack(self.pads.values[-1])[0])
        self.router.gamepad_state('wifi',NEUTRAL)
        self.assertEqual(2,STATE.unpack(self.pads.values[-1])[0])
        self.router.gamepad_state('bluetooth',NEUTRAL)
        self.assertEqual(0,STATE.unpack(self.pads.values[-1])[0])

    def test_desktop_state_unions_keys_and_never_replays_relative_motion(self):
        wifi=bytearray(NEUTRAL_DESKTOP); wifi[4//8] |= 1 << (4%8)
        bluetooth=bytearray(NEUTRAL_DESKTOP); bluetooth[5//8] |= 1 << (5%8)
        self.router.desktop_state('wifi',bytes(wifi))
        self.router.desktop_state('bluetooth',bytes(bluetooth))
        keys,_,x,y,wheel=decode_desktop(self.desktops.values[-1])
        self.assertEqual({4,5},keys)
        self.assertEqual((0,0,0),(x,y,wheel))
        move=bytearray(NEUTRAL_DESKTOP); move[33:39]=bytes.fromhex('000500000000')
        self.router.desktop_state('bluetooth',bytes(move))
        self.assertEqual(5,decode_desktop(self.desktops.values[-1])[2])
        self.router.desktop_state('wifi',bytes(wifi))
        self.assertEqual(0,decode_desktop(self.desktops.values[-1])[2])

    def test_observer_separates_controller_mouse_and_keyboard(self):
        events=[]; router=InputRouter(self.Sink(),self.Sink(),lambda *event:events.append(event))
        state=STATE.pack(0,8,0,0,0,0,0,0)
        router.gamepad_state('wifi',state)
        desktop=bytearray(NEUTRAL_DESKTOP); desktop[32]=1
        router.desktop_state('bluetooth',bytes(desktop))
        self.assertEqual(('wifi','controller'),events[0][:2])
        self.assertIn(('bluetooth','mouse'),[event[:2] for event in events])

    def test_test_snapshot_reports_only_active_inputs(self):
        events=[]; router=InputRouter(self.Sink(),self.Sink(),lambda *event:events.append(event))
        router.test_snapshot()
        self.assertEqual([],events)
        router.gamepad_state('wifi',STATE.pack(1,8,0,0,0,0,0,0))
        router.test_snapshot()
        self.assertEqual(('wifi','controller'),events[-1][:2])


if __name__=='__main__': unittest.main()
