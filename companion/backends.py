"""Host gamepad creation; imports remain optional for protocol tests."""
import time
import math
import uuid
from protocol import decode, NEUTRAL

BUTTON_BITS = (0, 1, 3, 4, 6, 7, 10, 11, 12, 13, 14, 15)
HATS = ((0,-1),(1,-1),(1,0),(1,1),(0,1),(-1,1),(-1,0),(-1,-1),(0,0))


class DryRun:
    def apply(self, body):
        if getattr(self, 'last', None) != body:
            print('Controller state:', decode(body), flush=True)
        self.last = body

    def rumble(self):
        return bytes(3)

    def poll(self):
        pass

    def stop_feedback(self):
        pass

    def close(self):
        self.apply(NEUTRAL)


class LinuxPad:
    def __init__(self):
        from evdev import UInput, AbsInfo, ecodes as e
        self.e = e
        # Xbox/xpad uses legacy BTN_X/BTN_Y ordering; geographic WEST/NORTH
        # reverses X and Y in Proton's direct XInput path for this identity.
        self.buttons = (e.BTN_A,e.BTN_B,e.BTN_X,e.BTN_Y,e.BTN_TL,e.BTN_TR,
                        e.BTN_SELECT,e.BTN_START,e.BTN_MODE,e.BTN_THUMBL,e.BTN_THUMBR,e.KEY_RECORD)
        self.axes = (e.ABS_X,e.ABS_Y,e.ABS_RX,e.ABS_RY,e.ABS_Z,e.ABS_RZ,e.ABS_HAT0X,e.ABS_HAT0Y)
        axes = [(a, AbsInfo(0, -32767, 32767, 0, 0, 0)) for a in self.axes[:4]]
        axes += [(a, AbsInfo(0, 0, 255, 0, 0, 0)) for a in self.axes[4:6]]
        axes += [(a, AbsInfo(0, -1, 1, 0, 0, 0)) for a in self.axes[6:]]
        self.ui = UInput({e.EV_KEY:list(self.buttons), e.EV_ABS:axes, e.EV_FF:[e.FF_RUMBLE,e.FF_GAIN]},
                         name='PhonePad Virtual Xbox Layout', vendor=0x045e, product=0x028e,
                         version=0x0114, bustype=0x03, max_effects=16, phys='phonepad/'+uuid.uuid4().hex+'/controller')
        self.effects, self.active, self.gain = {}, {}, 65535
        self.apply(NEUTRAL)

    def apply(self, body):
        buttons, hat, *axes = decode(body)
        for bit, code in zip(BUTTON_BITS, self.buttons):
            self.ui.write(self.e.EV_KEY, code, int(bool(buttons & (1 << bit))))
        for code, value in zip(self.axes, axes + list(HATS[hat])):
            self.ui.write(self.e.EV_ABS, code, value)
        self.ui.syn()

    def poll(self):
        e = self.e
        try:
            for event in self.ui.read():
                if event.type == e.EV_UINPUT and event.code == e.UI_FF_UPLOAD:
                    upload = self._begin_ff(event.value, 'upload')
                    try:
                        effect = upload.effect
                        if effect.type == e.FF_RUMBLE:
                            self.effects[effect.id] = (effect.u.ff_rumble_effect.strong_magnitude,
                                effect.u.ff_rumble_effect.weak_magnitude, effect.ff_replay.length/1000,
                                effect.ff_replay.delay/1000)
                            upload.retval = 0
                            if getattr(self,'trace_feedback',False):print('FF upload',effect.id,self.effects[effect.id],flush=True)
                        else:
                            upload.retval = -22
                    finally:
                        self.ui.end_upload(upload)
                elif event.type == e.EV_UINPUT and event.code == e.UI_FF_ERASE:
                    erase = self._begin_ff(event.value, 'erase')
                    self.effects.pop(erase.effect_id, None)
                    self.active.pop(erase.effect_id, None)
                    erase.retval = 0
                    if getattr(self,'trace_feedback',False):print('FF erase',erase.effect_id,flush=True)
                    self.ui.end_erase(erase)
                elif event.type == e.EV_FF:
                    if getattr(self,'trace_feedback',False):print('FF play/stop/gain',event.code,event.value,flush=True)
                    if event.code == e.FF_GAIN:
                        self.gain = max(0, min(65535,event.value))
                    elif event.value > 0 and event.code in self.effects:
                        # Resolve the uploaded effect on every poll: games update playing IDs.
                        self.active[event.code] = (time.monotonic(), event.value)
                    else:
                        self.active.pop(event.code, None)
        except BlockingIOError:
            pass

    def _begin_ff(self, request_id, operation):
        # evdev 2.0.0 begin_upload/begin_erase assign effect_id instead of request_id.
        # Set the kernel request identifier on the actual ctypes structure explicitly.
        import ctypes
        from evdev import ff
        request = ff.UInputUpload() if operation == 'upload' else ff.UInputErase()
        request.request_id = request_id
        begin = getattr(self.ui.dll, '_uinput_begin_' + operation)
        if begin(self.ui.fd, ctypes.byref(request)):
            raise OSError('Unable to begin force-feedback ' + operation)
        return request

    def rumble(self):
        now = time.monotonic()
        low=high=0
        end=now
        for key, (started, repeats) in list(self.active.items()):
            a,b,duration,delay = self.effects[key]
            elapsed=now-started
            period=duration+delay
            # Linux replay length 0 is indefinite until explicit stop/erase.
            if duration == 0:
                remaining=.25 if elapsed>=delay else 0
            elif elapsed >= period*repeats:
                del self.active[key]
                continue
            else:
                position=elapsed % period
                remaining=period-position if position>=delay else 0
            if remaining>0:
                low=max(low,a);high=max(high,b);end=max(end,now+remaining)
        scale = 255*self.gain/(65535*65535)
        # Short renewable feedback lease bounds phone vibration on lost transport.
        return bytes((round(low*scale),round(high*scale),min(25,max(0,math.ceil((end-now)*100)))))

    def stop_feedback(self):
        self.active.clear()

    def close(self):
        self.stop_feedback()
        try:
            self.apply(NEUTRAL)
        finally:
            self.ui.close()


class WindowsPad:
    def __init__(self):
        import vgamepad as vg
        self.vg=vg
        self.pad=vg.VX360Gamepad()
        self.feedback=bytes(3)
        self.feedback_at=0
        self.pad.register_notification(callback_function=self._notification)
        b=vg.XUSB_BUTTON
        self.mapping = (b.XUSB_GAMEPAD_A,b.XUSB_GAMEPAD_B,b.XUSB_GAMEPAD_X,b.XUSB_GAMEPAD_Y,
            b.XUSB_GAMEPAD_LEFT_SHOULDER,b.XUSB_GAMEPAD_RIGHT_SHOULDER,b.XUSB_GAMEPAD_BACK,
            b.XUSB_GAMEPAD_START,b.XUSB_GAMEPAD_GUIDE,b.XUSB_GAMEPAD_LEFT_THUMB,b.XUSB_GAMEPAD_RIGHT_THUMB)

    def _notification(self, client, target, large_motor, small_motor, led_number, user_data):
        self.feedback=bytes((large_motor,small_motor,25))
        self.feedback_at=time.monotonic()

    def apply(self, body):
        buttons, hat, lx,ly,rx,ry,lt,rt = decode(body)
        self.pad.reset()
        for bit, button in zip(BUTTON_BITS, self.mapping):
            if buttons & (1<<bit):
                self.pad.press_button(button=button)
        x,y=HATS[hat]
        b=self.vg.XUSB_BUTTON
        for pressed,button in ((x<0,b.XUSB_GAMEPAD_DPAD_LEFT),(x>0,b.XUSB_GAMEPAD_DPAD_RIGHT),
                               (y<0,b.XUSB_GAMEPAD_DPAD_UP),(y>0,b.XUSB_GAMEPAD_DPAD_DOWN)):
            if pressed:self.pad.press_button(button=button)
        self.pad.left_joystick(x_value=lx,y_value=-ly)
        self.pad.right_joystick(x_value=rx,y_value=-ry)
        self.pad.left_trigger(value=lt);self.pad.right_trigger(value=rt)
        self.pad.update()

    def poll(self):
        pass

    def rumble(self):
        # XInput reports amplitude changes, not duration; short leases are renewed by the transport.
        return self.feedback

    def stop_feedback(self):
        self.feedback=bytes(3)

    def close(self):
        self.stop_feedback()
        self.pad.reset();self.pad.update();self.pad.unregister_notification()
