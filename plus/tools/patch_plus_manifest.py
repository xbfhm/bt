#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""在原版 AndroidManifest.xml 上：minSdk 23->21，追加 5 个新 Activity"""
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import axml

ACTS = [
    {'name': 'com.yzb.btplus.PlusActivity', 'label': '\u514d\u8d39\u5f71\u89c6\u5e93',
     'launcher': False, 'orientation': 'landscape'},
    {'name': 'com.yzb.btplus.FeedActivity', 'label': '\u5237\u89c6\u9891',
     'launcher': False, 'orientation': 'landscape'},
    {'name': 'com.yzb.btplus.DetailActivity', 'launcher': False, 'orientation': 'landscape'},
    {'name': 'com.yzb.btplus.SearchActivity', 'launcher': False, 'orientation': 'landscape'},
    {'name': 'com.yzb.btplus.PlayerActivity', 'launcher': False, 'orientation': 'landscape'},
    {'name': 'com.yzb.btplus.LogActivity', 'label': '\u65e5\u5fd7', 'launcher': False, 'orientation': 'landscape'},
]

if __name__ == '__main__':
    src, dst = sys.argv[1], sys.argv[2]
    n = axml.rebuild(src, dst, ACTS)
    print('manifest 重建完成，strings=%d -> %s' % (n, dst))
