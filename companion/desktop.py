"""Full-state keyboard/mouse output. Relative movement is applied only once per sequence."""
import struct
import uuid

NEUTRAL_DESKTOP = bytes(39)
KEY_NAMES = {4+i: chr(65+i) for i in range(26)}
KEY_NAMES.update({30+i: str((i+1) % 10) for i in range(10)})
KEY_NAMES.update(dict(enumerate(('ENTER','ESC','BACKSPACE','TAB','SPACE','MINUS','EQUAL','LEFTBRACE','RIGHTBRACE','BACKSLASH','BACKSLASH','SEMICOLON','APOSTROPHE','GRAVE','COMMA','DOT','SLASH','CAPSLOCK'),40)))
KEY_NAMES.update({57+i: 'F'+str(i) for i in range(1,13)})
KEY_NAMES.update(dict(enumerate(('SYSRQ','SCROLLLOCK','PAUSE','INSERT','HOME','PAGEUP','DELETE','END','PAGEDOWN','RIGHT','LEFT','DOWN','UP','NUMLOCK','KPSLASH','KPASTERISK','KPMINUS','KPPLUS','KPENTER','KP1','KP2','KP3','KP4','KP5','KP6','KP7','KP8','KP9','KP0','KPDOT'),70)))
KEY_NAMES.update(dict(enumerate(('LEFTCTRL','LEFTSHIFT','LEFTALT','LEFTMETA','RIGHTCTRL','RIGHTSHIFT','RIGHTALT','RIGHTMETA'),224)))

def decode_desktop(body):
    if len(body) != 39 or body[32] & ~31:
        raise ValueError('Invalid desktop state')
    keys = {i for i in range(256) if body[i//8] & (1 << (i%8))}
    if not keys.issubset(KEY_NAMES):
        raise ValueError('Unsupported keyboard usage')
    return keys, body[32], *struct.unpack('!hhh',body[33:])

def key_edges(old, new, modifiers):
    """Release ordinary keys before modifiers; press modifiers before chord keys."""
    released=old-new;pressed=new-old
    return ([(key,0) for key in sorted(released-modifiers)] +
            [(key,0) for key in sorted(released & modifiers)] +
            [(key,1) for key in sorted(pressed & modifiers)] +
            [(key,1) for key in sorted(pressed-modifiers)])

class Desktop:
    def __init__(self, backend):
        self.keys=set();self.buttons=0;self.backend=backend
        if backend=='linux':
            from evdev import UInput, ecodes as e
            self.e=e
            self.codes={usage:getattr(e,'KEY_'+name) for usage,name in KEY_NAMES.items()}
            self.mouse_codes=(e.BTN_LEFT,e.BTN_RIGHT,e.BTN_MIDDLE,e.BTN_SIDE,e.BTN_EXTRA)
            # Separate event nodes let libinput classify both devices correctly.
            identity='phonepad/'+uuid.uuid4().hex
            self.ui=UInput({e.EV_KEY:sorted(set(self.codes.values()))},name='PhonePad Keyboard',phys=identity+'/keyboard')
            try:
                self.mouse_ui=UInput({e.EV_KEY:list(self.mouse_codes),e.EV_REL:[e.REL_X,e.REL_Y,e.REL_WHEEL]},name='PhonePad Mouse',phys=identity+'/mouse')
            except Exception:
                self.ui.close()
                raise
        elif backend=='windows':
            self.win=WindowsDesktop()

    def apply(self, body):
        keys,buttons,_,_,_=decode_desktop(body)
        try:self._apply(body)
        except Exception:
            # A partial OS write may already have pressed some new outputs.
            self.keys|=keys;self.buttons|=buttons
            raise

    def _apply(self, body):
        keys,buttons,x,y,wheel=decode_desktop(body)
        if self.backend=='linux':
            e=self.e
            old_codes={self.codes[k] for k in self.keys};new_codes={self.codes[k] for k in keys}
            modifiers={code for usage,code in self.codes.items() if 224<=usage<=231}
            for code,value in key_edges(old_codes,new_codes,modifiers):self.ui.write(e.EV_KEY,code,value)
            for i,code in enumerate(self.mouse_codes):
                if (self.buttons ^ buttons)&(1<<i):self.mouse_ui.write(e.EV_KEY,code,int(bool(buttons&(1<<i))))
            for code,value in ((e.REL_X,x),(e.REL_Y,y),(e.REL_WHEEL,wheel)):
                if value:self.mouse_ui.write(e.EV_REL,code,value)
            self.ui.syn()
            self.mouse_ui.syn()
        elif self.backend=='windows':self.win.apply(self.keys,keys,self.buttons,buttons,x,y,wheel)
        self.keys,self.buttons=keys,buttons

    def close(self):
        try:self.apply(NEUTRAL_DESKTOP)
        finally:
            try:
                if hasattr(self,'ui'):self.ui.close()
            finally:
                if hasattr(self,'mouse_ui'):self.mouse_ui.close()

class WindowsDesktop:
    """Win32 SendInput, with pointer-size-correct structures and explicit release edges."""
    def __init__(self):
        import ctypes as c
        from ctypes import wintypes as w
        self.c=c
        class Mouse(c.Structure):_fields_=[('dx',w.LONG),('dy',w.LONG),('mouseData',w.DWORD),('dwFlags',w.DWORD),('time',w.DWORD),('dwExtraInfo',c.c_size_t)]
        class Key(c.Structure):_fields_=[('wVk',w.WORD),('wScan',w.WORD),('dwFlags',w.DWORD),('time',w.DWORD),('dwExtraInfo',c.c_size_t)]
        class Payload(c.Union):_fields_=[('mi',Mouse),('ki',Key)]
        class Input(c.Structure):_fields_=[('type',w.DWORD),('data',Payload)]
        self.Input,self.Mouse,self.Key=Input,Mouse,Key
        self.api=c.WinDLL('user32',use_last_error=True)
        self.api.SendInput.argtypes=(w.UINT,c.POINTER(Input),c.c_int);self.api.SendInput.restype=w.UINT
        self.api.MapVirtualKeyW.argtypes=(w.UINT,w.UINT);self.api.MapVirtualKeyW.restype=w.UINT
        self.vk={4+i:65+i for i in range(26)};self.vk.update({30+i:ord(str((i+1)%10)) for i in range(10)})
        self.vk.update(dict(enumerate((13,27,8,9,32,0xBD,0xBB,0xDB,0xDD,0xDC,0xDC,0xBA,0xDE,0xC0,0xBC,0xBE,0xBF,20),40)))
        self.vk.update({57+i:0x6f+i for i in range(1,13)})
        self.vk.update(dict(enumerate((0x2c,0x91,0x13,0x2d,0x24,0x21,0x2e,0x23,0x22,0x27,0x25,0x28,0x26,0x90,0x6f,0x6a,0x6d,0x6b,13,0x61,0x62,0x63,0x64,0x65,0x66,0x67,0x68,0x69,0x60,0x6e),70)))
        self.vk.update(dict(enumerate((0xa2,0xa0,0xa4,0x5b,0xa3,0xa1,0xa5,0x5c),224)))

    def apply(self,old,keys,old_buttons,buttons,x,y,wheel):
        events=[]
        # HID backslash aliases share a physical Windows scan code.
        old={49 if usage==50 else usage for usage in old}
        keys={49 if usage==50 else usage for usage in keys}
        for usage,pressed in key_edges(old,keys,set(range(224,232))):
            scan=self.api.MapVirtualKeyW(self.vk[usage],0)
            extended=usage in {70,73,74,75,76,77,78,79,80,81,82,83,84,88,227,228,230,231}
            event=self.Input();event.type=1;event.data.ki=self.Key(0,scan,8|int(extended)|(0 if pressed else 2),0,0);events.append(event)
        def mouse(flags,data=0,dx=0,dy=0):
            event=self.Input();event.type=0;event.data.mi=self.Mouse(dx,dy,data&0xffffffff,flags,0,0);events.append(event)
        for bit,(down,up,data) in enumerate(((2,4,0),(8,16,0),(32,64,0),(128,256,1),(128,256,2))):
            if (old_buttons ^ buttons)&(1<<bit):mouse(down if buttons&(1<<bit) else up,data)
        if x or y:mouse(1,dx=x,dy=y)
        if wheel:mouse(0x800,wheel*120)
        if events:
            array=(self.Input*len(events))(*events)
            if self.api.SendInput(len(events),array,self.c.sizeof(self.Input))!=len(events):raise OSError('Windows blocked keyboard/mouse injection')
