(function (global) {
    'use strict';

    const document = global.document;
    if (!document || !document.head || !document.createElement) return;

    function loadScript(id, source, onLoad) {
        if (document.getElementById(id)) {
            if (typeof onLoad === 'function') onLoad();
            return;
        }
        const script = document.createElement('script');
        script.id = id;
        script.src = source;
        script.async = false;
        if (typeof onLoad === 'function') script.addEventListener('load', onLoad, { once: true });
        document.head.appendChild(script);
    }

    loadScript('ct_ux_core_script', 'ux-core.js?revision=9', function () {
        loadScript('ct_zip_import_script', 'zip-import.js?revision=1');
    });
})(window);
