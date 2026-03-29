# The user's output says:
# "Error: The process '/usr/local/lib/android/sdk/platform-tools/adb' failed with exit code 1"
# "adb command '/usr/local/lib/android/sdk/platform-tools/adb -e shell settings put system screen_off_timeout 2147483647 ' failed: 'adb: device offline'"
# This means `adb -e shell settings put system screen_off_timeout 2147483647` failed.
# This happens because the device is offline when the action runs `disable-animations`.
# Wait, why is it offline? Because `reactivecircus/android-emulator-runner@v2` has an issue with API 33 on GitHub Actions where the emulator boots but ADB connects as "offline" initially, and the runner doesn't properly wait for the device to be "device" before running disable-animations!
# OR, `disable-animations: false` avoids this check!
# Yes! `disable-animations: false` will bypass running those adb commands right after boot, allowing our custom script to handle things.
# And our custom script does `adb wait-for-device`, which handles the offline state better?
# Actually, the action natively waits for `sys.boot_completed` by polling `adb shell getprop sys.boot_completed`. If that succeeds, the device is considered booted.
# Why would it be offline right after? Sometimes adb server crashes or restarts.
# Let's check `disable-animations: false`.
# Another thing: We can add `disable-animations: false` to the YAML and do animations disabling ourselves in the script, or just not do it.
