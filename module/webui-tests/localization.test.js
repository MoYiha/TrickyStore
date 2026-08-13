const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const uxSource = fs.readFileSync('module/template/webroot/ux.js', 'utf8');

function loadI18n(locale) {
    const document = {
        readyState: 'loading',
        documentElement: {},
        body: null,
        addEventListener() {}
    };
    const context = {
        console,
        document,
        localStorage: { getItem: () => locale, setItem() {} },
        CleveresBridge: {},
        setTimeout() {},
        clearTimeout() {}
    };
    context.window = context;
    vm.createContext(context);
    vm.runInContext(uxSource, context, { filename: 'ux.js' });
    return context.CleveresI18n;
}

const sharedCoreCopy = [
    'Dashboard',
    'Info & Resources',
    'Runtime Health',
    'Resource Monitor',
    'Module Logs',
    'Language'
];

for (const locale of ['tr', 'zh-CN', 'es', 'de', 'ru', 'id', 'hi', 'ar']) {
    const i18n = loadI18n(locale);
    assert.strictEqual(i18n.locale, locale);
    for (const source of sharedCoreCopy) {
        assert.notStrictEqual(i18n.translate(source), source, `${locale} is missing core copy: ${source}`);
    }
}

const turkish = loadI18n('tr');
const turkishCompleteSurfaces = [
    'Always active.',
    'Identity Engine',
    'Select the attestation identity used for configured target applications.',
    'Attestation and Telephony Identifiers',
    'Application Privacy Shield',
    'Remote Servers',
    'Upload Keybox / CBOX',
    'Stored Keyboxes',
    'Checking module state...',
    'The last native activation attempt failed before the Keystore interceptor became operational.',
    'Measured daemon CPU and resident memory are shown above. Runtime rows describe configuration and execution scope. Hardware bootloader and root-of-trust warnings can remain visible because this page reports module state, not a physically relocked device.',
    'Feature Center',
    'What does this do?',
    'Security Patch',
    'Profiles',
    'Profile Editor',
    'Effective State',
    'View recent logs from the module. You can also download them for sharing.',
    'Support the Development',
    'Thank you for your support!'
];
for (const source of turkishCompleteSurfaces) {
    assert.notStrictEqual(turkish.translate(source), source, `Turkish surface copy is missing: ${source}`);
}

assert.match(
    turkish.translate('Native runtime is active with 4 verified keyboxes. Global application scope is enabled. Core boot/TEE compatibility remains active independently of Identity Engine; hardware bootloader and root-of-trust state remain genuine.'),
    /4 doğrulanmış keybox/
);
assert.strictEqual(turkish.translate('4 Keys Loaded'), '4 anahtar yüklendi');
assert.strictEqual(turkish.translate('com.example.app'), 'com.example.app');
assert.strictEqual(loadI18n('en').translate('Runtime Health'), 'Runtime Health');

console.log('WebUI localization coverage tests passed');
