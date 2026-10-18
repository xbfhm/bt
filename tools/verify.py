#!/usr/bin/env python3
"""
构建后自检 —— 任何一项不通过就 exit 1，阻止产出坏包。

用法: python3 verify.py <apk> <work目录>
"""
import struct
import sys
import zipfile

APK = sys.argv[1]
WORK = sys.argv[2] if len(sys.argv) > 2 else 'build'

fails = []
oks = []


def ok(m):
    oks.append(m)
    print('  ✅', m)


def bad(m):
    fails.append(m)
    print('  ❌', m)


z = zipfile.ZipFile(APK)
names = z.namelist()
print('APK:', APK, '| entries:', len(names))

# ---- 1) resources.arsc 必须 STORED ----
zi = z.getinfo('resources.arsc')
if zi.compress_type == 0:
    ok('resources.arsc 保持 STORED')
else:
    bad('resources.arsc 被压缩了（应为 STORED）')

# ---- 2) dex 魔数 ----
for n in ('classes.dex', 'classes2.dex'):
    if n not in names:
        bad('%s 缺失' % n)
        continue
    magic = z.read(n)[:8]
    if magic[:4] == b'dex\n':
        ok('%s 魔数正确 (%s)' % (n, magic.hex()))
    else:
        bad('%s 魔数异常: %s' % (n, magic.hex()))

# ---- 3) minSdk ----
ax = z.read('AndroidManifest.xml')
# 复用 patch_axml 的解析逻辑做只读检查
def read_min_sdk(data):
    RES_SP = 0x0001
    RES_SE = 0x0102
    off = 8
    strings = None
    n = len(data)
    chunks = []
    while off + 8 <= n:
        ct = struct.unpack_from('<H', data, off)[0]
        hs = struct.unpack_from('<H', data, off + 2)[0]
        cs = struct.unpack_from('<I', data, off + 4)[0]
        if cs <= 0:
            break
        chunks.append((off, ct, hs, cs))
        off += cs
    for off, ct, hs, cs in chunks:
        if ct == RES_SP:
            cnt = struct.unpack_from('<I', data, off + 8)[0]
            flags = struct.unpack_from('<I', data, off + 16)[0]
            sstart = struct.unpack_from('<I', data, off + 20)[0]
            is8 = bool(flags & 0x100)
            offs = [struct.unpack_from('<I', data, off + 28 + i * 4)[0] for i in range(cnt)]
            base = off + sstart
            strings = []
            for o in offs:
                p = base + o
                if is8:
                    p += 1
                    ln = data[p]; p += 1
                    if ln & 0x80:
                        ln = ((ln & 0x7f) << 8) | data[p]; p += 1
                    strings.append(bytes(data[p:p + ln]).decode('utf-8', 'replace'))
                else:
                    ln = struct.unpack_from('<H', data, p)[0]; p += 2
                    if ln & 0x8000:
                        ln = ((ln & 0x7fff) << 16) | struct.unpack_from('<H', data, p)[0]; p += 2
                    strings.append(bytes(data[p:p + ln * 2]).decode('utf-16-le', 'replace'))
            break
    if strings is None:
        return None
    def s(i):
        return strings[i] if 0 <= i < len(strings) else ''
    for off, ct, hs, cs in chunks:
        if ct != RES_SE:
            continue
        ext = off + hs
        if s(struct.unpack_from('<I', data, ext + 4)[0]) != 'uses-sdk':
            continue
        astart = struct.unpack_from('<H', data, ext + 8)[0]
        asize = struct.unpack_from('<H', data, ext + 10)[0]
        acount = struct.unpack_from('<H', data, ext + 12)[0]
        p = ext + astart
        for _ in range(acount):
            aname = s(struct.unpack_from('<I', data, p + 4)[0])
            atype = data[p + 15]
            adata = struct.unpack_from('<I', data, p + 16)[0]
            if aname == 'minSdkVersion':
                return adata
            p += asize
    return None

sdk = read_min_sdk(ax)
if sdk == 21:
    ok('minSdkVersion = 21')
elif sdk is None:
    bad('未找到 minSdkVersion')
else:
    bad('minSdkVersion = %s（应为 21）' % sdk)

# ---- 4) versionName ----
if '0.9.41'.encode('utf-16-le') in ax:
    ok('versionName = 0.9.41')
else:
    bad('versionName 不是 0.9.41')

# ---- 5) 源码级检查（在 work 目录的 smali 上）----
import os
a0 = os.path.join(WORK, 'x/smali1/a/a0.smali')
if os.path.exists(a0):
    src = open(a0, encoding='utf-8').read()
    # E() 必须直接调 Context.checkSelfPermission（不能递归到 D）
    i = src.find('.method public static bridge synthetic E(')
    j = src.find('.end method', i) if i >= 0 else -1
    if i >= 0 and j > i:
        body = src[i:j]
        if 'La/a0;->D(' in body:
            bad('a0.E() 递归调用 a0.D() —— 会导致无限递归崩溃')
        elif 'checkSelfPermission' in body:
            ok('a0.E() 直调 checkSelfPermission（无递归）')
        else:
            bad('a0.E() 方法体异常')
    else:
        bad('a0.E() 方法缺失')

    # D() 必须存在且含 checkPermission 降级
    i = src.find('.method public static synthetic D(')
    if i >= 0:
        j = src.find('.end method', i)
        body = src[i:j]
        if 'checkPermission' in body and 'myPid' in body:
            ok('a0.D() 含 API<23 降级路径')
        else:
            bad('a0.D() 缺少 checkPermission 降级')
    else:
        bad('a0.D() 方法缺失')
else:
    print('  ⚠️  跳过 smali 源码检查（%s 不存在）' % a0)

# ---- 6) dex 里不应残留裸调 getSystemService(Class) ----
for n in ('classes.dex', 'classes2.dex'):
    if n not in names:
        continue
    d = z.read(n)
    if b'(Ljava/lang/Class;)Ljava/lang/Object;' in d:
        # 可能被别的类用到，仅警告
        print('  ⚠️  %s 仍含 (Ljava/lang/Class;)Ljava/lang/Object; 原型' % n)
    else:
        ok('%s 无 getSystemService(Class) 原型' % n)

print()
print('=' * 50)
print('通过 %d 项，失败 %d 项' % (len(oks), len(fails)))
if fails:
    for f in fails:
        print('  ✗', f)
    sys.exit(1)
print('✅ 全部检查通过')
