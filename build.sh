#!/usr/bin/env bash
# Сборка APK без Gradle: aapt + javac + dx + zipalign + apksigner.
# Нужны: JDK, aapt, dalvik-exchange (dx), zipalign, apksigner
#   (Ubuntu: apt-get install aapt dalvik-exchange zipalign apksigner)
# и android.jar (API 30): переменная ANDROID_JAR.
set -euo pipefail
cd "$(dirname "$0")"

ANDROID_JAR="${ANDROID_JAR:-tools/android.jar}"
if [ ! -f "$ANDROID_JAR" ]; then
  mkdir -p "$(dirname "$ANDROID_JAR")"
  curl -fsSL -o "$ANDROID_JAR" \
    https://raw.githubusercontent.com/Sable/android-platforms/master/android-30/android.jar
fi
DX="$(command -v dx || command -v dalvik-exchange)"

SRC=app/src/main
OUT=build
rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "==> aapt: ресурсы"
aapt package -f -m -J "$OUT/gen" -M "$SRC/AndroidManifest.xml" -S "$SRC/res" \
  -I "$ANDROID_JAR" -F "$OUT/app.unsigned.apk"

echo "==> javac"
javac -source 8 -target 8 -encoding UTF-8 -Xlint:-options \
  -bootclasspath "$ANDROID_JAR" -d "$OUT/classes" \
  $(find "$SRC/java" "$OUT/gen" -name '*.java')

echo "==> dx"
"$DX" --dex --min-sdk-version=21 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && aapt add ../app.unsigned.apk classes.dex >/dev/null)

echo "==> zipalign + подпись"
zipalign -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"
KEYSTORE="${KEYSTORE:-debug.keystore}"
if [ ! -f "$KEYSTORE" ]; then
  keytool -genkeypair -keystore "$KEYSTORE" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Magic Ball Debug" >/dev/null 2>&1
fi
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android \
  --out "$OUT/MagicBall.apk" "$OUT/app.aligned.apk"
apksigner verify "$OUT/MagicBall.apk"
echo "Готово: $OUT/MagicBall.apk"
