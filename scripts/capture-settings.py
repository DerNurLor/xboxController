#!/usr/bin/env python3
"""Capture actual PhonePad settings screens over ADB without changing saved tuning.

Start PhonePad in Setup first. The final action returns to Overview; no host is
connected by this script. Bluetooth permission/enabled state is required for the picker.
"""
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

out = Path(__file__).resolve().parents[1] / 'build/ux-review'
out.mkdir(parents=True, exist_ok=True)


def adb(*args):
    return subprocess.check_output(['adb', *args])


def choose(label):
    adb('shell', 'uiautomator', 'dump', '/sdcard/phonepad-ui.xml')
    root = ET.fromstring(adb('shell', 'cat', '/sdcard/phonepad-ui.xml'))
    candidates = [n for n in root.iter('node')
                  if n.get('package') == 'dev.phonepad'
                  and n.get('clickable') == 'true'
                  and n.get('text', '').lstrip('⌂⊕▷⌁◐☷≡ ').startswith(label)]
    if not candidates:
        raise RuntimeError(f'PhonePad action unavailable: {label}')
    x1, y1, x2, y2 = map(int, re.findall(r'\d+', candidates[0].get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.3)


def capture(name):
    (out / f'live-{name}.png').write_bytes(adb('exec-out', 'screencap', '-p'))


def main():
    time.sleep(1)
    capture('setup')
    for name, route in [
        ('overview', ['Overview']),
        ('controller', ['Controller']),
        ('sticks', ['Sticks']),
        ('triggers', ['Controller', 'Triggers & racing', 'Independent LT / RT']),
        ('fps', ['Play experience', 'Camera & two-thumb play']),
        ('haptics', ['Play experience', 'Haptics']),
        ('game-mode-settings', ['Play experience', 'Game mode']),
        ('appearance', ['Appearance']),
        ('connection', ['Connection']),
        ('device-picker', ['Choose a computer']),
        ('advanced', ['Advanced']),
        ('diagnostics', ['Diagnostics']),
        ('return-to-overview', ['‹ Setup']),
    ]:
        for label in route:
            choose(label)
        capture(name)
    print(f'Captured foreground phone screens in {out}')


if __name__ == "__main__":
    main()
