'use strict';

const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..', '..');
const productionFiles = [
  path.join(root, 'service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt'),
  path.join(root, 'service/src/main/java/cleveres/tricky/cleverestech/PolicyApi.kt'),
];
const matrixFile = path.join(
  root,
  'service/src/androidTest/java/cleveres/tricky/cleverestech/WebUiFeatureMatrixInstrumentationTest.kt',
);

for (const file of [...productionFiles, matrixFile]) {
  if (!fs.existsSync(file)) throw new Error(`Required feature-contract source is missing: ${path.relative(root, file)}`);
}

const production = productionFiles.map(file => fs.readFileSync(file, 'utf8')).join('\n');
const matrix = fs.readFileSync(matrixFile, 'utf8');

function collectRouteNames(text) {
  const routes = new Set();
  const expression = /["'](\/api\/[A-Za-z0-9_\/-]+)["']/g;
  let match;
  while ((match = expression.exec(text)) !== null) routes.add(match[1]);
  return routes;
}

function collectProductionPairs(text) {
  const pairs = new Set();
  for (const line of text.split(/\r?\n/)) {
    const match = line.match(/uri\s*==\s*"(\/api\/[A-Za-z0-9_\/-]+)"\s*&&\s*method\s*==\s*(?:NanoHTTPD\.)?Method\.(GET|POST)/);
    if (match) pairs.add(`${match[2]} ${match[1]}`);
  }
  return pairs;
}

function collectMatrixPairs(text) {
  const pairs = new Set();
  const expression = /FeatureCase\(\s*"(GET|POST)"\s*,\s*"(\/api\/[A-Za-z0-9_\/-]+)"/g;
  let match;
  while ((match = expression.exec(text)) !== null) pairs.add(`${match[1]} ${match[2]}`);
  return pairs;
}

function difference(left, right) {
  return [...left].filter(value => !right.has(value)).sort();
}

function testBody(name) {
  const marker = `fun \`${name}\`() {`;
  const start = matrix.indexOf(marker);
  if (start < 0) throw new Error(`API 37 matrix is missing required test: ${name}`);
  const next = matrix.indexOf('\n    @Test', start + marker.length);
  return matrix.slice(start, next < 0 ? matrix.length : next);
}

const productionRoutes = collectRouteNames(production);
const matrixRoutes = collectRouteNames(matrix);
const missingRoutes = difference(productionRoutes, matrixRoutes);
const unknownRoutes = difference(matrixRoutes, productionRoutes);
if (missingRoutes.length || unknownRoutes.length) {
  throw new Error(
    [
      'Android 17 feature matrix route coverage drifted.',
      missingRoutes.length ? `Missing from emulator matrix: ${missingRoutes.join(', ')}` : '',
      unknownRoutes.length ? `Unknown matrix routes: ${unknownRoutes.join(', ')}` : '',
    ].filter(Boolean).join('\n'),
  );
}

const productionPairs = collectProductionPairs(production);
const matrixPairs = collectMatrixPairs(matrix);
const missingPairs = difference(productionPairs, matrixPairs);
const unknownPairs = difference(matrixPairs, productionPairs);
if (missingPairs.length || unknownPairs.length) {
  throw new Error(
    [
      'Android 17 feature matrix method/route coverage drifted.',
      missingPairs.length ? `Missing executable cases: ${missingPairs.join(', ')}` : '',
      unknownPairs.length ? `Matrix cases without production handlers: ${unknownPairs.join(', ')}` : '',
    ].filter(Boolean).join('\n'),
  );
}

if (!matrix.includes('bridge.processRequestBytes')) {
  throw new Error('API 37 matrix must execute the real WebUiBridge production request path.');
}
if (!matrix.includes('safe mutable feature surfaces round trip through production bridge')) {
  throw new Error('API 37 matrix must retain stateful WebUI feature round-trip coverage.');
}

const outage = testBody('native crypto outage fails closed without mutating configuration');
const outageEvidence = [
  '"/api/backup"',
  '"/api/restore"',
  'uploadId = uploadId',
  'uploadField = "file"',
  'assertEquals(before',
  'assertEquals(after',
  'staged.exists()',
];
const missingOutageEvidence = outageEvidence.filter(token => !outage.includes(token));
if (missingOutageEvidence.length) {
  throw new Error(
    `API 37 matrix must retain fail-closed backup/restore staging and state-preservation evidence: ${missingOutageEvidence.join(', ')}`,
  );
}

console.log(
  `Android 17 feature matrix covers ${productionPairs.size} method/route contracts across ${productionRoutes.size} routes, including native-crypto outage state preservation.`,
);
