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
  policySource.includes("pickerText('global_scope_warning'"),
  'scope block must render the carrier-risk warning',
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
  policySource.includes('style="margin:14px 0 6px"'),
  'warning note must keep breathing room from the toggle row',
);
assert.ok(
  policySource.includes('<div class="row" style="margin-top:10px"><label for="${prefix}_global_telephony"'),
  'sub-toggle row must keep breathing room from the warning',
);

// Full nine-locale coverage for the user-visible strings.
const newKeys = {
  global_telephony_title: ['Telephony for all targets', 'Tüm hedeflere telefoni'],
  global_telephony_desc: ['Apply telephony overrides to every targeted app', 'Telefon kimliği'],
  global_scope_warning: ['Making everything global can break carrier features', 'global yapmak'],
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

function renderIdentityCards({ telephonyFeature, legacyTelephony, globalTelephony }) {
  const context = {
    console,
    legacyConfig: { telephony: legacyTelephony, global_telephony_mode: globalTelephony },
    policyState: { features: { telephonyIdentity: telephonyFeature }, activeProfile: null },
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

const openHtml = renderIdentityCards({ telephonyFeature: true, legacyTelephony: false, globalTelephony: true });
assert.ok(openHtml.includes('id="ct_ident_telephony_scope" >'), 'scope block must show while telephony is on');
assert.ok(openHtml.includes('id="ct_ident_global_telephony" checked'), 'sub-toggle must reflect the stored opt-in');
assert.ok(
  openHtml.includes('Making everything global can break carrier features'),
  'carrier-risk warning must render',
);
assert.ok(openHtml.includes('Telephony for all targets'), 'sub-toggle title must render');

const legacyHtml = renderIdentityCards({ telephonyFeature: false, legacyTelephony: true, globalTelephony: false });
assert.ok(legacyHtml.includes('id="ct_ident_telephony_scope" >'), 'legacy telephony marker must also reveal scope');
assert.ok(
  !legacyHtml.includes('id="ct_ident_global_telephony" checked'),
  'sub-toggle must clear with the opt-in off',
);

const closedHtml = renderIdentityCards({ telephonyFeature: false, legacyTelephony: false, globalTelephony: false });
assert.ok(
  closedHtml.includes('id="ct_ident_telephony_scope" hidden>'),
  'scope block must hide while telephony is off',
);

console.log('Global scope sub-control regression checks passed');
