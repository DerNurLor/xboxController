#!/usr/bin/env python3
"""Download pinned official Android packages to this workspace and verify their repository SHA-1."""
import hashlib
from pathlib import Path
import subprocess
import zipfile

root=Path(__file__).resolve().parents[1]/'.tools'
root.mkdir(exist_ok=True)
packages=(('platform','platform-35_r02.zip','0bb560a90a7a2cbd0dd8348224d518b638fe7949'),
          ('build-tools','build-tools_r35_linux.zip','2cfaa0bbb2336e9ec18ed3ecea84fa2e2af607bc'))
for folder,name,digest in packages:
    archive=root/name
    if not archive.exists() or hashlib.sha1(archive.read_bytes()).hexdigest()!=digest:
        subprocess.run(['curl','-fL','--retry','3','-o',str(archive),'https://dl.google.com/android/repository/'+name],check=True)
    if hashlib.sha1(archive.read_bytes()).hexdigest()!=digest:raise SystemExit('Checksum mismatch: '+name)
    with zipfile.ZipFile(archive) as z:z.extractall(root/folder)
print('Build tools ready. See the SDK license files in .tools before redistributing SDK components.')
