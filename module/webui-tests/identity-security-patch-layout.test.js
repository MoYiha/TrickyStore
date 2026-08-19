const assert = require('assert');
const fs = require('fs');

const policy = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

assert.ok(!policy.includes("makeTab('patch','Security Patch','spoof')"), 'Security Patch must not be a top-level tab');
assert.ok(!policy.includes("makePage('patch','spoof')"), 'Security Patch must not own a standalone page');
assert.ok(policy.includes("const stalePatchTab = document.getElementById('tab_patch')"), 'stale Security Patch tabs must be retired');
assert.ok(policy.includes("const stalePatchPage = document.getElementById('patch')"), 'stale Security Patch pages must be retired');
assert.ok(policy.includes("patchHost.id = 'ct_identity_patch'"), 'Security Patch controls must be mounted under Identity');
assert.ok(policy.includes("const spoofPage = document.getElementById('spoof')"), 'Security Patch host must use the Identity page');

const featureStart = policy.indexOf('function buildFeatureCenterMarkup(prefix)');
const featureEnd = policy.indexOf('function renderFeatureCenter()', featureStart);
assert.ok(featureStart >= 0 && featureEnd > featureStart, 'Feature Center block must exist');
const featureBlock = policy.slice(featureStart, featureEnd);
assert.ok(!featureBlock.includes("cardMarkup(`${prefix}_patch`"), 'Dashboard must not expose Security Patch as a card');
assert.ok(!featureBlock.includes('identityFeatureCardsMarkup(`${prefix}_identity`)'), 'Dashboard must not own Identity switches');
assert.ok(featureBlock.includes('data-open-tab="spoof"'), 'Dashboard Identity card must open the Identity page');
assert.ok(featureBlock.includes('Open Identity'), 'Dashboard Identity card must expose a clear open action');

assert.ok(policy.includes("panel.id = 'ct_identity_controls'"), 'Identity page must own the Identity Controls panel');
assert.ok(policy.includes("host.innerHTML = identityControlsMarkup('ct_identity_page')"), 'Identity page must render the master and child controls');
assert.ok(policy.includes("bindIdentityControls(panel,'ct_identity_page')"), 'Identity page controls must be bound locally');
assert.ok(!policy.includes('id="ct_patch_master"'), 'Security Patch must not expose a second master toggle');
assert.ok(!policy.includes("document.getElementById('ct_patch_master')"), 'Security Patch renderer must not bind a retired master toggle');
assert.ok(policy.includes("securityPatch: FEATURE_KEYS.some(([key]) => Boolean(features[key]))"), 'global Security Patch state must follow Identity feature state');
assert.ok(policy.includes("['securityPatch', 'Security Patch'"), 'per-profile Security Patch override compatibility must remain intact');
assert.ok(!policy.includes("data-open-tab=\"patch\""), 'no UI control may navigate to the retired Security Patch tab');

const bindStart = policy.indexOf('function bindFeatureCenter(panel, prefix)');
const bindEnd = policy.indexOf('async function setLegacyToggle', bindStart);
const bindBlock = policy.slice(bindStart, bindEnd);
assert.ok(!bindBlock.includes('bindIdentityControls('), 'Dashboard must not bind Identity switches');
assert.ok(!bindBlock.includes('patchToggle'), 'Dashboard binding must not retain retired Security Patch wiring');
assert.ok(!bindBlock.includes('autoPatch'), 'Dashboard binding must not retain retired auto-patch wiring');

console.log('Identity / Security Patch ownership checks passed');
