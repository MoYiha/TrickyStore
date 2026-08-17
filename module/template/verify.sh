# shellcheck shell=sh
# shellcheck disable=SC2154
TMPDIR_FOR_VERIFY="$TMPDIR/.vunzip"
mkdir -p "$TMPDIR_FOR_VERIFY" || abort "! Could not create verification directory"

abort_verify() {
  ui_print "*********************************************************"
  ui_print "! $1"
  ui_print "! This zip may be corrupted; download it again"
  abort "*********************************************************"
}

verify_hash() {
  target=$1
  hash_file=$2
  expected=$(tr -d '[:space:]' < "$hash_file")
  case "$expected" in
    *[!0-9A-Fa-f]*|'') abort_verify "Invalid checksum for $(basename "$target")" ;;
  esac
  [ "${#expected}" -eq 64 ] || abort_verify "Invalid checksum length for $(basename "$target")"
  printf '%s  %s\n' "$expected" "$target" | sha256sum -c - >/dev/null 2>&1 \
    || abort_verify "Failed to verify $(basename "$target")"
}

prepare_extract_directory() {
  dir=$1
  if [ -L "$dir" ] || { [ -e "$dir" ] && [ ! -d "$dir" ]; }; then
    abort_verify "Unsafe extraction directory: $dir"
  fi
  if [ ! -d "$dir" ]; then
    mkdir -p "$dir" || abort_verify "Could not create extraction directory: $dir"
  fi
}

prepare_extract_target() {
  target=$1
  parent=${target%/*}
  if [ -L "$parent" ] || { [ -e "$parent" ] && [ ! -d "$parent" ]; }; then
    abort_verify "Unsafe extraction parent: $parent"
  fi
  if [ -L "$target" ] || { [ -e "$target" ] && [ ! -f "$target" ]; }; then
    abort_verify "Unsafe existing extraction target: $(basename "$target")"
  fi
  if [ -f "$target" ]; then
    rm -f "$target" || abort_verify "Could not replace $(basename "$target")"
  fi
}

# extract <zip> <file> <target dir> [junk paths]
extract() {
  zip=$1
  file=$2
  dir=$3
  junk_paths=${4:-false}

  prepare_extract_directory "$dir"
  if [ "$junk_paths" = true ]; then
    file_path="$dir/$(basename "$file")"
    hash_path="$dir/$(basename "$file").sha256"
  else
    file_path="$dir/$file"
    hash_path="$dir/$file.sha256"
  fi

  prepare_extract_target "$file_path"
  prepare_extract_target "$hash_path"

  if [ "$junk_paths" = true ]; then
    unzip -oj "$zip" "$file" -d "$dir" >&2 || abort_verify "Could not extract $file"
    unzip -oj "$zip" "$file.sha256" -d "$dir" >&2 || abort_verify "Checksum missing for $file"
  else
    unzip -o "$zip" "$file" -d "$dir" >&2 || abort_verify "Could not extract $file"
    unzip -o "$zip" "$file.sha256" -d "$dir" >&2 || abort_verify "Checksum missing for $file"
  fi

  [ ! -L "$file_path" ] && [ -f "$file_path" ] || abort_verify "$file does not exist safely"
  [ ! -L "$hash_path" ] && [ -f "$hash_path" ] || abort_verify "Checksum missing safely for $file"
  verify_hash "$file_path" "$hash_path"
  ui_print "- Verified $file"
}
