#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""把 SidebarInjector.schedule(this) 注入 NativeMainActivity.onCreate 开头。"""
import io, os, sys

TARGET = 'com/chinasoul/bt/NativeMainActivity.smali'
ANCHOR = 'invoke-super {p0, p1}, Landroid/app/Activity;->onCreate(Landroid/os/Bundle;)V'
CALL = 'invoke-static {p0}, Lcom/yzb/btplus/SidebarInjector;->schedule(Landroid/app/Activity;)V'
MARK = 'SidebarInjector;->schedule'


def find(root):
    for d in ('smali1', 'smali2'):
        p = os.path.join(root, d, TARGET)
        if os.path.isfile(p):
            return p
    return None


def main():
    root = sys.argv[1]
    p = find(root)
    if not p:
        print('未找到 NativeMainActivity.smali'); return 1
    s = io.open(p, encoding='utf-8').read()
    if MARK in s:
        print('已注入，跳过'); return 0
    i = s.find(ANCHOR)
    if i < 0:
        print('未找到 onCreate 锚点'); return 1
    j = s.find('\n', i)
    s = s[:j + 1] + '\n    ' + CALL + '\n' + s[j + 1:]
    io.open(p, 'w', encoding='utf-8').write(s)
    print('已注入 %s' % p)
    return 0


if __name__ == '__main__':
    sys.exit(main())
