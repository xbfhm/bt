#!/usr/bin/env python3
"""
AXML (二进制 AndroidManifest.xml) 最小修改器

只做一件事：把 <uses-sdk android:minSdkVersion="N"> 改成指定值。
不重新序列化整个文件，只改 typedValue.data 的 4 字节，保证零副作用。

用法: python3 patch_axml.py <AndroidManifest.xml> <newMinSdk>
"""
import struct
import sys

RES_XML_TYPE = 0x0003
RES_STRING_POOL_TYPE = 0x0001
RES_XML_START_ELEMENT_TYPE = 0x0102
ATTR_TYPE_INT_DEC = 0x10
ATTR_TYPE_INT_HEX = 0x11


def parse_string_pool(data, off):
    """返回该 chunk 内的字符串列表"""
    hsize = struct.unpack_from('<H', data, off + 2)[0]
    str_count = struct.unpack_from('<I', data, off + 8)[0]
    flags = struct.unpack_from('<I', data, off + 16)[0]
    strings_start = struct.unpack_from('<I', data, off + 20)[0]
    is_utf8 = bool(flags & 0x100)

    offsets = [struct.unpack_from('<I', data, off + 28 + i * 4)[0]
               for i in range(str_count)]
    base = off + strings_start
    out = []
    for o in offsets:
        p = base + o
        if is_utf8:
            p += 1                       # utf16 len (忽略)
            n = data[p]; p += 1
            if n & 0x80:
                n = ((n & 0x7f) << 8) | data[p]; p += 1
            out.append(bytes(data[p:p + n]).decode('utf-8', 'replace'))
        else:
            n = struct.unpack_from('<H', data, p)[0]; p += 2
            if n & 0x8000:
                n = ((n & 0x7fff) << 16) | struct.unpack_from('<H', data, p)[0]
                p += 2
            out.append(bytes(data[p:p + n * 2]).decode('utf-16-le', 'replace'))
    return out


def iter_chunks(data):
    off = 8                       # 跳过 ResChunk_header (type/size)
    n = len(data)
    while off + 8 <= n:
        ctype = struct.unpack_from('<H', data, off)[0]
        hsize = struct.unpack_from('<H', data, off + 2)[0]
        csize = struct.unpack_from('<I', data, off + 4)[0]
        if csize <= 0:
            break
        yield off, ctype, hsize, csize
        off += csize


def patch(path, new_sdk):
    data = bytearray(open(path, 'rb').read())

    # ---- 1. 找字符串池 ----
    strings = None
    for off, ctype, hsize, csize in iter_chunks(data):
        if ctype == RES_STRING_POOL_TYPE:
            strings = parse_string_pool(data, off)
            break
    if strings is None:
        raise SystemExit('字符串池未找到')

    def s(idx):
        return strings[idx] if 0 <= idx < len(strings) else ''

    # ---- 2. 遍历 START_ELEMENT，找 uses-sdk ----
    changed = 0
    for off, ctype, hsize, csize in iter_chunks(data):
        if ctype != RES_XML_START_ELEMENT_TYPE:
            continue
        # ResXMLTree_attrExt 紧跟在 header 之后
        # 结构: ns(4) name(4) attributeStart(2) attributeSize(2)
        #       attributeCount(2) idIndex(2) classIndex(2) styleIndex(2)
        ext = off + hsize
        tag_name = s(struct.unpack_from('<I', data, ext + 4)[0])
        if tag_name != 'uses-sdk':
            continue

        attr_start = struct.unpack_from('<H', data, ext + 8)[0]
        attr_size = struct.unpack_from('<H', data, ext + 10)[0]
        attr_count = struct.unpack_from('<H', data, ext + 12)[0]
        p = ext + attr_start
        for _ in range(attr_count):
            a_ns = struct.unpack_from('<I', data, p)[0]
            a_name = s(struct.unpack_from('<I', data, p + 4)[0])
            a_raw = struct.unpack_from('<I', data, p + 8)[0]
            a_vsize = struct.unpack_from('<H', data, p + 12)[0]
            a_res0 = data[p + 14]
            a_type = data[p + 15]
            a_data = struct.unpack_from('<I', data, p + 16)[0]

            if a_name == 'minSdkVersion' and a_type in (ATTR_TYPE_INT_DEC, ATTR_TYPE_INT_HEX):
                if a_data != new_sdk:
                    struct.pack_into('<I', data, p + 16, new_sdk)
                    print('minSdkVersion: %d -> %d @ 0x%X' % (a_data, new_sdk, p + 16))
                    changed += 1
                else:
                    print('minSdkVersion 已经是 %d，无需修改' % new_sdk)
            p += attr_size

    if changed == 0 and not any(
            s(struct.unpack_from('<I', data, off + hsize + 4)[0]) == 'uses-sdk'
            for off, ctype, hsize, csize in iter_chunks(data)
            if ctype == RES_XML_START_ELEMENT_TYPE):
        raise SystemExit('未找到 uses-sdk')

    open(path, 'wb').write(bytes(data))
    return changed


if __name__ == '__main__':
    f = sys.argv[1]
    sdk = int(sys.argv[2]) if len(sys.argv) > 2 else 21
    n = patch(f, sdk)
    print('完成，修改 %d 处' % n)
