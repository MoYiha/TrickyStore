const assert = require('assert');
const fs = require('fs');

const indexHtml = fs.readFileSync('module/template/webroot/index.html', 'utf8');
const uxJs = fs.readFileSync('module/template/webroot/ux.js', 'utf8');
const policyJs = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

// 1. Toggle & switch active color and knob styling
assert.ok(
    uxJs.includes('input[type="checkbox"].toggle:checked, input[type="checkbox"].ct-switch:checked { background: var(--success, #30d158) !important;'),
    'ux.js must style checked toggle to system green',
);
assert.ok(
    uxJs.includes('input[type="checkbox"].toggle:checked::after, input[type="checkbox"].ct-switch:checked::after { transform: translateX(20px) !important; background: #ffffff !important; }'),
    'ux.js checked toggle knob must be white (#ffffff), never black (#0b0b0c)',
);
assert.ok(!uxJs.includes('#0b0b0c'), 'ux.js must not force a black knob on checked toggles');

assert.ok(
    policyJs.includes('input[type="checkbox"].ct-switch:checked{background:var(--success,#30d158)!important;border-color:var(--success,#30d158)!important}'),
    'policy.js must style checked switch to system green',
);
assert.ok(
    policyJs.includes('input[type="checkbox"].ct-switch:checked::after{transform:translateX(20px)!important;background:#ffffff!important}'),
    'policy.js checked switch knob must be white',
);

assert.match(
    indexHtml,
    /input\[type="checkbox"\]\.toggle:checked\s*,\s*input\[type="checkbox"\]\.ct-switch:checked\s*\{\s*background:\s*var\(--success\)\s*;\s*border-color:\s*var\(--success\)\s*;?\s*\}/,
    'index.html must style checked toggle to var(--success)',
);

// 2. Debug logging toggle element classes
assert.ok(
    uxJs.includes('id="ct_debug_logging_toggle" class="ct-switch toggle"'),
    'ct_debug_logging_toggle must have ct-switch toggle classes',
);

// 3. Elimination of neon glare shadows
assert.ok(
    !/rgba\(10,\s*132,\s*255,\s*0?\.35\)/.test(indexHtml),
    'index.html must not have neon blue box-shadow on primary button',
);
assert.ok(
    !/rgba\(10,\s*132,\s*255,\s*0?\.45\)/.test(indexHtml),
    'index.html must not have neon blue box-shadow on hover',
);
assert.ok(
    !policyJs.includes('rgba(251,191,36,.5)'),
    'policy.js must not have glowing neon box-shadow on pending-reboot toggles',
);

// 4. Color palette alignment
assert.match(indexHtml, /--color-red:\s*rgb\(255,\s*69,\s*58\)\s*;/, 'dark red must match system red');
assert.match(indexHtml, /--color-orange:\s*rgb\(255,\s*159,\s*10\)\s*;/, 'dark orange must match system orange');
assert.match(indexHtml, /--color-yellow:\s*rgb\(255,\s*214,\s*10\)\s*;/, 'dark yellow must match system yellow');
assert.match(indexHtml, /--color-green:\s*rgb\(48,\s*209,\s*88\)\s*;/, 'dark green must match system green');
assert.match(indexHtml, /--color-blue:\s*rgb\(10,\s*132,\s*255\)\s*;/, 'dark blue must match system blue');
assert.match(indexHtml, /--color-indigo:\s*rgb\(94,\s*92,\s*230\)\s*;/, 'dark indigo must match system indigo');
assert.match(indexHtml, /--color-brown:\s*rgb\(172,\s*142,\s*104\)\s*;/, 'dark brown must match system brown');

console.log('Toggle states, debug logging switch, and theme palette regression checks passed');
