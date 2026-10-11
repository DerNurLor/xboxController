"""Framework-independent receiver lifecycle shared by desktop and headless clients.

Only the worker owns sockets, decoders and input devices. Commands cross a queue;
presentation consumes a bounded snapshot at 30 Hz, never one update per packet.
"""
import copy
import json
import os
import queue
import secrets
import socket
import threading
import time
from collections import Counter, deque
from pathlib import Path

from backends import DryRun, LinuxPad, WindowsPad
from bluetooth_input import BluetoothInput
from bluetooth_status import inspect_bluetooth, scan, reconnect, pair as pair_bluetooth
from configuration import validate
from connection_qr import local_ipv4, encode_payload
from desktop import Desktop
from game_events import GameEventServer
from input_router import InputRouter
from linux_input_diagnostics import permission_snapshot, describe_device
from pairing import PairingService, PREFIX
from protocol import Protocol
from multiplayer import MultiplayerProtocol, player_source
from source_monitor import SourceMonitor


class QuietDryRun(DryRun):
    def apply(self, body): self.last = body


class CompanionService:
    def __init__(self, config, directory=None, bluetooth_probe=inspect_bluetooth):
        self.config = validate(config)
        self.directory = Path(directory or Path.home()/'.config/phonepad')
        self.probe = bluetooth_probe
        self.commands = queue.Queue()
        self.shutdown = threading.Event()
        self.lock = threading.Lock()
        self.thread = None
        self.sock = self.pad = self.desktop = self.router = self.protocol = self.pairing = self.events = None
        self.bridge = None
        self.player_pads = {}
        self.last_counters = {}
        self.last_packet = {}
        self.native_counters = Counter(received_reports=0, decoded_reports=0, kernel_input_reports=0,
                                       invalid_reports=0, connection_interruptions=0)
        self.native_fd = None
        self.native_path = ''
        self.native_decoder = None
        self.native_input_monitor = None
        self.last_scan = 0
        self.scanning = False
        self.reconnecting_address = ''
        self.pairing_address = ''
        self.bluetooth_retry_address = ''
        self.bluetooth_retry_action = ''
        self.active = False
        self.device_info = []
        self.permissions = permission_snapshot()
        self.trace = False
        self.history = deque(maxlen=120)
        self.controller_samples = deque(maxlen=256)
        # A short bounded pulse queue keeps quick presses visible to the UI's
        # 30 Hz snapshot poll without retaining a production event log.
        self.input_pulses = deque(maxlen=64)
        # Relative mouse input is momentary; keep a small actual-event trail so
        # the monitor can render movement even between its 30 Hz UI snapshots.
        self.mouse_trail = deque(maxlen=160)
        self.errors = deque(maxlen=12)
        self.live = {'controller': [0,8,0,0,0,0,0,0], 'keyboard': [], 'mouse': [0,0,0,0]}
        self.source_live = {}
        self.source_monitor = SourceMonitor(self._source_report)
        self.mouse_activity = {}
        self.controller_order = {}
        self.event_count = 0
        self.last_input = 0
        self.rate_at = time.monotonic(); self.rate_count = 0; self.rate = 0
        self.bt_info = {'powered':None,'devices':[],'hid':[],'input_devices':[],'error':''}
        self.states = {'wifi':'Stopped','bluetooth':'Stopped'}
        self.requested = {'wifi':False, 'bluetooth':False}
        self.local_address = ''
        self.qr = ''; self.pair_message = ''; self.wifi_error = ''; self.bt_error = ''
        self.cached = {}
        self._publish()

    def launch(self):
        if self.thread and self.thread.is_alive(): return
        self.shutdown.clear()
        self.thread = threading.Thread(target=self._run, name='PhonePad receivers', daemon=True)
        self.thread.start()

    def command(self, name, value=None):
        self.launch()
        self.commands.put((name,value))

    def snapshot(self):
        with self.lock: return copy.deepcopy(self.cached)

    def close(self):
        self.shutdown.set()
        if self.thread: self.thread.join(5)
        if self.thread and self.thread.is_alive(): raise RuntimeError('Receiver worker did not stop')

    def _error(self, text):
        self.errors.append(str(text))

    def _observe(self, source, kind, state):
        received = list(state)
        old = self.source_live.get(source, {}).get(kind, [] if kind == 'keyboard' else [0,0,0,0])
        now = time.monotonic()
        if self.trace and kind == 'controller':
            axes = received[2:6]
            if not self.controller_samples or self.controller_samples[-1]['source'] != source or self.controller_samples[-1]['axes'] != axes:
                self.controller_samples.append(dict(source=source, axes=axes, monotonic=now))
        if kind == 'keyboard':
            pressed = [('keyboard', int(key)) for key in set(received) - set(old)]
        elif kind == 'mouse':
            changed = received[0] & ~old[0]
            pressed = [('mouse', bit) for bit in range(5) if changed & (1 << bit)]
        elif kind in ('controller','controller_source'):
            changed = received[0] & ~old[0]
            pressed = [(kind, bit) for bit in range(16) if changed & (1 << bit)]
        else:
            pressed = []
        self.input_pulses.extend(dict(kind=pulse_kind, code=code, source=source, expires=now + 0.5)
                                 for pulse_kind, code in pressed)
        if kind == 'mouse':
            previous = old
            activity = self.mouse_activity.setdefault(source, dict(movement_reports=0, wheel_reports=0,
                button_presses=0, travel_x=0, travel_y=0))
            buttons, x, y, wheel = received
            activity['button_presses'] += (buttons & ~previous[0]).bit_count()
            if x or y:
                activity['movement_reports'] += 1
                activity['travel_x'] += abs(x)
                activity['travel_y'] += abs(y)
                self.mouse_trail.append((x, y, now))
            if wheel:
                activity['wheel_reports'] += 1
        self.source_live.setdefault(source, {})[kind] = received
        self.event_count += 1; self.last_input = now
        if kind == 'keyboard':
            self.live[kind] = sorted({key for values in self.source_live.values()
                                      for key in values.get(kind, [])})
        elif kind == 'mouse':
            buttons = 0
            for values in self.source_live.values():
                buttons |= values.get(kind, [0])[0]
            # Relative deltas belong only to the current event, never saved sources.
            self.live[kind] = [buttons, *received[1:]]
        else:
            self.controller_order[source] = self.event_count
            active = [name for name, values in self.source_live.items()
                      if values.get(kind, [0,8,0,0,0,0,0,0]) != [0,8,0,0,0,0,0,0]]
            selected = max(active, key=lambda name:self.controller_order.get(name, 0)) if active else source
            self.live[kind] = list(self.source_live[selected][kind])
        if self.trace:
            event={'source':source,'kind':kind,'state':list(state),'monotonic':self.last_input}
            if source.startswith('wifi') and self.protocol and self.protocol.current_event:
                context=self.protocol.current_event
                event.update(channel=context['channel'],sequence=context['sequence'])
            self.history.append(event)

    def _notify(self, message):
        self.pair_message = message
        if message.startswith('Connected '): self.qr = ''

    def _start(self):
        if not any(self.config[kind] for kind in self.states):
            self._error('Enable at least one transport')
            return
        for kind in self.states:
            if self.config[kind]:
                self._start_receiver(kind)

    def _start_receiver(self, kind):
        if kind not in self.states:
            raise ValueError('Unknown transport')
        if not self.config[kind]:
            raise ValueError('Enable '+kind+' before starting it')
        if self.requested[kind] and self.states[kind] not in ('Failed', 'Stopped'):
            return
        self.requested[kind] = True
        self.active = True
        self.states[kind] = 'Starting'
        self._publish()
        if kind == 'wifi':
            self._start_wifi()
            return
        self.bt_error = ''
        self.states['bluetooth'] = 'Waiting for device'
        if self.config.get('bluetooth_path'):
            try:
                from bluetooth_bridge import BluetoothPump
                self._ensure_backend()
                self.bridge = BluetoothPump(self.config['bluetooth_path'], self.router,
                                            self.pad, self.config['backend'] != 'dry-run')
                self.states['bluetooth'] = 'Running'
            except Exception as error:
                self.bt_error = str(error)
                self._error('Bluetooth bridge: '+str(error))
                self.states['bluetooth'] = 'Failed'
                if self.sock is None:
                    self._close_backend()
        else:
            self._refresh()

    def _stop_receiver(self, kind):
        if kind not in self.states:
            raise ValueError('Unknown transport')
        self.states[kind] = 'Stopping'
        self._publish()
        if kind == 'wifi':
            self._close_wifi()
        else:
            self._close_native()
            if self.bridge:
                try:
                    self.bridge.close()
                except Exception as error:
                    self._error('Close Bluetooth bridge: '+str(error))
                self.bridge = None
            if not self.sock:
                self._close_backend()
        self.requested[kind] = False
        self.states[kind] = 'Stopped'
        self.active = any(self.requested.values())
        # Source cleanup is owned by its transport. Passive kernel HID input
        # can remain held and connected after managed receivers stop.

    def _configure(self, values):
        config = validate(values)
        previous = dict(self.requested)
        was_active = self.active
        changed = {key for key in set(config) | set(self.config)
                   if config.get(key) != self.config.get(key)}
        dependencies = {
            'wifi': {'backend', 'bind', 'port', 'name', 'key_file', 'identity_file', 'no_discovery'},
            'bluetooth': {'backend', 'bluetooth_path'}}
        for kind in self.states:
            if previous[kind] and (not config[kind] or changed & dependencies[kind]):
                self._stop_receiver(kind)
        old_config = self.config
        self.config = config
        for kind in self.states:
            if config[kind] and (previous[kind] or (was_active and not old_config[kind])):
                self._start_receiver(kind)
        if self.pad:
            self.pad.trace_feedback = config['trace_feedback']

    def _ensure_backend(self):
        if self.pad and self.desktop:return
        try:
            self.pad={'linux':LinuxPad,'windows':WindowsPad,'dry-run':QuietDryRun}[self.config['backend']]()
            self.pad.trace_feedback=self.config['trace_feedback']
            self.desktop=Desktop(self.config['backend'])
            self.router=InputRouter(self.pad,self.desktop,self._observe)
            self._inspect_backend()
        except Exception:
            self._close_backend()
            raise

    def _inspect_backend(self):
        self.permissions = permission_snapshot()
        self.device_info = []
        if self.config['backend'] != 'linux':
            return
        for role, ui in (('controller', getattr(self.pad, 'ui', None)),
                         ('keyboard', getattr(self.desktop, 'ui', None)),
                         ('mouse', getattr(self.desktop, 'mouse_ui', None))):
            if ui is not None:
                self.device_info.append(describe_device(ui, role))
        for slot,pad in self.player_pads.items():
            if getattr(pad,"ui",None) is not None:
                details=describe_device(pad.ui,"controller");details["player"]=slot;self.device_info.append(details)

    def _close_backend(self):
        for slot in list(self.player_pads):self._release_player(slot)
        for field in ('desktop','pad'):
            resource=getattr(self,field);setattr(self,field,None)
            if resource:
                try:resource.close()
                except Exception as error:self._error('Cleanup '+field+': '+str(error))
        self.router=None;self.device_info=[]

    def _create_player(self, slot):
        source=player_source(slot)
        pad=self.pad if slot==1 else {'linux':LinuxPad,'windows':WindowsPad,'dry-run':QuietDryRun}[self.config['backend']]()
        pad.trace_feedback=self.config['trace_feedback']
        if slot!=1:
            self.player_pads[slot]=pad
            self.router.player_gamepads[source]=pad
        self._inspect_backend()
        return Protocol(self.protocol.key,lambda body:self.router.gamepad_state(source,body),pad.rumble,
            pad.stop_feedback,lambda body:self.router.desktop_state(source,body),self._game_state,lambda body:self._source_report(source,body))

    def _game_state(self):
        value=json.loads(self.events.states.snapshot());value["_phonepad_source"]=1
        return json.dumps(value,separators=(",",":")).encode()

    def _source_report(self, source, body):
        from protocol import decode
        state=list(decode(body))
        if self.source_live.get(source, {}).get("controller_source") != state:
            self._observe(source,"controller_source",state)

    def _release_player(self, slot):
        source=player_source(slot)
        from protocol import NEUTRAL
        self._source_report(source,NEUTRAL)
        try:
            if self.router:self.router.remove_player(source)
        finally:
            pad=self.player_pads.pop(slot,None)
            if pad:pad.close()
            elif slot==1 and self.pad:self.pad.stop_feedback()
            self._inspect_backend()

    def _start_wifi(self):
        try:
            self.directory.mkdir(parents=True,exist_ok=True)
            path = Path(self.config.get('key_file') or self.directory/'pairing.key')
            path.parent.mkdir(parents=True,exist_ok=True)
            try:
                with path.open('x') as stream:
                    os.chmod(path,0o600); stream.write(secrets.token_hex(16)+'\n')
            except FileExistsError: pass
            key=bytes.fromhex(path.read_text().strip())
            if len(key)!=16: raise ValueError('Pairing key must be 16 bytes')
            # Bind before acquiring input devices so conflicts have no input side effects.
            self.sock=socket.socket(socket.AF_INET,socket.SOCK_DGRAM)
            self.sock.bind((self.config['bind'],self.config['port'])); self.sock.setblocking(False)
            self._ensure_backend()
            self.local_address=self.config['bind'] if self.config['bind']!='0.0.0.0' else local_ipv4()
            self.events=GameEventServer(self.directory/'events.json')
            self.protocol=MultiplayerProtocol(key,self._create_player,self._release_player)
            if not self.config.get('no_discovery'):
                self.pairing=PairingService(key,self.config.get('identity_file') or self.directory/'identity.pem',self.config['name'],self.config['port'],time.monotonic(),self._notify)
            self.last_counters={};self.wifi_error=''; self.states['wifi']='Running'; self._pair()
        except Exception as error:
            self.wifi_error=str(error); self._error('Wi-Fi: '+str(error)); self._close_wifi()
            self.states['wifi']='Failed'

    def _pair(self):
        if not self.pairing:
            self.pair_message='Pairing is disabled by --no-discovery.' if self.sock else 'Start the Wi-Fi receiver to create a pairing code.'; return
        self.pairing.command('pair',time.monotonic())
        host=self.config['bind'] if self.config['bind']!='0.0.0.0' else local_ipv4()
        if not host:
            self.qr=''; self.pair_message='No reachable IPv4 address found. Connect to the LAN and generate a new code.'; return
        self.local_address=host
        self.qr=encode_payload(host,self.config['port'],self.pairing.public.hex(),self.pairing.id,self.pairing.name,self.pairing.qr_token,self.pairing.qr_code)

    def _refresh(self, discover=False, address=None):
        if self.scanning: return
        self.scanning=True
        self.reconnecting_address=address or ''
        self.pairing_address='';self.bluetooth_retry_address='';self.bluetooth_retry_action=''
        def work():
            try:
                if address: reconnect(address)
                value=scan() if discover else self.probe()
                self.commands.put(('bluetooth_result',value))
            except Exception as error: self.commands.put(('bluetooth_error',str(error)))
        threading.Thread(target=work,name='PhonePad BlueZ query',daemon=True).start()

    def _pair_bluetooth(self, address):
        if self.scanning: return
        self.scanning=True;self.reconnecting_address='';self.pairing_address=address
        self.bluetooth_retry_address='';self.bluetooth_retry_action=''
        def work():
            try:
                pair_bluetooth(address)
                self.commands.put(('bluetooth_result',self.probe()))
            except Exception as error: self.commands.put(('bluetooth_error',str(error)))
        threading.Thread(target=work,name='PhonePad Bluetooth pairing',daemon=True).start()

    def _update_bluetooth(self, info):
        self.source_monitor.update(info.get("hid",[]))
        self.bt_info={**info,"input_devices":info.get("input_devices",[])}; self.scanning=False; self.reconnecting_address='';self.pairing_address='';self.bluetooth_retry_address='';self.bluetooth_retry_action=''
        requested=self.requested['bluetooth']
        if self.config.get('bluetooth_path') and requested:return
        self.bt_error=info.get('error','')
        devices=info.get('hid',[])
        input_devices=info.get('input_devices',[])
        # Prefer OS input evidence. Never feed both raw HID and evdev into the
        # visual state: relative motion would otherwise be counted twice.
        if input_devices:
            self._close_native()
            self._monitor_kernel_input(input_devices)
            if self.native_input_monitor:
                if requested:self.states['bluetooth']='Connected'
                return
        if not requested or not devices:
            self._close_native()
            self._monitor_kernel_input(input_devices)
            if requested:
                self.states['bluetooth']='Connected' if input_devices else 'Waiting for device'
                if input_devices and self.native_input_monitor is None:
                    self.bt_error='Phone HID is connected, but Companion cannot monitor its Linux input devices.'
            return
        self._close_kernel_input()
        # Android HID is already handled by the Linux kernel. This read-only
        # monitor neither grabs nor reinjects the keyboard/mouse/gamepad reports.
        self.states['bluetooth']='Connected'
        path=devices[0]['path']
        if path==self.native_path and self.native_fd is not None: return
        self._close_native()
        try:
            self.native_fd=os.open(path,os.O_RDONLY|os.O_NONBLOCK)
            self.native_path=path
            self.native_decoder=BluetoothInput(
                lambda body:self._native_pad(body), lambda body:self._native_desktop(body))
        except OSError as error:
            self.bt_error='Native HID exists; Companion cannot monitor it: '+str(error)
            self._monitor_kernel_input(input_devices)
            if self.native_input_monitor:
                self.bt_error=''

    def _monitor_kernel_input(self, devices):
        paths=tuple(sorted(item['path'] for item in devices))
        if self.native_input_monitor and self.native_input_monitor.paths==paths:return
        self._close_kernel_input()
        if not paths:return
        try:
            from native_input_monitor import NativeInputMonitor
            self.native_input_monitor=NativeInputMonitor(devices,self._observe)
            self.bt_error=''
        except (ImportError,OSError,PermissionError) as error:
            self.native_input_monitor=None
            self.bt_error='Native PhonePad HID is connected; Companion input monitor could not open it: '+str(error)

    def _close_kernel_input(self):
        if not self.native_input_monitor:return
        monitor=self.native_input_monitor
        self.native_input_monitor=None
        monitor.close()
        self._observe('bluetooth','keyboard',[])
        self._observe('bluetooth','mouse',(0,0,0,0))
        self._observe('bluetooth','controller',[0,8,0,0,0,0,0,0])

    def _native_pad(self, body):
        from protocol import decode
        self._observe('bluetooth','controller',decode(body))

    def _native_desktop(self, body):
        from desktop import decode_desktop
        keys,buttons,x,y,wheel=decode_desktop(body)
        self._observe('bluetooth','keyboard',sorted(keys))
        self._observe('bluetooth','mouse',(buttons,x,y,wheel))

    def _close_native(self):
        if self.native_decoder: self.native_decoder.neutral()
        if self.native_fd is not None: os.close(self.native_fd)
        self.native_fd=None; self.native_path=''; self.native_decoder=None

    def _close_wifi(self):
        if self.protocol:
            self.protocol.close()
            self.last_counters=dict(self.protocol.counters)
            self.last_packet=dict(self.protocol.last_event)
        if self.router:
            try:self.router.neutral('wifi')
            except Exception as error:self._error('Release Wi-Fi: '+str(error))
        self.qr=''; self.pairing=None; self.protocol=None
        for field in ('sock','events'):
            resource=getattr(self,field)
            setattr(self,field,None)
            if resource:
                try: resource.close()
                except Exception as error: self._error('Cleanup '+field+': '+str(error))
        if not self.bridge:self._close_backend()

    def _stop(self):
        for kind in self.states:
            if self.requested[kind] or self.states[kind] != 'Stopped':
                self._stop_receiver(kind)
        self._close_backend()

    def _handle(self, name, value):
        if name=='start': self._start()
        elif name=='stop': self._stop()
        elif name=='receiver':
            kind, enabled = value
            if enabled:self._start_receiver(kind)
            else:self._stop_receiver(kind)
        elif name=='configure':self._configure(value)
        elif name=='pair': self._pair()
        elif name=='approve' and self.pairing:self.pairing.command('y' if value else 'n',time.monotonic())
        elif name=='trace':self.trace=bool(value)
        elif name=='clear':self.history.clear();self.mouse_trail.clear();self.controller_samples.clear()
        elif name=='scan':self._refresh(discover=True)
        elif name=='refresh':self._refresh()
        elif name=='inspect_backend':self._inspect_backend()
        elif name=='reconnect':self._refresh(address=value)
        elif name=='pair_bluetooth':self._pair_bluetooth(value)
        elif name=='bluetooth_result':self._update_bluetooth(value)
        elif name=='bluetooth_error':self.scanning=False;self.bluetooth_retry_address=self.reconnecting_address or self.pairing_address;self.bluetooth_retry_action='pair' if self.pairing_address else 'reconnect' if self.reconnecting_address else '';self.reconnecting_address='';self.pairing_address='';self.bt_error=value;self._error(value)
        elif name=='rumble':
            feedback = self._feedback_status()
            if not feedback['available']:
                raise ValueError(feedback['reason'])
            if feedback['route'] == 'bluetooth':
                fd=os.open(feedback['path'],os.O_WRONLY|os.O_NONBLOCK)
                try:os.write(fd,bytes([2,100,100,25]))
                finally:os.close(fd)
            elif feedback['route'] == 'wifi':
                slot=value if value in self.protocol.players else min(self.protocol.players)
                target=self.pad if slot==1 else self.player_pads[slot]
                target.effects[-1]=(25000,25000,.25,0);target.active[-1]=(time.monotonic(),1)

    def _feedback_status(self):
        if isinstance(self.pad, LinuxPad) and self.protocol and self.protocol.session:
            return dict(available=True, route='wifi', path='', reason='Wi-Fi feedback channel ready')
        for device in self.bt_info.get('hid', []):
            path = device['path']
            if device.get('feedback_output') and os.access(path, os.W_OK):
                return dict(available=True, route='bluetooth', path=path, reason='PhonePad HID output ready; native game force feedback depends on host support')
        return dict(available=False, route='', path='', reason='Connect an authenticated Wi-Fi phone with the Linux backend, or a writable PhonePad HID output device')

    def _tick(self, now):
        self.source_monitor.poll(now)
        if self.sock:
            try:
                self.pad.poll()
                for pad in list(self.player_pads.values()):pad.poll()
                self.protocol.expire(now)
                for _ in range(64):
                    try:raw,address=self.sock.recvfrom(1537)
                    except BlockingIOError:break
                    reply=self.pairing.receive(raw,address,now,self.protocol.full) if self.pairing and raw.startswith(PREFIX) else self.protocol.receive(raw,address,now)
                    if reply:
                        self.sock.sendto(reply,address)
                        # Authentication proves the approved phone received its key.
                        # Release the pairing slot for the next player immediately.
                        if reply.startswith(b'PP01\x02') and self.pairing and self.pairing.pending:
                            pending=self.pairing.pending
                            if pending['state']=='approved' and pending['address'][0]==address[0]:
                                self.pairing.pending=None

                self.states['wifi']='Connected' if self.protocol.session else 'Running'
                if self.pairing and (self.pairing.qr_used or now>=self.pairing.qr_until):self.qr=''
            except Exception as error:
                self.wifi_error=str(error);self._error('Wi-Fi: '+str(error));self._close_wifi();self.states['wifi']='Failed'
        if self.bridge:
            try:
                if not self.sock:self.pad.poll()
                self.bridge.poll(now)
                self.states['bluetooth']='Connected' if any(self.bridge.counts.values()) else 'Waiting for device'
            except Exception as error:
                self.bt_error=str(error);self._error('Bluetooth bridge: '+str(error))
                try:self.bridge.close()
                except Exception as cleanup:self._error(str(cleanup))
                self.bridge=None;self.states['bluetooth']='Failed'
                if not self.sock:self._close_backend()
        if self.native_fd is not None:
            try:
                for _ in range(64):
                    try:report=os.read(self.native_fd,64)
                    except BlockingIOError:break
                    if not report:raise OSError('HID device disconnected')
                    self.native_counters['received_reports'] += 1
                    if self.native_decoder.receive(report,now):
                        self.native_counters['decoded_reports'] += 1
                    else:
                        self.native_counters['invalid_reports'] += 1
                self.native_decoder.expire(now)
            except OSError as error:
                self.bt_error=str(error);self._close_native();self.states['bluetooth']='Waiting for device'
                self.native_counters['connection_interruptions'] += 1
        if self.native_input_monitor is not None:
            try:self.native_counters['kernel_input_reports'] += self.native_input_monitor.poll()
            except OSError as error:
                self.bt_error='Native PhonePad input monitor stopped: '+str(error)
                self._close_kernel_input()
        if now-self.last_scan>3:
            self.last_scan=now;self._refresh()

    def _publish(self):
        now=time.monotonic()
        if now-self.rate_at>=1:
            self.rate=(self.event_count-self.rate_count)/(now-self.rate_at)
            self.rate_at=now;self.rate_count=self.event_count
        enabled=[self.states[k] for k in self.states if self.requested[k]]
        if not self.active:state='Stopped'
        elif 'Starting' in enabled:state='Starting'
        elif 'Stopping' in enabled:state='Stopping'
        elif 'Failed' in enabled:state='Failed' if all(x=='Failed' for x in enabled) else 'Partially running'
        elif 'Connected' in enabled:state='Connected'
        elif 'Running' in enabled:state='Running'
        else:state='Waiting for device'
        pairing_state=('Disabled' if self.sock else 'Stopped') if not self.pairing else 'Used' if self.pairing.qr_used else 'Expired' if now>=self.pairing.qr_until else 'Ready' if self.qr else 'Unavailable'
        virtual='Not created' if not self.pad else 'Dry run (no OS devices)' if self.config['backend']=='dry-run' else 'Created'
        devices=self.device_info
        virtual_states={role:virtual for role in ('controller','mouse','keyboard')}
        for device in devices:
            virtual_states[device['role']]=device['status']
        value=dict(state=state,active=self.active,receivers=dict(self.states),requested=dict(self.requested),config=self.config,
            wifi_error=self.wifi_error,bluetooth_error=self.bt_error,bluetooth=self.bt_info,scanning=self.scanning,
            local_address=self.local_address,pairing_state=pairing_state,pairing_message=self.pair_message,qr=self.qr,reconnecting_address=self.reconnecting_address,pairing_address=self.pairing_address,bluetooth_retry_address=self.bluetooth_retry_address,bluetooth_retry_action=self.bluetooth_retry_action,
            pairing_seconds=max(0,int(self.pairing.qr_until-now)) if self.pairing else 0,
            players=[dict(player,controller=self.source_live.get(player['source'],{}).get('controller',[0,8,0,0,0,0,0,0]),controller_source=self.source_live.get(player['source'],{}).get('controller_source',[0,8,0,0,0,0,0,0])) for player in self.protocol.snapshot()] if self.protocol else [],
            peer=list(self.protocol.session.address) if self.protocol and self.protocol.session else [],
            virtual=virtual,virtual_states=virtual_states,permissions=self.permissions,devices=devices,counters=dict(self.protocol.counters) if self.protocol else self.last_counters,
            mouse_activity=self.mouse_activity,mouse_trail=list(self.mouse_trail),live=self.live,input_pulses=[{key:item for key,item in pulse.items() if key != 'expires'} for pulse in self.input_pulses if pulse['expires'] > now],event_count=self.event_count,event_rate=round(self.rate,1),
            last_input_age=round(now-self.last_input,1) if self.last_input else None,
            history=list(self.history),controller_samples=list(self.controller_samples),errors=list(self.errors),trace=self.trace,
            source_reports=self.source_monitor.reports,source_errors=dict(self.source_monitor.errors),
            source_needs_pairing=bool(self.bt_info.get("hid")) and not any(d.get("source_input") for d in self.bt_info.get("hid",[])),
            native_observing=self.native_fd is not None or self.native_input_monitor is not None,native_counters=dict(self.native_counters),
            observation_source='Linux evdev' if self.native_input_monitor is not None else 'Raw HID' if self.native_fd is not None else 'None',
            last_packet=dict(self.protocol.last_event) if self.protocol else self.last_packet)
        from presentation_state import summarize
        value['connection'] = summarize(value)
        value['feedback'] = self._feedback_status()
        with self.lock:self.cached=copy.deepcopy(value)

    def _run(self):
        last_publish=0
        try:
            while not self.shutdown.is_set():
                for _ in range(32):
                    try:name,value=self.commands.get_nowait()
                    except queue.Empty:break
                    try:self._handle(name,value)
                    except Exception as error:self._error(str(error))
                now=time.monotonic();self._tick(now)
                if now-last_publish>=1/30:self._publish();last_publish=now
                self.shutdown.wait(.004)
        finally:
            self._stop();self._close_native();self._close_kernel_input();self.source_monitor.close();self._publish()
