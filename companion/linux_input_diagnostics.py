"""Read-only Linux input readiness checks; never change permissions or inject input."""
import os
import errno
import shlex
import struct
import sys
from pathlib import Path


def recovery_command():
    return 'sudo ' + shlex.quote(str(Path(__file__).resolve().with_name('install-linux-input-rule.sh')))


def permission_snapshot(device_root='/dev', module_root='/sys/module'):
    nodes = []
    for name in ('uinput', 'uhid'):
        path = Path(device_root) / name
        exists = path.exists()
        nodes.append(dict(name=name, path=str(path), exists=exists,
                          readable=exists and os.access(path, os.R_OK),
                          writable=exists and os.access(path, os.W_OK),
                          module_present=(Path(module_root) / name).exists(),
                          required=name == 'uinput'))
    return dict(supported=sys.platform.startswith('linux'), nodes=nodes,
                recovery_command=recovery_command(),
                note='Native Bluetooth HID is managed by BlueZ and the kernel. Companion does not need direct /dev/uhid access.')


def _bitmap(text):
    bits = struct.calcsize('L') * 8
    value = sum(int(word, 16) << (index * bits) for index, word in enumerate(reversed(text.split())))
    return [index for index in range(value.bit_length()) if value & (1 << index)]


def required_capabilities(role):
    from evdev import ecodes as e
    from desktop import KEY_NAMES
    if role == 'keyboard':
        return {e.EV_KEY: {getattr(e, 'KEY_' + name) for name in KEY_NAMES.values()}}
    if role == 'mouse':
        return {e.EV_KEY: {e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE, e.BTN_SIDE, e.BTN_EXTRA},
                e.EV_REL: {e.REL_X, e.REL_Y, e.REL_WHEEL}}
    if role != 'controller':
        raise ValueError('Unknown input role: ' + role)
    return {e.EV_KEY: {e.BTN_A, e.BTN_B, e.BTN_X, e.BTN_Y, e.BTN_TL, e.BTN_TR,
                      e.BTN_SELECT, e.BTN_START, e.BTN_MODE, e.BTN_THUMBL, e.BTN_THUMBR, e.KEY_RECORD},
            e.EV_ABS: {e.ABS_X, e.ABS_Y, e.ABS_RX, e.ABS_RY, e.ABS_Z, e.ABS_RZ, e.ABS_HAT0X, e.ABS_HAT0Y},
            e.EV_FF: {e.FF_RUMBLE}}


def describe_device(ui, role, sysfs_root='/sys/class/input', device_root='/dev/input'):
    """Locate only this UInput instance, even when other Companions are running.

    Unique phys identities avoid relying on reused event numbers or device names.
    Sysfs capability checks remain available when the event node is unreadable.
    """
    from evdev import InputDevice, ecodes as e
    result = dict(role=role, name=ui.name, path='', observed=False, readable=False,
                  status='Not observed', ready=False, permission_denied=False, capabilities={}, missing=[], error='')
    physical = getattr(ui, 'phys', '')
    if not physical.startswith('phonepad/'):
        result['error'] = 'Device has no instance identity; restart Companion to refresh diagnostics.'
        return result
    for entry in Path(sysfs_root).glob('event*'):
        device = entry / 'device'
        try:
            if (device / 'phys').read_text().strip() != physical:
                continue
            if (device / 'name').read_text().strip() != ui.name:
                continue
            result.update(path=str(Path(device_root) / entry.name), observed=True)
            caps = {}
            events = _bitmap((device / 'capabilities/ev').read_text())
            for kind, name in ((e.EV_KEY, 'key'), (e.EV_REL, 'rel'), (e.EV_ABS, 'abs'), (e.EV_FF, 'ff')):
                if kind in events:
                    caps[kind] = _bitmap((device / 'capabilities' / name).read_text())
            if e.EV_SYN not in events:
                result['missing'].append('EV_SYN')
            result['capabilities'] = {e.EV[kind]: codes for kind, codes in caps.items()}
            for kind, required in required_capabilities(role).items():
                for code in sorted(required - set(caps.get(kind, []))):
                    result['missing'].append(e.EV[kind] + ':' + str(code))
            try:
                probe = InputDevice(result['path'])
                probe.close()
                result['readable'] = True
            except OSError as error:
                result['error'] = str(error)
                result['permission_denied'] = error.errno in (errno.EACCES, errno.EPERM)
            result['ready'] = result['readable'] and not result['missing']
            result['status'] = ('Capabilities missing' if result['missing'] else
                                'Ready' if result['readable'] else 'Permission required' if result['permission_denied'] else 'Event node unavailable')
            return result
        except (OSError, ValueError) as error:
            if result['observed']:
                result['error'] = str(error)
                result['status'] = 'Inspection failed'
                return result
    return result


class CapturedInputs:
    """Grab every requested test device before the caller may send any input."""
    def __init__(self, devices, factory=None):
        if factory is None:
            from evdev import InputDevice
            factory = InputDevice
        self.devices = []
        if not devices:
            raise ValueError('No test devices to capture')
        try:
            for details in devices:
                if not details.get('ready'):
                    raise RuntimeError(details['name'] + ': ' + details['status'] +
                                       '. Install the active-session rule yourself: ' + recovery_command())
                device = factory(details['path'])
                try:
                    device.grab()
                except Exception:
                    device.close()
                    raise
                self.devices.append(device)
        except Exception:
            self.close()
            raise

    def close(self):
        while self.devices:
            device = self.devices.pop()
            try:
                device.ungrab()
            except OSError:
                pass
            try:
                device.close()
            except OSError:
                pass

    def __enter__(self):
        return self

    def __exit__(self, *args):
        self.close()
