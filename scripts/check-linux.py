#!/usr/bin/env python3
"""Bounded integration check: creates only a temporary PhonePad device, then destroys it."""
import sys
from pathlib import Path
import subprocess
import time
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'companion'))
from backends import LinuxPad
from protocol import STATE, NEUTRAL
from evdev import InputDevice, ecodes as e, ff

pad=LinuxPad()
try:
    device=InputDevice(pad.ui.device.path)
    device.grab()  # Test input must not reach unrelated applications.
    caps=device.capabilities();assert e.EV_FF in caps and e.FF_RUMBLE in caps[e.EV_FF]
    print("Measured virtual device EV_FF=PASS FF_RUMBLE=PASS:",device.path)
    pad.apply(STATE.pack((1<<0)|(1<<4)|(1<<13),1,-12345,23456,8000,-9000,127,255))
    time.sleep(.05)
    assert e.BTN_A in device.active_keys() and e.BTN_Y in device.active_keys()
    assert e.BTN_X not in device.active_keys()
    assert e.BTN_THUMBL in device.active_keys()
    assert device.absinfo(e.ABS_X).value==-12345
    assert device.absinfo(e.ABS_RZ).value==255
    assert device.absinfo(e.ABS_HAT0X).value==1 and device.absinfo(e.ABS_HAT0Y).value==-1
    pad.apply(STATE.pack(1<<3,8,0,0,0,0,0,0))
    time.sleep(.03)
    assert device.active_keys()==[e.BTN_X], 'Phone X must use Xbox BTN_X, not geographic BTN_WEST'
    pad.apply(NEUTRAL)
    # EVIOCSFF holds the Python GIL while waiting for the provider. Use another process,
    # just as an actual game would, so the provider can answer the upload ioctl.
    code='''
import sys,time
from evdev import InputDevice,ecodes as e,ff
d=InputDevice(sys.argv[1])
try:
 effect=ff.Effect(e.FF_RUMBLE,-1,0,ff.Trigger(0,0),ff.Replay(1000,0),ff.EffectType(ff_rumble_effect=ff.Rumble(65535,32768)))
 ident=d.upload_effect(effect)
 d.write(e.EV_FF,ident,1)
 time.sleep(.15)
 effect.id=ident;effect.u.ff_rumble_effect.strong_magnitude=0;effect.u.ff_rumble_effect.weak_magnitude=65535
 d.upload_effect(effect)
 time.sleep(.15)
 d.write(e.EV_FF,ident,0)
 time.sleep(.1)
 d.erase_effect(ident)
finally:d.close()
'''
    proc=subprocess.Popen([sys.executable,'-c',code,device.path])
    seen=False;updated=False
    try:
        deadline=time.monotonic()+10
        while proc.poll() is None and time.monotonic()<deadline:
            pad.poll()
            low,high,duration=pad.rumble()
            seen |= low==255 and high==128 and duration>0
            updated |= low==0 and high==255 and duration>0
            time.sleep(.005)
        assert proc.poll()==0,'Force-feedback subprocess failed or timed out'
        assert seen,'No force-feedback output received'
        assert updated,'Playing effect upload did not update feedback'
    finally:
        if proc.poll() is None:proc.kill();proc.wait()
    assert pad.rumble()==bytes(3)
    pad.apply(NEUTRAL)
    time.sleep(.03)
    assert not device.active_keys()
    assert device.absinfo(e.ABS_X).value==0
    device.close()
    print('Linux integration passed: buttons, sticks, triggers, diagonal hat, rumble upload/play/update/stop/erase, neutral release.')
finally:
    pad.close()
