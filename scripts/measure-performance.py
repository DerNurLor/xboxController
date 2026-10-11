#!/usr/bin/env python3
"""Measure idle Android frames without injecting gameplay or changing saved layouts."""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time

parser = argparse.ArgumentParser()
parser.add_argument('--seconds', type=int, default=12, choices=range(5, 31))
parser.add_argument('--output', default='build/performance-idle.json')
args = parser.parse_args()


def adb(*command):
    return subprocess.check_output(['adb', *command], text=True, timeout=20)


adb('shell', 'am', 'start', '-n', 'dev.phonepad/.MainActivity')
time.sleep(2)
adb('shell', 'dumpsys', 'gfxinfo', 'dev.phonepad', 'reset')
start = time.monotonic()
time.sleep(args.seconds)
raw = adb('shell', 'dumpsys', 'gfxinfo', 'dev.phonepad')
elapsed = time.monotonic() - start
if not re.search(r'dev\.phonepad/dev\.phonepad\.MainActivity/.*visibility=0', raw):
    raise RuntimeError('PhonePad has no visible activity; discard this sample')
match = re.search(r'Total frames rendered:\s*(\d+)', raw)
if not match:
    raise RuntimeError('Android did not return frame statistics')
result = {'seconds': round(elapsed, 2), 'frames': int(match[1]),
          'frames_per_second': round(int(match[1]) / elapsed, 2),
          'note': 'Idle rendering only; not touch-to-host latency.'}
destination = Path(args.output)
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_text(json.dumps(result, indent=2) + '\n')
destination.with_suffix('.txt').write_text(raw)
print(json.dumps(result, indent=2))
