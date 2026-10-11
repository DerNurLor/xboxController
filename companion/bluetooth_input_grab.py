"""Keep Linux from delivering PhonePad Bluetooth inputs twice."""


class BluetoothInputGrab:
    def __init__(self, serial_number):
        from evdev import InputDevice, list_devices

        self.devices = []
        target = (serial_number or '').lower()
        try:
            for path in list_devices():
                item = InputDevice(path)
                info = item.info
                if (info.bustype != 0x05 or info.vendor != 0x038f or
                        info.product != 0x1200 or (target and item.uniq.lower() != target)):
                    item.close()
                    continue
                try:
                    item.grab()
                except Exception:
                    item.close()
                    raise
                self.devices.append(item)
            if not self.devices:
                raise RuntimeError('No Linux input interfaces matched the selected PhonePad Bluetooth device.')
        except Exception as error:
            self.close()
            if isinstance(error, RuntimeError):
                raise
            raise RuntimeError(f'Could not take exclusive control of PhonePad Bluetooth input: {error}') from error

    def close(self):
        while self.devices:
            item = self.devices.pop()
            try:
                item.ungrab()
            except OSError:
                pass
            try:
                item.close()
            except OSError:
                pass
