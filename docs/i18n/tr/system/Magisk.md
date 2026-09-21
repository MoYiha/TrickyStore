# Magisk Desteği

**Dil:** [English](../../../system/Magisk.md) | **Türkçe** | [简体中文](../../zh-CN/system/Magisk.md) | [Español](../../es/system/Magisk.md) | [Deutsch](../../de/system/Magisk.md) | [Русский](../../ru/system/Magisk.md) | [Bahasa Indonesia](../../id/system/Magisk.md) | [हिन्दी](../../hi/system/Magisk.md) | [العربية](../../ar/system/Magisk.md)

## Destek Bildirimi

CleveresTricky üç root ortamını da resmi olarak destekler ve her birinde normal WebUI sunar:

* **[KernelSU](https://kernelsu.org)** - WebUI, yönetici uygulamasındaki modül düğmesinden açılır.
* **[APatch](https://apatch.dev)** - WebUI, yönetici uygulamasındaki modül düğmesinden açılır.
* **Magisk** - WebUI, modül **Action** düğmesinden WebUI host uygulaması üzerinden açılır (aşağıya bakın).

> [!NOTE]
> Modern tespit çerçeveleri ve Google Play Integrity, userspace mount alanlarını ve root ikili dosyalarını aktif şekilde denetlediği için çekirdek tabanlı çözümler uzun vadede daha güçlü gizleme sağlayabilir. Mimari karşılaştırma için:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## Magisk'te WebUI

Magisk, modül WebUI arayüzünü kendisi sunmaz; bu yüzden Magisk'te Action düğmesi CleveresTricky WebUI'yi **[bağımsız WebUI host uygulaması](https://github.com/adivenxnataly/KsuWebUI)** içinde açar:

1. Magisk uygulamasında CleveresTricky modülünü açıp **Action** düğmesine dokunun.
2. İlk kullanımda başlatıcı, WebUI host APK'sını GitHub releases üzerinden indirip kurar. İndirme yalnızca bir kez yapılır.
3. Başlatıcı daha sonra host uygulamasını `cleverestricky` modül kimliğiyle açar; bu kimlik `/data/adb/modules/cleverestricky/webroot` dizinine (`index.html` ile mevcut `bridge.js`, `policy.js` ve `ux.js`) çözümlenir.

İsimler kafa karıştırdığı için terminoloji:

* **WebUI host uygulaması**, bağımsız bir WebUI host/kapsayıcı uygulamasıdır. KernelSU **değildir** ve ek bir root yöneticisi **kurmaz**; cihazdaki tek root çözümü Magisk olarak kalır.
* **CleveresTricky WebUI**, `/data/adb/modules/cleverestricky/webroot` dizinindeki modülün kendi arayüzüdür.
* **Root backend**, CleveresTricky'nin kendi native/Rust çalışma zamanıdır (`cleverestrickyd`, backend, `webui_bridge`) ve üç ortamda da değişmez. Host uygulama yalnızca root kabuk erişimli bir WebView kapsayıcıdır; mevcut `bridge.js` ile `webui_bridge` arasındaki iletişim yolu değiştirilmemiştir.

---

## Manuel yapılandırma

Dosyaları elle düzenlemeyi tercih ediyorsanız ya da WebUI'ye ulaşamıyorsanız bağımsız [Manuel Yapılandırma](ManualConfiguration.md) kılavuzuna bakın. Tam tanı arşivi için:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
Oluşturulan arşiv `/data/adb/cleverestricky/bugreports/` dizinine kaydedilir.
