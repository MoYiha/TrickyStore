'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const indexSource = fs.readFileSync('module/template/webroot/index.html', 'utf8');
const policySource = fs.readFileSync('module/template/webroot/policy.js', 'utf8');
const uxSource = fs.readFileSync('module/template/webroot/ux.js', 'utf8');

// Part A: design-token and structural CSS contracts (single dark theme).
for (const token of ['--ct-space-1: 4px', '--ct-space-4: 16px', '--ct-space-8: 32px', '--ct-radius-md: 14px', '--ct-radius-lg: 18px']) {
  assert.ok(indexSource.includes(token), `spacing/radius token is missing: ${token}`);
}
assert.ok(indexSource.includes('.ct-package-option'), 'package option component style is missing');
assert.ok(indexSource.includes('min-height: 68px'), 'package options must stay thumb-sized');
assert.ok(indexSource.includes('.ct-appicon'), 'app icon style is missing');
assert.ok(indexSource.includes('.ct-empty'), 'empty-state style is missing');
assert.ok(indexSource.includes('.ct-skeleton'), 'skeleton style is missing');
assert.ok(indexSource.includes('prefers-reduced-motion'), 'reduced-motion guard is missing');
assert.ok(!indexSource.includes('prefers-color-scheme: light'), 'no light theme may be introduced');
assert.ok(!indexSource.includes('data-theme'), 'no theme switcher may be introduced');
assert.ok(indexSource.includes("img-src 'self' data: ksu:"), 'app icons must be allowed through the host scheme');
assert.ok(policySource.includes('slice(0,24)'), 'picker result cap must stay at 24');
assert.ok(policySource.includes('MAX_PACKAGE_LABEL_CACHE'), 'label cache must stay bounded');

// Part B: picker behavior in a DOM harness.
function makeElement(tagName) {
  const element = {
    tagName: String(tagName).toUpperCase(),
    children: [],
    attributes: {},
    dataset: {},
    style: {},
    hidden: false,
    className: '',
    id: '',
    value: '',
    disabled: false,
    type: '',
    _textContent: '',
    _handlers: {},
    _dispatched: [],
    addEventListener(event, handler) { this._handlers[event] = handler; },
    trigger(event, payload) { if (this._handlers[event]) this._handlers[event](payload || {}); },
    appendChild(child) { this.children.push(child); return child; },
    append(...items) { items.filter(Boolean).forEach(item => this.appendChild(item)); },
    replaceChildren() { this.children = []; },
    setAttribute(name, value) { this.attributes[name] = String(value); },
    getAttribute(name) { return Object.prototype.hasOwnProperty.call(this.attributes, name) ? this.attributes[name] : null; },
    removeAttribute(name) { delete this.attributes[name]; },
    focus() { this._focused = true; },
    click() {
      if (typeof this.onclick === 'function') this.onclick();
      this.trigger('click');
    },
    dispatchEvent(event) { this._dispatched.push(event && event.type); return true; },
    querySelectorAll(selector) {
      const out = [];
      const visit = (node) => {
        for (const child of node.children || []) {
          if (selector === '[role="option"]' && child.attributes && child.attributes.role === 'option') out.push(child);
          if (selector === '[data-ct-package-filter]' && child.dataset && child.dataset.ctPackageFilter) out.push(child);
          visit(child);
        }
      };
      visit(this);
      return out;
    }
  };
  Object.defineProperty(element, 'textContent', {
    get() { return this._textContent + this.children.map(child => child.textContent).join(''); },
    set(value) { this._textContent = String(value); }
  });
  Object.defineProperty(element, 'innerHTML', {
    get() { return ''; },
    set(value) { if (value === '') this.children = []; }
  });
  return element;
}

function makePickerHarness(ksu) {
  const input = makeElement('input');
  input.value = '';
  const parent = makeElement('div');
  parent.appendChild(input);
  parent.insertBefore = (node, ref) => {
    const index = parent.children.indexOf(ref);
    if (index < 0) parent.children.push(node);
    else parent.children.splice(index, 0, node);
    return node;
  };
  const elements = {};
  const context = {
    console,
    JSON,
    Event,
    setTimeout: (fn) => { fn(); return 0; },
    document: {
      getElementById: (id) => elements[id] || null,
      createElement: makeElement
    },
    packages: [],
    ksuCalls: []
  };
  if (ksu) {
    const partial = ksu === 'partial';
    context.ksu = {
      getPackagesInfo(payload) {
        const names = JSON.parse(payload);
        context.ksuCalls.push(names);
        assert.ok(names.length <= 120, 'metadata batch must stay bounded');
        return JSON.stringify(names.filter(name => !partial || !String(name).includes('mystery')).map(name => ({
          packageName: name,
          appLabel: 'Label of ' + name,
          isSystem: String(name).startsWith('com.android.')
        })));
      }
    };
  }
  context.window = context;
  context.global = context;
  elements.test_picker = input;
  input.parentElement = parent;
  vm.createContext(context);
  assert.ok(vm.isContext(context), 'picker harness context must be contextified');
  return { context, input, parent, elements };
}

const pickerStart = policySource.indexOf('const packageLabelCache');
const pickerEnd = policySource.indexOf('function installPackagePickers', pickerStart);
assert.ok(pickerStart >= 0 && pickerEnd > pickerStart, 'picker implementation block is missing');
const pickerCode = policySource.slice(pickerStart, pickerEnd);
assert.ok(pickerCode.includes('role'), 'picker options must carry listbox semantics');
assert.ok(pickerCode.includes('ArrowDown'), 'picker must support keyboard navigation');
assert.ok(pickerCode.includes("chip.addEventListener('pointerdown'"), 'filter chips must not collapse the list on click');

function loadPicker(context, names) {
  context.packages = names;
  vm.runInContext(`
    const MAX_REFERENCE_PACKAGES = 10000;
    let packages = ${JSON.stringify(names)};
    function normalizedPackageNames() {
      return [...new Set(packages.filter(value => typeof value === 'string'))].slice(0, MAX_REFERENCE_PACKAGES).sort();
    }
    ${pickerCode}
    this.installPackagePicker = installPackagePicker;
    this.renderPicker = () => {};
  `, context, { filename: 'policy.js#ux-refresh' });
}

const appNames = ['com.android.settings', 'com.example.app', 'com.example.other'];
const harness = makePickerHarness(true);
loadPicker(harness.context, appNames);
harness.context.installPackagePicker('test_picker');
const wrapper = harness.input.parentElement.children.find(child => child.className === 'ct-package-picker');
assert.ok(wrapper, 'picker must wrap the input in a picker container');
const suggestions = wrapper.children.find(child => child.className === 'ct-package-suggestions');
assert.ok(suggestions, 'picker must render a suggestions container');

harness.input.trigger('focus');
assert.strictEqual(suggestions.hidden, false, 'picker must open on focus');
assert.strictEqual(suggestions.children.length, 3, 'picker must render one option per match');
const first = suggestions.children[0];
assert.strictEqual(first.getAttribute('role'), 'option', 'options must expose listbox roles');
const icon = first.children[0];
assert.ok(icon.tagName === 'IMG' || icon.className === 'ct-appicon-fallback', 'options must lead with an icon or a controlled placeholder');
const label = first.children[1].children[0];
assert.strictEqual(label.textContent, 'Label of com.android.settings', 'labels must resolve through the host metadata');
const sub = first.children[1].children[1];
assert.strictEqual(sub.textContent, 'com.android.settings', 'package name must stay as secondary info');
assert.strictEqual(harness.context.ksuCalls.length, 1, 'metadata must be fetched in one bounded batch');
assert.ok(harness.context.ksuCalls[0].length <= 24, 'metadata batch must stay bounded');
const chips = wrapper.children.find(child => child.className === 'ct-cluster');
assert.ok(chips && !chips.hidden, 'system/user filter must appear after one focus once metadata resolves');
harness.input.trigger('focus');
assert.strictEqual(harness.context.ksuCalls.length, 1, 'resolved labels must be served from cache');

const userChip = chips.querySelectorAll('[data-ct-package-filter]').find(node => node.dataset.ctPackageFilter === 'user');
userChip.click();
assert.strictEqual(suggestions.children.length, 2, 'user filter must hide system packages');
assert.ok(suggestions.children.every(node => !node.children[1].children[1].textContent.startsWith('com.android.')));

harness.input.trigger('keydown', { key: 'ArrowDown', preventDefault() {} });
assert.strictEqual(suggestions.children[0].getAttribute('aria-selected'), 'true', 'keyboard must highlight the first option');
const activeId = suggestions.children[0].id;
assert.ok(activeId.startsWith('ct-pkg-opt-'), 'options must carry unique ids');
assert.strictEqual(harness.input.getAttribute('aria-activedescendant'), activeId, 'combobox must expose the active option');
harness.input.trigger('keydown', { key: 'Escape', preventDefault() {} });
assert.strictEqual(harness.input.getAttribute('aria-activedescendant'), null, 'escape must clear the active option');
harness.input.trigger('keydown', { key: 'ArrowDown', preventDefault() {} });
harness.input.trigger('keydown', { key: 'Enter', preventDefault() {} });
assert.strictEqual(harness.input.value, 'com.example.app', 'keyboard Enter must pick the highlighted option');
assert.deepStrictEqual(harness.input._dispatched, ['change'], 'picking must emit a change event');

harness.input.value = 'typed.manually';
harness.input.trigger('input');
assert.strictEqual(suggestions.children.length, 1, 'empty matches must show the typed-value hint');
assert.strictEqual(harness.input.value, 'typed.manually', 'manual entry must survive without picking');

// Large pools: filters classify a bounded window beyond the render cap.
const manyNames = Array.from({ length: 25 }, (_, index) => `com.android.sys${index}`);
for (let index = 0; index < 5; index++) manyNames.push(`com.example.user${index}`);
manyNames.push('com.mystery.unknown');
const many = makePickerHarness('partial');
loadPicker(many.context, manyNames);
many.context.installPackagePicker('test_picker');
const manyWrapper = many.input.parentElement.children.find(child => child.className === 'ct-package-picker');
const manySuggestions = manyWrapper.children.find(child => child.className === 'ct-package-suggestions');
many.input.trigger('focus');
const manyChips = manyWrapper.children.find(child => child.className === 'ct-cluster');
assert.ok(manyChips && !manyChips.hidden, 'filters must appear after one focus for bounded pools');
const manyUserChip = manyChips.querySelectorAll('[data-ct-package-filter]').find(node => node.dataset.ctPackageFilter === 'user');
manyUserChip.click();
assert.strictEqual(manySuggestions.children.length, 5, 'user filter must find classified matches past the render cap');
assert.ok(manySuggestions.children.every(node => node.children[1].children[1].textContent.startsWith('com.example.')));
const manySystemChip = manyChips.querySelectorAll('[data-ct-package-filter]').find(node => node.dataset.ctPackageFilter === 'system');
manySystemChip.click();
assert.ok(manySuggestions.children.length > 0 && manySuggestions.children.length <= 24, 'system filter must stay capped');
assert.ok(manySuggestions.children.every(node => node.children[1].children[1].textContent.startsWith('com.android.')));
assert.ok(!manySuggestions.textContent.includes('mystery'), 'unclassified packages must stay out of system/user results');

const plain = makePickerHarness(false);
loadPicker(plain.context, appNames);
plain.context.installPackagePicker('test_picker');
const plainWrapper = plain.input.parentElement.children.find(child => child.className === 'ct-package-picker');
assert.ok(plainWrapper, 'fallback picker must wrap the input');
const plainSuggestions = plainWrapper.children.find(child => child.className === 'ct-package-suggestions');
plain.input.trigger('focus');
assert.ok(plainSuggestions.children[0].children[0].className === 'ct-appicon-fallback', 'missing host metadata must fall back to a placeholder');
assert.ok(plainWrapper.children.find(child => child.className === 'ct-cluster').hidden, 'filters must hide without flag data');

// Part C: keybox card hierarchy contracts.
const renderStart = uxSource.search(/function\s+render\s*\(\s*\)/);
const renderEndMatch = /function\s+normalizeKeyboxScope\s*\(/.exec(uxSource.slice(Math.max(renderStart, 0)));
const renderEnd = renderEndMatch ? Math.max(renderStart, 0) + renderEndMatch.index : -1;
assert.ok(renderStart >= 0 && renderEnd > renderStart, 'keybox render block is missing');
const renderCode = uxSource.slice(renderStart, renderEnd);
assert.ok(renderCode.includes('ct-keybox-card'), 'keybox rows must use the card class');
assert.ok(renderCode.includes('ct-empty'), 'keybox list must render a structured empty state');
assert.ok(renderCode.includes('ct-skeleton'), 'keybox list must render skeletons while loading');
assert.ok(renderCode.indexOf('stateBadge') < renderCode.search(/security_level\s*===\s*'StrongBox'/), 'validity state must precede technical level badges');

// Part D: localization parity for every new key across all nine locales.
const newKeys = {
  app_filter_all: ['All', 'Tümü'],
  app_filter_user: ['User', 'Kullanıcı'],
  app_filter_system: ['System', 'Sistem'],
  no_matching_apps: ['No matching apps', 'Eşleşen uygulama yok'],
  keybox_empty_hint: ['Upload a keybox file', 'bir keybox dosyası yükleyin'],
  status_pending_reboot: ['Restart required', 'Yeniden başlatma gerekli'],
  active_profile: ['Active profile', 'Etkin profil']
};

// Part E: dashboard status strip was removed; the feature grid must stay
// single-column and crush-proof on every viewport.
assert.ok(!policySource.includes('function buildStatusStripMarkup'), 'status strip builder must be gone');
assert.ok(!policySource.includes('ct-status-strip'), 'status strip markup must be gone');
assert.ok(!policySource.includes('function statusTile'), 'status tile builder must be gone');
assert.ok(!indexSource.includes('.ct-status-tile'), 'status tile style must be gone');
assert.ok(!indexSource.includes('.ct-status-dot[data-state="on"]'), 'status dot states must be gone');
assert.ok(policySource.includes('.ct-feature-grid{display:grid;grid-template-columns:minmax(0,1fr)'), 'feature grid must stack to one column');
assert.ok(policySource.includes('.ct-feature-card .row{margin:0;align-items:center;flex-wrap:wrap'), 'feature card rows must wrap instead of crushing the label');
assert.ok(policySource.includes('flex:1 1 220px;min-width:0'), 'feature card labels must keep a readable wrap basis');

// Part F: state pills were removed (the toggle alone carries state); the
// identity summary stays.
assert.ok(!policySource.includes('data-ct-state-for'), 'state pill hooks must be gone');
assert.ok(!policySource.includes('function installStateLabelSync'), 'state pill sync must be gone');
assert.ok(!indexSource.includes('.ct-state-label'), 'state pill style must be gone');
assert.ok(policySource.includes('ct-identity-summary'), 'identity card must summarize the active profile');
assert.ok(indexSource.includes('.ct-identity-summary'), 'identity summary style is missing');
for (const [key, samples] of Object.entries(newKeys)) {
  assert.ok(uxSource.includes(`'${key}':`), `TRANSLATIONS/COPY is missing key: ${key}`);
  for (const sample of samples) {
    assert.ok(uxSource.includes(sample), `translation sample is missing for ${key}: ${sample}`);
  }
  assert.ok(uxSource.includes(`["${key}",`), `complete catalog row is missing for: ${key}`);
}

// Part G: no global change-listener sync may remain for the removed pills.
assert.ok(!policySource.includes('ctStateSync'), 'pill sync epoch flag must be gone');

console.log('UX refresh regression checks passed');
