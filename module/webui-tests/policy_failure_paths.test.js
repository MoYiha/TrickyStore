const assert = require('assert');
const fs = require('fs');

const policySource = fs.readFileSync('module/template/webroot/policy.js', 'utf8');

assert.match(
    policySource,
    /compatibilitySync\s*!==\s*'pending'/,
    'pending compatibility state must be recognized by the UI'
);
assert.match(
    policySource,
    /notifyPolicyMutation\(successMessage,\s*policyState\)/,
    'successful canonical saves must update UI state before showing compatibility warnings'
);
assert.match(
    policySource,
    /async function refreshSavedBuildIdentityBestEffort\(\)/,
    'saved identity refresh must have a best-effort boundary'
);
assert.match(
    policySource,
    /const refreshError\s*=\s*await refreshSavedBuildIdentityBestEffort\(\)/,
    'Apply Identity must not reject only because the saved identity view failed to refresh'
);
assert.doesNotMatch(
    policySource,
    /const result\s*=\s*await originalApply\.apply\(this,arguments\);\s*await loadSavedBuildIdentity\(\);/,
    'Apply Identity must not chain presentation refresh into persistence success'
);
assert.match(
    policySource,
    /Warning: the Identity Manager view could not be fully refreshed/,
    'Auto Identity must distinguish presentation refresh warnings from backend failure'
);

console.log('Policy failure-path source guards passed');
