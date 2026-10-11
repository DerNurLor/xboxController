#!/usr/bin/env python3
"""Verify real Android HID mouse/keyboard reports at Linux evdev.

Install the current app and scripts/build-device-tests.py test APK first.
Requires an already paired phone, adb, and access to its input event nodes.
The host grabs every PhonePad input interface before allowing test reports.
No uinput receiver, saved layouts, or pairing settings are changed.
"""
import argparse
import select
import socket
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from evdev import InputDevice, ecodes as e

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from service import CompanionService
from hid_devices import enumerate_phonepad_inputs


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--host-name', default=socket.gethostname())
    args = parser.parse_args()
    devices = {}
    observed = set()
    signaled = False
    android = None
    with tempfile.TemporaryDirectory() as root, tempfile.TemporaryFile(mode='w+') as output:
        service = CompanionService(dict(wifi=False, bluetooth=True, backend='dry-run'), root)
        service.command('trace', True)
        service.command('start')
        try:
            subprocess.run(['adb', 'shell', 'run-as', 'dev.phonepad', 'rm', '-f',
                            'cache/native-desktop-ready','cache/native-desktop-waiting'], check=True)
            android = subprocess.Popen([
                'adb', 'shell', 'am', 'instrument', '-w', '-e', 'phase', 'native-desktop',
                '-e', 'bluetoothHost', args.host_name,
                'dev.phonepad.tests/dev.phonepad.DeviceTests'],
                stdout=output, stderr=subprocess.STDOUT, text=True)
            started = time.monotonic()
            deadline = started + 35
            foreground = False
            waiting = False
            while android.poll() is None and time.monotonic() < deadline:
                if not foreground and time.monotonic() - started > 1:
                    subprocess.run(['adb', 'shell', 'am', 'start', '-n', 'dev.phonepad/.MainActivity'],
                                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
                    foreground = True
                if not waiting:
                    waiting = subprocess.run(['adb','shell','run-as','dev.phonepad','test','-f',
                                              'cache/native-desktop-waiting'],stdout=subprocess.DEVNULL,
                                              stderr=subprocess.DEVNULL).returncode == 0
                if waiting and not signaled:
                    for native in enumerate_phonepad_inputs():
                        path = native["path"]
                        if path in devices:
                            continue
                        try:
                            device = InputDevice(path)
                        except OSError:
                            continue
                        if (device.info.bustype, device.info.vendor, device.info.product) != (5, 0x038f, 0x1200):
                            device.close()
                            continue
                        try:
                            device.grab()
                        except Exception:
                            device.close()
                            raise
                        devices[path] = device
                    identities = {device.uniq for device in devices.values()}
                    if len(identities) > 1:
                        raise RuntimeError('Multiple PhonePad phones detected; disconnect the other phone before this check.')
                    caps = {}
                    for device in devices.values():
                        for kind, codes in device.capabilities().items():
                            caps.setdefault(kind, set()).update(codes)
                    mouse_keys = {e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE, e.BTN_SIDE, e.BTN_EXTRA}
                    if ({e.REL_X, e.REL_Y, e.REL_WHEEL} <= caps.get(e.EV_REL, set())
                            and mouse_keys | {e.KEY_A, e.KEY_LEFTCTRL} <= caps.get(e.EV_KEY, set())
                            and (service.snapshot()['native_observing'] or time.monotonic()-started > 12)):
                        print('Grabbed native mouse and keyboard interfaces:', ', '.join(devices), flush=True)
                        from hid_devices import enumerate_phonepad_hid
                        print('Source metadata capability:', [(d['path'], d['source_input']) for d in enumerate_phonepad_hid()], flush=True)
                        subprocess.run(['adb', 'shell', 'run-as', 'dev.phonepad', 'touch',
                                        'cache/native-desktop-ready'], check=True)
                        signaled = True
                if devices:
                    readable, _, _ = select.select(list(devices.values()), [], [], .05)
                    for device in readable:
                        try:
                            observed.update((event.type, event.code, event.value) for event in device.read())
                        except OSError:
                            # Android unregisters HID as instrumentation exits.
                            device.close()
                            devices.pop(device.path, None)
                else:
                    time.sleep(.05)
            if android.poll() is None:
                raise RuntimeError('Android HID verification timed out.')
            output.seek(0)
            result = output.read()
            print(result)
            required = {(e.EV_REL, e.REL_X, 17), (e.EV_REL, e.REL_Y, -19),
                        (e.EV_REL, e.REL_WHEEL, 2), (e.EV_SYN, e.SYN_REPORT, 0)}
            for code in (e.KEY_A, e.KEY_LEFTCTRL, e.BTN_LEFT, e.BTN_RIGHT,
                         e.BTN_MIDDLE, e.BTN_SIDE, e.BTN_EXTRA):
                required.update({(e.EV_KEY, code, 1), (e.EV_KEY, code, 0)})
            missing = required - observed
            if 'PASS:' not in result or missing:
                raise RuntimeError('Native HID verification failed; missing evdev events: ' + repr(sorted(missing)))
            snapshot = service.snapshot()
            print('Source monitor:',snapshot.get('source_reports'),snapshot.get('source_errors'), flush=True)
            sources = [event['state'] for event in snapshot['history'] if event['kind']=='controller_source']
            if not any(state[0]&2 and state[6:]==[255,255] for state in sources):
                raise RuntimeError('Mapped B/LT/RT source metadata not observed; reconnect HID to refresh its descriptor.')
            if (e.EV_KEY,e.BTN_EAST,1) in observed:
                raise RuntimeError('Source-only B press was incorrectly injected into Linux.')
            print('PASS: Companion observed original mapped B/LT/RT without injecting a gamepad press.')
            print('Companion native monitor observed updates:', snapshot['event_count'])
            print('PASS: kernel evdev observed X/Y, wheel, five mouse buttons, A + Ctrl, releases, and SYN_REPORT.')
            print('Native Android HID bypassed Companion injection. No game-consumption claim.')
        finally:
            if android is not None and android.poll() is None:
                subprocess.run(['adb', 'shell', 'am', 'force-stop', 'dev.phonepad'], check=False)
                android.terminate()
                android.wait(timeout=5)
            for device in devices.values():
                try:
                    device.ungrab()
                except OSError:
                    pass
                device.close()
            service.close()
            subprocess.run(['adb', 'shell', 'run-as', 'dev.phonepad', 'rm', '-f',
                            'cache/native-desktop-ready','cache/native-desktop-waiting'], check=False)


if __name__ == '__main__':
    main()
