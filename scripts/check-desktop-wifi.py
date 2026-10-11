#!/usr/bin/env python3
"""Real-phone UDP desktop/reconnect check with an isolated Companion.

Build/install PhonePad and scripts/build-device-tests.py's test APK first.
No saved pairing settings are changed. Requires LAN UDP. With --backend linux,
only test-owned devices are exclusively captured before input is sent.
"""
import sys,time,tempfile,subprocess,json,argparse,select,threading
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from service import CompanionService
from connection_qr import local_ipv4
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('--host',default=local_ipv4(),help='PC LAN address reachable by the phone')
parser.add_argument('--port',type=int,default=39570)
parser.add_argument('--backend',choices=('dry-run','linux'),default='dry-run',help='Observe actual Linux events with linux; requires uinput and readable event nodes')
args=parser.parse_args()
port=args.port
with tempfile.TemporaryDirectory() as root:
    service=CompanionService(dict(backend=args.backend,bind='0.0.0.0',port=port,no_discovery=True),root)
    captured=None;reader=None;stop=threading.Event();records=[];capture_errors=[]
    try:
        service.command('trace',True);service.command('start')
        deadline=time.monotonic()+5
        while service.snapshot().get('state')!='Running' and time.monotonic()<deadline:time.sleep(.05)
        assert service.snapshot()['state']=='Running',service.snapshot().get('errors')
        devices=list(service.device_info)
        if args.backend=='linux':
            from linux_input_diagnostics import CapturedInputs
            if {d['role'] for d in devices}!={'controller','mouse','keyboard'}:
                raise RuntimeError('All three test-owned virtual devices must be observable')
            captured=CapturedInputs(devices)
            def collect():
                try:
                    while not stop.is_set():
                        ready,_,_=select.select(captured.devices,[],[],.05)
                        for device in ready:
                            records.extend((device.path,event.type,event.code,event.value) for event in device.read())
                except Exception as error:capture_errors.append(error)
            reader=threading.Thread(target=collect,daemon=True);reader.start()
        key=(Path(root)/'pairing.key').read_text().strip()
        result=subprocess.run(['adb','shell','am','instrument','-w','-e','phase','desktop-transport','-e','host',args.host,'-e','port',str(port),'-e','key',key,'dev.phonepad.tests/dev.phonepad.DeviceTests'],capture_output=True,text=True,timeout=30)
        print(result.stdout.replace(key,'<redacted>'));print(result.stderr.replace(key,'<redacted>'))
        state=service.snapshot()
        print('COUNTERS',json.dumps(state['counters']))
        assert state['counters'].get('source_reports',0)>0, 'Companion source metadata was not received'
        history=state['history']
        assert any(e['kind']=='controller_source' and e['state'][0]==2 and e['state'][6:]==[255,255] for e in history), 'Mapped B/LT/RT source evidence missing'
        mouse=[e for e in history if e['kind']=='mouse' and e['state']==[31,17,-19,2]]
        keys=[e for e in history if e['kind']=='keyboard' and 4 in e['state'] and 224 in e['state']]
        print('MOUSE_FRAMES',len(mouse),'KEY_FRAMES',len(keys),'FINAL',state['live'])
        assert 'PASS:' in result.stdout and len(mouse)>=2 and len(keys)>=2, 'Real-phone UDP input verification failed; inspect authentication and counters above.'
        assert state['live']['keyboard']==[] and state['live']['mouse'][0]==0
        assert state['live']['controller']==[0,8,0,0,0,0,0,0], 'Controller remained held after disconnect'
        if captured:
            stop.set();reader.join(2)
            assert not reader.is_alive() and not capture_errors, capture_errors
            from evdev import ecodes as e
            paths={d['role']:d['path'] for d in devices}
            def values(role,kind,code):
                return [v for path,t,c,v in records if path==paths[role] and t==kind and c==code]
            for role,codes in [('keyboard',(e.KEY_A,e.KEY_LEFTCTRL)),
                               ('mouse',(e.BTN_LEFT,e.BTN_RIGHT,e.BTN_MIDDLE,e.BTN_SIDE,e.BTN_EXTRA)),
                               ('controller',(e.BTN_B,))]:
                for code in codes:
                    actual=values(role,e.EV_KEY,code)
                    assert actual==[1,0,1,0], (role,code,actual)
                assert values(role,e.EV_SYN,e.SYN_REPORT), (role,'Missing SYN_REPORT')
            for code,value in ((e.REL_X,17),(e.REL_Y,-19),(e.REL_WHEEL,2)):
                actual=values('mouse',e.EV_REL,code)
                assert actual==[value,value], ('Lost or duplicated movement',code,actual)
            for code,value in ((e.ABS_X,12000),(e.ABS_Y,-15000),(e.ABS_RX,6000),
                               (e.ABS_RY,-8000),(e.ABS_Z,128),(e.ABS_RZ,255),
                               (e.ABS_HAT0X,1),(e.ABS_HAT0Y,-1)):
                actual=values('controller',e.EV_ABS,code)
                assert actual==[value,0,value,0], ('Controller axis/release',code,actual)
            print('PASS: real Android touch/mapping/serialization over LAN reached Linux mouse, keyboard and controller event nodes; simultaneous input, explicit release, held-input disconnect cleanup and reconnect verified.')
    finally:
        stop.set()
        if reader:reader.join(2)
        try:service.close()
        finally:
            if captured:captured.close()
