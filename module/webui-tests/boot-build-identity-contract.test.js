'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..', '..');
const postFs = fs.readFileSync(path.join(repoRoot, 'module/template/post-fs-data.sh'), 'utf8');
const postMount = fs.readFileSync(path.join(repoRoot, 'module/template/post-mount.sh'), 'utf8');

function requireToken(token, message) {
  assert.ok(postFs.includes(token), message || `missing boot identity contract: ${token}`);
}

requireToken('[ -f "$CONFIG_DIR/spoof_enabled" ] || return 0');
requireToken('boot_policy_feature_enabled() {', 'boot projection gate must remain explicit');
requireToken('state="$CONFIG_DIR/boot_policy_state"', 'early boot must consume the derived projection');
requireToken('[ "$state_size" -ge 1 ] && [ "$state_size" -le 128 ]', 'projection read must stay bounded');
requireToken('[ "$policy_status" -eq 2 ] && return 0', 'legacy marker fallback is allowed only without v2/projection state');
requireToken('optional_marker_enabled buildIdentity spoof_build_identity || return 0');
requireToken('vars_file="$CONFIG_DIR/spoof_build_vars"');
requireToken('done < "$vars_file"');
assert.doesNotMatch(postFs, /awk -v target=|policy_state_v2\.json.*awk|depth == 1 && token == "features"/, 'shell must not parse policy JSON');

const conflictMatch = postFs.match(
  /if \[ "\$identity_conflict" = true \]; then([\s\S]*?)\n\s*fi\n\s*fi\n\n\s*CT_FINGERPRINT=/,
);
assert.ok(conflictMatch, 'boot identity conflict branch must remain explicit and adjacent to identity application');
assert.doesNotMatch(conflictMatch[1], /\breturn\s+0\b/);
assert.match(conflictMatch[1], /reasserting the enabled CleveresTricky Build Identity/);

requireToken('apply_core_boot_properties');
requireToken('apply_optional_identity_properties');
const earlyOwner = postFs.match(/apply_early_properties\(\) \{([\s\S]*?)\n\}/);
assert.ok(earlyOwner, 'early property owner must remain explicit');
assert.match(earlyOwner[1], /apply_core_boot_properties/);
assert.match(earlyOwner[1], /apply_optional_identity_properties/);

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

requireToken('resetprop -n "$1" "$2"');
assert.match(postMount, /CLEVERES_TRICKY_IDENTITY_ONLY=1/);
assert.match(postMount, /\. "\$MODDIR\/post-fs-data\.sh"/);

console.log('Build Identity boot contract uses a bounded projection and preserves the exhaustive property owner.');
