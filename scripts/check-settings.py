#!/usr/bin/env python3
"""Check persisted scalar coverage against the reviewed settings destination inventory."""
from pathlib import Path
import json,re,subprocess
root=Path(__file__).resolve().parents[1]
rows=json.loads((root/'settings-inventory.json').read_text())
seen=set()
for row in rows:
    key=(row['scope'],row['storage'],row['key'])
    assert key not in seen,('duplicate setting',key)
    seen.add(key)
    assert row['category'] and row['storage']
for scope,cls in [('layout','ControllerConfig'),('control','ControllerConfig$Control')]:
    raw=subprocess.check_output(['javap','-public','-classpath',str(root/'build/tests'),'dev.phonepad.'+cls],text=True)
    fields=set(re.findall(r'public (?:final )?(?:java.lang.String|boolean|float|int) (\w+);',raw))
    listed={r['key'] for r in rows if r['scope']==scope}
    assert fields==listed,(scope,'missing',fields-listed,'stale',listed-fields)
ui=(root/'app/src/main/java/dev/phonepad/ControllerSettings.java').read_text()
for category in {row['category'] for row in rows}:
    assert category in ui,('missing category',category)
print(f'Settings inventory passed: {len(rows)} persisted fields; layout/control schema and destinations covered')
