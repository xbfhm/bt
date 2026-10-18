#!/usr/bin/env python3
"""
BT · 重打包（改 versionName + 换 dex + 去旧签名）

用法: python3 repack.py <work目录> <原APK> <versionName>
"""
import os
import sys
import zipfile

WORK = sys.argv[1]
SRC = sys.argv[2]
VNAME = sys.argv[3] if len(sys.argv) > 3 else '0.9.41'

MAN = os.path.join(WORK, 'x/AndroidManifest.xml')
OUT = os.path.join(WORK, 'bt_tv_unsigned.apk')

# ---- 1) 改 versionName（AXML 里是 UTF-16LE，等长替换） ----
d = bytearray(open(MAN, 'rb').read())
orig_name = None
for cand in ('0.9.40', '0.9.39', '0.9.41', '0.9.42'):
    if cand.encode('utf-16-le') in d:
        orig_name = cand
        break
if orig_name and orig_name != VNAME and len(orig_name) == len(VNAME):
    o = orig_name.encode('utf-16-le')
    n = VNAME.encode('utf-16-le')
    i = d.find(o)
    d[i:i + len(o)] = n
    open(MAN, 'wb').write(bytes(d))
    print('versionName: %s -> %s @ %d' % (orig_name, VNAME, i))
else:
    print('versionName 保持不变（原=%s 目标=%s）' % (orig_name, VNAME))

# ---- 2) 打包 ----
replace = {
    'AndroidManifest.xml': open(MAN, 'rb').read(),
    'classes.dex': open(os.path.join(WORK, 'out1.dex'), 'rb').read(),
}
p2 = os.path.join(WORK, 'out2.dex')
if os.path.exists(p2):
    replace['classes2.dex'] = open(p2, 'rb').read()

zin = zipfile.ZipFile(SRC, 'r')
if os.path.exists(OUT):
    os.remove(OUT)
zout = zipfile.ZipFile(OUT, 'w')
STORE_ALWAYS = {'resources.arsc'}
skipped = 0
for zi in zin.infolist():
    name = zi.filename
    if name.startswith('META-INF/') and name.upper().endswith(
            ('.RSA', '.SF', '.MF', '.EC')):
        skipped += 1
        continue
    data = replace.get(name)
    if data is None:
        data = zin.read(name)
    ctype = (zipfile.ZIP_STORED
             if name in STORE_ALWAYS or zi.compress_type == zipfile.ZIP_STORED
             else zipfile.ZIP_DEFLATED)
    nzi = zipfile.ZipInfo(name, date_time=zi.date_time)
    nzi.compress_type = ctype
    nzi.external_attr = zi.external_attr
    nzi.internal_attr = zi.internal_attr
    nzi.create_system = zi.create_system
    zout.writestr(nzi, data)
zout.close()
zin.close()
print('已跳过旧签名条目: %d' % skipped)
print('输出: %s (%d 字节)' % (OUT, os.path.getsize(OUT)))
