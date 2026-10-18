#!/usr/bin/env bash
# BT · Android TV 版 —— 一键构建（把 base.apk 适配到 Android 5.1.1 / API 22）
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

WORK="$ROOT/build"
SRC="$ROOT/src/base.apk"
KS="$ROOT/keystore/bt-release.jks"
KSPASS="${KSPASS:-bt2026}"
VNAME="${VNAME:-0.9.41}"
UBER="${UBER_JAR:-$WORK/uber-apk-signer.jar}"

mkdir -p "$WORK"
[ -s "$UBER" ] || curl -fsSL -o "$UBER" \
  https://github.com/patrickfav/uber-apk-signer/releases/download/v1.3.0/uber-apk-signer-1.3.0.jar
echo "==> 1/7 准备 baksmali / smali"
MVN=https://repo1.maven.org/maven2
fetch() { # $1=path
  local f="$WORK/$(basename "$1")"
  if [ ! -s "$f" ]; then
    # 本地 jar 缓存回退（离线/沙箱环境）
    local c="$ROOT/jars/$(basename "$1")"
    if [ -s "$c" ]; then
      cp "$c" "$f"
    else
      curl -fsSL "$MVN/$1" -o "$f"
    fi
  fi
  echo "$f"
}
J_BAK=$(fetch org/smali/baksmali/2.5.2/baksmali-2.5.2.jar)
J_SMALI=$(fetch org/smali/smali/2.5.2/smali-2.5.2.jar)
J_DEXLIB=$(fetch org/smali/dexlib2/2.5.2/dexlib2-2.5.2.jar)
J_UTIL=$(fetch org/smali/util/2.5.2/util-2.5.2.jar)
J_GUAVA=$(fetch com/google/guava/guava/31.1-jre/guava-31.1-jre.jar)
J_CMD=$(fetch com/beust/jcommander/1.78/jcommander-1.78.jar)
J_ANTLR=$(fetch org/antlr/antlr-runtime/3.5.2/antlr-runtime-3.5.2.jar)
CP="$J_SMALI:$J_DEXLIB:$J_UTIL:$J_GUAVA:$J_CMD:$J_ANTLR"
CPB="$J_BAK:$J_DEXLIB:$J_UTIL:$J_GUAVA:$J_CMD:$J_ANTLR"

echo "==> 2/7 解包 APK"
rm -rf "$WORK/x"; mkdir -p "$WORK/x"
unzip -q "$SRC" -d "$WORK/x"

echo "==> 3/7 反编译 dex"
java -Xmx3g -cp "$CPB" org.jf.baksmali.Main d "$WORK/x/classes.dex"  -o "$WORK/x/smali1"
if [ -f "$WORK/x/classes2.dex" ]; then
  java -Xmx2g -cp "$CPB" org.jf.baksmali.Main d "$WORK/x/classes2.dex" -o "$WORK/x/smali2"
fi

echo "==> 4/7 打 API 22 兼容补丁"
python3 tools/patch_axml.py "$WORK/x/AndroidManifest.xml" 21
python3 tools/patch_api22.py "$WORK/x"

echo "==> 5/7 重新编译 dex"
java -Xmx3g -cp "$CP" org.jf.smali.Main assemble "$WORK/x/smali1" -o "$WORK/out1.dex"
if [ -d "$WORK/x/smali2" ]; then
  java -Xmx2g -cp "$CP" org.jf.smali.Main assemble "$WORK/x/smali2" -o "$WORK/out2.dex"
fi

echo "==> 6/7 重打包"
python3 tools/repack.py "$WORK" "$SRC" "$VNAME"

echo "==> 7/7 zipalign + V1/V2/V3 签名"
mkdir -p "$ROOT/dist"
java -jar "$UBER" -a "$WORK/bt_tv_unsigned.apk" \
  --ks "$KS" --ksAlias bt --ksKeyPass "$KSPASS" --ksPass "$KSPASS" \
  --out "$ROOT/dist" >/dev/null
SIGNED=$(ls "$ROOT/dist"/*aligned-signed.apk | head -1)
OUT="$ROOT/dist/BT_TV_${VNAME}_API22.apk"
mv -f "$SIGNED" "$OUT"

echo
echo "==> 自检"
python3 tools/verify.py "$OUT" "$WORK"

echo
echo "✅ 产物: $OUT"
ls -l "$OUT"
sha256sum "$OUT"
