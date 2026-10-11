import sys
import struct
import unittest
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from desktop import Desktop, NEUTRAL_DESKTOP, decode_desktop
from bluetooth_input import BluetoothInput
from input_router import InputRouter
from bluetooth_status import inspect_bluetooth
from protocol import NEUTRAL

class V2InputTests(unittest.TestCase):
    def test_quick_button_and_key_presses_remain_visible_to_monitor(self):
        from service import CompanionService
        service=CompanionService(dict(backend='dry-run'))
        service._observe('wifi','keyboard',[4])
        service._observe('wifi','keyboard',[])
        service._observe('wifi','mouse',(1,0,0,0))
        service._observe('wifi','mouse',(0,0,0,0))
        service._publish()
        pulses=service.snapshot()['input_pulses']
        self.assertIn(dict(kind='keyboard',code=4,source='wifi'),pulses)
        self.assertIn(dict(kind='mouse',code=0,source='wifi'),pulses)
        for pulse in service.input_pulses: pulse['expires']=0
        service._publish()
        self.assertEqual(service.snapshot()['input_pulses'],[])

    def test_mouse_evidence_separates_clicks_movement_scroll_and_sources(self):
        from service import CompanionService
        service=CompanionService(dict(backend='dry-run'))
        service._observe('wifi','mouse',(1,0,0,0))
        service._observe('wifi','mouse',(1,0,0,0))
        service._publish()
        activity=service.snapshot()['mouse_activity']['wifi']
        self.assertEqual(activity['button_presses'],1)
        self.assertEqual(activity['movement_reports'],0)
        for _ in range(2):service._observe('wifi','mouse',(1,5,-3,0))
        service._observe('wifi','mouse',(0,0,0,1))
        service._observe('bluetooth','mouse',(2,-4,0,0))
        service._publish()
        snapshot=service.snapshot()
        self.assertEqual(snapshot['mouse_activity']['wifi'],
            dict(button_presses=1,movement_reports=2,wheel_reports=1,travel_x=10,travel_y=6))
        self.assertEqual(snapshot['mouse_activity']['bluetooth']['travel_x'],4)
        self.assertEqual([sample[:2] for sample in snapshot['mouse_trail']],
                         [(5,-3),(5,-3),(-4,0)])
        self.assertFalse(snapshot['history'])  # visual movement is live; detailed logging stays opt-in
        snapshot['mouse_activity']['wifi']['travel_x']=999
        self.assertEqual(service.snapshot()['mouse_activity']['wifi']['travel_x'],10)

    def test_bluetooth_open_failure_closes_handle(self):
        from bluetooth_bridge import BluetoothPump
        device=SimpleNamespace(open_path=lambda path: (_ for _ in ()).throw(OSError("denied")),
                               close=unittest.mock.Mock())
        fake=SimpleNamespace(device=lambda:device)
        with patch.dict(sys.modules, {'hidraw':fake}), self.assertRaises(RuntimeError):
            BluetoothPump('/dev/hidraw-test',None,None)
        device.close.assert_called_once()

    def test_bluetooth_bridge_requires_unique_phone_before_grab(self):
        from bluetooth_bridge import BluetoothPump
        device=SimpleNamespace(open_path=lambda path:None,set_nonblocking=lambda value:None,
                               close=unittest.mock.Mock())
        fake=SimpleNamespace(device=lambda:device,enumerate=lambda:[])
        with patch.dict(sys.modules, {'hidraw':fake}), self.assertRaisesRegex(RuntimeError,'uniquely'):
            BluetoothPump('/dev/hidraw-test',SimpleNamespace(gamepad_state=lambda *a:None,desktop_state=lambda *a:None),None,True)
        device.close.assert_called_once()

    def test_actual_bluetooth_callback_contract_and_independent_leases(self):
        pads, desktops = [], []
        receiver = BluetoothInput(pads.append, desktops.append)
        self.assertTrue(receiver.receive(bytes([1])+NEUTRAL, 1))
        keyboard=bytearray(30); keyboard[0]=3; keyboard[1]=16
        receiver.receive(keyboard, 1)
        receiver.receive(bytes([4,31])+struct.pack('<hhh',17,-19,-2), 1.4)
        self.assertEqual(decode_desktop(desktops[-1]), ({4},31,17,-19,-2))
        receiver.expire(1.6)
        self.assertEqual(decode_desktop(desktops[-1]), (set(),31,0,0,0))
        receiver.expire(2)
        self.assertEqual(desktops[-1], NEUTRAL_DESKTOP)
        self.assertTrue(receiver.receive(bytes([4,1])+struct.pack('<hhh',1,2,3), 2.1))
        receiver.neutral()
        self.assertEqual(desktops[-1], NEUTRAL_DESKTOP)

    def test_identical_relative_events_are_observed_each_time(self):
        events=[]
        sink=SimpleNamespace(apply=lambda body:None)
        router=InputRouter(sink,sink,lambda *args:events.append(args))
        body=bytearray(NEUTRAL_DESKTOP);body[33:]=struct.pack('!hhh',5,0,0)
        router.desktop_state('wifi',body);router.desktop_state('wifi',body)
        self.assertEqual(sum(e[1]=='mouse' for e in events),2)

    def test_linux_chords_press_modifiers_first_and_release_them_last(self):
        from evdev import ecodes as e
        from desktop import KEY_NAMES
        events=[]
        sink=SimpleNamespace(write=lambda *args:events.append(args),syn=lambda:None)
        desktop=Desktop('dry-run');desktop.backend='linux';desktop.e=e
        desktop.codes={usage:getattr(e,'KEY_'+name) for usage,name in KEY_NAMES.items()}
        desktop.ui=desktop.mouse_ui=sink;desktop.mouse_codes=()
        frame=bytearray(NEUTRAL_DESKTOP);frame[0]=1<<6;frame[28]=1
        desktop.apply(frame);desktop.apply(NEUTRAL_DESKTOP)
        self.assertEqual(events,[(e.EV_KEY,e.KEY_LEFTCTRL,1),(e.EV_KEY,e.KEY_C,1),
                                 (e.EV_KEY,e.KEY_C,0),(e.EV_KEY,e.KEY_LEFTCTRL,0)])

    def test_separate_linux_devices_capabilities_edges_and_framing(self):
        import evdev
        from evdev import ecodes as e
        devices=[]
        class Device:
            def __init__(self, capabilities, **kwargs):
                self.capabilities=capabilities;self.events=[];self.closed=False;devices.append(self)
            def write(self,*event): self.events.append(event)
            def syn(self): self.events.append((e.EV_SYN,e.SYN_REPORT,0))
            def close(self): self.closed=True
        with patch.object(evdev,'UInput',Device): desktop=Desktop('linux')
        keyboard,mouse=devices
        self.assertNotIn(e.EV_REL,keyboard.capabilities)
        self.assertEqual(mouse.capabilities[e.EV_REL],[e.REL_X,e.REL_Y,e.REL_WHEEL])
        self.assertNotIn(e.KEY_A,mouse.capabilities[e.EV_KEY])
        body=bytearray(NEUTRAL_DESKTOP);body[0]=16;body[28]=3;body[32]=31
        body[33:]=struct.pack('!hhh',123,-456,2)
        desktop.apply(body)
        self.assertIn((e.EV_KEY,e.KEY_A,1),keyboard.events)
        self.assertIn((e.EV_KEY,e.KEY_LEFTCTRL,1),keyboard.events)
        self.assertIn((e.EV_REL,e.REL_X,123),mouse.events)
        self.assertIn((e.EV_REL,e.REL_Y,-456),mouse.events)
        self.assertIn((e.EV_REL,e.REL_WHEEL,2),mouse.events)
        for code in desktop.mouse_codes:self.assertIn((e.EV_KEY,code,1),mouse.events)
        self.assertEqual(mouse.events[-1],(e.EV_SYN,e.SYN_REPORT,0))
        desktop.close()
        for code in desktop.mouse_codes:self.assertIn((e.EV_KEY,code,0),mouse.events)
        self.assertTrue(all(d.closed for d in devices))

    def test_powered_adapter_is_not_a_hid_connection(self):
        def run(args,**kwargs):
            out='Powered: yes\n' if args[-1]=='show' else 'Device AA:BB:CC:DD:EE:FF Phone\n' if args[-1]=='devices' else 'Paired: yes\nConnected: yes\nUUID: Audio Sink\n'
            return SimpleNamespace(returncode=0,stdout=out,stderr='')
        with patch('bluetooth_status.enumerate_phonepad_hid',return_value=[]): info=inspect_bluetooth(run)
        self.assertTrue(info['powered']);self.assertTrue(info['devices'][0]['connected'])
        self.assertFalse(info['devices'][0]['hid_capable']);self.assertEqual(info['hid'],[])


class LifecycleTests(unittest.TestCase):
    def wait(self, service, condition):
        import time
        until=time.monotonic()+4
        while time.monotonic()<until:
            state=service.snapshot()
            if condition(state):return state
            time.sleep(.02)
        self.fail(str(service.snapshot()))

    def test_udp_pairing_input_disconnect_restart_and_port_conflict(self):
        import socket,tempfile,time,json
        from service import CompanionService
        from protocol import packet,unpack,sign
        with tempfile.TemporaryDirectory() as root, socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
            client.bind(('127.0.0.1',0));port=client.getsockname()[1]
            # Reuse this bound port first to exercise a visible acquisition failure.
            service=CompanionService(dict(backend='dry-run',bind='127.0.0.1',port=port),root)
            service.command('start')
            state=self.wait(service,lambda s:s['state']=='Failed')
            self.assertTrue(state['wifi_error']);self.assertEqual(state['pairing_state'],'Stopped')
            client.close()
            service.command('stop');self.wait(service,lambda s:s['state']=='Stopped')
            service.command('start');state=self.wait(service,lambda s:s['receivers']['wifi']=='Running')
            self.assertEqual(state['pairing_state'],'Ready');old=state['qr']
            service.command('pair');state=self.wait(service,lambda s:s['qr']!=old)
            with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as phone:
                phone.settimeout(2);host=('127.0.0.1',port)
                key=bytes.fromhex((Path(root)/'pairing.key').read_text().strip())
                phone.sendto(packet(1,bytes(16),0,b'',key),host)
                nonce=unpack(phone.recv(1537),key,2,16)[2];session=sign(key,bytes(16)+nonce)
                body=bytearray(NEUTRAL_DESKTOP);body[0]=16;body[32]=31;body[33:]=struct.pack('!hhh',20,-10,2)
                service.command('trace',True)
                phone.sendto(packet(5,nonce,1,body,session),host);self.assertEqual(phone.recv(1537)[4],6)
                state=self.wait(service,lambda s:s['live']['mouse']==[31,20,-10,2])
                self.assertEqual(state['live']['keyboard'],[4]);self.assertEqual(state['counters']['backend_writes'],1)
                state=self.wait(service,lambda s:s['live']['mouse']==[0,0,0,0])
                self.assertEqual(state['live']['keyboard'],[])
                self.assertEqual(state['counters']['connection_interruptions'],1)
            service.command('stop');self.wait(service,lambda s:s['state']=='Stopped')
            service.command('start');self.wait(service,lambda s:s['state']=='Running')
            service.close()
            self.assertEqual(service.snapshot()['state'],'Stopped')

    def test_bluetooth_waits_without_blocking_start_and_partial_wifi_failure(self):
        import socket,tempfile
        from service import CompanionService
        info={'powered':True,'devices':[],'hid':[],'error':''}
        with tempfile.TemporaryDirectory() as root, socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as busy:
            busy.bind(('127.0.0.1',0))
            service=CompanionService(dict(backend='dry-run',bluetooth=True,bind='127.0.0.1',port=busy.getsockname()[1]),root,lambda:info)
            try:
                service.command('start')
                state=self.wait(service,lambda s:s['state']=='Partially running' and s['bluetooth']['powered'])
                self.assertEqual(state['receivers']['bluetooth'],'Waiting for device')
            finally:service.close()

    def test_configuration_preserves_unknown_settings_and_applies_disable(self):
        import tempfile,socket
        from configuration import save,load
        from service import CompanionService
        with tempfile.TemporaryDirectory() as root:
            path=Path(root)/'settings.json'
            config=save(path,dict(backend='dry-run',wifi=False,bluetooth=True,custom='keep'))
            self.assertEqual(load(path)['custom'],'keep')
            with self.assertRaises(ValueError):save(path,dict(port=70000))
            service=CompanionService(config,root,lambda:{'powered':True,'hid':[],'devices':[],'error':''})
            try:
                service.command('start');self.wait(service,lambda s:s['state']=='Waiting for device')
                service.command('configure',{**config,'bluetooth':False})
                self.wait(service,lambda s:s['state']=='Stopped')
            finally:service.close()

if __name__ == '__main__':
    unittest.main()
