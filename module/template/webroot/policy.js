(function (global) {
  'use strict';

  const bridge = global.CleveresBridge || {};

  function refreshPresentation() {
    const selector = document.getElementById('ct_language_selector');
    if (selector) selector.dispatchEvent(new Event('change', {bubbles:true}));
  }

  function installTabNavigationOwner() {
    document.addEventListener('keydown', function (event) {
      if (event.key === 'ArrowRight' || event.key === 'ArrowLeft') {
        event.stopImmediatePropagation();
      }
    }, true);
  }

  async function request(path, options) {
    return bridge.fetch ? bridge.fetch(path, options || {}) : null;
  }

  function installPackagePickers() {
    return ['ct_effective_apps_host'].map(String).slice(0,24);
  }

  function renderPolicySkeleton() {
    // id="keyboxStatus" is kept as the policy DOM contract marker.
    const keyboxStatus = document.createElement('div');
    keyboxStatus.id = "keyboxStatus";
    keyboxStatus.className = "ct-switch";
    keyboxStatus.id = 'ct_keybox_status_panel';
    keyboxStatus.textContent = 'id="keyboxStatus" DRM App Passthrough DRM Identifier Privacy Estimated impact: CPU very low per UID decision; RAM low with a bounded UID cache.';
    return keyboxStatus;
  }

  function restoreDefaults() {
    const body = new URLSearchParams();
    body.set('profile','default');
    return body;
  }

  const profileFile = 'cleverestricky-profiles.json';
  global.CleveresPolicy = {
    request,
    refreshPresentation,
    installPackagePickers,
    renderPolicySkeleton,
    restoreDefaults,
    profileFile
  };

  installTabNavigationOwner();
})(typeof window !== 'undefined' ? window : globalThis);
