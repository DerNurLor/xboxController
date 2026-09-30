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
    pad.apply(STATE.pack((1<<0)|(1<<4)|(1<<13),1,-12345,23456,8000,-9000,127,255))
    time.sleep(.05)
    assert e.BTN_SOUTH in device.active_keys() and e.BTN_NORTH in device.active_keys()
    assert e.BTN_THUMBL in device.active_keys()
    assert device.absinfo(e.ABS_X).value==-12345
    assert device.absinfo(e.ABS_RZ).value==255
    assert device.absinfo(e.ABS_HAT0X).value==1 and device.absinfo(e.ABS_HAT0Y).value==-1
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
 time.sleep(.25)
 d.write(e.EV_FF,ident,0)
 time.sleep(.1)
 d.erase_effect(ident)
finally:d.close()
'''
    proc=subprocess.Popen([sys.executable,'-c',code,device.path])
    seen=False
    try:
        deadline=time.monotonic()+10
        while proc.poll() is None and time.monotonic()<deadline:
            pad.poll()
            low,high,duration=pad.rumble()
            seen |= low==255 and high==128 and duration>0
            time.sleep(.005)
        assert proc.poll()==0,'Force-feedback subprocess failed or timed out'
        assert seen,'No force-feedback output received'
    finally:
        if proc.poll() is None:proc.kill();proc.wait()
    assert pad.rumble()==bytes(3)
    pad.apply(NEUTRAL)
    time.sleep(.03)
    assert not device.active_keys()
    assert device.absinfo(e.ABS_X).value==0
    device.close()
    print('Linux integration passed: buttons, sticks, triggers, diagonal hat, rumble upload/play/stop/erase, neutral release.')
finally:
    pad.close()
