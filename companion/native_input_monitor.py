"""Read-only monitor for kernel-managed PhonePad Bluetooth HID devices."""

from backends import BUTTON_BITS

# The Android composite HID descriptor exposes 16 button usages. Linux maps
# its final, otherwise unnamed BTN_GAMEPAD usage to code 319 on this device.
PHONEPAD_SHARE_KEY = 319


class NativeInputMonitor:
    """Observe evdev reports without grabbing devices or reinjecting input."""

    def __init__(self, devices, on_input, evdev_module=None):
        if evdev_module is None:
            import evdev as evdev_module
        self.evdev = evdev_module
        self.on_input = on_input
        self.devices = []
        self.report_count = 0
        self.paths = tuple(sorted(info['path'] if isinstance(info, dict) else str(info)
                                  for info in devices))
        self.errors = []
        for info in devices:
            path = info['path'] if isinstance(info, dict) else str(info)
            try:
                device = self.evdev.InputDevice(path)
                kind = self._classify(device)
                if not kind:
                    device.close()
                    continue
                state = self._initial_state(device, kind)
                self.devices.append(dict(device=device, kind=kind, state=state))
                if state != ([] if kind == 'keyboard' else [0, 0, 0, 0] if kind == 'mouse' else [0, 8, 0, 0, 0, 0, 0, 0]):
                    self._emit(kind, state)
            except (OSError, PermissionError) as error:
                self.errors.append(f'{path}: {error}')
        if not self.devices:
            detail = '; '.join(self.errors) or 'No readable controller, mouse, or keyboard event devices.'
            raise OSError(detail)

    def _classify(self, device):
        e = self.evdev.ecodes
        capabilities = device.capabilities(absinfo=False)
        keys = set(capabilities.get(e.EV_KEY, []))
        rel = set(capabilities.get(e.EV_REL, []))
        absolute = set(capabilities.get(e.EV_ABS, []))
        if {e.REL_X, e.REL_Y}.issubset(rel) and keys.intersection(
                (e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE, e.BTN_SIDE, e.BTN_EXTRA)):
            return 'mouse'
        if e.ABS_X in absolute and e.ABS_Y in absolute:
            return 'controller'
        if keys:
            return 'keyboard'
        return ''

    def _initial_state(self, device, kind):
        e = self.evdev.ecodes
        active = set(device.active_keys())
        if kind == 'keyboard':
            return self._keyboard_usages(active)
        if kind == 'mouse':
            return [self._mouse_buttons(active), 0, 0, 0]
        state = [0, 8, 0, 0, 0, 0, 0, 0]
        state[0] = self._controller_buttons(active)
        for code, index in ((e.ABS_X, 2), (e.ABS_Y, 3), (e.ABS_RX, 4), (e.ABS_RY, 5)):
            try:
                state[index] = self._axis(device.absinfo(code), signed=True)
            except (OSError, ValueError):
                pass
        for code, index in ((e.ABS_Z, 6), (e.ABS_RZ, 7)):
            try:
                state[index] = self._axis(device.absinfo(code), signed=False, maximum=255)
            except (OSError, ValueError):
                pass
        try:
            x = device.absinfo(e.ABS_HAT0X).value
            y = device.absinfo(e.ABS_HAT0Y).value
            state[1] = self._hat(x, y)
        except (OSError, ValueError):
            pass
        return state

    @staticmethod
    def _keymap(e):
        result = {}
        for index, letter in enumerate('ABCDEFGHIJKLMNOPQRSTUVWXYZ'):
            result[getattr(e, 'KEY_' + letter)] = index + 4
        for index, digit in enumerate('1234567890'):
            result[getattr(e, 'KEY_' + digit)] = index + 30
        names = {
            'KEY_ENTER': 40, 'KEY_ESC': 41, 'KEY_BACKSPACE': 42, 'KEY_TAB': 43,
            'KEY_SPACE': 44, 'KEY_MINUS': 45, 'KEY_EQUAL': 46, 'KEY_LEFTBRACE': 47,
            'KEY_RIGHTBRACE': 48, 'KEY_BACKSLASH': 49, 'KEY_SEMICOLON': 51,
            'KEY_APOSTROPHE': 52, 'KEY_GRAVE': 53, 'KEY_COMMA': 54, 'KEY_DOT': 55,
            'KEY_SLASH': 56, 'KEY_CAPSLOCK': 57, 'KEY_SYSRQ': 70, 'KEY_SCROLLLOCK': 71,
            'KEY_PAUSE': 72, 'KEY_INSERT': 73, 'KEY_HOME': 74, 'KEY_PAGEUP': 75,
            'KEY_DELETE': 76, 'KEY_END': 77, 'KEY_PAGEDOWN': 78, 'KEY_RIGHT': 79,
            'KEY_LEFT': 80, 'KEY_DOWN': 81, 'KEY_UP': 82, 'KEY_NUMLOCK': 83,
            'KEY_KPSLASH': 84, 'KEY_KPASTERISK': 85, 'KEY_KPMINUS': 86,
            'KEY_KPPLUS': 87, 'KEY_KPENTER': 88, 'KEY_LEFTCTRL': 224,
            'KEY_LEFTSHIFT': 225, 'KEY_LEFTALT': 226, 'KEY_LEFTMETA': 227,
            'KEY_RIGHTCTRL': 228, 'KEY_RIGHTSHIFT': 229, 'KEY_RIGHTALT': 230,
            'KEY_RIGHTMETA': 231,
        }
        names.update({'KEY_F' + str(i): 57 + i for i in range(1, 13)})
        names.update({'KEY_KP' + str(i): 88 + i if i else 98 for i in range(10)})
        names['KEY_KPDOT'] = 99
        for name, usage in names.items():
            code = getattr(e, name, None)
            if code is not None:
                result[code] = usage
        return result

    def _keyboard_usages(self, active):
        mapping = self._keymap(self.evdev.ecodes)
        return sorted({mapping[code] for code in active if code in mapping})

    def _mouse_buttons(self, active):
        e = self.evdev.ecodes
        mapping = ((e.BTN_LEFT, 0), (e.BTN_RIGHT, 1), (e.BTN_MIDDLE, 2),
                   (e.BTN_SIDE, 3), (e.BTN_EXTRA, 4))
        return sum(1 << bit for code, bit in mapping if code in active)

    def _controller_buttons(self, active):
        e = self.evdev.ecodes
        names = ('BTN_A', 'BTN_B', 'BTN_X', 'BTN_Y', 'BTN_TL', 'BTN_TR',
                 'BTN_SELECT', 'BTN_START', 'BTN_MODE', 'BTN_THUMBL',
                 'BTN_THUMBR', 'KEY_RECORD')
        mapping = {getattr(e, name, -1): bit for name, bit in zip(names, BUTTON_BITS)}
        # KEY_RECORD is used by some HID drivers; the phone's native HID
        # descriptor instead arrives as the raw final BTN_GAMEPAD code.
        mapping[PHONEPAD_SHARE_KEY] = 15
        buttons = 0
        for code, bit in mapping.items():
            if code in active:
                buttons |= 1 << bit
        return buttons

    @staticmethod
    def _axis(info, signed, maximum=32767, value=None):
        value = info.value if value is None else value
        minimum = getattr(info, 'min', getattr(info, 'minimum', 0))
        upper = getattr(info, 'max', getattr(info, 'maximum', 0))
        if signed:
            span = max(abs(minimum), abs(upper))
            if span <= 0:
                return 0
            return max(-maximum, min(maximum, round(value / span * maximum)))
        span = upper - minimum
        if span <= 0:
            return 0
        return max(0, min(maximum, round((value - minimum) / span * maximum)))

    @staticmethod
    def _hat(x, y):
        hats = ((0, -1), (1, -1), (1, 0), (1, 1), (0, 1),
                (-1, 1), (-1, 0), (-1, -1), (0, 0))
        try:
            return hats.index((max(-1, min(1, x)), max(-1, min(1, y))))
        except ValueError:
            return 8

    def _emit(self, kind, state):
        self.report_count += 1
        self.on_input('bluetooth', kind, list(state))

    def _consume(self, entry, event):
        e = self.evdev.ecodes
        kind, state = entry['kind'], entry['state']
        if event.type == e.EV_SYN and event.code == e.SYN_DROPPED:
            entry['dropped'] = True
            return
        if entry.get('dropped'):
            if event.type == e.EV_SYN and event.code == e.SYN_REPORT:
                state[:] = self._initial_state(entry['device'], kind)
                entry['dropped'] = False
                self._emit(kind, state)
            return
        if event.type == e.EV_KEY and event.value != 2:
            down = event.value == 1
            if kind == 'keyboard':
                keys = set(state)
                mapping = self._keymap(e)
                usage = mapping.get(event.code)
                if usage is not None:
                    (keys.add if down else keys.discard)(usage)
                state[:] = sorted(keys)
            elif kind == 'mouse':
                mapping = {e.BTN_LEFT: 0, e.BTN_RIGHT: 1, e.BTN_MIDDLE: 2,
                           e.BTN_SIDE: 3, e.BTN_EXTRA: 4}
                bit = mapping.get(event.code)
                if bit is not None:
                    state[0] = state[0] | (1 << bit) if down else state[0] & ~(1 << bit)
            else:
                mapping = {getattr(e, name, -1): bit for name, bit in zip(
                    ('BTN_A', 'BTN_B', 'BTN_X', 'BTN_Y', 'BTN_TL', 'BTN_TR',
                     'BTN_SELECT', 'BTN_START', 'BTN_MODE', 'BTN_THUMBL',
                     'BTN_THUMBR', 'KEY_RECORD'), BUTTON_BITS)}
                mapping[PHONEPAD_SHARE_KEY] = 15
                bit = mapping.get(event.code)
                if bit is not None:
                    state[0] = state[0] | (1 << bit) if down else state[0] & ~(1 << bit)
        elif event.type == e.EV_REL and kind == 'mouse':
            if event.code == e.REL_X:
                state[1] += event.value
            elif event.code == e.REL_Y:
                state[2] += event.value
            elif event.code == e.REL_WHEEL:
                state[3] += event.value
        elif event.type == e.EV_ABS and kind == 'controller':
            device = entry['device']
            try:
                info = device.absinfo(event.code)
            except (OSError, ValueError):
                return
            if event.code in (e.ABS_X, e.ABS_Y, e.ABS_RX, e.ABS_RY):
                index = {e.ABS_X: 2, e.ABS_Y: 3, e.ABS_RX: 4, e.ABS_RY: 5}[event.code]
                state[index] = self._axis(info, signed=True, value=event.value)
            elif event.code in (e.ABS_Z, e.ABS_RZ):
                index = 6 if event.code == e.ABS_Z else 7
                state[index] = self._axis(info, signed=False, maximum=255, value=event.value)
            elif event.code in (e.ABS_HAT0X, e.ABS_HAT0Y):
                ecode_x, ecode_y = e.ABS_HAT0X, e.ABS_HAT0Y
                try:
                    x = event.value if event.code == ecode_x else device.absinfo(ecode_x).value
                    y = event.value if event.code == ecode_y else device.absinfo(ecode_y).value
                    state[1] = self._hat(x, y)
                except (OSError, ValueError):
                    pass
        elif event.type == e.EV_SYN and event.code == e.SYN_REPORT:
            self._emit(kind, state)
            if kind == 'mouse':
                state[1:4] = [0, 0, 0]

    def poll(self):
        starting_count = self.report_count
        failed = []
        for entry in self.devices:
            try:
                for event in entry['device'].read():
                    self._consume(entry, event)
            except BlockingIOError:
                continue
            except OSError as error:
                self.errors.append(f"{entry['device'].path}: {error}")
                failed.append(entry)
        for entry in failed:
            self._emit(entry['kind'], [] if entry['kind'] == 'keyboard' else
                       [0, 0, 0, 0] if entry['kind'] == 'mouse' else [0, 8, 0, 0, 0, 0, 0, 0])
            try:
                entry['device'].close()
            except OSError:
                pass
            self.devices.remove(entry)
        self.paths=tuple(sorted(entry['device'].path for entry in self.devices))
        if not self.devices and self.errors:
            raise OSError('; '.join(self.errors))
        return self.report_count - starting_count

    def close(self):
        for entry in self.devices:
            entry['device'].close()
        self.devices.clear()
