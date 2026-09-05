'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const VIOLATION_MESSAGE = 'Module change detected! Module has been disabled and the system is being restarted.';

const handlerSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'IntegrityViolationHandler.kt'),
    'utf8'
);
assert.ok(handlerSource.includes(VIOLATION_MESSAGE), 'IntegrityViolationHandler.kt must contain the violation message');
assert.ok(handlerSource.includes('internal var disableModule'), 'violation handling must use disable quarantine');
assert.ok(handlerSource.includes('createDisableMarker'), 'violation handling must create a disable marker');
assert.ok(!handlerSource.includes('deleteDirectoryRecursivelyNoFollow'), 'integrity failure must not recursively delete the module');
assert.ok(!handlerSource.includes('OP_INTEGRITY_DELETE_MODULE'), 'integrity handler must not request destructive daemon deletion');
assert.ok(!handlerSource.includes('ModuleIntegrityWatcher'), 'violation handling must not initialize the disabled runtime watcher');

const webServerSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'WebServer.kt'),
    'utf8'
);
assert.ok(
    webServerSource.includes('IntegrityViolationHandler.VIOLATION_MESSAGE'),
    'WebServer.kt must reference IntegrityViolationHandler.VIOLATION_MESSAGE'
);
assert.ok(webServerSource.includes('IntegrityViolationHandler.isViolated'), 'WebServer.kt must check integrity violation state');
const violatedIndex = webServerSource.indexOf('IntegrityViolationHandler.isViolated');
const tamperedIndex = webServerSource.indexOf('isTampered && (trustedBridge');
assert.ok(
    violatedIndex > 0 && tamperedIndex > 0 && violatedIndex < tamperedIndex,
    'Integrity violation check must occur before the isTampered check in WebServer.kt'
);

assert.ok(handlerSource.includes('AtomicBoolean'), 'IntegrityViolationHandler must use AtomicBoolean');
assert.ok(handlerSource.includes('compareAndSet(false, true)'), 'IntegrityViolationHandler must be idempotent');
assert.ok(handlerSource.includes('internal var rebootSystem'), 'IntegrityViolationHandler must keep injectable rebootSystem');
assert.ok(handlerSource.includes('resetForTesting'), 'IntegrityViolationHandler must keep resetForTesting');

const mainSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'Main.kt'),
    'utf8'
);
const integrityVerifyIndex = mainSource.indexOf('ModuleIntegrityVerifier.verifyFull');
const backendAwaitIndex = mainSource.indexOf('NativeBackend.awaitReady');
assert.ok(
    integrityVerifyIndex > 0 && backendAwaitIndex > 0 && integrityVerifyIndex < backendAwaitIndex,
    'Integrity verification must happen BEFORE NativeBackend.awaitReady in Main.kt'
);
assert.strictEqual(
    (mainSource.match(/ModuleIntegrityVerifier\.verifyFull\(\)/g) || []).length,
    1,
    'production runtime must perform exactly one full integrity verification at startup'
);
assert.ok(
    !mainSource.includes('ModuleIntegrityVerifier.loadManifest()'),
    'production runtime must not reload and retain the integrity manifest after startup verification'
);
assert.ok(
    !mainSource.includes('ModuleIntegrityWatcher'),
    'production Main.kt must not initialize, start, stop, or otherwise reference the runtime integrity watcher'
);
assert.ok(
    mainSource.includes('ModuleIntegrityVerifier.cachedManifest = null'),
    'startup integrity must release the parsed manifest after a successful boot verdict'
);

console.log('integrity-violation.test.js: all assertions passed');
