#!/usr/bin/env python3
"""Verify authenticated loopback UDP at kernel mouse/keyboard devices.

Exclusively captures this test's devices before sending any nonneutral input.
Never changes permissions. Does not disturb an existing Companion instance.
"""
import secrets
import select
import socket
import struct
import sys
import tempfile
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'companion'))
from evdev import ecodes as e
from linux_input_diagnostics import CapturedInputs
from protocol import packet, unpack, sign, STATE
from service import CompanionService


def main():
    with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as reservation:
        reservation.bind(('127.0.0.1', 0))
        port = reservation.getsockname()[1]
    with tempfile.TemporaryDirectory() as root:
        service = CompanionService(dict(backend='linux', bind='127.0.0.1',
                                       port=port, no_discovery=True), root)
        captured = None
        try:
            service.command('start')
            deadline = time.monotonic() + 10
            while service.snapshot().get('state') != 'Running' and time.monotonic() < deadline:
                time.sleep(.05)
            snapshot = service.snapshot()
            if snapshot.get('state') != 'Running':
                raise RuntimeError('Receiver startup failed: ' + str(snapshot.get('errors')))
            devices = list(service.device_info)
            if {d['role'] for d in devices} != {'keyboard', 'mouse', 'controller'}:
                raise RuntimeError('All three virtual devices must be independently observed')
            captured = CapturedInputs(devices)
            records = []
            def collect(duration):
                end = time.monotonic() + duration
                while time.monotonic() < end:
                    ready, _, _ = select.select(captured.devices, [], [], max(0, end - time.monotonic()))
                    for device in ready:
                        records.extend((device.name, event.type, event.code, event.value) for event in device.read())
            collect(.02)
            records.clear()
            key = bytes.fromhex((Path(root) / 'pairing.key').read_text().strip())
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as client:
                client.settimeout(2)
                client.connect(('127.0.0.1', port))
                for cycle in range(2):
                    identity = secrets.token_bytes(16)
                    client.send(packet(1, identity, 0, b'', key))
                    hello = unpack(client.recv(4096), key, 2, 16)
                    assert hello is not None and hello[0] == identity, 'Invalid handshake'
                    nonce = hello[2]
                    session_key = sign(key, identity + nonce)
                    body = bytearray(39)
                    for code in (4, 224):
                        body[code // 8] |= 1 << (code % 8)
                    body[32] = 31
                    struct.pack_into('!hhh', body, 33, 17, -19, 2)
                    for seq in (1, 1, 2):
                        client.send(packet(5, nonce, seq, bytes(body), session_key))
                        ack = client.recv(4096)
                        decoded = unpack(ack, session_key, 6, len(ack) - 61)
                        assert decoded is not None and decoded[:2] == (nonce, seq), 'Invalid desktop ACK'
                        client.send(packet(3, nonce, seq, STATE.pack(1 << 1, 1, 12000, -15000, 6000, -8000, 128, 255), session_key))
                        pad_ack = unpack(client.recv(4096), session_key, 4, 3)
                        assert pad_ack is not None and pad_ack[:2] == (nonce, seq), 'Invalid controller ACK'
                        collect(.025)
                    # A lost connection must release held input. New HELLO tests reconnection.
                    collect(.7)
            for name, codes in [('PhonePad Keyboard', (e.KEY_A, e.KEY_LEFTCTRL)),
                                ('PhonePad Mouse', (e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE, e.BTN_SIDE, e.BTN_EXTRA))]:
                for code in codes:
                    values = [v for n, t, c, v in records if n == name and t == e.EV_KEY and c == code]
                    assert values == [1, 0, 1, 0], (name, code, values)
                assert any(n == name and t == e.EV_SYN and c == e.SYN_REPORT for n, t, c, v in records), name
            for code, value in ((e.REL_X, 17), (e.REL_Y, -19), (e.REL_WHEEL, 2)):
                values = [v for n, t, c, v in records if n == 'PhonePad Mouse' and t == e.EV_REL and c == code]
                assert values == [value] * 4, ('Duplicate replay or lost movement', code, values)
            controller = next(d for d in devices if d['role'] == 'controller')['name']
            values = [v for n,t,c,v in records if n == controller and t == e.EV_KEY and c == e.BTN_B]
            assert values == [1,0,1,0], ('Controller release/reconnect', values)
            for code,value in ((e.ABS_X,12000),(e.ABS_Y,-15000),(e.ABS_RX,6000),(e.ABS_RY,-8000),(e.ABS_Z,128),(e.ABS_RZ,255),(e.ABS_HAT0X,1),(e.ABS_HAT0Y,-1)):
                values=[v for n,t,c,v in records if n == controller and t == e.EV_ABS and c == code]
                assert values == [value,0,value,0], ('Controller axis/release',code,values)
            print('PASS: authenticated UDP reached kernel controller/mouse/keyboard; sticks, triggers, D-pad, five mouse buttons, Ctrl+A, scrolling, simultaneous movement, duplicate suppression, disconnect release and reconnect.')
        finally:
            # Release and destroy our virtual inputs while their event nodes remain grabbed.
            try:
                service.close()
            finally:
                if captured:
                    captured.close()


if __name__ == '__main__':
    try:
        main()
    except (RuntimeError, OSError, AssertionError) as error:
        print('CHECK FAILED:', error, file=sys.stderr)
        sys.exit(1)
