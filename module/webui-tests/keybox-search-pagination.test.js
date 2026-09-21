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

test('stored keyboxes and Check All expose filtered selection, search and five-item pagination', () => {
    const source = locateUx();

    assert.match(source, /const\s+PAGE_SIZE\s*=\s*5;/);
    assert.match(source, /ct_keybox_select_filtered/);
    assert.match(source, /toggleFilteredSelection/);
    assert.match(source, /ct_verify_controls/);
    assert.match(source, /ct_verify_filter/);
    assert.match(source, /ct_verify_search/);
    assert.match(source, /ct_verify_clear/);
    assert.match(source, /ct_verify_pager/);
    assert.match(source, /input\.addEventListener\('input',\s*applySearch\)/);
    assert.match(source, /input\.addEventListener\('search',\s*applySearch\)/);
    assert.match(source, /if\s*\(pages\s*<=\s*1\)\s*\{\s*pager\.style\.display\s*=\s*'none';/);
    assert.match(source, /filteredVerification/);
    assert.match(source, /\[item\.filename,\s*item\.status,\s*item\.certificate_serial,\s*item\.details\]/);
    assert.match(source, /nameText\.textContent\s*=\s*String\(item\.filename\s*\|\|\s*''\);/);
    assert.match(source, /items\.slice\(\(verificationPage\s*-\s*1\)\s*\*\s*PAGE_SIZE,\s*verificationPage\s*\*\s*PAGE_SIZE\)/);
    assert.match(source, /\/api\/verify_keyboxes/);
    assert.match(source, /global\.verifyKeyboxes\s*=\s*verify/);
});
