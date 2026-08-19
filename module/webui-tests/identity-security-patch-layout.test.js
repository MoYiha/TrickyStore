const assert = require('assert');
const fs = require('fs');

const policy = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

assert.ok(!policy.includes("makeTab('patch','Security Patch','spoof')"), 'Security Patch must not be a top-level tab');
assert.ok(!policy.includes("makePage('patch','spoof')"), 'Security Patch must not own a standalone page');
assert.ok(policy.includes("const stalePatchTab = document.getElementById('tab_patch')"), 'stale Security Patch tabs must be retired');
assert.ok(policy.includes("const stalePatchPage = document.getElementById('patch')"), 'stale Security Patch pages must be retired');
assert.ok(policy.includes("makeTab('profiles','Profiles','spoof')"), 'Profiles must stay adjacent to Identity after Security Patch tab retirement');
assert.ok(policy.includes("const profilesPage = makePage('profiles','spoof')"), 'Profiles page placement must no longer depend on the retired patch page');
assert.ok(policy.includes("patchHost.id = 'ct_identity_patch'"), 'Security Patch controls must be mounted under Identity');
assert.ok(policy.includes("const spoofPage = document.getElementById('spoof')"), 'Security Patch host must use the Identity page');

const featureStart = policy.indexOf('function buildFeatureCenterMarkup(prefix)');
const featureEnd = policy.indexOf('function renderFeatureCenter()', featureStart);
assert.ok(featureStart >= 0 && featureEnd > featureStart, 'Feature Center block must exist');
const featureBlock = policy.slice(featureStart, featureEnd);
assert.ok(!featureBlock.includes("cardMarkup(`${prefix}_patch`"), 'Dashboard must not expose Security Patch as a card');
assert.ok(!featureBlock.includes('Advanced Security Patch'), 'Dashboard must not expose Security Patch child controls');
assert.ok(featureBlock.includes('identityFeatureCardsMarkup(`${prefix}_identity`)'), 'Dashboard must own the Identity parent and child controls');

const identityStart = policy.indexOf('function policyIdentityEnabled()');
const identityEnd = policy.indexOf('function identityEnabled()', identityStart);
const identityBlock = policy.slice(identityStart, identityEnd);
assert.ok(identityBlock.includes('FEATURE_KEYS.some'), 'Identity master state must remain based only on Identity feature keys');
assert.ok(!identityBlock.includes('securityPatch'), 'Moving Security Patch in the UI must not make it functionally depend on Identity master state');

assert.ok(policy.includes('securityPatch: Boolean(features.securityPatch)'), 'global Security Patch policy persistence must remain compatible');
assert.ok(policy.includes("['securityPatch', 'Security Patch'"), 'per-profile Security Patch override compatibility must remain intact');
assert.ok(policy.includes("document.getElementById('ct_patch_master')"), 'existing Security Patch renderer must keep its control IDs');
assert.ok(policy.includes("document.getElementById('ct_patch_save')"), 'existing Security Patch save flow must remain wired');
assert.ok(!policy.includes("data-open-tab=\"patch\""), 'no UI control may navigate to the retired Security Patch tab');

console.log('Identity / Security Patch layout compatibility checks passed');
