const assert = require('assert');
const fs = require('fs');

const root = 'module/template/webroot';
const runtimeScripts = fs.readdirSync(root).filter(name => name.endsWith('.js')).sort();
assert.deepStrictEqual(runtimeScripts, ['bridge.js', 'policy.js', 'ux.js'], 'WebUI runtime JS layout must stay fixed');

const ux = fs.readFileSync(`${root}/ux.js`, 'utf8');
const index = fs.readFileSync(`${root}/index.html`, 'utf8');
assert.ok(ux.includes('const MAX_SUPPORTED_FILES = 64;'), 'module ZIP import must respect the 64 active XML source runtime limit');
assert.ok(ux.includes('const MAX_FILE_BYTES = 10 * 1024 * 1024;'), 'per-keybox ZIP limit must remain 10 MiB');
assert.ok(ux.includes('I understand that every supported XML/CBOX file in this ZIP will be imported individually.'), 'ZIP confirmation copy is missing');
assert.ok(index.includes('accept=".xml,.cbox,.zip"'), 'keybox picker must accept ZIP');
assert.ok(!fs.existsSync(`${root}/ux-core.js`) && !fs.existsSync(`${root}/zip-import.js`), 'feature-specific runtime JS must not be reintroduced');

console.log('WebUI ZIP runtime layout checks passed');
