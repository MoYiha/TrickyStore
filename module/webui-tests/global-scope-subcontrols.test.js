'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..', '..');
const policySource = fs.readFileSync(path.join(root, 'module/template/webroot/policy.js'), 'utf8');
const uxSource = fs.readFileSync(path.join(root, 'module/template/webroot/ux.js'), 'utf8');

// Source shape: the Global Keybox card owns a collapsible sub-control block
// with the carrier-risk warning and the explicit global-telephony switch.
assert.ok(
  policySource.includes('id="${prefix}_global_children"'),
  'global card must own a collapsible children block',
);
assert.ok(
  policySource.includes('for="${prefix}_global_telephony"'),
  'global card must label the telephony sub-toggle',
);
assert.ok(
  policySource.includes('switchMarkup(`${prefix}_global_telephony`'),
  'global card must render the telephony sub-toggle',
);
assert.ok(
  policySource.includes("setLegacyToggle('global_telephony_mode'"),
  'telephony sub-toggle must persist through the legacy toggle queue',
);
assert.ok(
  policySource.includes("panel.querySelector(`#${prefix}_global_children`)"),
  'global toggle must show and hide its children block',
);
assert.ok(
  policySource.includes("pickerText('global_scope_warning'"),
  'global card must render the carrier-risk warning',
);
assert.ok(
  policySource.includes("pickerText('global_telephony_title'"),
  'telephony sub-toggle must carry a translated title',
);
assert.ok(
  policySource.includes("pickerText('global_telephony_desc'"),
  'telephony sub-toggle must carry a translated description',
);

// Full nine-locale coverage for the new user-visible strings.
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

// Behavior: execute the real buildFeatureCenterMarkup with stubbed
// collaborators and prove the children block follows the global switch.
const markupStart = policySource.indexOf('function buildFeatureCenterMarkup');
const markupEnd = policySource.indexOf('function refreshDynamicVisibility', markupStart);
assert.ok(markupStart >= 0 && markupEnd > markupStart, 'feature center markup block is missing');
const markupCode = policySource.slice(markupStart, markupEnd);

function renderWith(globalMode, globalTelephony) {
  const context = {
    console,
    legacyConfig: { global_mode: globalMode, global_telephony_mode: globalTelephony },
    policyState: { features: {} },
    identityFeatureCardsMarkup: () => '',
    helpMarkup: () => '',
    cardMarkup: (id, title, desc, checked, children) => `<card id="${id}">${children || ''}</card>`,
    switchMarkup: (id, checked) => `<input id="${id}"${checked ? ' checked' : ''}>`,
    escapeHtml: (value) => String(value),
    pickerText: (key, fallback) => fallback,
  };
  context.window = context;
  context.global = context;
  vm.createContext(context);
  vm.runInContext(`${markupCode}\nthis.build = buildFeatureCenterMarkup;`, context, {
    filename: 'policy.js#global-scope',
  });
  return vm.runInContext(`this.build('ct_dash')`, context, { filename: 'policy.js#global-scope-call' });
}

const openHtml = renderWith(true, true);
assert.ok(openHtml.includes('id="ct_dash_global_children" >'), 'children must be visible while global is on');
assert.ok(openHtml.includes('id="ct_dash_global_telephony" checked'), 'sub-toggle must reflect the stored opt-in');
assert.ok(
  openHtml.includes('Making everything global can break carrier features'),
  'carrier-risk warning must render',
);
assert.ok(openHtml.includes('Telephony for all targets'), 'sub-toggle title must render');

const closedHtml = renderWith(false, false);
assert.ok(
  closedHtml.includes('id="ct_dash_global_children" hidden>'),
  'children must hide while global is off',
);
assert.ok(!closedHtml.includes('id="ct_dash_global_telephony" checked'), 'sub-toggle must clear with the opt-in off');

console.log('Global scope sub-control regression checks passed');
