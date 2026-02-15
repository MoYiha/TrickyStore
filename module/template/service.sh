DEBUG=@DEBUG@

MODDIR=${0%/*}
CONFIG_DIR="/data/adb/cleverestricky"

cd $MODDIR

# Function to find busybox
find_busybox() {
  if [ -n "$BUSYBOX" ]; then return 0; fi
  for path in \
    /data/adb/modules/busybox-ndk/system/*/busybox \
    /data/adb/magisk/busybox \
    /data/adb/ksu/bin/busybox \
    /data/adb/ap/bin/busybox \
    /sbin/busybox \
    /system/bin/busybox \
    /system/xbin/busybox; do
    if [ -x "$path" ]; then
      BUSYBOX="$path"
      return 0
    fi
  done
  if command -v busybox >/dev/null 2>&1; then
    BUSYBOX="busybox"
    return 0
  fi
  return 1
}

# Boot script logic in background
(
  # Wait for boot completion
  if find_busybox; then
    until [ "$($BUSYBOX getprop sys.boot_completed)" = "1" ]; do
      sleep 5
    done
  else
    # Fallback to system getprop if busybox not found
    until [ "$(getprop sys.boot_completed)" = "1" ]; do
      sleep 5
    done
  fi

  # Auto Security Patch Update
  # Use busybox if available for date calculations
  if [ -n "$BUSYBOX" ]; then
      current_patch_str=$(getprop ro.build.version.security_patch)
      if [ -n "$current_patch_str" ]; then
        patch_timestamp=$($BUSYBOX date -d "$current_patch_str" +%s)
        current_timestamp=$($BUSYBOX date +%s)
        # 6 months in seconds (approx 180 days)
        seconds_in_6_months=15552000
        six_months_ago_timestamp=$((current_timestamp - seconds_in_6_months))

        if [ "$patch_timestamp" -lt "$six_months_ago_timestamp" ]; then
            sp="$CONFIG_DIR/security_patch.txt"
            current_year=$($BUSYBOX date +%Y)
            current_month_num=$((10#$($BUSYBOX date +%m)))

            if [ "$current_month_num" -eq 1 ]; then
                prev_month=12
                prev_year=$((current_year - 1))
            else
                prev_month=$((current_month_num - 1))
                prev_year=$current_year
            fi

            formatted_month=$($BUSYBOX printf "%02d" $prev_month)
            # Write YYYY-MM-DD format (e.g. 2024-11-05)
            echo "${prev_year}-${formatted_month}-05" > "$sp"
            chmod 600 "$sp"
        fi
      fi
  fi

  # Property Hiding
  if [ -d "/data/adb/modules/zygisk_shamiko" ]; then
    # Shamiko present: only set CN region prop as requested
    resetprop ro.boot.hwc CN
  else
    # Helper functions for resetprop
    check_reset_prop() {
      local NAME=$1
      local EXPECTED=$2
      local VALUE=$(resetprop $NAME)
      [ -z "$VALUE" ] || [ "$VALUE" = "$EXPECTED" ] || resetprop $NAME $EXPECTED
    }

    contains_reset_prop() {
      local NAME=$1
      local CONTAINS=$2
      local NEWVAL=$3
      local VAL=$(resetprop $NAME)
      if [[ "$VAL" == *"$CONTAINS"* ]]; then
        resetprop $NAME $NEWVAL
      fi
    }

    # Hide sensitive props
    check_reset_prop "ro.boot.vbmeta.device_state" "locked"
    check_reset_prop "ro.boot.verifiedbootstate" "green"
    check_reset_prop "ro.boot.flash.locked" "1"
    check_reset_prop "ro.boot.veritymode" "enforcing"
    check_reset_prop "ro.boot.warranty_bit" "0"
    check_reset_prop "ro.warranty_bit" "0"
    check_reset_prop "ro.debuggable" "0"
    check_reset_prop "ro.force.debuggable" "0"
    check_reset_prop "ro.secure" "1"
    check_reset_prop "ro.adb.secure" "1"
    check_reset_prop "ro.build.type" "user"
    check_reset_prop "ro.build.tags" "release-keys"
    check_reset_prop "ro.vendor.boot.warranty_bit" "0"
    check_reset_prop "ro.vendor.warranty_bit" "0"
    check_reset_prop "vendor.boot.vbmeta.device_state" "locked"
    check_reset_prop "vendor.boot.verifiedbootstate" "green"
    check_reset_prop "sys.oem_unlock_allowed" "0"
    check_reset_prop "ro.secureboot.lockstate" "locked"

    # Realme specific
    check_reset_prop "ro.boot.realmebootstate" "green"
    check_reset_prop "ro.boot.realme.lockstate" "1"

    # Bootmode
    contains_reset_prop "ro.bootmode" "recovery" "unknown"
    contains_reset_prop "ro.boot.bootmode" "recovery" "unknown"
    contains_reset_prop "vendor.boot.bootmode" "recovery" "unknown"

    # CN Region spoofing (as per request)
    check_reset_prop "gsm.operator.iso-country" "cn"
    check_reset_prop "ro.boot.hwc" "CN"
  fi

  # Remove Magisk 32bit binaries (safe subset)
  if [ -f "/debug_ramdisk/magisk32" ]; then
    rm -f "/debug_ramdisk/magisk32"
  fi
  if [ -f "/data/adb/magisk/magisk32" ]; then
    rm -f "/data/adb/magisk/magisk32"
  fi

  # Additional properties from user script
  resetprop -n persist.radio.skhwc_matchres "MATCH"
  resetprop -n ro.boot.hwlevel "MP"

) &

(
while [ true ]; do
  chcon u:object_r:cleverestricky_exec:s0 "$MODDIR/daemon"
  ./daemon
  if [ $? -ne 0 ]; then
    exit 1
  fi
done
) &
