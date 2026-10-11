#!/usr/bin/env python3
"""Small PhonePad-only UI helper for reproducible connection checks over ADB."""
import argparse
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

def adb(*args):return subprocess.check_output(['adb',*args],text=True)
def tree():
    adb('shell','uiautomator','dump','/sdcard/phonepad-ui.xml')
    t=ET.fromstring(adb('shell','cat','/sdcard/phonepad-ui.xml'))
    if not any(n.get('package')=='dev.phonepad' for n in t.iter('node')):raise RuntimeError('PhonePad is not foreground')
    return t
def bounds(n):return list(map(int,re.findall(r'\d+',n.get('bounds'))))
def tap(n):
    a,b,c,d=bounds(n);adb('shell','input','tap',str((a+c)//2),str((b+d)//2));time.sleep(.4)
def choose(text):
    node=next(n for n in tree().iter('node') if text in n.get('text',''))
    tap(node)
def open_connect():
    hierarchy=tree()
    node=next((n for n in hierarchy.iter('node') if n.get('text','').lstrip('⌁ ').startswith('Connection')),None)
    if node is not None:
        tap(node)
        return
    # Back deliberately pauses game mode; it never taps a hidden gameplay hitbox.
    adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.4)
    if any(n.get('text')=='Exit game mode' for n in tree().iter('node')):choose('Exit game mode')
    choose('Connection')
def field(index,value):
    edits=[n for n in tree().iter('node') if n.get('class')=='android.widget.EditText']
    n=edits[index];tap(n)
    adb('shell','input','keyevent','KEYCODE_MOVE_END')
    # Delete the bounded, known host/port text. These fields contain no user documents.
    adb('shell','input','keyevent',*(['KEYCODE_DEL']*64))
    adb('shell','input','text',value)
    adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.3)

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('mode',choices=('bluetooth','wifi'))
p.add_argument('--host',default='cachyos-x8664')
p.add_argument('--port',default='39570')
p.add_argument('--key-file',type=Path)
args=p.parse_args()
open_connect()
if args.mode=='bluetooth':
    choose('Choose Bluetooth computer');choose(args.host)
else:
    key=args.key_file.read_text().strip()
    if not re.fullmatch(r'[0-9a-fA-F]{32}',key) or not re.fullmatch(r'[0-9.]+',args.host) or not args.port.isdigit():raise ValueError('Invalid connection parameters')
    choose('Find my computer')
    choose('Manual connection')
    field(0,args.host);field(1,args.port);field(2,key)
    button=next(n for n in tree().iter('node') if n.get('text')=='Connect' or n.get('resource-id')=='android:id/button1')
    tap(button)
for node in tree().iter('node'):
    if node.get('text')=='‹ Setup':tap(node);choose('Start game mode');break
print('PhonePad connection selected:',args.mode)
