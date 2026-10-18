#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""构建后自检：结构 / minSdk / 新 Activity / dex3 / 签名"""
import sys, os, zipfile, struct
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import axml

def check(path):
    ok, fail = [], []
    def t(cond, good, bad):
        (ok if cond else fail).append(good if cond else bad)

    z = zipfile.ZipFile(path)
    t(z.testzip() is None, 'ZIP 完整', 'ZIP 损坏')
    names = z.namelist()
    t(z.getinfo('resources.arsc').compress_type == 0, 'resources.arsc STORED', 'resources.arsc 非 STORED')
    t('classes3.dex' in names, 'classes3.dex 存在', 'classes3.dex 缺失')

    for d in ('classes.dex', 'classes2.dex', 'classes3.dex'):
        if d in names:
            t(z.read(d)[:8] == b'dex\n035\x00', '%s 魔数正确' % d, '%s 魔数错误' % d)

    m = z.read('AndroidManifest.xml')
    strings, _, chunks = axml.parse(m)
    pkg = minsdk = None
    acts = []
    for typ, hsz, size, off in chunks:
        if typ != 0x0102:
            continue
        nm = strings[axml._u32(m, off + 20)]
        aS = axml._u16(m, off + 24); aZ = axml._u16(m, off + 26); aC = axml._u16(m, off + 28)
        attrs = {}
        for i in range(aC):
            ao = off + 16 + aS + i * aZ
            an = strings[axml._u32(m, ao + 4)]
            dt = m[ao + 15]; dv = axml._u32(m, ao + 16)
            attrs[an] = strings[dv] if dt == 3 and dv < len(strings) else dv
        if nm == 'manifest':
            pkg = attrs.get('package')
        elif nm == 'uses-sdk':
            minsdk = attrs.get('minSdkVersion')
        elif nm == 'activity':
            acts.append(attrs.get('name'))

    t(pkg == 'com.chinasoul.bt', 'package = com.chinasoul.bt', 'package = %s' % pkg)
    t(minsdk == 21, 'minSdkVersion = 21', 'minSdkVersion = %s（应为 21）' % minsdk)
    need = ['com.yzb.btplus.PlusActivity', 'com.yzb.btplus.FeedActivity',
            'com.yzb.btplus.DetailActivity', 'com.yzb.btplus.SearchActivity',
            'com.yzb.btplus.PlayerActivity']
    miss = [x for x in need if x not in acts]
    t(not miss, '5 个新 Activity 齐全', '缺 Activity: %s' % miss)
    t('com.chinasoul.bt.NativeMainActivity' in acts, '原启动页保留', '原启动页丢失')

    d3 = z.read('classes3.dex')
    t(b'Lcom/yzb/btplus/FeedActivity;' in d3, 'dex3 含 FeedActivity', 'dex3 缺 FeedActivity')
    t(b'Lcom/yzb/btplus/PlayerActivity;' in d3, 'dex3 含 PlayerActivity', 'dex3 缺 PlayerActivity')

    print('APK: %s' % path)
    for s in ok:
        print('  \u2705 ' + s)
    for s in fail:
        print('  \u274c ' + s)
    print('通过 %d 项，失败 %d 项' % (len(ok), len(fail)))
    return 0 if not fail else 1

if __name__ == '__main__':
    sys.exit(check(sys.argv[1]))
