'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..', '..');
const policySource = fs.readFileSync(path.join(root, 'module/template/webroot/policy.js'), 'utf8');
const uxSource = fs.readFileSync(path.join(root, 'module/template/webroot/ux.js'), 'utf8');

// Source shape: the telephony scope block lives inside the Identity cards,
// never under the Global Keybox card.
assert.ok(
  policySource.includes('id="${prefix}_telephony_scope"'),
  'identity cards must own the telephony scope block',
);
assert.ok(
  !policySource.includes('id="${prefix}_global_children"'),
  'global card must not own scope children',
);
assert.ok(
  policySource.includes('for="${prefix}_global_telephony"'),
  'scope block must label the telephony sub-toggle',
);
assert.ok(
  policySource.includes('switchMarkup(`${prefix}_global_telephony`'),
  'scope block must render the telephony sub-toggle',
);
assert.ok(
  policySource.includes("setLegacyToggle('global_telephony_mode'"),
  'telephony sub-toggle must persist through the legacy toggle queue',
);
assert.ok(
  !policySource.includes("pickerText('global_scope_warning'"),
  'retired carrier warning must not render anywhere',
);
assert.ok(
  policySource.includes("pickerText('global_telephony_title'"),
  'telephony sub-toggle must carry a translated title',
);
assert.ok(
  policySource.includes("pickerText('global_telephony_desc'"),
  'telephony sub-toggle must carry a translated description',
);
assert.ok(
  policySource.includes('id="${prefix}_attestation_scope"'),
  'identity cards must own the attestation scope block',
);
assert.ok(
  policySource.includes('for="${prefix}_global_attestation"'),
  'scope block must label the attestation sub-toggle',
);
assert.ok(
  policySource.includes('switchMarkup(`${prefix}_global_attestation`'),
  'scope block must render the attestation sub-toggle',
);
assert.ok(
  policySource.includes("setLegacyToggle('global_attestation_mode'"),
  'attestation sub-toggle must persist through the legacy toggle queue',
);
assert.ok(
  policySource.includes("pickerText('global_attestation_title'"),
  'attestation sub-toggle must carry a translated title',
);
assert.ok(
  policySource.includes("pickerText('global_attestation_desc'"),
  'attestation sub-toggle must carry a translated description',
);
assert.ok(
  !policySource.includes('globalIdentityRow'),
  'retired Global Identity row must be gone',
);
assert.ok(
  policySource.includes('<div class="row" style="margin-top:10px"><label for="${prefix}_global_telephony"'),
  'sub-toggle row must keep breathing room',
);

// Full nine-locale coverage for the user-visible strings.
const newKeys = {
  global_telephony_title: ['Telephony for all targets', 'Tüm hedeflere telefoni'],
  global_telephony_desc: ['Apply telephony overrides to every targeted app', 'Telefon kimliği'],
  global_attestation_title: ['Attestation for all targets', 'Tüm hedeflere attestasyon'],
  global_attestation_desc: ['Apply attestation identifiers to every targeted app', 'Attestasyon tanımlayıcılarını'],
};
for (const [key, samples] of Object.entries(newKeys)) {
  assert.ok(uxSource.includes(`'${key}':`), `TRANSLATIONS/COPY is missing key: ${key}`);
  for (const sample of samples) {
    assert.ok(uxSource.includes(sample), `translation sample is missing for ${key}: ${sample}`);
  }
  assert.ok(uxSource.includes(`["${key}",`), `complete catalog row is missing for: ${key}`);
}

// Behavior: execute the real identityFeatureCardsMarkup with stubbed
// collaborators and prove the scope block follows the telephony feature.
const cardsStart = policySource.indexOf('function identityFeatureCardsMarkup');
const cardsEnd = policySource.indexOf('function identityControlsMarkup', cardsStart);
assert.ok(cardsStart >= 0 && cardsEnd > cardsStart, 'identity cards markup block is missing');
const cardsCode = policySource.slice(cardsStart, cardsEnd);

function renderIdentityCards({ telephonyFeature, legacyTelephony, globalTelephony, attestationFeature, legacyEngine, globalAttestation }) {
  const context = {
    console,
    legacyConfig: {
      telephony: legacyTelephony,
      global_telephony_mode: globalTelephony,
      spoof_enabled: legacyEngine,
      global_attestation_mode: globalAttestation,
    },
    policyState: { features: { telephonyIdentity: telephonyFeature, attestationIdentity: attestationFeature }, activeProfile: null },
    FEATURE_KEYS: [],
    policyIdentityEnabled: () => true,
    escapeHtml: (value) => String(value),
    pickerText: (key, fallback) => fallback,
    switchMarkup: (id, checked) => `<input id="${id}"${checked ? ' checked' : ''}>`,
    helpMarkup: () => '',
    cardMarkup: (id, title, desc, checked, children) => `<card id="${id}">${children || ''}</card>`,
  };
  context.window = context;
  context.global = context;
  vm.createContext(context);
  vm.runInContext(`${cardsCode}\nthis.cards = identityFeatureCardsMarkup;`, context, {
    filename: 'policy.js#telephony-scope',
  });
  return vm.runInContext(`this.cards('ct_ident')`, context, { filename: 'policy.js#telephony-scope-call' });
}

const openHtml = renderIdentityCards({
  telephonyFeature: true,
  legacyTelephony: false,
  globalTelephony: true,
  attestationFeature: true,
  legacyEngine: false,
  globalAttestation: true,
});
assert.ok(openHtml.includes('id="ct_ident_telephony_scope" >'), 'scope block must show while telephony is on');
assert.ok(openHtml.includes('id="ct_ident_global_telephony" checked'), 'sub-toggle must reflect the stored opt-in');
assert.ok(openHtml.includes('Telephony for all targets'), 'sub-toggle title must render');
assert.ok(openHtml.includes('id="ct_ident_attestation_scope" >'), 'attestation block must show while attestation is on');
assert.ok(openHtml.includes('id="ct_ident_global_attestation" checked'), 'attestation toggle must reflect the stored opt-in');
assert.ok(openHtml.includes('Attestation for all targets'), 'attestation title must render');

const legacyHtml = renderIdentityCards({
  telephonyFeature: false,
  legacyTelephony: true,
  globalTelephony: false,
  attestationFeature: false,
  legacyEngine: true,
  globalAttestation: false,
});
assert.ok(legacyHtml.includes('id="ct_ident_telephony_scope" >'), 'legacy telephony marker must also reveal scope');
assert.ok(
  !legacyHtml.includes('id="ct_ident_global_telephony" checked'),
  'sub-toggle must clear with the opt-in off',
);
assert.ok(legacyHtml.includes('id="ct_ident_attestation_scope" >'), 'legacy engine must reveal attestation scope');

const closedHtml = renderIdentityCards({
  telephonyFeature: false,
  legacyTelephony: false,
  globalTelephony: false,
  attestationFeature: false,
  legacyEngine: false,
  globalAttestation: false,
});
assert.ok(
  closedHtml.includes('id="ct_ident_telephony_scope" hidden>'),
  'scope block must hide while telephony is off',
);
assert.ok(
  closedHtml.includes('id="ct_ident_attestation_scope" hidden>'),
  'attestation block must hide while attestation is off',
);

console.log('Global scope sub-control regression checks passed');
