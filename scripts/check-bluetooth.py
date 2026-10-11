#!/usr/bin/env python3
"""Read only PhonePad's HID device; inject bounded gestures using exported view geometry.

Run DeviceTests first to export live-layout.json from the user's restored view.
PhonePad must be foreground, connected to this host, with that layout unchanged.
"""
import json
from pathlib import Path
import re
import select
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from evdev import InputDevice, list_devices, ecodes as e


def adb(*args):
    return subprocess.check_output(['adb', *args], text=True, timeout=15)


def main():
    geometry = json.loads(adb('exec-out', 'run-as', 'dev.phonepad', 'cat', 'cache/live-layout.json'))
    config = geometry['config']
    prefs = ET.fromstring(adb('exec-out', 'run-as', 'dev.phonepad', 'cat', 'shared_prefs/pad.xml'))
    strings = {n.attrib['name']: n.text for n in prefs.findall('string')}
    active = strings.get('active.v4', strings.get('active.v2', 'Default'))
    saved = json.loads(strings.get('profiles.v4', strings.get('profiles.v2', '{}'))).get(active)
    if saved is not None:
        assert active == geometry['activeLayout'] and saved == geometry.get('storedLayout'), 'Layout changed since instrumentation; run DeviceTests again'
    else:
        assert active == config['base'], 'Active preset changed'
    adb('shell', 'uiautomator', 'dump', '/sdcard/phonepad-ui.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/phonepad-ui.xml'))
    assert int(tree.get('rotation', '-1')) == geometry.get('rotation'), 'Orientation changed; rerun instrumentation'
    node = next((n for n in tree.iter('node') if n.attrib.get('content-desc', '').startswith('PhonePad touch gamepad')), None)
    if node is None:
        raise RuntimeError('PhonePad is not the foreground view; no gestures were sent')
    x0, y0, x1, y1 = map(int, re.findall(r'\d+', node.attrib['bounds']))
    assert x1-x0 == geometry['viewWidth'] and y1-y0 == geometry['viewHeight'], 'View size changed; rerun instrumentation'
    controls = config['controls']
    w, h = geometry['width'], geometry['height']

    def point(c):
        return x0+geometry['left']+c['x']*w, y0+geometry['top']+c['y']*h

    def xy(point):
        return [str(round(v)) for v in point]

    def foreground():
        windows = adb('shell', 'dumpsys', 'window')
        focus = re.search(r'mCurrentFocus=.*', windows)
        if focus is None or 'dev.phonepad/' not in focus.group():
            raise RuntimeError('PhonePad lost focus; stopping before the next gesture')

    def available(c):
        return c['visible'] and not (c['kind'] == 'trigger' and config['integrated'] and config['hideTriggers'])

    phone = None
    for path in list_devices():
        device = InputDevice(path)
        if device.name in ('Redmi Note 12 Pro+ 5G', 'PhonePad', 'PhonePad Bluetooth Gamepad') and e.EV_ABS in device.capabilities():
            phone = device
            break
        device.close()
    if phone is None:
        raise RuntimeError('No connected PhonePad HID device; connect Bluetooth first')
    events, results = [], {}
    stop = threading.Event()

    def read():
        while not stop.is_set():
            if select.select([phone.fd], [], [], .05)[0]:
                events.extend((v.type, v.code, v.value) for v in phone.read())

    thread = threading.Thread(target=read)
    thread.start()
    try:
        for name, bit, code in [('A', 0, e.BTN_SOUTH), ('B', 1, e.BTN_EAST), ('X', 3, e.BTN_NORTH), ('Y', 4, e.BTN_WEST), ('LB', 6, e.BTN_TL), ('RB', 7, e.BTN_TR)]:
            c = next((c for c in controls if available(c) and c['kind'] == 'button' and c['bit'] == bit
                      and c.get('gesture', 'HOLD') == 'HOLD' and c.get('action', 'BUTTONS') == 'BUTTONS'
                      and c.get('buttons', -1) in (-1, 1 << bit) and not c.get('outputLT') and not c.get('outputRT')), None)
            if c is None:
                continue
            start = len(events)
            foreground()
            # ADB's instantaneous tap can begin and end between 16 ms HID reports.
            # Use a bounded stationary press to check host delivery and release.
            position = xy(point(c))
            adb('shell', 'input', 'swipe', *position, *position, '150')
            time.sleep(.12)
            values = [v for typ, key, v in events[start:] if typ == e.EV_KEY and key == code]
            assert 1 in values and values[-1] == 0, (name, code, values)
            results[name] = 'press and neutral release received'
        for name, kind, bit, code in [('LS', 'stick', 0, e.ABS_X), ('RS', 'stick', 1, e.ABS_RX), ('LT', 'trigger', 0, e.ABS_Z), ('RT', 'trigger', 1, e.ABS_RZ)]:
            c = next((c for c in controls if available(c) and c['kind'] == kind and c['bit'] == bit), None)
            if c is None:
                continue
            start_point = point(c)
            mode = c.get('mode') or (config['triggerMode'] if c['kind'] == 'trigger' else config['leftMode'] if name == 'LS' else config['rightMode'])
            if mode == 'TOUCHPAD':
                continue  # Velocity testing needs timed native MotionEvents, covered by InputTest.
            end = list(start_point)
            if c['kind'] == 'stick':
                end[0] += min(c['w']*w, c['h']*h)/2
                gain = c.get('sensitivity', -1)
                if gain < 0:
                    gain = config['sensitivity']
                expected = round(30000 * min(gain, 1))
            elif mode != 'DIGITAL':
                sign = -1 if c.get('reverse') else 1
                if mode == 'VERTICAL':
                    end[1] += config['travel']*h*sign
                else:
                    end[0] += config['travel']*w*sign*(1 if name == 'LT' else -1)
                expected = 250
            else:
                expected = 250
            assert 0 <= end[0] < geometry['viewWidth'] and 0 <= end[1] < geometry['viewHeight'], (name, 'travel off screen')
            start = len(events)
            foreground()
            adb('shell', 'input', 'swipe', *xy(start_point), *xy(end), '350')
            time.sleep(.15)
            values = [v for typ, key, v in events[start:] if typ == e.EV_ABS and key == code]
            assert values and max(abs(v) for v in values) >= expected and abs(values[-1]) <= phone.absinfo(code).fuzz, (name, values)
            results[name] = 'range and neutral release received'
        assert len(results) >= 6, 'Too few supported controls for meaningful verification'
        assert not phone.active_keys(), 'Host still reports held buttons'
        neutral = {}
        for axis in (e.ABS_X, e.ABS_Y, e.ABS_RX, e.ABS_RY, e.ABS_Z, e.ABS_RZ, e.ABS_HAT0X, e.ABS_HAT0Y):
            info = phone.absinfo(axis)
            # Linux input defuzzing can retain a small previous value after an
            # exact zero HID report. Respect only declared fuzz, not deadzone/flat.
            assert abs(info.value) <= info.fuzz, ('Host axis is not neutral', axis, info)
            neutral[str(axis)] = {'value': info.value, 'fuzz': info.fuzz}
        result = {'device': phone.path, 'id': list(phone.info), 'layout': config['name'], 'checks': results, 'neutralAxes': neutral}
        destination = Path(__file__).resolve().parents[1]/'build/bluetooth-check.json'
        destination.write_text(json.dumps(result, indent=2)+'\n')
        print(json.dumps(result, indent=2))
    finally:
        stop.set()
        thread.join(timeout=1)
        phone.close()


if __name__ == '__main__':
    main()
