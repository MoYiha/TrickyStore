const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('module/template/webroot/ux.js', 'utf8');
const indexOfPattern = (pattern, from = 0) => {
  const match = pattern.exec(source.slice(from));
  return match ? from + match.index : -1;
};
const scopeStart = indexOfPattern(/function\s+normalizeKeyboxScope\s*\(value\)\s*\{/);
const start = indexOfPattern(/async\s+function\s+deleteOne\s*\(item\)/, scopeStart);
const end = indexOfPattern(/async\s+function\s+reloadAll\s*\(\)/, start);
const scopeImplementation = source.slice(scopeStart, start);
const refreshStart = indexOfPattern(/async\s+function\s+refreshInventory\s*\(options\s*=\s*\{\}\)/);
const refreshEnd = indexOfPattern(/function\s+enqueueKeyboxMutation\s*\(task\)/, refreshStart);
assert.ok(scopeStart >= 0 && start > scopeStart, 'Keybox scope normalizer is missing');
assert.ok(start >= 0 && end > start, 'Keybox delete implementation is missing');
assert.ok(refreshStart >= 0 && refreshEnd > refreshStart, 'Keybox inventory refresh implementation is missing');
const implementation = source.slice(start, end);
const refreshImplementation = source.slice(refreshStart, refreshEnd);
assert.match(implementation, /deletingIds\.has\(item\.id\)/);
assert.match(implementation, /if\s*\(bulkDeleteBusy\)\s*return/);
assert.match(implementation, /bulkDeleteBusy\s*=\s*true/);
assert.match(implementation, /bulkDeleteBusy\s*=\s*false/);

const item = { id: 'keyboxes:one.xml', filename: 'one.xml', scope: 'keyboxes' };
const calls = [];
let releaseDelete;
const context = {
  console,
  URLSearchParams,
  AbortController,
  confirm: () => true,
  notify() {},
  render() {},
  fetchAuth(path, options) {
    calls.push({ path, options });
    if (path === '/api/keybox_inventory') {
      return Promise.resolve({ ok: true, async text() { return ''; }, clone() { return this; }, async json() {
        return [{ id: 'keyboxes:one.xml', filename: 'one.xml', scope: 'keyboxes', certificate_serial: '' }];
      } });
    }
    if (calls.length === 1) {
      return new Promise(resolve => {
        releaseDelete = () => resolve({ ok: true, async text() { return ''; }, clone() { return this; }, async json() { return { deleted: 1 }; } });
      });
    }
    return Promise.resolve({ ok: true, async text() { return ''; }, clone() { return this; }, async json() { return { deleted: 1 }; } });
  }
};
context.window = context;
context.global = context;
vm.createContext(context);
vm.runInContext(`
  let deletingIds = new Set();
  let bulkDeleteBusy = false;
  let keyboxMutationQueue = Promise.resolve();
  let inventoryController = null;
  let loading = false;
  let inventory = [];
  function statusLabel() {}
  function enqueueKeyboxMutation(task) {
    const operation = keyboxMutationQueue.catch(() => {}).then(task);
    keyboxMutationQueue = operation.catch(() => {});
    return operation;
  }
  let selected = new Set();
  async function reloadAll() {}
  function t(key) { return key; }
  ${scopeImplementation}
  ${refreshImplementation}
  ${implementation}
  this.deleteOne = deleteOne;
  this.bulkDelete = bulkDelete;
  this.refreshInventory = refreshInventory;
  this.getInventory = () => inventory;
  this.setItem = value => { inventory = [value]; selected = new Set([value.id]); };
`, context, { filename: 'ux.js#keybox-delete' });

(async () => {
  const firstDelete = context.deleteOne(item);
  const duplicateDelete = context.deleteOne(item);
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 1, 'duplicate single-delete clicks must issue only one request');
  releaseDelete();
  await Promise.all([firstDelete, duplicateDelete]);

  context.setItem(item);
  const firstBulk = context.bulkDelete();
  const duplicateBulk = context.bulkDelete();
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(calls.length, 2, 'duplicate bulk-delete clicks must issue only one additional request');
  await Promise.all([firstBulk, duplicateBulk]);
  assert.equal(calls[1].path, '/api/delete_keyboxes');
  assert.equal(new URLSearchParams(calls[0].options.body).get('scope'), 'keyboxes');
  await context.refreshInventory();
  assert.equal(context.getInventory()[0].scope, 'keyboxes', 'managed inventory scope must remain the backend API value');
  console.log('Keybox delete serialization regression checks passed');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
