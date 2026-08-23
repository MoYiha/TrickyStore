'use strict';

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const webServer = fs.readFileSync(path.join(root, 'service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt'), 'utf8');
const policyState = fs.readFileSync(path.join(root, 'service/src/main/java/cleveres/tricky/cleverestech/PolicyState.kt'), 'utf8');
const matrix = fs.readFileSync(
  path.join(root, 'service/src/androidTest/java/cleveres/tricky/cleverestech/WebUiFeatureFamiliesInstrumentationTest.kt'),
  'utf8',
);

function section(text, start, end) {
  const from = text.indexOf(start);
  if (from < 0) throw new Error(`Missing coverage source marker: ${start}`);
  const to = text.indexOf(end, from + start.length);
  if (to < 0) throw new Error(`Missing coverage source terminator: ${end}`);
  return text.slice(from, to);
}

function quoted(text) {
  return new Set([...text.matchAll(/"([^"]+)"/g)].map(match => match[1]));
}

function assertSame(label, production, covered) {
  const missing = [...production].filter(value => !covered.has(value)).sort();
  const extra = [...covered].filter(value => !production.has(value)).sort();
  if (missing.length || extra.length) {
    throw new Error([
      `${label} API 37 coverage drifted.`,
      missing.length ? `Missing emulator cases: ${missing.join(', ')}` : '',
      extra.length ? `Unknown emulator cases: ${extra.join(', ')}` : '',
    ].filter(Boolean).join('\n'));
  }
}

const productionToggles = quoted(section(webServer, 'private val WEB_UI_SETTINGS =', 'private val EDITABLE_CONFIG_FILES'));
const coveredToggles = quoted(section(matrix, 'private val TOGGLE_SETTINGS =', 'private val PROFILE_MARKERS ='));
assertSame('WebUI toggle family', productionToggles, coveredToggles);

const profileValidator = section(webServer, 'private fun isValidProfile', 'private fun toggleFile');
const productionProfiles = quoted(profileValidator);
const coveredProfiles = new Set(
  [...section(matrix, 'private val PROFILE_MARKERS =', 'private val RANDOM_IDENTITY_SELECTORS =').matchAll(/"([^"]+)"\s+to\s+setOf/g)]
    .map(match => match[1]),
);
assertSame('Built-in profile family', productionProfiles, coveredProfiles);

const randomFunction = section(webServer, 'private fun randomIdentityJson', 'private fun parseIdentityUpdates');
const productionRandomSelectors = new Set();
for (const match of randomFunction.matchAll(/((?:\s*"[^"]+"\s*,?)+)\s*->/g)) {
  for (const value of match[1].matchAll(/"([^"]+)"/g)) productionRandomSelectors.add(value[1]);
}
const coveredRandomSelectors = quoted(section(matrix, 'private val RANDOM_IDENTITY_SELECTORS =', 'private val POLICY_FEATURES ='));
assertSame('Random Identity selector family', productionRandomSelectors, coveredRandomSelectors);

const featureEnum = section(policyState, 'enum class Feature', 'enum class PatchMode');
const productionPolicyFeatures = new Set([...featureEnum.matchAll(/\("([^"]+)"\)/g)].map(match => match[1]));
const coveredPolicyFeatures = quoted(section(matrix, 'private val POLICY_FEATURES =', 'private val COMPATIBILITY_MARKERS ='));
assertSame('V2 policy feature family', productionPolicyFeatures, coveredPolicyFeatures);

for (const requiredBehavior of [
  'every WebUI toggle persists and reads back on Android 17',
  'every built in profile produces its documented marker state',
  'every random identity selector executes on Android 17',
  'every V2 policy feature persists reads back and synchronizes compatibility markers',
]) {
  if (!matrix.includes(requiredBehavior)) throw new Error(`Missing executable API 37 feature-family contract: ${requiredBehavior}`);
}

console.log(
  `Android 17 feature-family coverage: ${productionToggles.size} toggles, ` +
  `${productionProfiles.size} profiles, ${productionRandomSelectors.size} random selectors, ` +
  `${productionPolicyFeatures.size} V2 policy features.`,
);
