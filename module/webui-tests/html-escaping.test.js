const assert = require('assert');
const fs = require('fs');
const path = require('path');
const vm = require('vm');

const webroot = 'module/template/webroot';
const runtimeFiles = fs.readdirSync(webroot)
    .filter(name => name.endsWith('.js') || name.endsWith('.html'))
    .sort();

const entityGuards = [
    ['&amp', /&amp(?!;)/g],
    ['&lt', /&lt(?!;)/g],
    ['&gt', /&gt(?!;)/g],
    ['&quot', /&quot(?!;)/g],
    ['&apos', /&apos(?!;)/g],
    ['&#39', /&#39(?!;)/g],
    ['&#x27', /&#x27(?!;)/gi]
];

const malformed = [];
for (const name of runtimeFiles) {
    const source = fs.readFileSync(path.join(webroot, name), 'utf8');
    for (const [entity, pattern] of entityGuards) {
        pattern.lastIndex = 0;
        for (const match of source.matchAll(pattern)) {
            const line = source.slice(0, match.index).split('\n').length;
            malformed.push(`${name}:${line}: malformed ${entity} entity`);
        }
    }
}

assert.deepStrictEqual(
    malformed,
    [],
    `HTML escaping entities must be semicolon-terminated in every WebUI runtime source:\n${malformed.join('\n')}`
);

const jsFiles = runtimeFiles.filter(name => name.endsWith('.js'));
const escapeHelpers = [];
for (const name of jsFiles) {
    const source = fs.readFileSync(path.join(webroot, name), 'utf8');
    if (/\bfunction\s+escapeHtml\s*\(/.test(source)) escapeHelpers.push([name, source]);
}

assert.ok(escapeHelpers.length > 0, 'WebUI must retain at least one explicit HTML escaping primitive');
for (const [name, source] of escapeHelpers) {
    assert.match(source, /['"]&amp;['"]/, `${name} escapeHtml must encode ampersands with a complete entity`);
    assert.match(source, /['"]&lt;['"]/, `${name} escapeHtml must encode less-than with a complete entity`);
    assert.match(source, /['"]&gt;['"]/, `${name} escapeHtml must encode greater-than with a complete entity`);
    assert.match(source, /['"]&quot;['"]/, `${name} escapeHtml must encode double quotes with a complete entity`);
    assert.match(source, /['"]&#39;['"]|['"]&#x27;['"]/i, `${name} escapeHtml must encode single quotes with a complete entity`);
}

// Exercise the policy escaping primitive as behavior, not only as source text. Expose
// it only inside this test VM so production does not grow a test-only global API.
const policyPath = path.join(webroot, 'policy.js');
const policySource = fs.readFileSync(policyPath, 'utf8');
assert.ok(policySource.includes('onReady(initialize);'), 'policy test hook anchor must remain explicit');
const testablePolicy = policySource.replace(
    'onReady(initialize);',
    'global.__ctTestEscapeHtml = escapeHtml;'
);
const sandbox = { CleveresBridge: {}, console };
sandbox.window = sandbox;
vm.runInNewContext(testablePolicy, sandbox, { filename: 'policy.js' });
const escapeHtml = sandbox.__ctTestEscapeHtml;
assert.strictEqual(typeof escapeHtml, 'function', 'policy escapeHtml must be testable');
assert.strictEqual(
    escapeHtml(`&<>"'`),
    '&amp;&lt;&gt;&quot;&#39;',
    'escapeHtml must encode every HTML-reserved character with complete entities'
);
const attackerShaped = `" autofocus onfocus=alert(1) data-x="<tag>&'`;
const escapedAttribute = `value="${escapeHtml(attackerShaped)}"`;
assert.strictEqual(
    escapedAttribute,
    'value="&quot; autofocus onfocus=alert(1) data-x=&quot;&lt;tag&gt;&amp;&#39;"',
    'quote escaping must keep attacker-shaped text inside the generated attribute value'
);

console.log('WebUI HTML escaping guard checks passed');
