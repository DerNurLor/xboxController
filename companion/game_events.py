"""Local, token-authenticated plugin API. Data only: never executes plugin code."""
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import threading
import time
from http.server import BaseHTTPRequestHandler, HTTPServer

class GameStates:
    def __init__(self):self.values={};self.lock=threading.Lock()
    def publish(self,payload,now=None):
        now=time.monotonic() if now is None else now
        if not isinstance(payload,dict) or set(payload)-{'state','ttl_ms'}:raise ValueError('Use state and ttl_ms')
        state=payload.get('state');ttl=payload.get('ttl_ms',2000)
        if not isinstance(state,dict) or len(state)>16 or type(ttl) is not int or not 100<=ttl<=30000:raise ValueError('Invalid state or TTL')
        clean={}
        for key,value in state.items():
            if not isinstance(key,str) or not re.fullmatch(r'[A-Za-z0-9_.-]{1,48}',key) or key.startswith('input.'):raise ValueError('Invalid state name')
            if value is None:clean[key]=None;continue
            if type(value) not in (str,int,float,bool):raise ValueError('State values must be scalar')
            text=str(value).lower() if isinstance(value,bool) else str(value)
            if not re.fullmatch(r'[A-Za-z0-9_.-]{1,64}',text):raise ValueError('Invalid state value')
            clean[key]=(text,now+ttl/1000)
        with self.lock:
            updated={k:v for k,v in self.values.items() if v[1]>now}
            for k,v in clean.items():
                if v is None:updated.pop(k,None)
                else:updated[k]=v
            if len(updated)>16 or len(json.dumps({k:v[0] for k,v in updated.items()},separators=(',',':')).encode())>900:raise ValueError('State snapshot is too large')
            self.values=updated
    def snapshot(self,now=None):
        now=time.monotonic() if now is None else now
        with self.lock:return json.dumps({k:v[0] for k,v in self.values.items() if v[1]>now},separators=(',',':')).encode()

class GameEventServer:
    def __init__(self,path=None):
        self.states=GameStates();self.token=secrets.token_hex(32)
        owner=self
        class Handler(BaseHTTPRequestHandler):
            def do_POST(self):
                if self.path!='/state':self.send_error(404);return
                if not hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+owner.token):self.send_error(401);return
                try:
                    size=int(self.headers.get('Content-Length','0'))
                    if not 0<size<=8192:raise ValueError('Invalid body size')
                    owner.states.publish(json.loads(self.rfile.read(size)))
                except (ValueError,UnicodeError):self.send_error(400);return
                self.send_response(204);self.end_headers()
            def setup(self):super().setup();self.connection.settimeout(2)
            def log_message(self,*args):pass
        self.server=HTTPServer(('127.0.0.1',0),Handler)
        self.path=Path(path) if path else Path.home()/'.config/phonepad/game-events.json'
        self.path.parent.mkdir(parents=True,exist_ok=True)
        # A per-instance file avoids collisions between companion instances.
        self.path=self.path.with_name(self.path.stem+'-'+str(self.server.server_port)+self.path.suffix)
        fd=os.open(self.path,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'w') as f:json.dump({'url':f'http://127.0.0.1:{self.server.server_port}/state','token':self.token},f)
        self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start()
    def close(self):
        self.server.shutdown();self.server.server_close();self.thread.join(timeout=3);self.path.unlink(missing_ok=True)
