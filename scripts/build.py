#!/usr/bin/env python3
"""Build a debug APK with the official Android CLI tools; no Gradle download required."""
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT=Path(__file__).resolve().parents[1]
BUILD=ROOT/'build'


def run(*args):
    subprocess.run([str(a) for a in args],check=True,cwd=ROOT)


def main():
    sdk=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if sdk and (Path(sdk)/'platforms/android-35/android.jar').exists():
        sdk=Path(sdk)
        android=sdk/'platforms/android-35/android.jar'
        tools=sdk/'build-tools/35.0.0'
    else:
        android=next((ROOT/'.tools/platform').glob('*/android.jar'))
        tools=next((ROOT/'.tools/build-tools').glob('*/aapt2')).parent
    for name in ('aapt2','zipalign','apksigner','d8'):
        p=tools/name
        if not os.access(p,os.X_OK):p.chmod(p.stat().st_mode|0o111)
    BUILD.mkdir(exist_ok=True)
    for name in ('classes','generated','dex'):
        d=BUILD/name
        if d.exists():shutil.rmtree(d)
        d.mkdir()
    run(tools/'aapt2','compile','--dir',ROOT/'app/src/main/res','-o',BUILD/'resources.zip')
    run(tools/'aapt2','link','-o',BUILD/'base.apk','--manifest',ROOT/'app/src/main/AndroidManifest.xml',
        '-I',android,'--java',BUILD/'generated','--min-sdk-version','28','--target-sdk-version','35',
        '--version-code','1','--version-name','0.1.0','--debug-mode',BUILD/'resources.zip')
    sources=sorted((ROOT/'app/src/main/java').rglob('*.java'))+sorted((BUILD/'generated').rglob('*.java'))
    run('javac','--release','17','-Xlint:unchecked','-cp',android,'-d',BUILD/'classes',*sources)
    run('jar','--create','--file',BUILD/'classes.jar','-C',BUILD/'classes','.')
    run(tools/'d8','--lib',android,'--min-api','28','--output',BUILD/'dex',BUILD/'classes.jar')
    with zipfile.ZipFile(BUILD/'base.apk','a',compression=zipfile.ZIP_DEFLATED) as z:
        for dex in (BUILD/'dex').glob('*.dex'):z.write(dex,dex.name)
    run(tools/'zipalign','-f','4',BUILD/'base.apk',BUILD/'aligned.apk')
    key=ROOT/'.tools/phonepad-debug.jks'
    key.parent.mkdir(exist_ok=True)
    if not key.exists():
        run('keytool','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','androiddebugkey',
            '-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=PhonePad Debug,O=Development,C=US')
    run(tools/'apksigner','sign','--ks',key,'--ks-pass','pass:android','--out',BUILD/'PhonePad-debug.apk',BUILD/'aligned.apk')
    run(tools/'apksigner','verify','--verbose',BUILD/'PhonePad-debug.apk')
    print(BUILD/'PhonePad-debug.apk')


if __name__=='__main__':main()
