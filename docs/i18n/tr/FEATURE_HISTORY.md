# Özellik Geçmişi ve Önceki Çalışmalar

Bu belge CleveresTricky'deki önemli özelliklerin ne zaman herkese açık olarak geliştirildiğini belgelemek için tutulur. Amaç tarihsel kayıt ve atıf sağlamaktır; başka bir projenin kaynak kodunu kopyaladığı iddiası değildir.

## Cihaz kimliği ve attestation

- **#79 — Uygulamaya özel yapılandırma ve `ATTESTATION_ID_*` işlemleri (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79
  IMEI ve Serial gibi alanlar dahil uygulama kapsamlı attestation kimliği işlemleri.

- **#139 — Rastgele cihaz kimliği (2026-02-05)**
  https://github.com/tryigit/CleveresTricky/pull/139
  IMEI ve Serial dahil rastgele kimlik üretimi ve WebUI üzerinden üretim.

- **#871 — Uygulama kapsamlı dual-SIM/cihaz kimliği kontrolleri (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/871
  IMEI, IMEI2, MEID, IMSI, ICCID, telefon numarası ve Serial kapsamını; uygulama/profil kapsamını ve runtime lifecycle kontrollerini genişletti.

## Keybox / attestation altyapısı

- **#77 — Çoklu keybox yönetimi ve rotasyonu (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/77
  Çoklu keybox yükleme, rotasyon ve WebUI yönetimi.

- **#79 — Keybox doğrulama ve attestation kimliği çalışmaları (2026-02-01)**
  https://github.com/tryigit/CleveresTricky/pull/79

## Native / Rust mimarisi

- **#876 — Rust/native interceptor mimarisi ve lifecycle çalışmaları (2026-08-09)**
  https://github.com/tryigit/CleveresTricky/pull/876
  Injector/interception altyapısının ve runtime lifecycle mekanizmalarının büyük Rust/native dönüşümü.

## Diğer modül özellikleri

Proje ayrıca profil/template yönetimi, uygulama kapsamlandırması, runtime hook lifecycle kontrolleri, identity isolation/redaction, RKP/DRM ile ilgili işlemler, WebUI yönetimi ve StrongBox/attestation entegrasyonu gibi yetenekleri de zaman içinde geliştirdi.

- **#376** — profil/yapılandırma ve modül gelişimi
  https://github.com/tryigit/CleveresTricky/pull/376
- **#476** — identity/modül gelişimi
  https://github.com/tryigit/CleveresTricky/pull/476
- **#618** — modül/profil gelişimi
  https://github.com/tryigit/CleveresTricky/pull/618
- **#908** — sonraki identity/modül çalışmaları
  https://github.com/tryigit/CleveresTricky/pull/908
- **#909** — sonraki modül çalışmaları
  https://github.com/tryigit/CleveresTricky/pull/909
- **#910** — sonraki modül çalışmaları
  https://github.com/tryigit/CleveresTricky/pull/910
- **#952** — sonraki modül/profil çalışmaları
  https://github.com/tryigit/CleveresTricky/pull/952
- **#1132 — StrongBox → TEE yönlendirmesi ve attestation security-level uyumlulaştırması**
  https://github.com/tryigit/CleveresTricky/pull/1132
  Bu değişiklik daha sonra geri alındı; mevcut `master` bu değişikliği içermemektedir.

## Tarihsel not

Yukarıdaki bağlantılar projenin kendi GitHub geliştirme geçmişine doğrudan bağlantılardır. Tarihler ve ayrıntılar ilgili PR ve commit kayıtlarından doğrulanabilir. Projeler arasında benzer işlevlerin bulunması tek başına kaynak kodu kopyalandığını veya lisans ihlali olduğunu göstermez.
