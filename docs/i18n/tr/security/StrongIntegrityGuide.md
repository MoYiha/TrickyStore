# Strong Integrity Rehberi

**Dil:** [English](../../../security/StrongIntegrityGuide.md) | **Türkçe** | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | [Español](../../es/security/StrongIntegrityGuide.md) | [Deutsch](../../de/security/StrongIntegrityGuide.md) | [Русский](../../ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | [हिन्दी](../../hi/security/StrongIntegrityGuide.md) | [العربية](../../ar/security/StrongIntegrityGuide.md)

CleveresTricky ile Google Play Integrity (`MEETS_STRONG_INTEGRITY`) geçişi için ROM tipine göre hızlı adımlar:

- **Orijinal (Stock) ROM**: CleveresTricky'yi kurun ve geçerli bir Keybox ekleyin. Genellikle başka hiçbir ayara gerek yoktur.
- **Eski Güvenlik Yamalı Orijinal ROM**: WebUI Dashboard'dan `Security Patch` özelliğini açın ve `Auto` moduna ayarlayın.
- **AOSP ROM**: WebUI Dashboard'dan `Identity` (Spoof Engine) özelliğini açın, parmak izinizi spoof edin (Auto Pixel Identity veya sertifikalı bir model şablonu seçebilirsiniz). *(Not: Identity ve otomatik spoofing dinamik property eşlemesi nedeniyle RAM kullanımını bir miktar artırabilir.)*
- **Custom ROM**: Custom ROM'lar resmi olarak desteklenmez. Keystore'unuz bozuksa veya native donanım attestation çalışmıyorsa eski yöntemleri inceleyin.

**Keybox Araçları ve İçe Aktarma:**
- Keybox Doğrulayıcı (Online Checker): https://keybox.tryigit.dev/checker
- Keybox İndirme ve Bilgi: https://keybox.tryigit.dev/
- İçe Aktarma: CleveresTricky WebUI → Keybox Manager üzerinden yükleyin. Dosyayı manuel olarak eski TrickyStore dizinlerine kopyalamanıza gerek yoktur; CleveresTricky güvenli ve yalıtılmış şekilde depolar.

**Yasal Uyarı ve Topluluk Bildirimi:** CleveresTricky bağımsız bir açık kaynak topluluk projesidir, Google LLC ile bağlantısı yoktur. Google Play Integrity kuralları ve tespit yöntemleri Google tarafından habersiz olarak her an değiştirilebilir; bu nedenle kalıcı veya garantili geçiş taahhüt edilemez. Sadece test etmeye yetkili olduğunuz kendi anahtarlarınızı kullanın.
