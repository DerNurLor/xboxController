"""Find the PhonePad Bluetooth composite HID exposed by the OS."""
from pathlib import Path

PHONEPAD_VENDOR = 0x038F
PHONEPAD_PRODUCT = 0x1200


def enumerate_phonepad_hid(hid_module=None, sysfs_root='/sys/class/hidraw'):
    """Use hidapi normally; on Linux also find the UHID node via sysfs.

    Some hidapi builds omit UHID devices (and hidraw permission problems can
    hide them), even though the kernel has created the PhonePad input device.
    """
    devices = {}
    output_paths = set()
    source_paths = set()
    if hid_module is None:
        try:
            if __import__('sys').platform.startswith('linux'):
                import hidraw as hid_module
            else:
                import hid as hid_module
        except ImportError:
            # Enumeration via sysfs remains useful even without hidapi.
            hid_module = None
    try:
        for item in hid_module.enumerate():
            if item.get('vendor_id') != PHONEPAD_VENDOR or item.get('product_id') != PHONEPAD_PRODUCT:
                continue
            if item.get('usage_page') not in (None, 1) or item.get('usage') not in (None, 2, 4, 5, 6):
                continue
            raw_path = item.get('path', b'')
            path = raw_path.decode(errors='replace') if isinstance(raw_path, bytes) else str(raw_path)
            if path:
                devices[path] = item.get('product_string') or 'PhonePad Bluetooth HID'
    except (AttributeError, OSError):
        pass

    root = Path(sysfs_root)
    if root.exists():
        for entry in root.glob('hidraw*'):
            try:
                values = dict(line.split('=', 1) for line in (entry / 'device/uevent').read_text().splitlines() if '=' in line)
                bus, vendor, product = (int(part, 16) for part in values['HID_ID'].split(':'))
            except (OSError, KeyError, ValueError):
                continue
            path = '/dev/' + entry.name
            if bus == 5 and vendor == PHONEPAD_VENDOR and product == PHONEPAD_PRODUCT:
                devices.setdefault(path, values.get('HID_NAME') or 'PhonePad Bluetooth HID')
                try:
                    descriptor = (entry / 'device/report_descriptor').read_bytes()
                    if bytes.fromhex("85 05 15 00 26 ff 00 75 08 95 0d 09 02 81 02") in descriptor:source_paths.add(path)
                    if bytes.fromhex('85 02 06 00 ff 09 01 15 00 26 ff 00 75 08 95 03 91 02') in descriptor:
                        output_paths.add(path)
                except OSError:
                    pass
    serials = {}
    try:
        for item in hid_module.enumerate():
            raw_path = item.get('path', b'')
            candidate = raw_path.decode(errors='replace') if isinstance(raw_path, bytes) else str(raw_path)
            if candidate in devices and item.get('serial_number'):
                serials[candidate] = item['serial_number']
    except (AttributeError, OSError):
        pass
    return [{'path': path, 'product': product, 'serial_number': serials.get(path, ''), 'source_input': path in source_paths, 'feedback_output': path in output_paths, 'vendor_id': PHONEPAD_VENDOR,
             'product_id': PHONEPAD_PRODUCT, 'usage_page': 1, 'usage': 5}
            for path, product in sorted(devices.items())]


def enumerate_phonepad_inputs(sysfs_root='/sys/class/input'):
    """Find kernel HID input interfaces, excluding AVRCP and uinput lookalikes.

    Android's AVRCP device can reuse the phone's HID vendor/product IDs.
    A real HID ancestor is therefore required; the input IDs alone are not proof.
    This works without permission to open the event node or hidraw interface.
    """
    result = []
    for entry in Path(sysfs_root).glob('event*'):
        try:
            device = (entry / 'device').resolve()
            identity = tuple(int((device / 'id' / field).read_text().strip(), 16)
                             for field in ('bustype', 'vendor', 'product'))
            if identity != (5, PHONEPAD_VENDOR, PHONEPAD_PRODUCT):
                continue
            for parent in device.parents:
                try:
                    values = dict(line.split('=', 1) for line in
                                  (parent / 'uevent').read_text().splitlines() if '=' in line)
                    hid_id = tuple(int(part, 16) for part in values.get('HID_ID', '').split(':'))
                except (OSError, ValueError):
                    continue
                if hid_id == identity:
                    result.append({'path': '/dev/input/' + entry.name,
                                   'name': (device / 'name').read_text().strip(),
                                   'serial_number': values.get('HID_UNIQ', '')})
                    break
        except (OSError, ValueError):
            continue
    return result
