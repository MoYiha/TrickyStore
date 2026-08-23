#!/system/bin/sh
CONFIG_DIR="/data/adb/cleverestricky"
CONFIG_ROOT_SAFE=false

if [ -d "$CONFIG_DIR" ] && [ ! -L "$CONFIG_DIR" ]; then
  if chown 0:0 "$CONFIG_DIR" 2>/dev/null && chmod 700 "$CONFIG_DIR"; then
    CONFIG_ROOT_SAFE=true
  else
    log -t CleveresTricky "Config root permissions could not be secured; early config processing was skipped"
  fi
  chcon u:object_r:system_file:s0 "$CONFIG_DIR" 2>/dev/null
fi

promote_staged_identity() {
  staged_file="$CONFIG_DIR/spoof_build_vars.next"
  active_file="$CONFIG_DIR/spoof_build_vars"

  [ "$CONFIG_ROOT_SAFE" = true ] || return 0
  if [ ! -e "$staged_file" ] && [ ! -L "$staged_file" ]; then
    return 0
  fi
  if [ -L "$staged_file" ]; then
    rm -f "$staged_file"
    log -t CleveresTricky "Removed an unsafe staged identity link"
    return 0
  fi
  if [ ! -f "$staged_file" ]; then
    log -t CleveresTricky "Non-regular staged identity was ignored"
    return 0
  fi

  # Identity refresh is optional and belongs to Spoof Engine. Core boot protection
  # below never depends on either of these files.
  if [ ! -f "$CONFIG_DIR/spoof_enabled" ] || [ -L "$CONFIG_DIR/spoof_enabled" ] ||
    [ ! -f "$CONFIG_DIR/random_on_boot" ] || [ -L "$CONFIG_DIR/random_on_boot" ]; then
    rm -f "$staged_file"
    return 0
  fi
  if [ -L "$active_file" ] || { [ -e "$active_file" ] && [ ! -f "$active_file" ]; }; then
    log -t CleveresTricky "Unsafe active identity path; staged identity was ignored"
    return 0
  fi

  staged_size=$(wc -c < "$staged_file" 2>/dev/null) || return 0
  if [ "$staged_size" -lt 1 ] || [ "$staged_size" -gt 1048576 ]; then
    rm -f "$staged_file"
    log -t CleveresTricky "Invalid staged identity size; staged identity was removed"
    return 0
  fi

  if ! chown 0:0 "$staged_file" 2>/dev/null || ! chmod 600 "$staged_file"; then
    log -t CleveresTricky "Staged identity permissions could not be secured"
    return 0
  fi
  chcon u:object_r:system_file:s0 "$staged_file" 2>/dev/null
  if mv -f "$staged_file" "$active_file"; then
    log -t CleveresTricky "Activated the prepared identity snapshot"
  else
    log -t CleveresTricky "Could not activate the prepared identity snapshot"
  fi
}

apply_early_properties() {
  [ "$CONFIG_ROOT_SAFE" = true ] || return 0
  command -v resetprop >/dev/null 2>&1 || {
    log -t CleveresTricky "resetprop is unavailable; boot property protection was skipped"
    return 0
  }

  apply_prop() {
    resetprop -n "$1" "$2" >/dev/null 2>&1 || {
      log -t CleveresTricky "Failed to apply an app-visible boot property"
      return 1
    }
  }

  remove_prop() {
    resetprop --delete "$1" >/dev/null 2>&1 || {
      log -t CleveresTricky "Failed to remove a legacy boot property"
      return 1
    }
  }

  hide_boot_mode() {
    current_value=$(getprop "$1")
    case "$current_value" in
      *recovery*|*RECOVERY*) apply_prop "$1" unknown || return 1 ;;
    esac
  }

  # Core bootloader / verified-boot property protection. This is intentionally
  # unconditional: Spoof Engine controls identity only, and legacy
  # hide_sensitive_props / boot_props_mode files cannot disable this path.
  apply_prop ro.boot.vbmeta.device_state locked || return 0
  apply_prop ro.boot.verifiedbootstate green || return 0
  apply_prop ro.boot.flash.locked 1 || return 0
  apply_prop ro.boot.warranty_bit 0 || return 0
  apply_prop ro.warranty_bit 0 || return 0
  apply_prop ro.debuggable 0 || return 0
  apply_prop ro.force.debuggable 0 || return 0
  apply_prop ro.secure 1 || return 0
  apply_prop ro.adb.secure 1 || return 0
  apply_prop ro.build.type user || return 0
  apply_prop ro.build.tags release-keys || return 0
  apply_prop ro.vendor.boot.warranty_bit 0 || return 0
  apply_prop ro.vendor.warranty_bit 0 || return 0
  android_sdk=$(getprop ro.build.version.sdk)
  case "$android_sdk" in
    ''|*[!0-9]*) android_sdk=0 ;;
  esac
  if [ "$android_sdk" -ge 36 ]; then
    remove_prop sys.oem_unlock_allowed || return 0
  else
    apply_prop sys.oem_unlock_allowed 0 || return 0
  fi
  apply_prop ro.secureboot.lockstate locked || return 0
  apply_prop ro.boot.realmebootstate green || return 0
  apply_prop ro.boot.realme.lockstate 1 || return 0
  hide_boot_mode ro.bootmode || return 0
  hide_boot_mode ro.boot.bootmode || return 0
  hide_boot_mode vendor.boot.bootmode || return 0

  # Everything below this point is optional identity spoofing.
  [ -f "$CONFIG_DIR/spoof_enabled" ] || return 0
  [ ! -L "$CONFIG_DIR/spoof_enabled" ] || return 0

  boot_mode=auto
  if [ -f "$CONFIG_DIR/boot_props_mode" ] && [ ! -L "$CONFIG_DIR/boot_props_mode" ]; then
    IFS= read -r boot_mode < "$CONFIG_DIR/boot_props_mode"
  fi
  case "$boot_mode" in
    force|disable|auto) ;;
    *) boot_mode=auto ;;
  esac
  [ "$boot_mode" != disable ] || return 0

  if [ -f "$CONFIG_DIR/spoof_region_cn" ] && [ ! -L "$CONFIG_DIR/spoof_region_cn" ]; then
    apply_prop ro.boot.hwc CN || return 0
    apply_prop gsm.operator.iso-country cn || return 0
    apply_prop gsm.sim.operator.iso-country cn || return 0
    apply_prop ro.boot.hwlevel MP || return 0
    apply_prop persist.radio.skhwc_matchres MATCH || return 0
  fi

  [ -f "$CONFIG_DIR/spoof_build_identity" ] || return 0
  [ ! -L "$CONFIG_DIR/spoof_build_identity" ] || return 0
  vars_file="$CONFIG_DIR/spoof_build_vars"
  [ -f "$vars_file" ] && [ ! -L "$vars_file" ] || return 0
  vars_size=$(wc -c < "$vars_file" 2>/dev/null) || return 0
  [ "$vars_size" -le 1048576 ] || return 0

  if [ "$boot_mode" = auto ]; then
    identity_conflict=false
    for module_root in /data/adb/modules /data/adb/ksu/modules /data/adb/ap/modules; do
      [ "$identity_conflict" = false ] || break
      if [ ! -d "$module_root" ] || [ -L "$module_root" ]; then
        continue
      fi
      for candidate in "$module_root"/*; do
        if [ ! -d "$candidate" ] || [ -L "$candidate" ] || [ -f "$candidate/disable" ]; then
          continue
        fi
        module_id=${candidate##*/}
        module_id=$(printf '%s' "$module_id" | tr '[:upper:]' '[:lower:]')
        case "$module_id" in
          *playintegrity*|*autopif*|*auto_pif*|pif|pif_*|*playcurl*)
            identity_conflict=true
            break
            ;;
        esac
      done
    done
    if [ "$identity_conflict" = true ]; then
      # Build Identity is an explicit user choice. A second identity provider may
      # overwrite these properties later, but CleveresTricky must never silently
      # turn its own enabled feature into a no-op.
      log -t CleveresTricky "Another build-identity provider is active; applying the enabled CleveresTricky Build Identity anyway"
    fi
  fi

  CT_FINGERPRINT=
  CT_BRAND=
  CT_DEVICE=
  CT_PRODUCT=
  CT_MANUFACTURER=
  CT_MODEL=
  CT_BUILD_ID=
  CT_RELEASE=
  CT_INCREMENTAL=
  CT_TYPE=
  CT_TAGS=
  CT_SECURITY_PATCH=
  while IFS= read -r line || [ -n "$line" ]; do
    case "$line" in ''|'#'*) continue ;; esac
    key=${line%%=*}
    [ "$key" != "$line" ] || continue
    value=${line#*=}
    [ "${#value}" -le 512 ] || continue
    case "$value" in *[![:print:]]*) continue ;; esac
    case "$key" in
      FINGERPRINT) CT_FINGERPRINT=$value ;;
      BRAND) CT_BRAND=$value ;;
      DEVICE) CT_DEVICE=$value ;;
      PRODUCT) CT_PRODUCT=$value ;;
      MANUFACTURER) CT_MANUFACTURER=$value ;;
      MODEL) CT_MODEL=$value ;;
      BUILD_ID) CT_BUILD_ID=$value ;;
      RELEASE) CT_RELEASE=$value ;;
      INCREMENTAL) CT_INCREMENTAL=$value ;;
      TYPE) CT_TYPE=$value ;;
      TAGS) CT_TAGS=$value ;;
      SECURITY_PATCH) CT_SECURITY_PATCH=$value ;;
    esac
  done < "$vars_file"

  [ -n "$CT_FINGERPRINT" ] || {
    log -t CleveresTricky "Build identity is enabled, but no persisted fingerprint is available"
    return 0
  }
  case "$CT_FINGERPRINT" in *[!A-Za-z0-9._:/+-]*) return 0 ;; esac

  apply_prop ro.build.fingerprint "$CT_FINGERPRINT" || return 0
  if [ -n "$CT_BRAND" ]; then apply_prop ro.product.brand "$CT_BRAND"; fi
  if [ -n "$CT_DEVICE" ]; then apply_prop ro.product.device "$CT_DEVICE"; fi
  if [ -n "$CT_PRODUCT" ]; then apply_prop ro.product.name "$CT_PRODUCT"; fi
  if [ -n "$CT_MANUFACTURER" ]; then apply_prop ro.product.manufacturer "$CT_MANUFACTURER"; fi
  if [ -n "$CT_MODEL" ]; then apply_prop ro.product.model "$CT_MODEL"; fi
  if [ -n "$CT_BUILD_ID" ]; then apply_prop ro.build.id "$CT_BUILD_ID"; fi
  if [ -n "$CT_RELEASE" ]; then
    apply_prop ro.build.version.release "$CT_RELEASE"
    apply_prop ro.build.version.release_or_codename "$CT_RELEASE"
  fi
  if [ -n "$CT_INCREMENTAL" ]; then apply_prop ro.build.version.incremental "$CT_INCREMENTAL"; fi
  if [ -n "$CT_TYPE" ]; then apply_prop ro.build.type "$CT_TYPE"; fi
  if [ -n "$CT_TAGS" ]; then apply_prop ro.build.tags "$CT_TAGS"; fi
  if [ -n "$CT_SECURITY_PATCH" ]; then
    case "$CT_SECURITY_PATCH" in
      [0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9])
        apply_prop ro.build.version.security_patch "$CT_SECURITY_PATCH"
        ;;
    esac
  fi
}

promote_staged_identity
apply_early_properties
