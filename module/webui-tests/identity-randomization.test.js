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
assert.ok(featureCenter.includes('Open Identity'), 'Dashboard must navigate into Identity');
assert.ok(!featureCenter.includes('identityFeatureCardsMarkup(`${prefix}_identity`)'), 'Identity switches must not remain on Dashboard');
assert.ok(policy.includes("panel.id = 'ct_identity_controls'"), 'Identity Controls panel must live on the Identity page');
assert.ok(policy.includes("host.innerHTML = identityControlsMarkup('ct_identity_page')"), 'Identity page must render Identity master and child controls');
assert.ok(policy.includes("bindIdentityControls(panel,'ct_identity_page')"), 'Identity page controls must be bound');

console.log('Identity randomization and placement regression tests passed');

assert(index.includes('id="inputVisibleSimCount"'), 'visible SIM count control must exist');
assert(index.includes("randomizeIdentityField('visible_sim_count')"), 'visible SIM count must support single-field randomization');
assert(index.includes("generateRandomIdentity('telephony')"), 'Telephony section must support grouped randomization');
assert(index.includes("visible_sim_count: 'inputVisibleSimCount'"), 'random payload must map visible SIM count');

assert(index.includes('id="inputVisibleCameraCount"'), 'visible camera count control must exist');
assert(index.includes("randomizeIdentityField('visible_camera_count')"), 'camera count must support single-field randomization');
assert(index.includes("visible_camera_count: 'inputVisibleCameraCount'"), 'random payload must map visible camera count');
assert(policy.includes("setLegacyToggle('camera_visibility'"), 'camera visibility must be an explicit opt-in legacy toggle');
assert(policy.includes('Disabled means no cameraserver interceptor is started.'), 'camera control must document disabled lifecycle');
