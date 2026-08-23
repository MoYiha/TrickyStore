'use strict';

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const bridgeFile = path.join(root, 'service/src/main/java/cleveres/tricky/cleverestech/WebUiBridge.kt');
const androidTestFile = path.join(
  root,
  'service/src/androidTest/java/cleveres/tricky/cleverestech/WebUiSecurityAndResourceInstrumentationTest.kt',
);

for (const file of [bridgeFile, androidTestFile]) {
  if (!fs.existsSync(file)) throw new Error(`Required Android 17 security/resource contract is missing: ${path.relative(root, file)}`);
}

const bridge = fs.readFileSync(bridgeFile, 'utf8');
const androidTest = fs.readFileSync(androidTestFile, 'utf8');

const productionBounds = [
  ['MAX_REQUEST_BYTES', /MAX_REQUEST_BYTES\s*=\s*1024\s*\*\s*1024/, /MAX_BRIDGE_REQUEST_BYTES\s*=\s*1024\s*\*\s*1024/],
  ['MAX_PARAMETER_KEYS', /MAX_PARAMETER_KEYS\s*=\s*128/, /repeat\(129\)/],
  ['MAX_PARAMETER_VALUES', /MAX_PARAMETER_VALUES\s*=\s*32/, /repeat\(33\)/],
];
for (const [name, productionPattern, testPattern] of productionBounds) {
  if (!productionPattern.test(bridge)) throw new Error(`Production ${name} changed; update the Android 17 abuse contract deliberately.`);
  if (!testPattern.test(androidTest)) throw new Error(`Android 17 abuse contract no longer crosses production ${name}.`);
}

const requiredBridgeProtections = [
  'decodeUtf8Strict',
  '".." !in path',
  "'\\\\' !in path",
  "'\\u0000' !in path",
  'LinkOption.NOFOLLOW_LINKS',
  'ID_PATTERN',
  'FIELD_PATTERN',
];
for (const token of requiredBridgeProtections) {
  if (!bridge.includes(token)) throw new Error(`WebUiBridge security boundary lost required protection: ${token}`);
}

const requiredAndroidEvidence = [
  'bridge rejects malformed traversal oversized and parameter abuse before dispatch',
  '/api/../config',
  '../../escape',
  'Files.createSymbolicLink',
  'config symlink cannot redirect writes outside the configuration root',
  'staged upload symlink is rejected without touching its target',
  '/proc/self/fd',
  'repeat(64)',
  'BROAD_LATENCY_BUDGET_MS = 15_000L',
  'MAX_FD_GROWTH = 4',
  'stagingEntryCount()',
  'response.envelopeBytes <= MAX_NORMAL_ENVELOPE_BYTES',
];
for (const token of requiredAndroidEvidence) {
  if (!androidTest.includes(token)) throw new Error(`Android 17 security/resource regression evidence is missing: ${token}`);
}

const symlinkExercises = (androidTest.match(/Files\.createSymbolicLink/g) || []).length;
if (symlinkExercises < 2) throw new Error('Android 17 must exercise both config-write and staged-upload symlink attacks.');

console.log('Android 17 security/resource coverage guards parser bounds, path/upload symlinks, broad latency, response size, FD growth, and staging leaks.');
