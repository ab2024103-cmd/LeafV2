#!/usr/bin/env bash
# Leaf — emulator smoke test (runs inside reactivecircus/android-emulator-runner).
#
# Installs the debug APK on the emulator, launches Leaf, drives a few screens
# with adb and captures screenshots + the UI hierarchy + logcat so the results
# can be reviewed from CI (the artifacts are committed to the branch).
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
sleep 10

capture() {
  local name="$1"
  adb exec-out screencap -p > "${OUT}/${name}.png" 2>/dev/null || true
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1 || true
  adb pull /sdcard/ui.xml "${OUT}/${name}.xml" >/dev/null 2>&1 || true
  echo "captured ${name}"
}

capture 01-library

echo "== tapping the first document =="
python3 scripts/tap_text.py "${OUT}/01-library.xml" "leaf" || true
sleep 4
capture 02-reader

echo "== opening the overflow / tabs =="
python3 scripts/tap_text.py "${OUT}/02-reader.xml" "Tabs" || true
sleep 2
capture 03-tabs

echo "== going back to the library =="
adb shell input keyevent 4 || true
sleep 2
capture 04-back

echo "== logs =="
adb logcat -d -v threadtime > "${OUT}/logcat.txt" 2>/dev/null || true
adb shell dumpsys activity activities 2>/dev/null | head -60 > "${OUT}/activities.txt" || true
adb shell pm list packages 2>/dev/null | grep leaf > "${OUT}/packages.txt" || true

{
  echo "package: ${PKG}"
  echo "apk: ${APK}"
  echo "screens: 01-library 02-reader 03-tabs 04-back"
  if grep -q "FATAL EXCEPTION" "${OUT}/logcat.txt"; then
    echo "result: CRASH"
    grep -n -A 25 "FATAL EXCEPTION" "${OUT}/logcat.txt" | head -80
  else
    echo "result: no fatal exception"
  fi
  echo "--- leaf related log lines ---"
  grep -iE "leaf|AndroidRuntime|FATAL" "${OUT}/logcat.txt" | head -120
} > "${OUT}/report.txt" 2>&1

cat "${OUT}/report.txt" | head -60
echo "== smoke test finished =="
