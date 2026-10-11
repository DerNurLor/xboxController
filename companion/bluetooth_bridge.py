"""Optional exclusive HID-to-virtual-input bridge (native HID is the default)."""
import os
import sys
import time
from bluetooth_input import BluetoothInput

class BluetoothPump:
    def __init__(self, path, router, backend, grab_native=False):
        if sys.platform.startswith('linux'):
            try:
                import hidraw as hid
            except ImportError:
                import hid
        else:
            import hid
        self.hid = hid
        self.device = hid.device()
        try:
            try:
                self.device.open_path(os.fsencode(path))
            except OSError as error:
                if sys.platform.startswith('linux'):
                    raise RuntimeError(f'Cannot read {path}. Install companion/71-phonepad-uinput.rules with sudo and reconnect the phone; this grants the active desktop session access to PhonePad Bluetooth HID.') from error
                raise
            self.device.set_nonblocking(True)
            self.inputs = BluetoothInput(lambda body: router.gamepad_state('bluetooth', body),
                                         lambda body: router.desktop_state('bluetooth', body))
            self.backend = backend
            self.native_grab = None
            self.last_pad_at = time.monotonic()
            self.last_log = self.last_pad_at
            self.stale = False
            self.counts = {1: 0, 3: 0, 4: 0}
            if grab_native and sys.platform.startswith('linux'):
                from bluetooth_input_grab import BluetoothInputGrab
                selected = next((item for item in hid.enumerate()
                    if (item['path'].decode(errors='replace') if isinstance(item['path'], bytes) else str(item['path'])) == path
                    and item.get('vendor_id') == 0x038f and item.get('product_id') == 0x1200), {})
                serial = selected.get('serial_number')
                if not serial:
                    raise RuntimeError('Cannot identify the selected Bluetooth phone uniquely; use native HID mode.')
                self.native_grab = BluetoothInputGrab(serial)
                print(f'Bluetooth native inputs exclusively captured ({len(self.native_grab.devices)} interfaces).', flush=True)
        except Exception:
            self.device.close()
            raise
        print('Bluetooth connected: gamepad, keyboard, and mouse are enabled.', flush=True)

    def poll(self, now):
        data = self.device.read(64)
        if data:
            report = bytes(data)
            if self.inputs.receive(report, now):
                self.counts[report[0]] = self.counts.get(report[0], 0) + 1
                if report[0] == 1: self.last_pad_at = now
        self.inputs.expire(now)
        if now - self.last_log >= 5:
            print('Bluetooth reports: controller={}, keyboard={}, mouse={}'.format(
                self.counts.get(1, 0), self.counts.get(3, 0), self.counts.get(4, 0)), flush=True)
            self.last_log = now
        if now - self.last_pad_at >= .5:
            self.backend.stop_feedback()
            if not self.stale: self.device.write(bytes([2, 0, 0, 0])); self.stale = True
        else:
            self.device.write(bytes([2]) + self.backend.rumble())
            self.stale = False

    def close(self):
        try:
            self.inputs.neutral()
            self.backend.stop_feedback()
            self.device.write(bytes([2, 0, 0, 0]))
        finally:
            try:
                if self.native_grab: self.native_grab.close()
            finally:
                self.device.close()
