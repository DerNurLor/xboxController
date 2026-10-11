"""Validated, atomically persisted Companion settings, independent of the UI."""
import json
import os
import socket
import sys
import tempfile
from pathlib import Path

DEFAULTS = dict(wifi=True, bluetooth=False, bind='0.0.0.0', port=39570,
                name=socket.gethostname(), backend='linux' if sys.platform.startswith('linux') else 'windows' if sys.platform=='win32' else 'dry-run',
                trace_feedback=False)


def validate(values):
    config = {**DEFAULTS, **values}
    config['port'] = int(config['port'])
    if not 1 <= config['port'] <= 65535: raise ValueError('UDP port must be 1–65535')
    socket.inet_pton(socket.AF_INET, config['bind'])
    if config['backend'] not in ('linux', 'windows', 'dry-run'): raise ValueError('Unknown input backend')
    if not str(config['name']).strip(): raise ValueError('Computer name is required')
    for key in ('wifi', 'bluetooth', 'trace_feedback'):
        if not isinstance(config[key], bool): raise ValueError(key + ' must be true or false')
    return config


def load(path):
    try: return validate(json.loads(Path(path).read_text()))
    except FileNotFoundError: return dict(DEFAULTS)


def save(path, values):
    values = validate(values)
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    fd, temporary = tempfile.mkstemp(dir=path.parent, prefix='.settings-')
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(values, stream, indent=2); stream.flush(); os.fsync(stream.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary): os.unlink(temporary)
    return values
