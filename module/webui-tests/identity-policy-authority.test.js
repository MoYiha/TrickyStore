'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');

const script = path.resolve('module/template/post-fs-data.sh');
const scriptText = fs.readFileSync(script, 'utf8');
const gateStart = scriptText.indexOf('boot_policy_feature_enabled() {');
const gateEnd = scriptText.indexOf('\npromote_staged_identity() {', gateStart);
assert.ok(gateStart >= 0 && gateEnd > gateStart, 'boot projection gate functions are missing');
const gateSource = scriptText.slice(gateStart, gateEnd);

function runCase({ projection = null, v2 = false, symlink = false, expected }) {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'ct-boot-projection-'));
    const config = path.join(root, 'config');
    fs.mkdirSync(config);
    fs.writeFileSync(path.join(config, 'spoof_build_identity'), '');
    if (v2) fs.writeFileSync(path.join(config, 'policy_state_v2.json'), '{}');
    if (projection !== null) {
        const target = path.join(config, 'projection-target');
        if (symlink) {
            fs.writeFileSync(target, projection);
            fs.symlinkSync(target, path.join(config, 'boot_policy_state'));
        } else {
            fs.writeFileSync(path.join(config, 'boot_policy_state'), projection);
        }
    }

    const result = spawnSync('/bin/sh', ['-c', `${gateSource}\noptional_marker_enabled buildIdentity spoof_build_identity`], {
        encoding: 'utf8',
        env: { ...process.env, CONFIG_DIR: config, CLEVERES_TRICKY_CONFIG_DIR: config }
    });
    assert.equal(result.status, expected ? 0 : 1, result.stderr || result.stdout || `status=${result.status}`);
    fs.rmSync(root, { recursive: true, force: true });
}

const enabled = 'version=1\nbuild=1\nregion=0\nrefresh=0\n';
const disabled = 'version=1\nbuild=0\nregion=0\nrefresh=0\n';
runCase({ projection: enabled, v2: true, expected: true });
runCase({ projection: disabled, v2: true, expected: false });
runCase({ projection: null, v2: false, expected: true });
runCase({ projection: null, v2: true, expected: false });
runCase({ projection: 'version=1\nbuild=1\nregion=0\nrefresh=0\nextra=1\n', v2: true, expected: false });
runCase({ projection: 'version=1\nbuild=2\nregion=0\nrefresh=0\n', v2: true, expected: false });
runCase({ projection: enabled, v2: true, symlink: true, expected: false });

assert.doesNotMatch(scriptText, /awk -v target=.*buildIdentity|depth == 1 && token == "features"/, 'shell must not parse v2 JSON');
assert.match(scriptText, /state_size.*-le 128/, 'projection read must remain bounded');
console.log('boot identity projection is bounded, v2-authoritative and legacy-compatible');
