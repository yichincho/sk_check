#!/usr/bin/env bash
set -euo pipefail
package=com.yichincho.skcheck
activity=$package/.MainActivity
adb install -r sk-check.apk
if [ "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" -ge 33 ]; then
  adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
fi
adb logcat -c
resolved=$(adb shell cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "$package" | tr -d '\r')
echo "$resolved" | tee launcher-resolution.txt
echo "$resolved" | grep -Eq 'com.yichincho.skcheck/(\.MainActivity|com.yichincho.skcheck.MainActivity)'
adb shell am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n "$activity" | tee launcher-start.txt
grep -q 'Status: ok' launcher-start.txt
# Poll for the resumed Activity; avoid marking a process that immediately crashes as healthy.
for attempt in $(seq 1 15); do
  adb shell dumpsys activity activities > launcher-activity.txt
  if grep -E '(mResumedActivity|topResumedActivity).*com.yichincho.skcheck/.MainActivity' launcher-activity.txt; then
    break
  fi
  sleep 1
done
sleep 3
adb shell dumpsys activity activities > launcher-activity.txt
grep -E '(mResumedActivity|topResumedActivity).*com.yichincho.skcheck/.MainActivity' launcher-activity.txt
adb logcat -d -b crash > launcher-crashes.txt
if grep -q 'Process: com.yichincho.skcheck' launcher-crashes.txt; then
  cat launcher-crashes.txt
  exit 1
fi
adb shell screencap -p /sdcard/sk-check.png
adb pull /sdcard/sk-check.png launcher-screen.png
echo "PASS: release APK installed, launcher resolved, MainActivity started and remained resumed (emulator only)"
