'use strict';

const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const assert = require('node:assert/strict');

function locateUx() {
    const candidates = [
        path.resolve('template/webroot/ux.js'),
        path.resolve('module/template/webroot/ux.js')
    ];
    const file = candidates.find(candidate => fs.existsSync(candidate));
    if (!file) throw new Error('Could not locate module/template/webroot/ux.js');
    return fs.readFileSync(file, 'utf8');
}

test('keybox status observer update is idempotent and cannot self-trigger forever', () => {
    const source = locateUx();
    const start = source.indexOf('function statusLabel()');
    const end = source.indexOf('function filtered()', start);
    assert.ok(start >= 0 && end > start, 'statusLabel block must exist');
    const block = source.slice(start, end);
    assert.match(block, /const value = t\('keyboxesLoaded', \{ count: match\[1\] \}\);/);
    assert.match(block, /if \(node\.textContent !== value\) node\.textContent = value;/);
    assert.doesNotMatch(block, /if \(match\) node\.textContent =/);
});
