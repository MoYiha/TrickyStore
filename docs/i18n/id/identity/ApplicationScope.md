# Application Scope

**Bahasa:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | [简体中文](../../zh-CN/identity/ApplicationScope.md) | [Español](../../es/identity/ApplicationScope.md) | [Deutsch](../../de/identity/ApplicationScope.md) | [Русский](../../ru/identity/ApplicationScope.md) | **Bahasa Indonesia** | [हिन्दी](../../hi/identity/ApplicationScope.md) | [العربية](../../ar/identity/ApplicationScope.md)

Menentukan UID aplikasi mana yang menerima kompatibilitas sertifikat/Keybox atau identitas. Modul menyediakan dua berkas target dan dua mode global terpisah:

- **Target Keybox (`target.txt`)**: Menentukan paket yang menerima Keybox kustom dan atestasi TEE saat Mode Keybox Global dinonaktifkan.
- **Target Identitas (`identity_target.txt`)**: Menentukan paket untuk properti identitas per aplikasi (Build, Telephony, Region) saat Mode Identitas Global dinonaktifkan.
- **Mode Keybox Global**: Menerapkan Keybox kustom ke semua aplikasi pengguna tanpa memerlukan `target.txt`. UID sistem dan infrastruktur tetap terlindungi.
- **Mode Identitas Global**: Menerapkan properti Build di seluruh sistem. Saat nonaktif, identitas hanya memengaruhi `identity_target.txt` dan profil yang ditetapkan.
- **Modul Patch Keamanan Independen**: Patch keamanan dapat diatur secara mandiri di Dasbor tanpa mengaktifkan mesin identitas penuh.

Paket dengan shared UID berbagi identitas Binder. Pembaruan yang tidak valid ditolak secara fail-closed.
