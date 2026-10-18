#!/usr/bin/env bash
# BT Plus · 一键构建（API22 兼容补丁 + 免费影视库 + 刷视频）
# 依赖：JDK 17+ / python3 / curl；需自备 android.jar（含 android.* 类）
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$HERE/.." && pwd)"
WORK="${WORK:-$ROOT/build-plus}"
SRC="$ROOT/src/base.apk"
KS="$ROOT/keystore/bt-release.jks"
KSPASS="${KSPASS:-bt2026}"
VNAME="${VNAME:-0.9.41}"

mkdir -p "$WORK"
cd "$ROOT"

# ---------- 0) 依赖 ----------
MVN=https://repo1.maven.org/maven2
fetch() {
  local f="$WORK/$(basename "$1")"
  if [ ! -s "$f" ]; then
    local c="$ROOT/jars/$(basename "$1")"
    if [ -s "$c" ]; then cp "$c" "$f"; else curl -fsSL "$MVN/$1" -o "$f"; fi
  fi
  echo "$f"
}
echo "==> 1/8 准备工具"
J_BAK=$(fetch org/smali/baksmali/2.5.2/baksmali-2.5.2.jar)
J_SMALI=$(fetch org/smali/smali/2.5.2/smali-2.5.2.jar)
J_DEXLIB=$(fetch org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar)
J_UTIL=$(fetch org/smali/util/2.5.2/util-2.5.2.jar)
J_GUAVA=$(fetch com/google/guava/guava/31.1-jre/guava-31.1-jre.jar)
J_CMD=$(fetch com/beust/jcommander/1.78/jcommander-1.78.jar)
J_ANTLR=$(fetch org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar)
CP="$J_SMALI:$J_DEXLIB:$J_UTIL:$J_GUAVA:$J_CMD:$J_ANTLR"
CPB="$J_BAK:$J_DEXLIB:$J_UTIL:$J_GUAVA:$J_CMD:$J_ANTLR"

AJAR="${ANDROID_JAR:-$WORK/android.jar}"
R8="${R8_JAR:-$WORK/r8.jar}"
UBER="${UBER_JAR:-$WORK/uber-apk-signer.jar}"
[ -s "$AJAR" ] || { echo "缺少 android.jar，请设置 ANDROID_JAR"; exit 1; }
[ -s "$R8" ]   || curl -fsSL -o "$R8" https://dl.google.com/dl/android/maven2/com/android/tools/r8/8.2.42/r8-8.2.42.jar
[ -s "$UBER" ] || curl -fsSL -o "$UBER" https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar

# ---------- 1) 解包 ----------
echo "==> 2/8 解包原版 APK"
rm -rf "$WORK/x"; mkdir -p "$WORK/x"
unzip -q "$SRC" -d "$WORK/x"

# ---------- 2) API22 补丁（必须先做） ----------
echo "==> 3/8 反编译 dex"
java -Xmx3g -cp "$CPB" org.jf.baksmali.Main d "$WORK/x/classes.dex" -o "$WORK/x/smali1"
if [ -f "$WORK/x/classes2.dex" ]; then
  java -Xmx2g -cp "$CPB" org.jf.baksmali.Main d "$WORK/x/classes2.dex" -o "$WORK/x/smali2"
fi

echo "==> 4/8 应用 API 22 兼容补丁"
python3 tools/patch_axml.py "$WORK/x/AndroidManifest.xml" 21
python3 tools/patch_api22.py "$WORK/x"

echo "==> 4.5/8 注入侧边栏菜单项（看番 / 刷视频）"
python3 "$HERE/tools/patch_sidebar.py" "$WORK/x"

echo "==> 5/8 重新编译 dex"
java -Xmx3g -cp "$CP" org.jf.smali.Main assemble "$WORK/x/smali1" -o "$WORK/out1.dex"
if [ -d "$WORK/x/smali2" ]; then
  java -Xmx2g -cp "$CP" org.jf.smali.Main assemble "$WORK/x/smali2" -o "$WORK/out2.dex"
fi

# ---------- 3) 新功能 dex ----------
echo "==> 6/8 编译新功能（classes3.dex）"
rm -rf "$WORK/classes" "$WORK/d8"; mkdir -p "$WORK/classes" "$WORK/d8"
javac -nowarn -source 8 -target 8 -encoding UTF-8 \
  -bootclasspath "$AJAR" -classpath "$AJAR" \
  -d "$WORK/classes" "$HERE"/src/com/yzb/btplus/*.java
rm -f "$WORK/classes3.dex"
# D8 单 dex 时输出固定叫 classes.dex，先落到独立目录再改名，避免与解包内容混淆
java -cp "$R8" com.android.tools.r8.D8 --min-api 21 --release \
  --lib "$AJAR" --output "$WORK/d8" $(find "$WORK/classes" -name '*.class')
[ -s "$WORK/d8/classes.dex" ] || { echo "❌ D8 未生成 classes.dex"; exit 1; }
mv -f "$WORK/d8/classes.dex" "$WORK/classes3.dex"
[ -s "$WORK/classes3.dex" ] || { echo "❌ classes3.dex 未生成"; exit 1; }
echo "classes3.dex = $(stat -c%s "$WORK/classes3.dex") 字节"

# ---------- 4) manifest + 组装 ----------
echo "==> 7/8 重建 manifest 并组装"
python3 "$HERE/tools/patch_plus_manifest.py" "$WORK/x/AndroidManifest.xml" "$WORK/AndroidManifest.xml"
python3 tools/repack.py "$WORK" "$SRC" "$VNAME"
# repack.py 只换 out1/out2，这里追加 classes3.dex
python3 - "$WORK" "$VNAME" <<'PY'
import sys, os, zipfile
WORK = sys.argv[1]
VNAME = sys.argv[2]
SRC = os.path.join(WORK, 'bt_tv_unsigned.apk')
OUT = os.path.join(WORK, 'bt_plus_unsigned.apk')
MAN = os.path.join(WORK, 'AndroidManifest.xml')
D3 = os.path.join(WORK, 'classes3.dex')
man = bytearray(open(MAN, 'rb').read())
# versionName 0.9.40 -> VNAME（AXML 字符串池是 UTF-16LE，等长替换）
for cand in ('0.9.40', '0.9.41', '0.9.42'):
    o = cand.encode('utf-16-le')
    if o in man and cand != VNAME:
        n = VNAME.encode('utf-16-le')
        if len(n) == len(o):
            i = man.find(o)
            man[i:i + len(o)] = n
            print('versionName %s -> %s @ %d' % (cand, VNAME, i))
        break
man = bytes(man)
d3 = open(D3, 'rb').read()
zin = zipfile.ZipFile(SRC); zout = zipfile.ZipFile(OUT, 'w')
for zi in zin.infolist():
    n = zi.filename
    if n.startswith('META-INF/'):
        continue
    data = man if n == 'AndroidManifest.xml' else zin.read(n)
    ct = zipfile.ZIP_STORED if n == 'resources.arsc' or zi.compress_type == zipfile.ZIP_STORED else zipfile.ZIP_DEFLATED
    nzi = zipfile.ZipInfo(n, date_time=zi.date_time)
    nzi.compress_type = ct
    nzi.external_attr = zi.external_attr
    zout.writestr(nzi, data)
nzi = zipfile.ZipInfo('classes3.dex', date_time=(2026, 10, 18, 8, 0, 0))
nzi.compress_type = zipfile.ZIP_DEFLATED
nzi.external_attr = 0o644 << 16
zout.writestr(nzi, d3)
zout.close(); zin.close()
print('plus unsigned -> %s (%d)' % (OUT, os.path.getsize(OUT)))
PY

# ---------- 5) 签名 ----------
echo "==> 8/8 zipalign + V1/V2/V3 签名"
mkdir -p "$ROOT/dist"
java -jar "$UBER" -a "$WORK/bt_plus_unsigned.apk" \
  --ks "$KS" --ksAlias bt --ksKeyPass "$KSPASS" --ksPass "$KSPASS" \
  --out "$ROOT/dist" >/dev/null
OUT=$(ls "$ROOT/dist"/*aligned-signed.apk | head -1)
FINAL="$ROOT/dist/BT_Plus_${VNAME}_API22.apk"
mv -f "$OUT" "$FINAL"

# ---------- 6) 自检 ----------
echo; echo "==> 自检"
python3 "$HERE/tools/verify_plus.py" "$FINAL"
echo; echo "✅ 产物: $FINAL"
ls -l "$FINAL"; sha256sum "$FINAL"
