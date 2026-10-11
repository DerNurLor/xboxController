"""Read BlueZ state independently from HID enumeration; never infer power from HID."""
import re
import subprocess
from hid_devices import enumerate_phonepad_hid, enumerate_phonepad_inputs


def inspect_bluetooth(run=subprocess.run):
    result = {'powered': None, 'adapter': '', 'devices': [], 'hid': [], 'input_devices': [], 'error': ''}
    def command(*args):
        p = run(['bluetoothctl', *args], capture_output=True, text=True, timeout=4)
        if p.returncode:
            raise RuntimeError(p.stderr.strip() or p.stdout.strip() or 'BlueZ query failed')
        return re.sub(r'\x1b\[[0-9;]*m', '', p.stdout)
    try:
        info = command('show')
        result['adapter'] = info.strip()
        match = re.search(r'Powered:\s*(yes|no)', info)
        if match: result['powered'] = match[1] == 'yes'
        for address, name in re.findall(r'^Device ([0-9A-Fa-f:]{17}) (.+)$', command('devices'), re.M):
            details = command('info', address)
            result['devices'].append(dict(address=address, name=name,
                paired=bool(re.search(r'(?:Paired|Bonded): yes', details)),
                connected='Connected: yes' in details,
                hid_capable='00001124-' in details or '00001812-' in details,
                services_resolved='ServicesResolved: yes' in details))
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        result['error'] = str(error)
    try:
        result['hid'] = enumerate_phonepad_hid()
    except (OSError, ImportError) as error:
        result['error'] += ' HID enumeration: ' + str(error)
    result['input_devices'] = enumerate_phonepad_inputs()
    native_addresses = {item.get('serial_number', '').upper() for item in result['hid'] + result['input_devices']}
    for device in result['devices']:
        device['input_created'] = device['address'].upper() in native_addresses
    return result


def reconnect(address):
    if not re.fullmatch(r'[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}', address):
        raise ValueError('Select a paired Bluetooth address')
    p = subprocess.run(['bluetoothctl', 'connect', address], capture_output=True, text=True, timeout=12)
    if p.returncode: raise RuntimeError(p.stderr.strip() or p.stdout.strip())
    return p.stdout.strip()


def pair(address, run=subprocess.run):
    if not re.fullmatch(r'[0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){5}', address):
        raise ValueError('Select a Bluetooth device from the scan results')
    p = run(['bluetoothctl', 'pair', address], capture_output=True, text=True, timeout=25)
    if p.returncode: raise RuntimeError(p.stderr.strip() or p.stdout.strip() or 'Bluetooth pairing failed')
    return p.stdout.strip()


def scan():
    p = subprocess.run(['bluetoothctl', '--timeout', '5', 'scan', 'on'], capture_output=True, text=True, timeout=8)
    if p.returncode: raise RuntimeError(p.stderr.strip() or p.stdout.strip())
    return inspect_bluetooth()
