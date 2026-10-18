#!/usr/bin/env bash
# Leaf — emulator smoke test (runs inside reactivecircus/android-emulator-runner).
#
# Installs the debug APK on an Android 6 emulator, drives the app with adb and captures
# screenshots + the UI hierarchy + logcat. The results are committed to the branch so they
# can be reviewed from CI.
set -u

PKG="com.leaf.reader"
ACTIVITY="${PKG}/.MainActivity"
OUT="smoke"
mkdir -p "${OUT}"

echo "== waiting for device =="
adb wait-for-device
adb shell 'while [[ -z $(getprop sys.boot_completed) ]]; do sleep 2; done' || true
adb shell input keyevent 82 || true
sleep 2

APK=""
for candidate in apks/app/build/outputs/apk/debug/*.apk apks/*debug*.apk apks/*.apk; do
  if [ -f "${candidate}" ]; then APK="${candidate}"; break; fi
done
if [ -z "${APK}" ]; then
  echo "!! no APK found" | tee "${OUT}/report.txt"
  exit 1
fi
echo "== installing ${APK} =="
adb install -r -t "${APK}" 2>&1 | tee "${OUT}/install.txt"

adb logcat -c || true
adb shell am force-stop "${PKG}" || true
echo "== launching ${ACTIVITY} =="
adb shell am start -W -n "${ACTIVITY}" 2>&1 | tee "${OUT}/launch.txt"
sleep 12

capture() {
  local name="$1"
  adb exec-out screencap -p > "${OUT}/${name}.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "${OUT}/${name}.xml" >/dev/null 2>&1 || true
  echo "captured ${name}"
}

tap() {
  python3 scripts/tap_text.py "${OUT}/$1.xml" "$2" || true
}

texts() {
  python3 - "$1" <<'PY'
import re, sys
data = open(sys.argv[1], encoding="utf-8", errors="ignore").read()
print(" | ".join(t for t in re.findall(r'text="([^"]*)"', data) if t.strip())[:1200])
PY
}

echo "== 1. library =="
capture 01-library
echo "   visible: $(texts "${OUT}/01-library.xml")"

echo "== 2. open a word document =="
tap 01-library "quarterly-report.docx"
sleep 6
capture 02-reader-docx
echo "   visible: $(texts "${OUT}/02-reader-docx.xml")"

echo "== 3. open the pdf =="
adb shell input keyevent 4 || true
sleep 2
adb shell input swipe 540 1100 540 500 300 || true
sleep 2
capture 03-scrolled
tap 03-scrolled "field-guide.pdf"
sleep 7
capture 04-reader-pdf
echo "   visible: $(texts "${OUT}/04-reader-pdf.xml")"

echo "== 4. back to the library and open the device screen =="
adb shell input keyevent 4 || true
sleep 3
tap 04-reader-pdf "Device" || true
sleep 4
capture 05-device
echo "   visible: $(texts "${OUT}/05-device.xml")"

echo "== 5. logs =="
adb logcat -d -v threadtime > "${OUT}/logcat.txt" 2>/dev/null || true
adb shell dumpsys activity activities 2>/dev/null | head -60 > "${OUT}/activities.txt" || true

{
  echo "package: ${PKG}"
  echo "apk: ${APK}"
  echo "screens: 01-library 02-reader-docx 03-scrolled 04-reader-pdf 05-device"
  for screen in 01-library 02-reader-docx 04-reader-pdf 05-device; do
    echo "--- ${screen} ---"
    texts "${OUT}/${screen}.xml"
  done
  if grep -q "FATAL EXCEPTION" "${OUT}/logcat.txt"; then
    echo "result: CRASH"
    grep -n -A 30 "FATAL EXCEPTION" "${OUT}/logcat.txt" | head -100
  else
    echo "result: no fatal exception"
  fi
  echo "--- leaf log lines ---"
  grep -iE "leaf|AndroidRuntime|FATAL|PdfRenderer" "${OUT}/logcat.txt" | grep -v "uiautomator" | head -80
} > "${OUT}/report.txt" 2>&1

head -40 "${OUT}/report.txt"
echo "== smoke test finished =="
