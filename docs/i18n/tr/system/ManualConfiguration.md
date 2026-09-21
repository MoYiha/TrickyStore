# Manuel Yapılandırma

**Dil:** [English](../../../system/ManualConfiguration.md) | **Türkçe** | [简体中文](../../zh-CN/system/ManualConfiguration.md) | [Español](../../es/system/ManualConfiguration.md) | [Deutsch](../../de/system/ManualConfiguration.md) | [Русский](../../ru/system/ManualConfiguration.md) | [Bahasa Indonesia](../../id/system/ManualConfiguration.md) | [हिन्दी](../../hi/system/ManualConfiguration.md) | [العربية](../../ar/system/ManualConfiguration.md)

Bu kılavuz, WebUI kullanılmadan dosyalar üzerinden manuel yapılandırma anlatır. Ayarları değiştirmenin normal yolu WebUI'dir; dosyaları elle düzenlemeyi tercih ediyorsanız ya da WebUI'ye ulaşamıyorsanız bu kılavuzu kullanın. Tüm modül ayar ve kural dosyaları `/data/adb/cleverestricky/` klasöründe yer alır.

---

## 1. Kapsam ve Hedef Belirleme

* **`target.txt`**: Keystore attestation taklidi uygulanacak paket isimleri (her satıra bir paket):
  ```text
  com.google.android.gms
  com.google.android.gms.unstable
  com.android.vending
  ```
* **`global_mode`**: Boş işaretçi dosyası.
  * **Mevcutsa**: Genel Mod (Global Mode) devrededir; sistem ve root dışındaki tüm uygulamalar kancalanır.
  * **Mevcut değilse**: Yalnızca `target.txt` içindeki paketler taklit edilir.
  * *Komut:* `touch /data/adb/cleverestricky/global_mode` (aç) veya `rm -f /data/adb/cleverestricky/global_mode` (kapat).

* **`identity_target.txt`**: Cihaz kimliği ve build özellikleri taklit edilecek hedef paketler.
* **`global_identity_mode`**: Boş işaretçi dosyası. Varsa kimlik taklidi tüm sistem dışı uygulamalara uygulanır.

---

## 2. Cihaz Kimliği ve Model Taklidi

* **`spoof_build_vars`**: Taklit edilecek cihaz özellikleri `ANAHTAR=DEĞER` formatında girilir:
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
* **`security_patch.txt`**: Güvenlik yaması tarihi (örn. `2026-03-05`). Otomatik sistem eşleşmesi için boş bırakın veya dosyayı kaldırın.
* **`boot_props_mode`**: Bootloader özellik simülasyonunu yönetir (`auto`, `force` veya `disable`).

---

## 3. Donanım Keybox (Attestation Anahtarı)

* **`keybox.xml`**: Geçerli donanım sertifikasyon anahtar kutunuzu doğrudan `/data/adb/cleverestricky/keybox.xml` yoluna yerleştirin. İzinlerin kısıtlı olduğundan emin olun (`chmod 600`).
* **`keyboxes/`**: Birden fazla keybox dosyası saklamak için kullanılan dizin.
* **Yükleme üzerine yazmaz:** Bırakılan veya yapıştırılan keybox verilen adla kaydedilir, ad verilmemişse `keybox.xml` olarak; bu ad zaten kullanımdaysa sıradaki boş ad (`keybox2.xml`, `keybox3.xml`, ...) otomatik olarak kullanılır.
* **`disabled_keyboxes`**: Havuzdan çıkarma listesi. Her satır bir `kapsam:dosyaadı` tanımlayıcısı içerir (`keyboxes:keybox2.xml`, `root:keybox.xml`) ve WebUI Keybox panelinde gösterilen dosya adlarıyla eşleşir. Listelenen keybox'lar görünür ve yönetilebilir kalır ancak sertifikasyon havuzuna hiçbir zaman yüklenmez. WebUI'deki Devre dışı bırak ve Etkinleştir düğmeleri bu dosyayı okur ve yazar; dosyayı elle de düzenleyebilirsiniz.

---

## 4. DRM ve Gizlilik Kapsamı

* **`drm_packages.txt`**: Widevine L1 donanım korumasını kaybetmemek adına Keystore kancasından muaf tutulacak akış uygulamaları:
  ```text
  com.netflix.mediaclient
  com.amazon.avod.thirdpartyclient
  com.disney.disneyplus
  ```

---

## 5. Özellik Bayrakları (İşaretçi Dosyaları)

Bir özelliği açmak için dosyasını oluşturun (`touch <dosya>`), kapatmak için silin (`rm -f <dosya>`):

| İşaretçi Dosyası | Dosya Mevcut Olduğunda Görevi |
| :--- | :--- |
| `spoof_enabled` | Kimlik taklit motorunu (Spoof Engine) devreye sokar. |
| `spoof_build_identity` | Build özellikleri taklidini etkinleştirir. |
| `auto_keybox_check` | Keybox geçerliliğini ve iptal durumunu periyodik doğrular. |
| `drm_passthrough` | `drm_packages.txt` listesindeki paketlere DRM muafiyeti uygular. |
| `hide_sensitive_props` | Root ve bootloader durum göstergesi özelliklerini gizler. |
| `tee_broken_mode` | Eski geçiş/uyumluluk durumu. Dosya mevcutsa servis eski geçiş işlemlerini korur; çekirdek koruma değişmez ve donanım TEE iletişim hatasında devre kesici ayrıca devreye girer. |
| `debug_logging` | `native_runtime.log` dosyasına ayrıntılı hata ayıklama günlüğü yazar. |

---

## Değişiklikleri Uygulama ve Doğrulama

### Ayarların Geçerli Olması
WebUI'de yapılan değişiklikler normal çalışma zamanı yoluyla uygulanır. Yapılandırma veya bayrak dosyalarını elle düzenlediyseniz sonrasında cihazı yeniden başlatmanız önerilir:
```sh
su -c "reboot"
```

### Günlükleri İnceleme
Servisin sorunsuz çalıştığını kontrol etmek için:
```sh
su -c "cat /data/adb/cleverestricky/native_runtime.log"
```

### Çalışan Süreçleri Doğrulama
```sh
su -c "ps -A | grep -E 'cleverestrickyd|cleverestricky_backend'"
```

### Hata Raporu (Bugreport) Oluşturma
Tam tanı ve günlük paketini çıkarmak için (Magisk'te Action düğmesi WebUI'yi açtığından bu dosyayı doğrudan çalıştırın):
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
Oluşturulan arşiv `/data/adb/cleverestricky/bugreports/` dizinine kaydedilir.
