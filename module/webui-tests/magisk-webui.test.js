'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');

function read(path) {
  return fs.readFileSync(path, 'utf8');
}

const action = read('module/template/action.sh');
const emergency = read('module/template/emergency-report.sh');
const customize = read('module/template/customize.sh');
const bridge = read('module/template/webroot/bridge.js');
const buildGradle = read('module/build.gradle.kts');
const buildYml = read('.github/workflows/build.yml');

// Action.sh is the WebUI launcher entrypoint, not a diagnostics tool.
assert.match(action, /^#!\/system\/bin\/sh/, 'action.sh must keep the system shell shebang');
assert.match(action, /MODULE_ID="\$\{MODULE_ID:-cleverestricky\}"/, 'action.sh must default the module id to cleverestricky');
assert.match(action, /MODDIR="\$\{MODDIR:-\/data\/adb\/modules\/\$MODULE_ID\}"/, 'action.sh must resolve the stock module directory by default');
assert.match(action, /WEBUI_HOST_PKG="\$\{WEBUI_HOST_PKG:-io\.github\.a13e300\.ksuwebui\}"/, 'action.sh must default to the WebUI host package');
assert.match(action, /WEBUI_HOST_REPO="\$\{WEBUI_HOST_REPO:-adivenxnataly\/KsuWebUI\}"/, 'action.sh must default to the reviewed host repository');
assert.match(action, /WEBUI_HOST_ACTIVITY="\$\{WEBUI_HOST_ACTIVITY:-io\.github\.a13e300\.ksuwebui\/\.WebUIActivity\}"/, 'action.sh must default to the WebUIActivity component');
assert.match(action, /pm path "\$WEBUI_HOST_PKG"/, 'action.sh must detect the installed host via pm path');
assert.match(action, /am start -n "\$WEBUI_HOST_ACTIVITY" -e id "\$MODULE_ID"/, 'action.sh must launch WebUIActivity with -e id <module-id>');
assert.match(action, /api\.github\.com\/repos\/\$WEBUI_HOST_REPO\/releases\/latest/, 'action.sh must resolve the host release via the GitHub API');
assert.match(action, /browser_download_url.*\\.apk/, 'action.sh must select an APK asset from the release metadata');
assert.match(action, /pm install -r "\$APK_PATH"/, 'action.sh must install the downloaded host APK');
assert.match(action, /host_installed/, 'action.sh must verify the host install via pm path before launching');
assert.match(action, /--connect-timeout/, 'action.sh downloads must bound connection setup');
assert.match(action, /--max-time/, 'action.sh downloads must bound total transfer time');
assert.match(action, /trap cleanup EXIT/, 'action.sh must clean the APK and temp dir on every exit path');
assert.match(action, /emergency-report\.sh/, 'action.sh must point users at emergency-report.sh for diagnostics');
assert.match(action, /objects\.githubusercontent\.com/, 'action.sh must pin release-asset transport hosts');
assert.ok(action.includes('is_allowed_apk_url'), 'action.sh must gate the APK URL through the transport allowlist');
assert.ok(action.includes('manager_webui_available'), 'action.sh must detect manager WebUI before downloading');
assert.ok(action.includes('probe_downloader'), 'action.sh must probe downloaders in capability order');
assert.ok(action.includes('exec "$report_script"'), 'action.sh must run the emergency report where manager WebUI exists');
assert.ok(action.includes('MAGISK_BUSYBOX'), 'action.sh must address Magisk busybox explicitly instead of relying on PATH wget flags');
assert.ok(action.includes('read_host_pin'), 'action.sh must verify downloads against the reviewed pin');
assert.ok(action.includes('sha256sum'), 'action.sh must hash the download before install');
assert.ok(fs.existsSync('module/template/webui-host.sha256'), 'reviewed host pin file must exist');
const pin = read('module/template/webui-host.sha256').trim().split('\n').filter((line) => !line.startsWith('#'));
assert.strictEqual(pin.length, 1, 'pin file must carry exactly one reviewed release');
assert.match(pin[0], /^[A-Za-z0-9_.-]+ [0-9a-f]{64} https:\/\/\S+\.apk$/, 'pin entry must be version, sha256, and asset URL');
assert.match(pin[0], /github\.com\/adivenxnataly\/KsuWebUI\/releases\/download\//, 'pin must reference the reviewed host release asset');
assert.ok(!action.includes('--no-check-certificate'), 'action.sh must not disable TLS verification');
for (const remnant of ['REPORT_FILE_BLOCK_LIMIT', 'publish-report', 'logcat -b all', 'FROM_WEBUI', 'CleveresTricky-bugreport-']) {
  assert.ok(!action.includes(remnant), `action.sh must not retain bugreport logic (${remnant})`);
}

// Emergency diagnostics live in their own packaged script with the same guards.
assert.match(emergency, /^#!\/system\/bin\/sh/, 'emergency-report.sh must keep the system shell shebang');
for (const marker of [
  'generate_report_nonce()',
  'REPORT_COPY_FILE_LIMIT=128',
  'REPORT_LOG_FILE_BLOCK_LIMIT=8192',
  'copy-report-file "$report_nonce"',
  'publish-report "$report_nonce"',
  'logcat -b all -d -v threadtime',
  'FROM_WEBUI'
]) {
  assert.ok(emergency.includes(marker), `emergency-report.sh lost bugreport contract: ${marker}`);
}

// Installer extracts and secures both scripts and treats Magisk as supported.
assert.ok(customize.includes("extract \"$ZIPFILE\" 'action.sh' \"$MODPATH\""), 'customize.sh must extract action.sh');
assert.ok(customize.includes("extract \"$ZIPFILE\" 'emergency-report.sh' \"$MODPATH\""), 'customize.sh must extract emergency-report.sh');
assert.ok(customize.includes('emergency-report.sh'), 'customize.sh payload verification must cover emergency-report.sh');
assert.ok(!customize.includes('Magisk is NOT recommended'), 'customize.sh must not discourage Magisk');
assert.ok(!customize.includes('WebUI is unavailable on Magisk'), 'customize.sh must not claim WebUI is unavailable on Magisk');
assert.ok(customize.includes('Magisk is supported'), 'customize.sh must state Magisk support');
assert.ok(customize.includes('standalone WebUI host app'), 'customize.sh must mention the WebUI host on Magisk');

// The existing native bridge is preserved; only the offline error copy changed.
assert.ok(!bridge.includes('Open this page from the KernelSU or APatch WebUI button'), 'bridge.js must not single out KernelSU/APatch anymore');
assert.ok(bridge.includes('global.ksu'), 'bridge.js must keep using the ksu-compatible host API');
assert.ok(bridge.includes('/data/adb/modules/cleverestricky/webui_bridge'), 'bridge.js must keep the Magisk webui_bridge path first');

// Packaging keeps the canonical four-file webroot and ships both scripts.
for (const file of ['webroot/index.html', 'webroot/bridge.js', 'webroot/policy.js', 'webroot/ux.js']) {
  assert.ok(fs.existsSync(`module/template/${file}`), `canonical webroot file is missing: ${file}`);
}
assert.ok(buildGradle.includes('"emergency-report.sh",'), 'module packaging must stage emergency-report.sh');
assert.ok(buildGradle.includes('"emergency-report.sh" to "executable"'), 'integrity manifest must cover emergency-report.sh');
assert.ok(buildGradle.includes('"webui-host.sha256" to "regular"'), 'integrity manifest must cover the host pin');
assert.ok(customize.includes("'webui-host.sha256'"), 'customize.sh must extract the host pin');
assert.ok(buildYml.includes('action.sh emergency-report.sh webui-host.sha256 sepolicy.rule'), 'CI module structure check must require the new module files');
assert.ok(buildYml.includes('module/template/action.sh module/template/emergency-report.sh'), 'CI shell syntax check must cover emergency-report.sh');
assert.ok(buildYml.includes('post-fs-data.sh action.sh emergency-report.sh webui-host.sha256 sepolicy.rule'), 'CI archive check must require the new module files');

// Documentation contract: official three-environment support with host terminology.
const magiskDoc = read('docs/system/Magisk.md');
assert.ok(magiskDoc.includes('github.com/adivenxnataly/KsuWebUI'), 'Magisk.md must document the WebUI host app');
assert.ok(magiskDoc.includes('id `cleverestricky`') || magiskDoc.includes('`cleverestricky`'), 'Magisk.md must document the module id');
assert.ok(magiskDoc.includes('/data/adb/modules/cleverestricky/webroot'), 'Magisk.md must document the webroot resolution');
assert.ok(magiskDoc.includes('emergency-report.sh'), 'Magisk.md must point diagnostics at emergency-report.sh');
assert.ok(!magiskDoc.includes('WebUI is unavailable on Magisk'), 'Magisk.md must not block Magisk WebUI');
assert.ok(!magiskDoc.includes('Magisk is NOT recommended'), 'Magisk.md must not discourage Magisk');
assert.ok(!magiskDoc.includes('headless daemon mode without a WebUI'), 'Magisk.md must not describe headless-only operation');
assert.ok(!magiskDoc.includes('modules/cleverestricky/action.sh'), 'Magisk.md must not route diagnostics through action.sh');

const i18nMagiskDocs = ['tr', 'zh-CN', 'es', 'de', 'ru', 'id', 'hi', 'ar'].map((locale) => `docs/i18n/${locale}/system/Magisk.md`);
for (const path of i18nMagiskDocs) {
  const doc = read(path);
  assert.ok(doc.includes('github.com/adivenxnataly/KsuWebUI'), `${path} must document the WebUI host app`);
  assert.ok(doc.includes('emergency-report.sh'), `${path} must point diagnostics at emergency-report.sh`);
  assert.ok(!doc.includes('modules/cleverestricky/action.sh'), `${path} must not route diagnostics through action.sh`);
}

const readmes = ['README.md', 'README.tr.md', 'README.zh-CN.md', 'README.es.md', 'README.de.md', 'README.ru.md', 'README.id.md', 'README.hi.md', 'README.ar.md'];
for (const path of readmes) {
  const doc = read(path);
  const lowered = doc.toLowerCase();
  assert.ok(doc.includes('**Magisk**'), `${path} must list Magisk as a supported environment`);
  assert.ok(doc.includes('APatch%20%7C%20Magisk-6f42c1'), `${path} badge must list Magisk`);
  assert.ok(!lowered.includes('headless'), `${path} must not describe headless-only operation`);
  assert.ok(!lowered.includes('not recommended') && !lowered.includes('not-recommended'), `${path} must not discourage Magisk`);
  assert.ok(!doc.includes('system/Magisk.md'), `${path} must not link the Magisk guide (docs only)`);
}
assert.ok(!read('README.md').includes('or the module **Action** to create'), 'README.md must not present Action as the diagnostics path');

const manualGuides = ['docs/system/ManualConfiguration.md', 'docs/i18n/tr/system/ManualConfiguration.md', 'docs/i18n/zh-CN/system/ManualConfiguration.md', 'docs/i18n/es/system/ManualConfiguration.md', 'docs/i18n/de/system/ManualConfiguration.md', 'docs/i18n/ru/system/ManualConfiguration.md', 'docs/i18n/id/system/ManualConfiguration.md', 'docs/i18n/hi/system/ManualConfiguration.md', 'docs/i18n/ar/system/ManualConfiguration.md'];
for (const path of manualGuides) {
  assert.ok(fs.existsSync(path), `standalone manual guide is missing: ${path}`);
  const doc = read(path);
  assert.ok(doc.includes('disabled_keyboxes'), `${path} must carry the manual configuration body`);
  assert.ok(doc.includes('emergency-report.sh'), `${path} must route diagnostics at emergency-report.sh`);
  assert.ok(!doc.includes('modules/cleverestricky/action.sh'), `${path} must not route diagnostics through action.sh`);
}
assert.ok(read('docs/system/Magisk.md').includes('ManualConfiguration.md'), 'Magisk.md must link the standalone manual guide');
for (const path of ['docs/system/Magisk.md', ...i18nMagiskDocs]) {
  const doc = read(path);
  assert.ok(doc.includes('(ManualConfiguration.md)'), `${path} must link the standalone manual guide`);
  assert.ok(!doc.includes('disabled_keyboxes'), `${path} must not retain the moved manual body`);
}
const docHubs = ['docs/README.md', 'docs/i18n/tr/README.md', 'docs/i18n/zh-CN/README.md', 'docs/i18n/es/README.md', 'docs/i18n/de/README.md', 'docs/i18n/ru/README.md', 'docs/i18n/id/README.md', 'docs/i18n/hi/README.md', 'docs/i18n/ar/README.md'];
for (const path of docHubs) {
  assert.ok(read(path).includes('system/ManualConfiguration.md'), `${path} must index the manual guide`);
}

const guide = read('docs/security/StrongIntegrityGuide.md');
assert.ok(guide.includes('KernelSU') && guide.includes('Magisk'), 'StrongIntegrityGuide overview must list Magisk');
assert.ok(guide.includes('Action button'), 'StrongIntegrityGuide must mention the Action-button WebUI path');

const installer = read('docs/system/Installer.md');
assert.ok(installer.includes("grep -E 'cleverestrickyd|cleverestricky_backend'"), 'Installer.md must use the actual daemon process names');

const logDocs = ['LOG.md', 'docs/LOG.md', 'docs/i18n/tr/LOG.md', 'docs/i18n/zh-CN/LOG.md', 'docs/i18n/es/LOG.md', 'docs/i18n/de/LOG.md', 'docs/i18n/ru/LOG.md', 'docs/i18n/id/LOG.md', 'docs/i18n/hi/LOG.md', 'docs/i18n/ar/LOG.md'];
for (const path of logDocs) {
  const doc = read(path);
  assert.ok(doc.includes('emergency-report.sh'), `${path} must route bug reports through emergency-report.sh`);
  assert.ok(!doc.includes('modules/cleverestricky/action.sh'), `${path} must not route bug reports through action.sh`);
}

console.log('Magisk WebUI host, packaging, and documentation contract checks passed');
