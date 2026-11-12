#!/bin/bash
# Builds and signs the companion APK with the bare SDK tools (no Gradle).
# Output: app/build/tvcompanion.apk
# d8 34.0.0 crashes (NullPointerException, "Compilation failed with an internal error") on any
# anonymous or inner (non-static) class compiled by this javac; use a static nested class or a lambda.
set -euo pipefail
cd "$(dirname "$0")"
SDK=${ANDROID_HOME:-$HOME/Library/Android/sdk}
BT=$SDK/build-tools/34.0.0
JAR=$SDK/platforms/android-34/android.jar
rm -rf build && mkdir -p build/classes build/dex
"$BT/aapt2" link --manifest AndroidManifest.xml -I "$JAR" -o build/base.apk
javac -source 11 -target 11 -classpath "$JAR" -d build/classes $(find src -name '*.java')
"$BT/d8" --lib "$JAR" --min-api 30 --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unsigned.apk
(cd build/dex && zip -q ../unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/unsigned.apk build/aligned.apk
KS=$HOME/.android/debug.keystore
[ -f "$KS" ] || keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:android --out build/tvcompanion.apk build/aligned.apk
echo build/tvcompanion.apk
