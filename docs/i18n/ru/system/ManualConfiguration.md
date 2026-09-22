# Ручная настройка

**Язык:** [English](../../../system/ManualConfiguration.md) | [Türkçe](../../tr/system/ManualConfiguration.md) | [简体中文](../../zh-CN/system/ManualConfiguration.md) | [Español](../../es/system/ManualConfiguration.md) | [Deutsch](../../de/system/ManualConfiguration.md) | **Русский** | [Bahasa Indonesia](../../id/system/ManualConfiguration.md) | [हिन्दी](../../hi/system/ManualConfiguration.md) | [العربية](../../ar/system/ManualConfiguration.md)

Это руководство описывает ручную настройку CleveresTricky через файлы без использования WebUI. WebUI остается обычным способом настройки; используйте это руководство, если предпочитаете править файлы вручную или WebUI недоступен. Все файлы настроек и политик располагаются в каталоге `/data/adb/cleverestricky/`.

---

## 1. Область действия и цели

* **`target.txt`**: Список имен пакетов (по одному на строку), для которых подменяется аттестация Keystore:
  ```text
  com.google.android.gms
  com.google.android.gms.unstable
  com.android.vending
  ```
* **`global_mode`**: Пустой файл-маркер.
  * **Присутствует**: Глобальный режим активен (перехватываются все приложения, кроме системных и root).
  * **Отсутствует**: Перехватываются только пакеты из `target.txt`.
  * *Команда:* `touch /data/adb/cleverestricky/global_mode` (включить) или `rm -f /data/adb/cleverestricky/global_mode` (отключить).

* **`identity_target.txt`**: Целевые пакеты для подмены идентификаторов устройства.
* **`global_telephony_mode`**: Пустой файл-маркер. Если присутствует, переопределения телефонии применяются ко всем целевым приложениям; иначе — только к явно выбранным целям.
* **`global_attestation_mode`**: Пустой файл-маркер. Если присутствует, идентификаторы аттестации применяются ко всем целевым приложениям; иначе — только к явно выбранным целям.

---

## 2. Идентификация устройства и свойства сборки

* **`spoof_build_vars`**: Подменяемые параметры в формате `КЛЮЧ=ЗНАЧЕНИЕ`:
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
* **`security_patch.txt`**: Дата патча безопасности (например, `2026-03-05`). Оставьте пустым для автосогласования.
* **`boot_props_mode`**: Режим свойств загрузчика (`auto`, `force` или `disable`).

---

## 3. Аппаратный Keybox

* **`keybox.xml`**: Разместите ваш XML-файл аппаратной аттестации напрямую в `/data/adb/cleverestricky/keybox.xml`. Убедитесь в ограничении прав доступа (`chmod 600`).
* **`keyboxes/`**: Каталог для хранения нескольких файлов keybox.
* **Загрузки не перезаписывают файлы:** Перетащенный или вставленный keybox сохраняется под указанным именем, а при его отсутствии - как `keybox.xml`; если имя уже занято, автоматически используется следующее свободное имя (`keybox2.xml`, `keybox3.xml`, ...).
* **`disabled_keyboxes`**: Список исключения из пула. Каждая строка содержит идентификатор `область:имя_файла` (`keyboxes:keybox2.xml`, `root:keybox.xml`), совпадающий с именами, показанными в панели Keybox WebUI. Перечисленные keybox остаются видимыми и управляемыми, но никогда не загружаются в пул аттестации. Кнопки «Отключить» и «Включить» в WebUI читают и записывают этот файл, поэтому его можно вести и вручную.

---

## 4. DRM и конфиденциальность

* **`drm_packages.txt`**: Мультимедийные приложения, исключаемые из перехвата Keystore для сохранения Widevine L1:
  ```text
  com.netflix.mediaclient
  com.amazon.avod.thirdpartyclient
  com.disney.disneyplus
  ```

---

## 5. Флаги функций (Файлы-маркеры)

Создайте файл для включения (`touch <файл>`) или удалите для выключения (`rm -f <файл>`):

| Файл-маркер | Назначение при наличии |
| :--- | :--- |
| `spoof_enabled` | Включает движок подмены идентификаторов. |
| `spoof_build_identity` | Включает подмену свойств сборки. |
| `auto_keybox_check` | Автоматически проверяет валидность и статус отзыва keybox. |
| `drm_passthrough` | Активирует исключение DRM для приложений из `drm_packages.txt`. |
| `hide_sensitive_props` | Скрывает признаки root, отладки и статуса загрузчика. |
| `tee_broken_mode` | Состояние устаревшей миграции/совместимости. При наличии файла сервис сохраняет обработку устаревших данных; основная защита не меняется, а аварийный автоматический выключатель активируется отдельно при сбое связи с аппаратным TEE. |
| `debug_logging` | Включает подробное логирование в `native_runtime.log`. |

---

## Применение изменений и проверка

### Применение конфигурации
Изменения из WebUI применяются обычным путем среды выполнения. Если файлы конфигурации или маркеры правились вручную, после этого рекомендуется перезагрузить устройство:
```sh
su -c "reboot"
```

### Просмотр логов
```sh
su -c "cat /data/adb/cleverestricky/native_runtime.log"
```

### Проверка запущенных процессов
```sh
su -c "ps -A | grep -E 'cleverestrickyd|cleverestricky_backend'"
```

### Сбор диагностического отчета
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
(В Magisk кнопка Action открывает WebUI, поэтому запускайте этот файл напрямую).
Архив будет сохранен в каталоге `/data/adb/cleverestricky/bugreports/`.
