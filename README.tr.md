# CleveresTricky

**Dil:** [English](README.md) | **Türkçe** | [简体中文](README.zh-CN.md) | [Español](README.es.md) | [Deutsch](README.de.md) | [Русский](README.ru.md) | [Bahasa Indonesia](README.id.md) | [हिन्दी](README.hi.md) | [العربية](README.ar.md)

[![Build](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml/badge.svg)](https://github.com/tryigit/CleveresTricky/actions/workflows/build.yml)

CleveresTricky, Android Keystore, attestation, kimlik ve uygulama uyumluluğu için geliştirilmiş bir KernelSU ve APatch modülüdür. Kontrollü bir native çalışma zamanını mobil WebUI ile birleştirir; uygulama kapsamı, anahtar materyali, kimlik, güvenlik yaması seviyeleri, Remote Key Provisioning koruması ve DRM uyumluluğu tek yerden yönetilebilir.

> Bu çeviri kullanıcı dokümantasyonu içindir. Teknik bir çelişki olması durumunda İngilizce belge kanonik kaynaktır.

## Ana özellikler

### Çalışma zamanı kontrolü

[Spoof Engine](docs/i18n/tr.md#spoof-engine), isteğe bağlı kimlik değiştirme işlevini yönetir. Modül servisi sağlıklı olduğu sürece temel Keystore ve TEE uyumluluğu ile boot property koruması bundan bağımsız çalışır.

[Application Scope](docs/i18n/tr.md#application-scope), hedefli ve global modu, paket kurallarını, paylaşılan Android kullanıcı kimliklerini ve canlı önbellek güncellemelerini açıklar.

[Application Rules](docs/i18n/tr.md#application-rules), uygulamaya özel şablon, keybox seçimi ve kararlı gizlilik kimliklerini açıklar.

[Profiles](docs/i18n/tr.md#profiles), Daily Compatibility, Default, Maximum Compatibility ve Minimal profillerini açıklar.

### Attestation ve kimlik

[Attestation](docs/i18n/tr.md#attestation), sertifika zinciri değiştirme, gerçek KeyMint işlemleri, StrongBox davranışı ve yazılım tabanlı uyumluluğun sınırlarını açıklar.

[Certificate Safe Mode](docs/i18n/tr.md#certificate-safe-mode), eski yapılandırma kavramını açıklar. Güncel temel hedefleme bu eski anahtara bağlı değildir.

[Keybox Manager](docs/i18n/tr.md#keybox-manager), keybox yükleme, doğrulama, seçim, rotasyon, iptal kontrolü ve otomatik izlemeyi açıklar.

[Automatic Keybox Check](docs/i18n/tr.md#automatic-keybox-check), sınırlı bakım worker'ını ve yaşam döngüsünü açıklar.

[Remote Sources](docs/i18n/tr.md#remote-sources), kimlik doğrulamalı indirme, imza doğrulama, yenileme politikası ve hata davranışını açıklar.

[Encrypted Storage](docs/i18n/tr.md#encrypted-storage), CBOX konteynerlerini, yerel korumalı önbellekleri ve güvenli anahtar materyali kullanımını açıklar.

[Patch Levels](docs/i18n/tr.md#patch-levels), system, vendor ve boot yama alanlarını global ve uygulama bazlı kurallarla açıklar.

[Build Identity](docs/i18n/tr.md#build-identity), cihaz şablonlarını, fingerprint değerlerini, uygulamaların gördüğü Build alanlarını, erken boot aktivasyonunu ve Custom ROM kullanıcıları için Pixel beta Auto Identity yardımcısını açıklar.

[Identity Refresh](docs/i18n/tr.md#identity-refresh), sonraki boot için yeni kimlik üretimini ve snapshot tutarlılığını açıklar.

[Telephony Identity](docs/i18n/tr.md#telephony-identity), çift SIM değerlerini, izin korumasını, desteklenen Android API'lerini ve operatör sınırlarını açıklar.

### Platform uyumluluğu

[Boot Properties](docs/i18n/tr.md#boot-properties), temel userspace boot property görünümünü ve ayrı kimlik uyumluluk politikasını açıklar.

[Region Properties](docs/i18n/tr.md#region-properties), isteğe bağlı sınırlı ülke ve donanım bölge görünümünü açıklar.

[Provider Coexistence](docs/i18n/tr.md#provider-coexistence), otomatik modun başka bir fingerprint sağlayıcısını ezmesini nasıl önlediğini açıklar.

[RKP Protection](docs/i18n/tr.md#rkp-protection), korunan Android altyapısını ve gerçek generated-key passthrough davranışını açıklar.

[DRM Passthrough and Privacy](docs/i18n/tr.md#drm-passthrough), iki ayrı kontrolü açıklar. Seçilen medya uygulamaları Android'in gerçek Keystore sertifika yolunda kalabilir; `privacy=isolate` kullanılan uygulamalarda ise desteklenen modern DRM HAL `deviceUniqueId` değeri uygulamaya özel kararlı bir takma kimlikle değiştirilebilir.

Bu DRM özelliği Widevine veya DRM bypass değildir. Raporlanan güvenlik seviyesini, lisansları, provisioning mesajlarını, içerik anahtarlarını, oturumları, HDCP durumunu veya string property değerlerini değiştirmez.

### Arayüz ve kullanım

[Web Interface](docs/i18n/tr.md#web-interface), native modül yöneticisi taşımasını, mobil navigasyonu, canlı durumu, doğrulamayı ve erişilebilirliği açıklar.

Yerleşik WebUI dilleri: **English**, **Türkçe**, **简体中文**, **Español**, **Deutsch**, **Русский**, **Bahasa Indonesia**, **हिन्दी** ve **العربية**. Çeviri katalogları yereldir; dil değiştirmek ağ bağlantısı gerektirmez.

[Backup and Restore](docs/i18n/tr.md#backup-restore), şifreli dışa aktarma, sınırlı içe aktarma ve güvenli kurtarmayı açıklar.

[Installer](docs/i18n/tr.md#installer), KernelSU ve APatch paket yapısını, payload doğrulamasını, desteklenen cihazları ve kurulum akışını açıklar.

[Diagnostics](docs/i18n/tr.md#diagnostics), logları, durum kontrollerini, sık hataları ve kontrollü sorun giderme sırasını açıklar.

### Mühendislik referansları

[Security Model](docs/i18n/tr.md#security-model), güven sınırlarını, korunan dosyaları, giriş doğrulamasını ve modülün iddia etmediği yetenekleri açıklar.

[Performance](docs/i18n/tr.md#performance), hook yaşam döngüsünü, sınırlı önbellekleri, arka plan işlerini, CPU ve bellek davranışını açıklar.

[Building](docs/i18n/tr.md#building), araç zincirini, doğrulama görevlerini ve üretilen artifact'leri açıklar.

[Native Architecture](docs/i18n/tr.md#native-architecture), Rust injector'ı, Rust native core'u, zorunlu dil politikasını ve dar Android C++ ABI sınırını açıklar.

## Hızlı başlangıç

1. Güncel release ZIP dosyasını resmi proje release sayfasından indirin.
2. Resmi build kaynağını doğrulamak gerekiyorsa yayımlanan `SHA256SUMS` kaydını ve GitHub build provenance bilgisini kontrol edin.
3. Android çalışırken KernelSU veya APatch'i açın.
4. ZIP dosyasını kurun ve yeniden başlatın.
5. Modül yöneticisinden CleveresTricky WebUI'yi açın.
6. Yeni kurulumlar Global Mode açık, isteğe bağlı kimlik spoofing kapalı başlar.
7. Yalnızca size ait veya test etme yetkiniz olan anahtar materyalini ekleyin.
8. Kimlik seçeneklerini yalnız gerektiğinde yapılandırın.
9. Şablon build identity değerlerini değiştirdikten sonra yeniden başlatın.

Projeyle birlikte kullanılabilir bir keybox veya özel attestation anahtarı dağıtılmaz.

## Desteklenen ortam

CleveresTricky Android 12 ile Android 17 arasını, API 31 ile 37 arasını destekler. Desteklenen işlemci hedefleri ARM64 ve x86 64'tür. Kurulum Android çalışırken KernelSU veya APatch üzerinden desteklenir.

Magisk ve recovery kurulumu desteklenmez. Installer, yarım bir modül bırakmak yerine desteklenmeyen yolları durdurur.

## Önemli sınırlar

Sonuçlar gerçek cihaz durumuna, firmware'e, sertifikasyona, anahtar materyaline, Google Play services durumuna ve uzak servis politikasına bağlıdır. CleveresTricky yerel uyumluluk yolunu iyileştirir ancak her cihaz için belirli bir uzak doğrulama sonucu garanti etmez.

Telephony değerleri yalnız desteklenen uygulama API'lerinde görünür. Modem, baseband, EFS, fiziksel SIM veya mobil operatörün gördüğü kimlik değiştirilmez.

Modern Android'de Android ID uygulama imza kimliği, kullanıcı ve cihaz kapsamında SettingsProvider tarafından yönetilir. CleveresTricky yanıltıcı bir global Android ID kontrolü sunmaz.

Gerçek kernel sürümü değişmez. Boot property görünümü bootloader'ı fiziksel olarak kilitlemez, verified boot'u onarmaz, vbmeta'yı yeniden yazmaz ve donanım root of trust değerini değiştirmez.

Bootloader kilidinin açık olması her DRM uygulamasının çalışmayacağı anlamına gelmez. Gerçek davranış cihaz, vendor uygulaması, provisioning durumu, güvenlik seviyesi, servis politikası ve firmware'e bağlıdır. Mevcut DRM çalışması bypass değil gizlilik odaklıdır.

Modül içi SHA 256 kayıtları eksik, değiştirilmiş, eklenmiş, linklenmiş veya beklenmeyen payload'ları tespit eder ve doğrulama başarısız olduğunda servisi tamper lockdown durumuna alır. Resmi release kaynağı ise ayrı release digest ve GitHub imzalı build provenance ile doğrulanır.

## Önerilen ilk yapılandırma

Önce yeni kurulum varsayılanlarını kullanın. Global Mode uygun uygulama UID'lerini seçerken temel boot ve Keystore koruması aktif kalır. İsteğe bağlı kimlik spoofing, Identity bölümünden açılana kadar kapalıdır.

Custom ROM kullanıyorsanız ve Play Integrity testi için güncel build identity gerekiyorsa Auto Identity, Google'ın herkese açık metadata kaynaklarından Pixel beta veya canary kimliği alıp yerel olarak kaydedebilir. Kaydedilen değerleri göstermek istediğinizde Identity Spoof Engine'i açın ve yeniden başlatın.

DRM identifier gizliliği için medya uygulamasına bir Application Rule oluşturun ve privacy mode değerini `isolate` yapın. DRM Keystore Passthrough açık kalabilir; gerçek Keystore sertifika yolu ile takma DRM cihaz kimliği birbirinden bağımsızdır.

## Yardım ve proje bilgileri

Sorun giderirken WebUI Logs ekranını veya `CleveresTricky` etiketli Android logcat'i kullanın. Ayrıntılı yönlendirme [Diagnostics](docs/i18n/tr.md#diagnostics) bölümündedir.

Proje geçmişi [CHANGELOG](docs/i18n/tr.md#changelog), katkı rehberi [Contributing](docs/i18n/tr.md#contributing), çeviri yapısı [Language Support](docs/i18n/tr.md#languages) ve tema bilgileri [Theme](docs/i18n/tr.md#theme) bölümlerinde yer alır.

Resmi [Android attestation dokümantasyonu](https://source.android.com/docs/security/features/keystore/attestation), [Play Integrity verdict dokümantasyonu](https://developer.android.com/google/play/integrity/verdicts) ve [KernelSU modül rehberi](https://kernelsu.org/guide/module.html) kendi platformları için yetkili kaynaklardır.

## Ayrıntılı isteğe bağlı kimlik kontrolleri

CleveresTricky, Device and Build Identity, Attestation Identity, Telephony Identity, Region Identity, Identity Refresh ve Security Patch davranışlarını birbirinden bağımsız çözümler. Security Patch, Device and Build Identity'den bağımsızdır.

Temel Keystore interception, gerçek KeyMint ve StrongBox private key işlemleri, root of trust işleme, Binder güvenliği ve gerekli boot uyumluluğu bu isteğe bağlı kontrollerden bağımsız kalır. Arayüz yakalanan cihaz durumunu, yapılandırılmış sunum durumunu ve uygulamanın gördüğü etkin durumu ayrı gösterir.

Security Patch görünümü System, Vendor ve Boot için bağımsız politikalar sunar. Profiller uygulamalara tutarlı isteğe bağlı ayarlar atayabilir ve Effective State inspector çalışma zamanında kullanılan resolver çıktısını gösterir.
