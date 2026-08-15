const assert = require('assert');
const fs = require('fs');

const index = fs.readFileSync('module/template/webroot/index.html', 'utf8');
const policy = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

const fields = [
  ['imei','inputImei'], ['meid','inputMeid'], ['imsi','inputImsi'], ['iccid','inputIccid'], ['phone_number','inputPhoneNumber'],
  ['imei2','inputImei2'], ['meid2','inputMeid2'], ['imsi2','inputImsi2'], ['iccid2','inputIccid2'], ['phone_number2','inputPhoneNumber2'],
  ['serial','inputSerial']
];
for (const [field,id] of fields) {
  assert.ok(index.includes(`id="${id}"`), `missing identity input ${id}`);
  assert.ok(index.includes(`randomizeIdentityField('${field}')`), `missing per-value Random action for ${field}`);
}
assert.ok(index.includes("generateRandomIdentity('template')"), 'template Random action is missing');
assert.ok(index.includes("generateRandomIdentity('all')"), 'Randomize All action is missing');
assert.ok(index.includes("/api/random_identity?field="), 'randomization must use the bounded on-demand API');
assert.ok(!/setInterval\s*\([^)]*random/i.test(index), 'randomization must not create a periodic worker');

const featureStart = policy.indexOf('function buildFeatureCenterMarkup(prefix)');
const featureEnd = policy.indexOf('function renderFeatureCenter()', featureStart);
assert.ok(featureStart >= 0 && featureEnd > featureStart, 'Feature Center function is missing');
const featureCenter = policy.slice(featureStart, featureEnd);
assert.ok(!featureCenter.includes("cardMarkup(`${prefix}_identity`"), 'Identity master must not remain on Dashboard');
assert.ok(policy.includes("panel.id = 'ct_identity_controls'"), 'Identity Controls panel is missing');
assert.ok(policy.includes('function renderIdentityControls()'), 'Identity Controls renderer is missing');
assert.ok(policy.includes("panel.id !== 'ct_identity_controls'"), 'legacy cleanup must preserve policy-owned Identity Controls');

console.log('Identity randomization and placement regression tests passed');
