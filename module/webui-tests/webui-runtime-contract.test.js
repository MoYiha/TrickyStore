const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const policySource = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

function makeResponse({ ok = true, jsonValue = {}, textValue = '', contentType = 'application/json' } = {}) {
    return {
        ok,
        status: ok ? 200 : 500,
        headers: {
            get(name) {
                return String(name).toLowerCase() === 'content-type' ? contentType : '';
            }
        },
        async json() {
            return jsonValue;
        },
        async text() {
            return textValue;
        }
    };
}

function makeElement(tagName = 'div') {
    return {
        tagName: String(tagName).toUpperCase(),
        id: '',
        className: '',
        textContent: '',
        style: {},
        dataset: {},
        hidden: false,
        disabled: false,
        setAttribute() {},
        removeAttribute() {},
        append() {},
        appendChild() {},
        replaceChildren() {},
        querySelector() { return null; },
        querySelectorAll() { return []; },
        addEventListener() {}
    };
}

function loadPolicyRuntime(fetchImpl = async () => makeResponse()) {
    const notifications = [];
    const quietConsole = { log() {}, warn() {}, error() {} };
    const document = {
        body: null,
        head: { appendChild() {} },
        documentElement: { classList: { add() {}, remove() {} } },
        addEventListener() {},
        getElementById() { return null; },
        createElement: makeElement,
        createTextNode(text) { return { textContent: String(text) }; }
    };
    const window = {
        CleveresBridge: { fetch: fetchImpl },
        notify(message, type) {
            notifications.push({ message: String(message), type: type || 'normal' });
        },
        setTimeout,
        clearTimeout,
        requestAnimationFrame(callback) { callback(); },
        console: quietConsole
    };

    const hookExport = `global.__ctPolicyTestHooks = Object.freeze({
        escapeHtml,
        policyCompatibilityWarning,
        notifyPolicyMutation,
        refreshSavedBuildIdentityBestEffort,
        installIdentityManagerState,
        installAutoIdentityOverride
    });`;
    const instrumented = policySource.replace('onReady(initialize);', hookExport);
    assert.notStrictEqual(instrumented, policySource, 'policy.js test hook injection point is missing');

    vm.runInNewContext(instrumented, {
        window,
        document,
        console: quietConsole,
        URLSearchParams,
        Event: class Event {
            constructor(type, options) {
                this.type = type;
                this.options = options;
            }
        },
        setTimeout,
        clearTimeout
    }, { filename: 'policy.js' });

    assert.ok(window.__ctPolicyTestHooks, 'policy.js runtime hooks were not exposed to the test harness');
    return { window, document, notifications, hooks: window.__ctPolicyTestHooks };
}

async function testEscapingPrimitive() {
    const { hooks } = loadPolicyRuntime();
    assert.strictEqual(
        hooks.escapeHtml(`&<>"'`),
        '&amp;&lt;&gt;&quot;&#39;',
        'HTML escaping must preserve complete entities for all markup-significant characters'
    );
    assert.strictEqual(
        hooks.escapeHtml('value" autofocus onfocus="boom'),
        'value&quot; autofocus onfocus=&quot;boom',
        'double quotes must remain safely encoded when escaped text is placed in an attribute value'
    );
}

async function testCanonicalSaveWarningIsNotReportedAsFailure() {
    const { hooks, notifications } = loadPolicyRuntime();
    hooks.notifyPolicyMutation('Policy saved', {
        compatibilitySync: 'pending',
        compatibilityWarning: 'Retry compatibility sync before reboot.'
    });

    assert.deepStrictEqual(notifications, [{
        message: 'Policy saved. Warning: Retry compatibility sync before reboot.',
        type: 'normal'
    }], 'a committed canonical policy with a pending compatibility sync must remain a successful action with a warning');
}

async function testApplyIdentitySurvivesPresentationRefreshFailure() {
    const runtime = loadPolicyRuntime(async () => makeResponse({
        ok: false,
        textValue: 'saved identity view unavailable',
        contentType: 'text/plain'
    }));
    runtime.window.applySpoofing = async () => 'persisted';

    runtime.hooks.installIdentityManagerState();
    const result = await runtime.window.applySpoofing();

    assert.strictEqual(result, 'persisted', 'Apply Identity success must not be rejected by a later read-only refresh failure');
    const warning = runtime.notifications.find(item => item.message.startsWith('Identity was applied. Warning:'));
    assert.ok(warning, 'Apply Identity must surface a separate presentation warning after persistence succeeds');
    assert.strictEqual(warning.type, 'normal', 'a presentation refresh warning must not be rendered as an action failure');
}

function makeAutoIdentityButton() {
    const listeners = {};
    const button = makeElement('button');
    button.textContent = 'AUTO IDENTITY';
    button.onclick = () => {};
    button.removeAttribute = name => {
        if (name === 'onclick') button.onclick = null;
    };
    button.addEventListener = (type, listener) => {
        listeners[type] = listener;
    };
    return { button, listeners };
}

async function testAutoIdentitySurvivesPresentationRefreshFailure() {
    const runtime = loadPolicyRuntime(async path => {
        if (path === '/api/auto_identity') {
            return makeResponse({ jsonValue: { model: 'Pixel Test', build_id: 'AP3A.TEST' } });
        }
        if (String(path).startsWith('/api/file?filename=spoof_build_vars')) {
            return makeResponse({ ok: false, contentType: 'text/plain', textValue: 'refresh unavailable' });
        }
        return makeResponse();
    });
    const { button, listeners } = makeAutoIdentityButton();
    const spoof = { querySelectorAll(selector) { return selector === 'button' ? [button] : []; } };
    runtime.document.getElementById = id => id === 'spoof' ? spoof : null;
    runtime.window.loadIdentity = async () => { throw new Error('legacy view refresh failed'); };

    runtime.hooks.installAutoIdentityOverride();
    assert.strictEqual(typeof listeners.click, 'function', 'Auto Identity click handler must be installed');
    await listeners.click({ preventDefault() {} });

    const success = runtime.notifications.find(item => item.message.startsWith('Identity ready:'));
    assert.ok(success, 'Auto Identity backend success must remain visible even if presentation refresh fails');
    assert.match(success.message, /Warning: the Identity Manager view could not be fully refreshed/);
    assert.strictEqual(success.type, 'normal', 'Auto Identity presentation refresh failure must not turn a successful backend action into an error state');
    assert.ok(!runtime.notifications.some(item => /Auto Identity failed/.test(item.message)), 'presentation refresh failures must not enter the Auto Identity backend failure path');
    assert.strictEqual(button.disabled, false, 'Auto Identity button must be re-enabled after completion');
}

async function testAutoIdentityBackendFailureStillFails() {
    const runtime = loadPolicyRuntime(async path => {
        if (path === '/api/auto_identity') {
            return makeResponse({ ok: false, contentType: 'text/plain', textValue: 'identity backend rejected request' });
        }
        return makeResponse();
    });
    const { button, listeners } = makeAutoIdentityButton();
    const spoof = { querySelectorAll(selector) { return selector === 'button' ? [button] : []; } };
    runtime.document.getElementById = id => id === 'spoof' ? spoof : null;

    runtime.hooks.installAutoIdentityOverride();
    await listeners.click({ preventDefault() {} });

    const failure = runtime.notifications.find(item => item.message === 'identity backend rejected request');
    assert.ok(failure, 'real Auto Identity backend failures must still be reported');
    assert.strictEqual(failure.type, 'error', 'real backend failures must retain error presentation');
}

(async () => {
    await testEscapingPrimitive();
    await testCanonicalSaveWarningIsNotReportedAsFailure();
    await testApplyIdentitySurvivesPresentationRefreshFailure();
    await testAutoIdentitySurvivesPresentationRefreshFailure();
    await testAutoIdentityBackendFailureStillFails();
    console.log('Executable WebUI runtime contract checks passed');
})().catch(error => {
    console.error(error);
    process.exitCode = 1;
});
