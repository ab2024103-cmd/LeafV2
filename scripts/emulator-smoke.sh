#!/usr/bin/env bash
# Leaf — emulator smoke test (runs inside reactivecircus/android-emulator-runner).
#
# Installs the debug APK on an Android 6 emulator, drives the app with adb and captures
# screenshots + the UI hierarchy + logcat. The results are committed to the branch so they
# can be reviewed from CI.
set -u

OUT="smoke"
mkdir -p "${OUT}"
: > "${OUT}/script.log"
exec > >(tee -a "${OUT}/script.log") 2>&1

PKG="com.leaf.reader"
ACTIVITY="${PKG}/.MainActivity"

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

CURRENT="${OUT}/01-library.xml"

capture() {
  local name="$1"
  adb exec-out screencap -p > "${OUT}/${name}.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "${OUT}/${name}.xml" >/dev/null 2>&1 || true
  CURRENT="${OUT}/${name}.xml"
  echo "captured ${name}"
}

tap() {
  python3 scripts/tap_text.py "${CURRENT}" "$1" "${2:-0}" || true
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
tap "quarterly-report.docx"
sleep 6
capture 02-reader-docx
echo "   visible: $(texts "${OUT}/02-reader-docx.xml")"

echo "== 3. back to the library and scroll to the pdf =="
adb shell input keyevent 4 || true
sleep 3
adb shell input swipe 540 1700 540 700 300 || true
sleep 2
adb shell input swipe 540 1700 540 700 300 || true
sleep 3
capture 03-scrolled
tap "field-guide.pdf" || true
sleep 9
capture 04-reader-pdf
echo "   visible: $(texts "${OUT}/04-reader-pdf.xml")"

echo "== 4. search inside the pdf =="
tap "Find in document" || true
sleep 2
capture 05a-findbar
tap "Find in document" 1 || true
sleep 1
adb shell input text "page" || true
sleep 7
capture 05-find
echo "   visible: $(texts "${OUT}/05-find.xml")"

echo "== 5. the device screen =="
adb shell input keyevent 4 || true
sleep 2
adb shell input keyevent 4 || true
sleep 3
capture 05b-library
tap "Device" || true
sleep 4
capture 06-device
echo "   visible: $(texts "${OUT}/06-device.xml")"

echo "== 6. the other tabs =="
capture 06b-library
tap "Search" || true
sleep 3
capture 07-search
tap "Recents" || true
sleep 3
capture 08-recents
tap "Favorites" || true
sleep 3
capture 09-favorites
tap "Settings" || true
sleep 3
capture 10-settings
tap "Search" || true
sleep 3
capture 11-search
tap "the" 0 || true
sleep 1
adb shell input text "the" || true
sleep 6
capture 12-search-results
echo "   visible: $(texts "${OUT}/12-search-results.xml")"

echo "== 7. logs =="
adb logcat -d -v threadtime > "${OUT}/logcat.txt" 2>/dev/null || true
adb shell dumpsys activity activities 2>/dev/null | head -60 > "${OUT}/activities.txt" || true

echo "== 8. native crash reports =="
adb root >/dev/null 2>&1 || true
sleep 2
adb wait-for-device
adb shell 'ls -t /data/tombstones/ 2>/dev/null | head -3' > "${OUT}/tombstones.txt" 2>/dev/null || true
for stone in $(cat "${OUT}/tombstones.txt" 2>/dev/null); do
  echo "--- tombstone ${stone} ---" >> "${OUT}/tombstone.txt"
  adb shell "cat /data/tombstones/${stone} 2>/dev/null" >> "${OUT}/tombstone.txt" 2>/dev/null || true
done
adb shell dumpsys dropbox --print SYSTEM_TOMBSTONE 2>/dev/null | head -200 > "${OUT}/dropbox.txt" || true

{
  echo "package: ${PKG}"
  echo "apk: ${APK}"
  echo "screens: 01-library 02-reader-docx 03-scrolled 04-reader-pdf 05-find 06-device 07-search 08-recents 09-favorites 10-settings 12-search-results"
  for screen in 01-library 02-reader-docx 04-reader-pdf 05-find 06-device 07-search 08-recents 09-favorites 10-settings 12-search-results; do
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
  echo "--- native crash (tombstone) ---"
  head -80 "${OUT}/tombstone.txt" 2>/dev/null || echo "(none)"
  echo "--- script log ---"
  cat "${OUT}/script.log" 2>/dev/null
} > "${OUT}/report.txt" 2>&1

head -40 "${OUT}/report.txt"
echo "== smoke test finished =="
