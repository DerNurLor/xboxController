#!/usr/bin/env python3
"""Example data-only game plugin client; adapt publication to your game's actual events."""
import argparse
import json
from pathlib import Path
import urllib.request

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--connection',type=Path,required=True,help='Game plugin API file printed by the companion')
    parser.add_argument('--ttl-ms',type=int,default=2000)
    parser.add_argument('states',nargs='+',metavar='NAME=VALUE')
    args=parser.parse_args()
    state={}
    for pair in args.states:
        name,separator,value=pair.partition('=')
        if not separator:parser.error('Use NAME=VALUE')
        state[name]=None if value=='null' else value
    connection=json.loads(args.connection.read_text())
    request=urllib.request.Request(connection['url'],json.dumps({'state':state,'ttl_ms':args.ttl_ms}).encode(),
        {'Authorization':'Bearer '+connection['token'],'Content-Type':'application/json'},method='POST')
    with urllib.request.urlopen(request,timeout=3) as response:
        print('State published' if response.status==204 else f'HTTP {response.status}')

if __name__=='__main__':main()
