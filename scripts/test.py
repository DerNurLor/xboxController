#!/usr/bin/env python3
from pathlib import Path
import subprocess

root=Path(__file__).resolve().parents[1]
dest=root/'build/tests'
dest.mkdir(parents=True,exist_ok=True)
src=root/'app/src/main/java/dev/phonepad'
subprocess.run(['javac','-d',str(dest),*[str(src/n) for n in ('PadState.java','HidDescriptor.java','WifiPad.java','ControllerConfig.java','ControllerInput.java','GyroFilter.java')],str(root/'tests/CoreTest.java'),str(root/'tests/InputTest.java')],check=True)
out=subprocess.check_output(['java','-cp',str(dest),'dev.phonepad.CoreTest'],text=True)
(root/'build/core-tests.txt').write_text(out)
print(out,end='')
subprocess.run(['python3','-m','unittest','discover','-s','tests','-v'],cwd=root,check=True)

subprocess.run(['java','-cp',str(dest),'dev.phonepad.InputTest'],check=True)
