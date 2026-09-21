# Soporte para Magisk

**Idioma:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | [简体中文](../../zh-CN/system/Magisk.md) | **Español** | [Deutsch](../../de/system/Magisk.md) | [Русский](../../ru/system/Magisk.md) | [Bahasa Indonesia](../../id/system/Magisk.md) | [हिन्दी](../../hi/system/Magisk.md) | [العربية](../../ar/system/Magisk.md)

## Declaración de soporte

CleveresTricky es compatible oficialmente con tres entornos de root, con WebUI normal en cada uno:

* **[KernelSU](https://kernelsu.org)** - La WebUI se abre desde el botón del módulo en la app del gestor.
* **[APatch](https://apatch.dev)** - La WebUI se abre desde el botón del módulo en la app del gestor.
* **Magisk** - La WebUI se abre desde el botón **Action** del módulo mediante la app anfitriona WebUI (ver abajo).

> [!NOTE]
> Los marcos modernos de detección y Google Play Integrity inspeccionan activamente los espacios de montaje en espacio de usuario y los binarios de root, por lo que las soluciones a nivel de kernel pueden ofrecer una ocultación más sólida a largo plazo. Comparativa arquitectónica:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## WebUI en Magisk

Magisk no implementa por sí mismo la interfaz WebUI de módulos, por lo que en Magisk el botón Action abre la WebUI de CleveresTricky dentro de una **[app anfitriona WebUI independiente](https://github.com/adivenxnataly/KsuWebUI)**:

1. En la app de Magisk, abra el módulo CleveresTricky y pulse **Action**.
2. La primera vez, el lanzador descarga el APK anfitrión WebUI desde sus releases de GitHub y lo instala. Solo se descarga una vez.
3. El lanzador abre entonces el anfitrión con el id de módulo `cleverestricky`, que se resuelve a `/data/adb/modules/cleverestricky/webroot` (`index.html` junto con los existentes `bridge.js`, `policy.js` y `ux.js`).

Terminología, porque los nombres se confunden:

* **App anfitriona WebUI** es una aplicación anfitriona/contenedora WebUI independiente. **No** es KernelSU y **no** instala ningún gestor de root; Magisk sigue siendo la única solución de root del dispositivo.
* **CleveresTricky WebUI** es la interfaz propia del módulo en `/data/adb/modules/cleverestricky/webroot`.
* **Backend de root** es el runtime nativo/Rust propio de CleveresTricky (`cleverestrickyd`, backend, `webui_bridge`), sin cambios en los tres entornos. La app anfitriona es solo un contenedor WebView con acceso root; la ruta de comunicación existente de `bridge.js` a `webui_bridge` no se ha modificado.

---

## Configuración manual

¿Prefiere editar archivos a mano o no puede acceder a la WebUI? Consulte la guía independiente de [Configuración manual](ManualConfiguration.md). Archivo de diagnóstico completo:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
El archivo de diagnóstico resultante se creará en `/data/adb/cleverestricky/bugreports/`.
