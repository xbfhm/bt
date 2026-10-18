#!/usr/bin/env python3
"""
BT · API 22 (Android 5.1.1) 兼容性补丁

针对反编译后的 smali 目录做三件事：
  1. 在 La/a0; 里新增 D(Context,String) -> int 的兼容方法
     - SDK >= 23 : 调 Context.checkSelfPermission
     - SDK <  23 : 调 Context.checkPermission(perm, myPid(), myUid())
  2. 把所有 Context.checkSelfPermission 调用改到 a0.D
  3. 把所有 Context.getSystemService(Class) 改成 getSystemService(String)
     （const-class NotificationManager -> const-string "notification"）

用法: python3 patch_api22.py <反编译根目录>   # 该目录下应有 smali1/ smali2/
"""
import os
import re
import shutil
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else '.'
SM1 = os.path.join(ROOT, 'smali1')
SM2 = os.path.join(ROOT, 'smali2')
report = []


def backup(p):
    b = p + '.orig22'
    if not os.path.exists(b):
        shutil.copy2(p, b)



# 注意：本段必须在「新增 D()/E()」之前执行，否则 E() 内部的
# Context.checkSelfPermission 会被一起替换成 a0.D，造成 D->E->D 无限递归。

# ---------------- 2) checkSelfPermission -> a0.D ----------------
pat = re.compile(
    r'invoke-virtual \{([vp]\d+), ([vp]\d+)\}, '
    r'Landroid/content/Context;->checkSelfPermission\(Ljava/lang/String;\)I'
)
for dirpath, _, files in os.walk(SM1):
    for fn in files:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dirpath, fn)
        s = open(p, encoding='utf-8').read()
        n = len(pat.findall(s))
        if n:
            s2 = pat.sub(
                lambda m: 'invoke-static {%s, %s}, '
                          'La/a0;->D(Landroid/content/Context;Ljava/lang/String;)I'
                          % (m.group(1), m.group(2)), s)
            backup(p)
            open(p, 'w', encoding='utf-8').write(s2)
            report.append('%s: %d 处 checkSelfPermission -> a0.D' % (p, n))

# ---------------- 3) getSystemService(Class) -> (String) ----------------
gs_pat = re.compile(
    r'invoke-virtual \{([vp]\d+), ([vp]\d+)\}, '
    r'Landroid/content/Context;->getSystemService\(Ljava/lang/Class;\)Ljava/lang/Object;'
)
CC_PAT = re.compile(r'const-class (v\d+), Landroid/app/NotificationManager;')
for dirpath, _, files in os.walk(SM1):
    for fn in files:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dirpath, fn)
        s = open(p, encoding='utf-8').read()
        if not gs_pat.search(s):
            continue
        s2, n1 = CC_PAT.subn(r'const-string \1, "notification"', s)
        s2, n2 = gs_pat.subn(
            r'invoke-virtual {\1, \2}, '
            r'Landroid/content/Context;->getSystemService(Ljava/lang/String;)Ljava/lang/Object;', s2)
        backup(p)
        open(p, 'w', encoding='utf-8').write(s2)
        report.append('%s: const-class->const-string x%d, getSystemService x%d'
                      % (p, n1, n2))

# ---------------- 1) a0 增加 D() / E() ----------------
A0 = os.path.join(SM1, 'a/a0.smali')
src = open(A0, encoding='utf-8').read()
if '->D(Landroid/content/Context;Ljava/lang/String;)I' not in src:
    NEW = '''
.method public static synthetic D(Landroid/content/Context;Ljava/lang/String;)I
    .registers 4

    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I

    const/16 v1, 0x17

    if-lt v0, v1, :cond_old

    invoke-static {p0, p1}, La/a0;->E(Landroid/content/Context;Ljava/lang/String;)I

    move-result v0

    return v0

    :cond_old
    invoke-static {}, Landroid/os/Process;->myPid()I

    move-result v0

    invoke-static {}, Landroid/os/Process;->myUid()I

    move-result v1

    invoke-virtual {p0, p1, v0, v1}, Landroid/content/Context;->checkPermission(Ljava/lang/String;II)I

    move-result v0

    return v0
.end method

.method public static bridge synthetic E(Landroid/content/Context;Ljava/lang/String;)I
    .registers 2

    invoke-virtual {p0, p1}, Landroid/content/Context;->checkSelfPermission(Ljava/lang/String;)I

    move-result p0

    return p0
.end method
'''
    m = re.search(r'^# direct methods\s*$', src, re.M)
    assert m, '# direct methods 标记未找到'
    src = src[:m.end()] + '\n' + NEW + src[m.end():]
    backup(A0)
    open(A0, 'w', encoding='utf-8').write(src)
    report.append('a0: 新增 D()/E() 兼容方法')
else:
    report.append('a0: D() 已存在，跳过')

# ---------------- 4) requestPermissions 加 SDK 守卫 ----------------
REQ_PAT = re.compile(
    r'(\.method public static y\(Landroid/app/Activity;\[Ljava/lang/String;I\)V\n'
    r'    \.registers \d+\n)'
)
for dirpath, _, files in os.walk(SM1):
    for fn in files:
        if not fn.endswith('.smali'):
            continue
        p = os.path.join(dirpath, fn)
        s = open(p, encoding='utf-8').read()
        if 'Activity;->requestPermissions([Ljava/lang/String;I)V' not in s:
            continue
        if 'cond_sdk23ok' in s:
            report.append('%s: requestPermissions 已有守卫，跳过' % p)
            continue
        s2, n = REQ_PAT.subn(
            r'\1\n'
            '    sget v0, Landroid/os/Build$VERSION;->SDK_INT:I\n\n'
            '    const/16 v1, 0x17\n\n'
            '    if-lt v0, v1, :cond_sdk23ok\n\n'
            '    return-void\n\n'
            '    :cond_sdk23ok\n', s)
        if n:
            backup(p)
            open(p, 'w', encoding='utf-8').write(s2)
            report.append('%s: requestPermissions 加 SDK<23 短路' % p)

print('=== 补丁报告 ===')
for r in report:
    print(' -', r)
