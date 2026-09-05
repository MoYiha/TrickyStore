'use strict';

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const VIOLATION_MESSAGE = 'Module change detected! Module has been disabled and the system is being restarted.';

// Verify the violation message is defined in IntegrityViolationHandler.kt
const handlerSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'IntegrityViolationHandler.kt'),
    'utf8'
);

assert.ok(
    handlerSource.includes(VIOLATION_MESSAGE),
    'IntegrityViolationHandler.kt must contain the exact violation message'
);

// Runtime integrity failures must fail closed without recursively deleting the installed module.
assert.ok(
    handlerSource.includes('internal var disableModule'),
    'IntegrityViolationHandler must expose an injectable disableModule quarantine action'
);
assert.ok(
    handlerSource.includes('createDisableMarker'),
    'IntegrityViolationHandler must quarantine with a module disable marker'
);
assert.ok(
    !handlerSource.includes('deleteDirectoryRecursivelyNoFollow'),
    'IntegrityViolationHandler must not recursively delete the module after a runtime integrity failure'
);
assert.ok(
    !handlerSource.includes('OP_INTEGRITY_DELETE_MODULE'),
    'IntegrityViolationHandler must not request destructive daemon module deletion'
);

// FileObserver activity is only an invalidation signal. A settled cryptographic verifier result must
// decide whether a transient delete/move/write sequence is a real integrity violation.
const watcherSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'ModuleIntegrityWatcher.kt'),
    'utf8'
);
assert.ok(
    watcherSource.includes('Only a cryptographic verification of the settled final'),
    'ModuleIntegrityWatcher must document settled-state verification as the authority'
);
assert.ok(
    watcherSource.includes('scheduleFullCheckLocked()'),
    'Structural FileObserver events must schedule settled full verification'
);
assert.ok(
    watcherSource.includes('pendingWritePaths'),
    'ModuleIntegrityWatcher must track writes that have not reached CLOSE_WRITE'
);
assert.ok(
    watcherSource.includes('mutationEpoch != verificationEpoch'),
    'Stale verifier failures must be discarded when a newer filesystem event arrives'
);
assert.ok(
    watcherSource.includes('val requireFullVerification = fullReverificationPending'),
    'Stable events must preserve an already-required structural full verification'
);
assert.ok(
    !watcherSource.includes('violationHandler(listOf("Critical payload deleted:'),
    'A DELETE callback must not directly declare a critical payload violation'
);
assert.ok(
    !watcherSource.includes('violationHandler(listOf("Module directory was deleted or moved'),
    'DELETE_SELF/MOVE_SELF callbacks must not directly declare a module violation'
);

// Verify the violation message is used in WebServer.kt
const webServerSource = fs.readFileSync(
    path.resolve(__dirname, '..', '..', 'service', 'src', 'main', 'java',
        'cleveres', 'tricky', 'cleverestech', 'WebServer.kt'),
    'utf8'
);

assert.ok(
    webServerSource.includes('IntegrityViolationHandler.VIOLATION_MESSAGE'),
    'WebServer.kt must reference IntegrityViolationHandler.VIOLATION_MESSAGE for the violation page'
);

assert.ok(
    webServerSource.includes('IntegrityViolationHandler.isViolated'),
    'WebServer.kt must check IntegrityViolationHandler.isViolated'
);

// Verify the violation check happens BEFORE the isTampered check
const violatedIndex = webServerSource.indexOf('IntegrityViolationHandler.isViolated');
const tamperedIndex = webServerSource.indexOf('isTampered && (trustedBridge');
assert.ok(
    violatedIndex > 0 && tamperedIndex > 0 && violatedIndex < tamperedIndex,
    'Integrity violation check must occur before the isTampered check in WebServer.kt'
);

// Verify IntegrityViolationHandler has idempotent AtomicBoolean guard
assert.ok(
    handlerSource.includes('AtomicBoolean'),
    'IntegrityViolationHandler must use AtomicBoolean for idempotent violation handling'
);
assert.ok(
    handlerSource.includes('compareAndSet(false, true)'),
    'IntegrityViolationHandler must use compareAndSet for idempotent guard'
);
assert.ok(
    handlerSource.includes('internal var rebootSystem'),
    'IntegrityViolationHandler must have injectable rebootSystem for testing'
);
assert.ok(
    handlerSource.includes('resetForTesting'),
    'IntegrityViolationHandler must have resetForTesting'
);

// Verify Main.kt integrates integrity verification before native loading
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

console.log('integrity-violation.test.js: all assertions passed');
