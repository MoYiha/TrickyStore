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
assert.ok(!handlerSource.includes('deleteDirectoryRecursivelyNoFollow'), 'runtime integrity failure must not recursively delete the module');
assert.ok(!handlerSource.includes('OP_INTEGRITY_DELETE_MODULE'), 'runtime handler must not request destructive daemon deletion');
assert.ok(!handlerSource.includes('ModuleIntegrityWatcher'), 'violation handling must not initialize the disabled runtime watcher');

const watcherSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'ModuleIntegrityWatcher.kt'),
    'utf8'
);
assert.ok(
    watcherSource.includes('Only a cryptographic verification of the settled final'),
    'FileObserver activity must be treated as an invalidation hint, not proof of tampering'
);
assert.ok(watcherSource.includes('pendingWritePaths'), 'in-progress writes must be tracked until CLOSE_WRITE');
assert.ok(watcherSource.includes('mutationEpoch != verificationEpoch'), 'stale verifier results must be discarded');
assert.ok(watcherSource.includes('fullReverificationPending'), 'structural events must require a settled full recheck');
assert.ok(
    !watcherSource.includes('violationHandler(listOf("Critical payload deleted:'),
    'DELETE callbacks must not directly declare a payload violation'
);
assert.ok(
    !watcherSource.includes('violationHandler(listOf("Module directory was deleted or moved'),
    'DELETE_SELF/MOVE_SELF callbacks must not directly declare a module violation'
);

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
assert.ok(
    !mainSource.includes('ModuleIntegrityWatcher.start('),
    'production runtime must not register a persistent integrity FileObserver'
);
assert.ok(
    !mainSource.includes('ModuleIntegrityWatcher.stop()'),
    'production runtime must not initialize ModuleIntegrityWatcher merely for shutdown cleanup'
);
assert.ok(
    mainSource.includes('ModuleIntegrityVerifier.cachedManifest = null'),
    'startup integrity must release the parsed manifest after a successful boot verdict'
);

console.log('integrity-violation.test.js: all assertions passed');
