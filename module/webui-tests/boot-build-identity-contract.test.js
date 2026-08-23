'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..', '..');
const postFs = fs.readFileSync(path.join(repoRoot, 'module/template/post-fs-data.sh'), 'utf8');

function requireToken(token, message) {
  assert.ok(postFs.includes(token), message || `missing boot identity contract: ${token}`);
}

// Identity is an explicit user feature: both owner markers and the canonical
// persisted build-vars file must gate the early-boot application path.
requireToken('[ -f "$CONFIG_DIR/spoof_enabled" ] || return 0');
requireToken('[ -f "$CONFIG_DIR/spoof_build_identity" ] || return 0');
requireToken('vars_file="$CONFIG_DIR/spoof_build_vars"');
requireToken('done < "$vars_file"');

// A competing PIF/Play Integrity identity provider is useful diagnostic
// information, but must never silently turn an enabled CleveresTricky feature
// into a no-op. This is the physical-device regression that previously left
// Build.*, fingerprint and model values untouched even while the UI reported
// Build Identity as enabled.
const conflictMatch = postFs.match(
  /if \[ "\$identity_conflict" = true \]; then([\s\S]*?)\n\s*fi\n\s*fi\n\n\s*CT_FINGERPRINT=/,
);
assert.ok(conflictMatch, 'boot identity conflict branch must remain explicit and adjacent to identity application');
assert.doesNotMatch(
  conflictMatch[1],
  /\breturn\s+0\b/,
  'an enabled Build Identity must not be skipped just because another identity provider is installed',
);
assert.match(
  conflictMatch[1],
  /applying the enabled CleveresTricky Build Identity anyway/,
  'provider conflicts must be logged while honoring the enabled feature',
);

// The saved Identity Manager fields must reach the properties Android Build
// snapshots before Zygote starts. Keep this list exhaustive for the fields the
// persisted template exposes to applications.
for (const mapping of [
  ['FINGERPRINT', 'ro.build.fingerprint'],
  ['BRAND', 'ro.product.brand'],
  ['DEVICE', 'ro.product.device'],
  ['PRODUCT', 'ro.product.name'],
  ['MANUFACTURER', 'ro.product.manufacturer'],
  ['MODEL', 'ro.product.model'],
  ['BUILD_ID', 'ro.build.id'],
  ['RELEASE', 'ro.build.version.release'],
  ['RELEASE', 'ro.build.version.release_or_codename'],
  ['INCREMENTAL', 'ro.build.version.incremental'],
  ['TYPE', 'ro.build.type'],
  ['TAGS', 'ro.build.tags'],
  ['SECURITY_PATCH', 'ro.build.version.security_patch'],
]) {
  const [field, property] = mapping;
  requireToken(`apply_prop ${property} "$CT_${field}"`, `${field} must be applied to ${property}`);
}

requireToken('resetprop -n "$1" "$2"', 'boot identity must use KernelSU/APatch-safe resetprop -n');
requireToken('apply_early_properties', 'post-fs-data must execute the early property application owner');

console.log('Build Identity boot contract honors the enabled feature and maps every persisted Build field before Zygote.');
