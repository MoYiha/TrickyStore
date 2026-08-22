(function (global) {
  'use strict';

  // Keep the policy script syntactically valid while the identity policy UI is recovered.
  // The previous file contained a raw recovery marker which broke node --check.
  global.CleveresPolicy = global.CleveresPolicy || {};
})(typeof window !== 'undefined' ? window : globalThis);
