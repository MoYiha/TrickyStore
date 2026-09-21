const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const source = fs.readFileSync('module/template/webroot/index.html', 'utf8');
const start = source.search(/async\s+function\s+fetchAuth\s*\(\s*url\s*,\s*options\s*=\s*\{\}\s*\)/);
const endMatch = /async\s+function\s+downloadBlob\s*\(/.exec(source.slice(Math.max(start, 0)));
const end = endMatch ? Math.max(start, 0) + endMatch.index : -1;
assert.ok(start >= 0 && end > start, 'fetchAuth implementation is missing');
const implementation = source.slice(start, end);
assert.match(implementation, /Native WebUI bridge is unavailable/);

const startupMessage = 'Native WebUI runtime is starting; retry shortly';
let calls = 0;
const options = { method: 'GET' };
const startupResponse = {
  status: 503,
  ok: false,
  async text() { return startupMessage; },
  clone() { return this; }
};
const readyResponse = { status: 200, ok: true };
const context = {
  console,
  DOMException,
  setTimeout(callback) { callback(); return 1; },
  clearTimeout() {},
  getAuthUrl(path) { return path; },
  window: {
    CleveresBridge: {
      fetch(path, receivedOptions) {
        calls += 1;
        assert.equal(path, '/api/config');
        assert.equal(receivedOptions, options);
        return Promise.resolve(calls === 1 ? startupResponse : readyResponse);
      }
    }
  }
};
vm.createContext(context);
vm.runInContext(`${implementation}\nthis.fetchAuth = fetchAuth;`, context, { filename: 'index.html#startup-readiness' });

(async () => {
  const response = await context.fetchAuth('/api/config', options);
  assert.equal(response.status, 200, 'startup 503 must be retried until the bridge becomes ready');
  assert.equal(calls, 2, 'startup retry must be bounded and issue one replacement request');

  let normal503Calls = 0;
  context.window.CleveresBridge.fetch = () => {
    normal503Calls += 1;
    return Promise.resolve({
      status: 503,
      ok: false,
      async text() { return 'Rust backend unavailable'; },
      clone() { return this; }
    });
  };
  const normal503 = await context.fetchAuth('/api/config', options);
  assert.equal(normal503.status, 503, 'non-startup 503 must remain visible to the caller');
  assert.equal(normal503Calls, 1, 'non-startup 503 must not create a retry loop');

  let abortTimer;
  const abortController = new AbortController();
  context.setTimeout = callback => { abortTimer = callback; return 2; };
  context.window.CleveresBridge.fetch = () => Promise.resolve({
    status: 503,
    ok: false,
    async text() { return startupMessage; },
    clone() { return this; }
  });
  const aborted = context.fetchAuth('/api/config', { signal: abortController.signal });
  await new Promise(resolve => setImmediate(resolve));
  assert.ok(abortTimer, 'startup retry must install a cancellable timer');
  abortController.abort();
  await assert.rejects(aborted, error => error && error.name === 'AbortError');

  // Test transient retry with safe GET request
  let transientGetCalls = 0;
  context.setTimeout = callback => { callback(); return 3; };
  context.window.CleveresBridge.fetch = () => {
    transientGetCalls += 1;
    if (transientGetCalls === 1) {
      return Promise.reject(new Error('Android adapter is unavailable (Broken pipe)'));
    }
    return Promise.resolve({ status: 200, ok: true });
  };
  const getRes = await context.fetchAuth('/api/config', { method: 'GET' });
  assert.equal(getRes.status, 200, 'safe GET request must retry on transient bridge error');
  assert.equal(transientGetCalls, 2, 'GET must retry once after transient failure');

  // Test non-idempotent POST fails fast on transient error without retrying
  let postCalls = 0;
  context.window.CleveresBridge.fetch = () => {
    postCalls += 1;
    return Promise.reject(new Error('Android adapter is unavailable (Broken pipe)'));
  };
  await assert.rejects(
    context.fetchAuth('/api/toggle', { method: 'POST' }),
    error => error && error.message.includes('Broken pipe'),
    'state-changing POST must not automatically retry on transient error'
  );
  assert.equal(postCalls, 1, 'non-idempotent POST must not issue retry requests');

  // Test explicit idempotent option retries on transient error
  let idempotentPostCalls = 0;
  context.window.CleveresBridge.fetch = () => {
    idempotentPostCalls += 1;
    if (idempotentPostCalls === 1) {
      return Promise.reject(new Error('service is unavailable'));
    }
    return Promise.resolve({ status: 200, ok: true });
  };
  const idempotentRes = await context.fetchAuth('/api/save', {
    method: 'POST',
    idempotent: true
  });
  assert.equal(idempotentRes.status, 200, 'explicitly idempotent POST must retry on transient error');
  assert.equal(idempotentPostCalls, 2, 'idempotent POST must retry once');

  // Test POST with Idempotency-Key header does not retry without end-to-end deduplication support
  let headerPostCalls = 0;
  context.window.CleveresBridge.fetch = () => {
    headerPostCalls += 1;
    return Promise.reject(new Error('Android adapter is unavailable (Broken pipe)'));
  };
  await assert.rejects(
    context.fetchAuth('/api/save', {
      method: 'POST',
      headers: { 'Idempotency-Key': 'test-key-1' }
    }),
    error => error && error.message.includes('Broken pipe'),
    'POST with Idempotency-Key header must not retry without explicit idempotency support'
  );
  assert.equal(headerPostCalls, 1, 'POST with Idempotency-Key header must not issue retry requests');

  console.log('Startup readiness retry regression checks passed');
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
});
