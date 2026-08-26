const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('module/template/webroot/policy.js', 'utf8');
const instrumented = source.replace(
    'onReady(initialize);',
    'global.__policyTest = { switchMarkup, markPendingReboot, clearPendingReboot, reconcilePolicyPendingReboot, notifyPolicyMutation };\nonReady(() => {});',
);

function createPolicyHarness(initialStorage = {}) {
    const storage = new Map(Object.entries(initialStorage));
    const messages = [];
    const context = {
        CleveresBridge: { fetch() { throw new Error('network should not be used'); } },
        document: {
            body: undefined,
            addEventListener() {},
        },
        sessionStorage: {
            getItem(key) { return storage.has(key) ? storage.get(key) : null; },
            setItem(key, value) { storage.set(key, value); },
        },
        console,
        JSON,
        Set,
        Array,
        Promise,
        notify(message, type) { messages.push({ message, type }); },
    };
    context.window = context;
    vm.runInNewContext(instrumented, context, { filename: 'policy.js' });
    return { api: context.__policyTest, storage, messages };
}

function pending(storage) {
    const raw = storage.get('ct_pending_reboot');
    return raw ? JSON.parse(raw) : [];
}

const initial = createPolicyHarness();
assert.doesNotMatch(
    initial.api.switchMarkup('ct_dash_identity_build', true, 'data-policy-feature="buildIdentity"'),
    /pending-reboot/,
    'a reboot marker must not appear before a policy transition requires it',
);

initial.api.reconcilePolicyPendingReboot(
    { features: { buildIdentity: false, regionIdentity: false, identityRefresh: false } },
    {
        features: { buildIdentity: true, regionIdentity: false, identityRefresh: false },
        runtimeTransition: { rebootRequired: true, apply: { buildApplied: false, regionApplied: false } },
    },
);
assert.deepStrictEqual(pending(initial.storage), ['feature:buildIdentity']);
assert.match(
    initial.api.switchMarkup('ct_dash_identity_build', true, 'data-policy-feature="buildIdentity"'),
    /class="ct-switch pending-reboot"/,
    'Build Identity must render yellow when the backend reports a reboot requirement',
);
assert.match(
    initial.api.switchMarkup('ct_dash_identity_build', true, 'data-policy-feature="buildIdentity"'),
    /data-pending-reboot="true"/,
);

initial.api.reconcilePolicyPendingReboot(
    { features: { buildIdentity: true, regionIdentity: false, identityRefresh: false } },
    {
        features: { buildIdentity: false, regionIdentity: false, identityRefresh: false },
        runtimeTransition: { rebootRequired: false, restore: { buildApplied: true, regionApplied: false } },
    },
);
assert.deepStrictEqual(pending(initial.storage), []);
assert.doesNotMatch(
    initial.api.switchMarkup('ct_dash_identity_build', false, 'data-policy-feature="buildIdentity"'),
    /pending-reboot/,
    'a successfully restored Build Identity must clear its pending marker',
);

const region = createPolicyHarness();
region.api.reconcilePolicyPendingReboot(
    { features: { buildIdentity: false, regionIdentity: false, identityRefresh: false } },
    {
        features: { buildIdentity: false, regionIdentity: true, identityRefresh: false },
        runtimeTransition: { rebootRequired: true, apply: { buildApplied: true, regionApplied: false } },
    },
);
assert.match(
    region.api.switchMarkup('ct_dash_identity_region', true, 'data-policy-feature="regionIdentity"'),
    /pending-reboot/,
    'Region Identity must render yellow when only the region transition needs a reboot',
);
assert.doesNotMatch(
    region.api.switchMarkup('ct_dash_identity_build', true, 'data-policy-feature="buildIdentity"'),
    /pending-reboot/,
    'Build Identity must not be marked when the backend confirmed it was applied',
);

const refresh = createPolicyHarness();
refresh.api.reconcilePolicyPendingReboot(
    { features: { buildIdentity: false, regionIdentity: false, identityRefresh: false } },
    {
        features: { buildIdentity: false, regionIdentity: false, identityRefresh: true },
        runtimeTransition: undefined,
    },
);
assert.match(
    refresh.api.switchMarkup('ct_dash_identity_refresh', true, 'data-policy-feature="identityRefresh"'),
    /pending-reboot/,
    'Identity Refresh must render yellow because it only takes effect on the next boot',
);

const warning = createPolicyHarness();
warning.api.notifyPolicyMutation('Identity enabled', { runtimeWarning: 'Reboot is required.' });
assert.deepStrictEqual(warning.messages, [{ message: 'Identity enabled. Warning: Reboot is required.', type: 'warning' }]);

const malformed = createPolicyHarness({ ct_pending_reboot: '{"unexpected":true}' });
assert.doesNotMatch(
    malformed.api.switchMarkup('ct_dash_identity_build', true, 'data-policy-feature="buildIdentity"'),
    /pending-reboot/,
    'malformed pending storage must fail closed without marking controls',
);

console.log('Policy reboot-pending indicator regression checks passed');
