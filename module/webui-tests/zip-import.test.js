const assert = require('assert');
const fs = require('fs');
const vm = require('vm');

const source = fs.readFileSync('module/template/webroot/zip-import.js', 'utf8');
assert.ok(source.includes('const MAX_SUPPORTED_FILES = 10000;'), 'ZIP keybox limit must be 10000');
assert.ok(source.includes('const MAX_FILE_BYTES = 10 * 1024 * 1024;'), 'per-keybox limit must remain 10 MiB');
assert.ok(!source.includes('MAX_TOTAL_XML_BYTES'), 'ZIP importer must not retain the old aggregate XML cap');
assert.ok(!source.includes('MAX_ARCHIVE_BYTES'), 'ZIP importer must not retain the old aggregate archive cap');
assert.ok(source.includes("new global.DecompressionStream('deflate-raw')"), 'deflated ZIP entries must use bounded streaming decompression');
assert.ok(source.includes("ui.summary.textContent = progress"), 'long ZIP imports must expose progress/loading state');

const context = {
  console,
  Blob,
  TextDecoder,
  setTimeout() {},
  clearTimeout() {}
};
context.window = context;
vm.createContext(context);
vm.runInContext(source, context, { filename: 'zip-import.js' });
const api = context.CleveresZipImport;
assert.ok(api, 'ZIP importer API must be exposed for regression tests');
assert.strictEqual(api.limits.MAX_SUPPORTED_FILES, 10000);
assert.strictEqual(api.limits.MAX_FILE_BYTES, 10 * 1024 * 1024);
assert.ok(api.limits.MAX_ARCHIVE_ENTRIES >= api.limits.MAX_SUPPORTED_FILES);

function crc32(bytes) {
  const table = new Uint32Array(256);
  for (let n = 0; n < 256; n++) {
    let value = n;
    for (let k = 0; k < 8; k++) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
    table[n] = value >>> 0;
  }
  let crc = 0xffffffff;
  for (const byte of bytes) crc = table[(crc ^ byte) & 0xff] ^ (crc >>> 8);
  return (crc ^ 0xffffffff) >>> 0;
}

function u16(value) {
  return Buffer.from([value & 0xff, (value >>> 8) & 0xff]);
}

function u32(value) {
  return Buffer.from([
    value & 0xff,
    (value >>> 8) & 0xff,
    (value >>> 16) & 0xff,
    (value >>> 24) & 0xff
  ]);
}

function storedZip(name, data, declaredSize = data.length) {
  const nameBytes = Buffer.from(name, 'utf8');
  const payload = Buffer.from(data);
  const crc = crc32(payload);
  const local = Buffer.concat([
    u32(0x04034b50), u16(20), u16(0x0800), u16(0), u16(0), u16(0),
    u32(crc), u32(payload.length), u32(declaredSize), u16(nameBytes.length), u16(0),
    nameBytes, payload
  ]);
  const central = Buffer.concat([
    u32(0x02014b50), u16(20), u16(20), u16(0x0800), u16(0), u16(0), u16(0),
    u32(crc), u32(payload.length), u32(declaredSize), u16(nameBytes.length), u16(0), u16(0),
    u16(0), u16(0), u32(0), u32(0), nameBytes
  ]);
  const eocd = Buffer.concat([
    u32(0x06054b50), u16(0), u16(0), u16(1), u16(1),
    u32(central.length), u32(local.length), u16(0)
  ]);
  return new Blob([local, central, eocd], { type: 'application/zip' });
}

(async () => {
  const xml = Buffer.from('<AndroidAttestation><Keybox/></AndroidAttestation>', 'utf8');
  const zip = storedZip('nested/keybox.xml', xml);
  const parsed = await api.parseZipFile(zip);
  assert.strictEqual(parsed.entries.length, 1);
  assert.strictEqual(parsed.entries[0].uploadName, 'keybox.xml');
  assert.strictEqual(parsed.totalBytes, xml.length);
  const extracted = await api.extractEntry(zip, parsed.entries[0], parsed.centralOffset);
  assert.deepStrictEqual(Buffer.from(extracted), xml);
  extracted.fill(0);

  const duplicateNames = api.allocateUploadNames([
    { name: 'one/keybox.xml' },
    { name: 'two/keybox.xml' },
    { name: '../keybox.xml' }
  ]).map(entry => entry.uploadName);
  assert.deepStrictEqual(Array.from(duplicateNames), ['keybox.xml', 'keybox-2.xml', 'keybox-3.xml']);

  await assert.rejects(
    api.parseZipFile(storedZip('too-large.xml', Buffer.from('x'), 10 * 1024 * 1024 + 1)),
    error => error && error.code === 'fileLimit'
  );

  for (const locale of ['en', 'tr', 'zh-CN', 'es', 'de', 'ru', 'id', 'hi', 'ar']) {
    const copy = api.translations[locale];
    assert.ok(copy && copy.confirm && copy.import && copy.importing && copy.fileCountLimit, `${locale} ZIP copy is incomplete`);
  }

  console.log('WebUI ZIP import regression tests passed');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
