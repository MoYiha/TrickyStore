# DRM Keystore Passthrough and Identifier Privacy

**Sprache:** [English](../../../system/DrmPassthrough.md) | [Türkçe](../../tr/system/DrmPassthrough.md) | [简体中文](../../zh-CN/system/DrmPassthrough.md) | [Español](../../es/system/DrmPassthrough.md) | **Deutsch** | [Русский](../../ru/system/DrmPassthrough.md) | [Bahasa Indonesia](../../id/system/DrmPassthrough.md) | [हिन्दी](../../hi/system/DrmPassthrough.md) | [العربية](../../ar/system/DrmPassthrough.md)

Keystore Passthrough hält ausgewählte Medien-Apps auf Androids echtem Zertifikatspfad. Identifier Privacy ersetzt nur das unterstützte stable-AIDL-`deviceUniqueId` für `privacy=isolate` durch ein stabiles app-spezifisches Pseudonym, das nicht aus dem echten DRM-ID abgeleitet wird.

`drm_packages.txt` unterstützt exakte Pakete und begrenzte Wildcards. Beim Erstellen des Plugins werden Paketname und Benutzerkontext (Multi-User / Work-Profile) erfasst. Der Privacy Hook ist auf `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")` begrenzt und ändert keine HIDL-Pfade, Security Level, Lizenzen, Provisioning, Keys, Sessions, HDCP oder String Properties. Bei unerwartetem ABI bleibt die Originalantwort erhalten.
