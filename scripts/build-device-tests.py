#!/usr/bin/env python3
from pathlib import Path
import subprocess
import zipfile
root=Path(__file__).resolve().parents[1]
tools=next((root/'.tools/build-tools').glob('*/aapt2')).parent
android=next((root/'.tools/platform').glob('*/android.jar'))
out=root/'build/device-tests'
out.mkdir(parents=True,exist_ok=True)
(out/'classes').mkdir(exist_ok=True)
(out/'dex').mkdir(exist_ok=True)
def run(*args):subprocess.run([str(a) for a in args],check=True)
run(tools/'aapt2','link','-o',out/'base.apk','--manifest',root/'tests/android/AndroidManifest.xml','-I',android)
run('javac','--release','17','-cp',str(android)+':'+str(root/'build/classes'),'-d',out/'classes',root/'tests/android/DeviceTests.java')
run('jar','--create','--file',out/'classes.jar','-C',out/'classes','.')
run(tools/'d8','--lib',android,'--classpath',root/'build/classes.jar','--min-api','28','--output',out/'dex',out/'classes.jar')
with zipfile.ZipFile(out/'base.apk','a') as z:z.write(out/'dex/classes.dex','classes.dex')
run(tools/'zipalign','-f','4',out/'base.apk',out/'aligned.apk')
run(tools/'apksigner','sign','--ks',root/'.tools/phonepad-debug.jks','--ks-pass','pass:android','--out',out/'PhonePad-tests.apk',out/'aligned.apk')
print(out/'PhonePad-tests.apk')
