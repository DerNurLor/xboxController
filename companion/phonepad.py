#!/usr/bin/env python3
"""Run on the receiving PC. See README for driver setup and Bluetooth bridge usage."""
import argparse
import os
from pathlib import Path
import secrets
import socket
import sys
import time
from backends import DryRun, LinuxPad, WindowsPad
from protocol import Protocol, NEUTRAL, decode


def bluetooth_bridge(path, backend):
    import hid
    device=hid.device()
    device.open_path(path.encode())
    device.set_nonblocking(True)
    last=time.monotonic()
    neutral=True
    try:
        while True:
            now=time.monotonic()
            data=device.read(64)
            if data and len(data)==14 and data[0]==1:
                body=bytes(data[1:])
                try:decode(body)
                except ValueError:continue
                backend.apply(body);last=now;neutral=body==NEUTRAL
            if now-last>.5 and not neutral:
                backend.apply(NEUTRAL);neutral=True
            backend.poll()
            if now-last<.5:
                device.write(bytes([2])+backend.rumble())
            time.sleep(.016)
    finally:
        try:device.write(bytes([2,0,0,0]))
        finally:device.close()


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind',default='0.0.0.0')
    parser.add_argument('--port',type=int,default=39570)
    parser.add_argument('--backend',choices=('linux','windows','dry-run'),default='windows' if sys.platform=='win32' else 'linux')
    parser.add_argument('--key-file',type=Path,help='Load/create a persistent pairing key; otherwise generate one each run')
    parser.add_argument('--list-hid',action='store_true',help='List gamepad HID paths for an optional Bluetooth-to-XInput bridge')
    parser.add_argument('--bluetooth-path',help='Exact PhonePad HID path from --list-hid; do not select another device')
    args=parser.parse_args()
    if args.list_hid:
        import hid
        for d in hid.enumerate():
            if d.get('usage_page')==1 and d.get('usage') in (4,5):
                print(d.get('product_string'),repr(d['path'].decode()))
        return
    key=secrets.token_bytes(16)
    if args.key_file:
        try:
            with args.key_file.open('x') as f:
                if os.name=='posix':os.fchmod(f.fileno(),0o600)
                f.write(key.hex()+'\n')
        except FileExistsError:
            key=bytes.fromhex(args.key_file.read_text().strip())
    if len(key)!=16:raise ValueError('Pairing key must be 16 bytes')
    backend={'linux':LinuxPad,'windows':WindowsPad,'dry-run':DryRun}[args.backend]()
    try:
        if args.bluetooth_path:
            bluetooth_bridge(args.bluetooth_path,backend)
            return
        protocol=Protocol(key,backend.apply,backend.rumble)
        with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as sock:
            sock.bind((args.bind,args.port));sock.settimeout(.02)
            print(f'PhonePad listening on UDP {args.bind}:{args.port}',flush=True)
            print(f'Pairing key: {key.hex()}',flush=True)
            print('Enter this PC’s LAN IPv4 address in the phone. Ctrl+C stops and releases controls.',flush=True)
            while True:
                backend.poll();protocol.expire(time.monotonic())
                try:raw,address=sock.recvfrom(256)
                except socket.timeout:continue
                reply=protocol.receive(raw,address,time.monotonic())
                if reply:sock.sendto(reply,address)
    finally:
        backend.close()


if __name__=='__main__':
    try:main()
    except KeyboardInterrupt:pass
    except (OSError,ValueError,ImportError,RuntimeError) as error:
        print(f'PhonePad: {error}',file=sys.stderr)
        sys.exit(1)
