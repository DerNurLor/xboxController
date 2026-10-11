#!/usr/bin/env python3
"""PhonePad terminal receiver; uses the same service as the Qt desktop app."""
import argparse
import json
import os
from pathlib import Path
import socket
import sys
import threading
import time
from bluetooth_bridge import BluetoothPump  # compatibility import
from configuration import DEFAULTS
from hid_devices import enumerate_phonepad_hid
from service import CompanionService


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--bind',default='0.0.0.0')
    parser.add_argument('--port',type=int,default=39570)
    parser.add_argument('--backend',choices=('linux','windows','dry-run'),default=DEFAULTS['backend'])
    parser.add_argument('--key-file',type=Path,default=Path.home()/'.config/phonepad/pairing.key')
    parser.add_argument('--identity-file',type=Path,default=Path.home()/'.config/phonepad/identity.pem')
    parser.add_argument('--name',default=socket.gethostname())
    parser.add_argument('--no-wifi',action='store_true')
    parser.add_argument('--bluetooth',action='store_true',help='Observe native Bluetooth HID without reinjecting input')
    parser.add_argument('--bluetooth-path',help='Optional exclusive HID bridge; requires native input capture permissions')
    parser.add_argument('--no-discovery',action='store_true')
    parser.add_argument('--show-key',action='store_true')
    parser.add_argument('--list-hid',action='store_true')
    parser.add_argument('--trace-feedback',action='store_true')
    parser.add_argument('--qr-payload-file',type=Path)
    args=parser.parse_args()
    if args.list_hid:
        for device in enumerate_phonepad_hid():print(json.dumps(device))
        return 0
    if args.no_wifi and not (args.bluetooth or args.bluetooth_path):parser.error('Enable Wi-Fi or Bluetooth')
    config=dict(wifi=not args.no_wifi,bluetooth=bool(args.bluetooth or args.bluetooth_path),
        bind=args.bind,port=args.port,backend=args.backend,name=args.name,
        key_file=str(args.key_file),identity_file=str(args.identity_file),bluetooth_path=args.bluetooth_path,
        no_discovery=args.no_discovery,trace_feedback=args.trace_feedback)
    service=CompanionService(config,args.key_file.parent)
    service.command('start')
    def commands():
        for line in sys.stdin:
            command=line.strip().lower()
            if command in ('y','yes','n','no'):service.command('approve',command in ('y','yes'))
            elif command in ('trace on','trace off'):service.command('trace',command.endswith('on'))
            elif command in ('pair','stop','start','scan','clear'):service.command(command)
    threading.Thread(target=commands,daemon=True,name='PhonePad terminal commands').start()
    previous={};last_history=0;shown_key=False
    print('Commands: pair, y/n, trace on/off, stop, start, scan. Ctrl+C exits.',flush=True)
    try:
        while True:
            state=service.snapshot()
            for transport,value in state['receivers'].items():
                if previous.get(transport)!=value:
                    print(f'{transport}: {value}',flush=True);previous[transport]=value
                    if transport=='wifi' and value=='Running':print(f'PhonePad listening on UDP {args.bind}:{args.port}',flush=True)
            for field in ('pairing_message','wifi_error','bluetooth_error'):
                value=state[field]
                if value and previous.get(field)!=value:print(value,flush=True)
                previous[field]=value
            if state['qr']!=previous.get('qr'):
                previous['qr']=state['qr']
                if args.qr_payload_file:
                    if state['qr']:
                        args.qr_payload_file.parent.mkdir(parents=True,exist_ok=True)
                        fd=os.open(args.qr_payload_file,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
                        with os.fdopen(fd,'w') as output:output.write(state['qr'])
                        print('PhonePad QR ready',flush=True)
                    else:args.qr_payload_file.unlink(missing_ok=True)
            if args.show_key and not shown_key and service.protocol:
                print('Pairing key: '+args.key_file.read_text().strip(),flush=True);shown_key=True
            for event in state['history']:
                if event['monotonic']>last_history:print('INPUT '+json.dumps(event),flush=True)
            if state['history']:last_history=state['history'][-1]['monotonic']
            if state['state']=='Failed':return 1
            time.sleep(.05)
    except KeyboardInterrupt:return 0
    finally:
        service.close()
        if args.qr_payload_file:args.qr_payload_file.unlink(missing_ok=True)

if __name__=='__main__':
    try:sys.exit(main())
    except (OSError,ValueError,ImportError,RuntimeError) as error:
        print('PhonePad: '+str(error),file=sys.stderr);sys.exit(1)
