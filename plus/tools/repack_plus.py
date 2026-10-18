#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把新 manifest + classes3.dex 打进原 APK"""
import sys, zipfile, os

def main(src_apk, manifest, dex3, out_apk):
    zin = zipfile.ZipFile(src_apk)
    zout = zipfile.ZipFile(out_apk, 'w', zipfile.ZIP_DEFLATED)
    man = open(manifest, 'rb').read()
    d3 = open(dex3, 'rb').read()
    for info in zin.infolist():
        n = info.filename
        if n.startswith('META-INF/'):
            continue
        data = man if n == 'AndroidManifest.xml' else zin.read(n)
        zi = zipfile.ZipInfo(n, date_time=info.date_time)
        zi.compress_type = info.compress_type
        zi.external_attr = info.external_attr
        zout.writestr(zi, data)
    zi = zipfile.ZipInfo('classes3.dex', date_time=(2026, 10, 18, 8, 0, 0))
    zi.compress_type = zipfile.ZIP_DEFLATED
    zi.external_attr = 0o644 << 16
    zout.writestr(zi, d3)
    zout.close(); zin.close()
    print('repacked -> %s (%d bytes)' % (out_apk, os.path.getsize(out_apk)))

if __name__ == '__main__':
    main(*sys.argv[1:5])
