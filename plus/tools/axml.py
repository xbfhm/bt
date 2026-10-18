# -*- coding: utf-8 -*-
"""极简 AXML 二进制读写（只做本任务需要的事：读字符串池、遍历元素、追加 activity 节点）"""
import struct

RES_STRING_POOL = 0x0001
RES_XML_START_NS = 0x0100
RES_XML_END_NS = 0x0101
RES_XML_START_EL = 0x0102
RES_XML_END_EL = 0x0103
RES_XML_RES_MAP = 0x0180
RES_XML_CDATA = 0x0104

NS_ANDROID = 'http://schemas.android.com/apk/res/android'


class AXMLError(Exception):
    pass


def _u16(d, o): return struct.unpack_from('<H', d, o)[0]
def _u32(d, o): return struct.unpack_from('<I', d, o)[0]


def parse_string_pool(d, off):
    typ, hsz, size = _u16(d, off), _u16(d, off + 2), _u32(d, off + 4)
    if typ != RES_STRING_POOL:
        raise AXMLError('not a string pool at %d' % off)
    count, styleCount, flags, stringsStart, stylesStart = struct.unpack_from('<IIIII', d, off + 8)
    utf8 = bool(flags & 0x100)
    offs = [_u32(d, off + 28 + 4 * i) for i in range(count)]
    base = off + stringsStart
    out = []
    for o in offs:
        p = base + o
        if utf8:
            n = d[p]; p += 1
            if n & 0x80:
                n = ((n & 0x7f) << 8) | d[p]; p += 1
            n2 = d[p]; p += 1
            if n2 & 0x80:
                n2 = ((n2 & 0x7f) << 8) | d[p]; p += 1
            out.append(d[p:p + n2].decode('utf-8', 'replace'))
        else:
            n = _u16(d, p); p += 2
            if n & 0x8000:
                n = ((n & 0x7fff) << 16) | _u16(d, p); p += 2
            out.append(d[p:p + n * 2].decode('utf-16-le', 'replace'))
    return out, utf8, off + size


def build_string_pool(strings, utf8=False):
    """构建字符串池（UTF-16 模式最简单可靠）"""
    data = b''
    offs = []
    for s in strings:
        offs.append(len(data))
        b = s.encode('utf-16-le')
        n = len(s)
        if n > 0x7FFF:
            data += struct.pack('<HH', (n >> 16) | 0x8000, n & 0xFFFF)
        else:
            data += struct.pack('<H', n)
        data += b
        data += b'\x00\x00'  # 结尾
    while len(data) % 4:
        data += b'\x00'
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    size = strings_start + len(data)
    out = struct.pack('<HHI', RES_STRING_POOL, header_size, size)
    out += struct.pack('<IIIII', len(strings), 0, 0, strings_start, 0)
    for o in offs:
        out += struct.pack('<I', o)
    out += data
    return out


def iter_chunks(d, start, end):
    p = start
    while p + 8 <= end:
        typ, hsz, size = _u16(d, p), _u16(d, p + 2), _u32(d, p + 4)
        if size <= 0 or p + size > end:
            break
        yield typ, hsz, size, p
        p += size


def parse(d):
    """返回 (strings, utf8, chunks[list of (typ,hsz,size,offset)])"""
    typ, hsz, size = _u16(d, 0), _u16(d, 2), _u32(d, 4)
    if typ != 0x0003:
        raise AXMLError('not AXML')
    strings, utf8, sp_end = parse_string_pool(d, 8)
    chunks = list(iter_chunks(d, 8, len(d)))
    return strings, utf8, chunks


def describe(path):
    d = open(path, 'rb').read()
    strings, utf8, chunks = parse(d)
    depth = 0
    for typ, hsz, size, off in chunks:
        if typ == RES_XML_START_EL:
            name = _u32(d, off + 20)
            nattr = _u16(d, off + 28)
            print('  ' * depth + '<%s> attrs=%d @%d' % (strings[name], nattr, off))
            depth += 1
        elif typ == RES_XML_END_EL:
            depth -= 1
            name = _u32(d, off + 20)
            print('  ' * depth + '</%s>' % strings[name])
        elif typ == RES_XML_START_NS:
            print('  ' * depth + 'NS %s=%s' % (strings[_u32(d, off + 16)], strings[_u32(d, off + 20)]))
    return strings, utf8, chunks, d


if __name__ == '__main__':
    import sys
    describe(sys.argv[1])


# ---------------- 重建 ----------------

def _attr(d, off):
    """解析一个属性：返回 dict"""
    ns, name, raw = _u32(d, off), _u32(d, off + 4), _u32(d, off + 8)
    size = _u16(d, off + 10)
    res0 = d[off + 12]
    dtype = d[off + 15]
    data = _u32(d, off + 16)
    return {'ns': ns, 'name': name, 'raw': raw, 'dtype': dtype, 'data': data, 'size': size}


def rebuild(path_in, path_out, add_activities, extra_strings=None, patch_attrs=None):
    """
    add_activities: list of dict {
        'name': 'com.x.y.Z',
        'label': str or None,
        'launcher': bool,          # 加 MAIN/LAUNCHER + LEANBACK
        'orientation': 'landscape' or None,
        'exported': True
    }
    patch_attrs: {'uses-sdk': {'minSdkVersion': 21}} 之类（按元素名改整数属性）
    """
    d = open(path_in, 'rb').read()
    strings, utf8, chunks = parse(d)

    # 1. 收集所有 chunk 的原始字节 + 需要解析的结构
    parsed = []
    for typ, hsz, size, off in chunks:
        if typ == RES_STRING_POOL:
            continue  # 字符串池单独重建，不作为普通 chunk
        raw = d[off:off + size]
        item = {'typ': typ, 'hsz': hsz, 'size': size, 'off': off, 'raw': raw}
        if typ == RES_XML_START_EL:
            # 结构: hsz(16) + ns, name, attrStart, attrSize, attrCount, idIndex, classIndex, styleIndex
            item['ns'] = _u32(d, off + 16)
            item['name'] = _u32(d, off + 20)
            attrStart = _u16(d, off + 24)
            attrSize = _u16(d, off + 26)
            attrCount = _u16(d, off + 28)
            item['attrs'] = []
            for i in range(attrCount):
                ao = off + 16 + attrStart + i * attrSize
                item['attrs'].append(_attr(d, ao))
            item['attrStart'] = attrStart
            item['attrSize'] = attrSize
        elif typ == RES_XML_END_EL:
            item['ns'] = _u32(d, off + 16)
            item['name'] = _u32(d, off + 20)
        elif typ == RES_XML_START_NS:
            item['prefix'] = _u32(d, off + 16)
            item['uri'] = _u32(d, off + 20)
        elif typ == RES_XML_END_NS:
            item['prefix'] = _u32(d, off + 16)
            item['uri'] = _u32(d, off + 20)
        parsed.append(item)

    # 2. 建立字符串表：原索引不变，新字符串追加
    str_list = list(strings)
    str_index = {}
    for i, s in enumerate(str_list):
        if s not in str_index:
            str_index[s] = i

    def intern(s):
        if s in str_index:
            return str_index[s]
        str_index[s] = len(str_list)
        str_list.append(s)
        return str_index[s]

    # 预置需要的字符串
    android_uri = NS_ANDROID
    intern(android_uri)
    for el in ('action', 'category', 'activity', 'intent-filter', 'screenOrientation',
               'exported', 'label', 'name'):
        intern(el)
    for v in ('android.intent.action.MAIN',
              'android.intent.category.LAUNCHER',
              'android.intent.category.LEANBACK_LAUNCHER',
              'landscape'):
        intern(v)
    for a in add_activities:
        intern(a['name'])
        if a.get('label'):
            intern(a['label'])
    if extra_strings:
        for s in extra_strings:
            intern(s)

    # 3. 找 application 的 start/end，确定插入位置（end 之前）
    app_start = None
    app_end = None
    depth = 0
    for i, it in enumerate(parsed):
        if it['typ'] == RES_XML_START_EL:
            if str_list[it['name']] == 'application' and depth == 1:
                app_start = i
            depth += 1
        elif it['typ'] == RES_XML_END_EL:
            depth -= 1
            if str_list[it['name']] == 'application' and depth == 1:
                app_end = i
                break

    if app_start is None or app_end is None:
        raise AXMLError('application element not found')

    # 4. 生成新节点（activity 子树）
    NS_A = 0xFFFFFFFF  # 无命名空间标记（在 serialize 时用 0xFFFFFFFF）

    def make_start_el(name, attrs, ns_idx=0xFFFFFFFF):
        """attrs: list of (ns_idx, name_str, dtype, data_int)"""
        intern(name)
        for (_, an, _, _) in attrs:
            intern(an)
        return {'typ': RES_XML_START_EL, 'ns': ns_idx, 'name': -1, 'name_s': name,
                'attrs': [{'ns': a[0], 'name': -1, 'name_s': a[1], 'raw': 0xFFFFFFFF,
                           'dtype': a[2], 'data': a[3], 'size': 20} for a in attrs],
                'attrStart': 20, 'attrSize': 20}

    def make_end_el(name, ns_idx=0xFFFFFFFF):
        return {'typ': RES_XML_END_EL, 'ns': ns_idx, 'name': -1, 'name_s': name}

    # 属性值类型
    T_STR = 0x03
    T_INT_DEC = 0x10
    T_INT_BOOL = 0x12

    new_nodes = []
    android_ns = str_index[android_uri]
    for a in add_activities:
        attrs = [
            (android_ns, 'name', T_STR, 0),      # data 稍后填
            (android_ns, 'exported', T_INT_BOOL, 1 if a.get('launcher') else 0),
        ]
        if a.get('label'):
            attrs.append((android_ns, 'label', T_STR, 0))
        if a.get('orientation') == 'landscape':
            attrs.append((android_ns, 'screenOrientation', T_INT_DEC, 0))
        st = make_start_el('activity', attrs)
        st['_fill'] = {'name': a['name'], 'label': a.get('label'),
                       'landscape': a.get('orientation') == 'landscape'}
        new_nodes.append(st)

        # intent-filter
        if a.get('launcher'):
            new_nodes.append(make_start_el('intent-filter', []))
            new_nodes.append(make_start_el('action', [(android_ns, 'name', T_STR, 0)]))
            new_nodes[-1]['_fill_action'] = 'android.intent.action.MAIN'
            new_nodes.append(make_end_el('action'))
            new_nodes.append(make_start_el('category', [(android_ns, 'name', T_STR, 0)]))
            new_nodes[-1]['_fill_action'] = 'android.intent.category.LAUNCHER'
            new_nodes.append(make_end_el('category'))
            new_nodes.append(make_start_el('category', [(android_ns, 'name', T_STR, 0)]))
            new_nodes[-1]['_fill_action'] = 'android.intent.category.LEANBACK_LAUNCHER'
            new_nodes.append(make_end_el('category'))
            new_nodes.append(make_end_el('intent-filter'))
        new_nodes.append(make_end_el('activity'))

    # 5. 序列化全部 chunk（字符串池重建后，所有字符串索引都要重算）
    def serialize(it):
        typ = it['typ']
        if typ == RES_XML_START_EL:
            name_i = str_index[it['name_s']] if 'name_s' in it else it['name']
            nattr = len(it['attrs'])
            hsz = 16
            body = b''
            for a in it['attrs']:
                an = str_index[a['name_s']] if 'name_s' in a else a['name']
                if '_fill_action' in it:
                    data = str_index[it['_fill_action']]
                elif it.get('_fill') and a['name_s'] == 'name':
                    data = str_index[it['_fill']['name']]
                elif it.get('_fill') and a['name_s'] == 'label':
                    data = str_index[it['_fill']['label']]
                elif it.get('_fill') and a['name_s'] == 'screenOrientation':
                    data = 0  # SCREEN_ORIENTATION_LANDSCAPE = 0
                else:
                    data = a['data']
                raw = data if a['dtype'] == 3 else 0xFFFFFFFF
                body += struct.pack('<IIIHBBI', a['ns'], an, raw,
                                    20, 0, a['dtype'], data)
            size = hsz + 4 + len(body)   # hsz + (ns,name,attrStart,attrSize,attrCount,3个idx 共 20)
            # 重新精确计算：header 16 字节(typ,hsz,size,lineNumber,comment) + ns,name,
            # attrStart,attrSize,attrCount,idIndex,classIndex,styleIndex = 16+4+4+2+2+2+2+2+2=36
            header = struct.pack('<HHI', typ, 16, 0) + struct.pack('<II', 0xFFFFFFFF, 0xFFFFFFFF)
            ext = struct.pack('<II', it['ns'], name_i)
            ext += struct.pack('<HHHHHH', 20, 20, nattr, 0, 0, 0)
            # 修正：attrStart/attrSize 相对 ext 起点(off+16)
            total = 16 + len(ext) + len(body)
            out = struct.pack('<HHI', typ, 16, total) + struct.pack('<II', 0xFFFFFFFF, 0xFFFFFFFF)
            out += ext + body
            return out
        if typ == RES_XML_END_EL:
            name_i = str_index[it['name_s']] if 'name_s' in it else it['name']
            total = 16 + 8
            return struct.pack('<HHI', typ, 16, total) + struct.pack('<II', 0xFFFFFFFF, 0xFFFFFFFF) \
                   + struct.pack('<II', it['ns'], name_i)
        if typ == RES_XML_START_NS:
            return struct.pack('<HHI', typ, 16, 24) + struct.pack('<II', 0xFFFFFFFF, 0xFFFFFFFF) \
                   + struct.pack('<II', str_index[str_list[it['prefix']]],
                                 str_index[str_list[it['uri']]])
        if typ == RES_XML_END_NS:
            return struct.pack('<HHI', typ, 16, 24) + struct.pack('<II', 0xFFFFFFFF, 0xFFFFFFFF) \
                   + struct.pack('<II', str_index[str_list[it['prefix']]],
                                 str_index[str_list[it['uri']]])
        if typ == RES_XML_RES_MAP:
            return it['raw']  # 资源映射原样保留
        if typ == RES_XML_CDATA:
            return it['raw']
        return it['raw']

    out_chunks = []
    for i, it in enumerate(parsed):
        if i == app_end:
            for n in new_nodes:
                out_chunks.append(serialize(n))
        if it['typ'] == RES_XML_START_EL and 'name_s' not in it:
            it = dict(it)
            it['name_s'] = str_list[it['name']]
            it['attrs'] = [dict(a, name_s=str_list[a['name']]) for a in it['attrs']]
        out_chunks.append(serialize(it))

    # 6. 组装文件
    sp = build_string_pool(str_list, utf8=False)
    body = b''.join(out_chunks)
    total = 8 + len(sp) + len(body)
    head = struct.pack('<HHI', 0x0003, 8, total)
    open(path_out, 'wb').write(head + sp + body)
    return len(str_list)
