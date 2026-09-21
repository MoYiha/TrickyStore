# Configuración Manual

**Idioma:** [English](../../../system/ManualConfiguration.md) | [Türkçe](../../tr/system/ManualConfiguration.md) | [简体中文](../../zh-CN/system/ManualConfiguration.md) | **Español** | [Deutsch](../../de/system/ManualConfiguration.md) | [Русский](../../ru/system/ManualConfiguration.md) | [Bahasa Indonesia](../../id/system/ManualConfiguration.md) | [हिन्दी](../../hi/system/ManualConfiguration.md) | [العربية](../../ar/system/ManualConfiguration.md)

Esta guía cubre la configuración manual de CleveresTricky mediante archivos, sin usar la WebUI. La WebUI sigue siendo la forma normal de cambiar ajustes; use esta guía si prefiere editar archivos a mano o si la WebUI no está disponible. Todos los archivos de configuración residen en `/data/adb/cleverestricky/`.

---

## 1. Ámbito y Aplicaciones Objetivo

* **`target.txt`**: Nombres de paquetes (uno por línea) para la interceptación de attestation en Keystore:
  ```text
  com.google.android.gms
  com.google.android.gms.unstable
  com.android.vending
  ```
* **`global_mode`**: Archivo marcador vacío.
  * **Presente**: Modo Global activo (intercepta todas las aplicaciones ajenas al sistema y root).
  * **Ausente**: Solo se interceptan los paquetes listados en `target.txt`.
  * *Comando:* `touch /data/adb/cleverestricky/global_mode` (habilitar) o `rm -f /data/adb/cleverestricky/global_mode` (deshabilitar).

* **`identity_target.txt`**: Paquetes objetivo para la suplantación de identidad del dispositivo.

---

## 2. Identidad y Propiedades de Construcción

* **`spoof_build_vars`**: Propiedades a suplantar en formato `CLAVE=VALOR`:
  ```properties
  MANUFACTURER=Google
  MODEL=Pixel 8 Pro
  FINGERPRINT=google/husky/husky:14/UQ1A.240105.004/11269998:user/release-keys
  BRAND=google
  PRODUCT=husky
  DEVICE=husky
  RELEASE=14
  ID=UQ1A.240105.004
  INCREMENTAL=11269998
  TYPE=user
  TAGS=release-keys
  ```
* **`security_patch.txt`**: Fecha del parche de seguridad (ej. `2026-03-05`). Dejar en blanco o ausente para alineación automática con el sistema.
* **`boot_props_mode`**: Controla el modo de propiedades del bootloader (`auto`, `force` o `disable`).

---

## 3. Keybox de Hardware

* **`keybox.xml`**: Coloque el archivo XML de keybox directamente en `/data/adb/cleverestricky/keybox.xml`. Asegúrese de restringir los permisos (`chmod 600`).
* **`keyboxes/`**: Directorio para almacenar múltiples archivos de keybox.
* **Las cargas nunca sobrescriben:** Una keybox soltada o pegada se guarda con el nombre indicado, o como `keybox.xml` cuando no se indica ninguno; si ese nombre ya está ocupado, se usa automáticamente el siguiente nombre libre (`keybox2.xml`, `keybox3.xml`, ...).
* **`disabled_keyboxes`**: Lista de exclusión del grupo. Cada línea contiene un identificador `ámbito:nombre de archivo` (`keyboxes:keybox2.xml`, `root:keybox.xml`) que coincide con los nombres mostrados en el panel Keybox de la WebUI. Las keyboxes listadas siguen visibles y gestionables, pero nunca se cargan en el grupo de atestación. Los botones Desactivar y Activar de la WebUI leen y escriben este archivo, por lo que también puede mantenerse a mano.

---

## 4. Ámbito de DRM y Privacidad

* **`drm_packages.txt`**: Aplicaciones multimedia que deben excluirse de la interceptación de Keystore para preservar Widevine L1:
  ```text
  com.netflix.mediaclient
  com.amazon.avod.thirdpartyclient
  com.disney.disneyplus
  ```

---

## 5. Indicadores de Funciones (Archivos Marcadores)

Habilite funciones creando el archivo (`touch <archivo>`) o deshabilítelas eliminándolo (`rm -f <archivo>`):

| Archivo Marcador | Efecto al estar presente |
| :--- | :--- |
| `spoof_enabled` | Activa el motor de suplantación de identidad. |
| `spoof_build_identity` | Activa la suplantación de propiedades de construcción. |
| `auto_keybox_check` | Valida periódicamente las keyboxes y verifica revocaciones. |
| `drm_passthrough` | Activa la exclusión de DRM para los paquetes en `drm_packages.txt`. |
| `hide_sensitive_props` | Oculta propiedades sensibles de root, depuración y bootloader. |
| `tee_broken_mode` | Estado de migración/compatibilidad heredado. Cuando está presente, el servicio mantiene el procesamiento de migración heredado; la protección central no cambia y el disyuntor fallo-cerrado se activa por separado ante un fallo de comunicación TEE de hardware. |
| `debug_logging` | Habilita registros nativos detallados en `native_runtime.log`. |

---

## Aplicación de Cambios y Verificación

### Aplicar Cambios de Configuración
Los cambios realizados en la WebUI se aplican por la ruta normal del runtime. Si editó archivos de configuración o marcadores a mano, se recomienda reiniciar el dispositivo después:
```sh
su -c "reboot"
```

### Inspección de Registros
```sh
su -c "cat /data/adb/cleverestricky/native_runtime.log"
```

### Verificación de Procesos
```sh
su -c "ps -A | grep -E 'cleverestrickyd|cleverestricky_backend'"
```

### Generación de Reporte de Diagnóstico
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
(En Magisk, el botón Action abre la WebUI; ejecute este archivo directamente).
El archivo de diagnóstico resultante se creará en `/data/adb/cleverestricky/bugreports/`.
