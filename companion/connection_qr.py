"""Connection QR payload shared with the PhonePad Android scanner."""
import json
import socket

PREFIX = 'PPQR1:'


def local_ipv4():
    """Return the IPv4 address selected for the PC's default network route."""
    for destination in ('8.8.8.8', '192.0.2.1'):
        try:
            with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
                probe.connect((destination, 80))
                address = probe.getsockname()[0]
                if address and not address.startswith('127.'):
                    return address
        except OSError:
            pass
    try:
        candidates = sorted({item[4][0] for item in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET)
                             if item[4][0] and not item[4][0].startswith('127.')})
    except OSError:
        candidates = []
    return candidates[0] if candidates else ''


def encode_payload(host, port, public_key, computer_id, name, token, code):
    if not host or not 1 <= int(port) <= 65535:
        raise ValueError('A local address and valid UDP port are required')
    if len(public_key) != 182 or any(char not in '0123456789abcdefABCDEF' for char in public_key):
        raise ValueError('Companion public key must be 91 bytes of hexadecimal data')
    if len(token) != 32 or any(char not in '0123456789abcdefABCDEF' for char in token):
        raise ValueError('QR pairing token must be 16 bytes of hexadecimal data')
    if len(code) != 8 or any(char not in '23456789ABCDEFGHJKLMNPQRSTUVWXYZ' for char in code):
        raise ValueError('Pairing code must be eight unambiguous characters')
    return PREFIX + json.dumps({
        'v': 1, 'host': host, 'port': int(port), 'public': public_key.lower(),
        'id': computer_id, 'name': name, 'token': token.lower(), 'code': code,
    }, separators=(',', ':'), ensure_ascii=True)
