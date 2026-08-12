<div dir="rtl">

# توثيق CleveresTricky

**اللغة:** [English](../../README.md) | [Türkçe](tr.md) | [简体中文](zh-CN.md) | [Español](es.md) | [Deutsch](de.md) | [Русский](ru.md) | [Bahasa Indonesia](id.md) | [हिन्दी](hi.md) | **العربية**

[README العربية](../../README.ar.md)

> هذا مرجع مترجم لكل ملفات Markdown الموجهة للمستخدم. عند وجود اختلاف تقني تكون الوثائق الإنجليزية والكود المصدري المرجع الأساسي.

<a id="application-rules"></a>
## Application Rules

تسمح بتعيين template أو keybox محلي متحقق أو privacy policy لتطبيق مؤهل. القاعدة الصحيحة تعد target صريحا. `inherit` يحافظ على السياسة العامة، و`isolate` يشتق IMEI/IMSI/ICCID/MEID/phone/serial وattestation identifiers وDRM `deviceUniqueId` pseudonym مستقرة خاصة بالتطبيق، و`redact` يفرغ القيم المدعومة مع الحفاظ على أخطاء صلاحيات Android.

Attestation Identity يحتاج keybox فعالة ومتحققا منها. DRM isolation مستقل عن DRM Keystore Passthrough. Shared UID يحل بشكل حتمي عبر Package Manager ولا يتم الوثوق باسم package داخل الطلب. تنشر الحالة الجديدة atomically ويتم مسح الكاش المرتبط.

<a id="application-scope"></a>
## Application Scope

يحدد Android UID التي تحصل على certificate/identity compatibility. Targeted Mode يستخدم packages دقيقة أو bounded wildcard في `target.txt` ويحلها عبر Package Manager إلى caller الحقيقي. Shared UID تشترك في Binder identity.

Global Mode لا يحتاج target entry لكنه يستبعد system identity والبنية المحمية. Unknown package resolution يفشل بشكل مغلق. يتم استبدال rules وshort decision cache معا.

<a id="attestation"></a>
## Attestation

يوفر certificate-chain compatibility مضبوطا مع إبقاء key creation وprivate-key operations الحقيقية داخل Android KeyMint/StrongBox. يمكن لردود certificates القائمة استخدام verified replacement chain بينما تبقى RKP provisioning keys على genuine path بالكامل.

قبل التفعيل يتم فحص key/certificate match وalgorithm وchain وdates وambiguity وrevocation. Broken mixed pool يرفض بالكامل. لا تنشئ الميزة hardware root of trust ولا تصلح firmware/verified boot ولا تعيد قفل bootloader ولا تضمن remote verdict.

<a id="automatic-keybox-check"></a>
## Automatic Keybox Check

يحافظ على keybox/revocation محدثة دون continuous storage scan. File observer يتعامل مع التغييرات الطبيعية ويستخدم low-frequency fallback عند الحاجة.

كل refresh يعيد فحص key وchain وalgorithm وvalidity وambiguity وrevocation. إذا تعذر تحديد revocation فلا تفعل المواد الجديدة. الكاش محدود بعدد وحجم الملفات.

<a id="backup-restore"></a>
## Backup and Restore

ينقل config وauthorized key material داخل authenticated encrypted archive. Export يحتاج password من 12 حرفا على الأقل ويستخدم allowlist، ويرفض symlink وunknown path والحجم الزائد.

Import يقبل encrypted CTSB فقط ويضع حدودا للرفع وعدد entries/keyboxes والحجم expanded. Traversal وduplicates وdirectories وsymlink destination وsettings/keybox غير الصالحة ترفض قبل الكتابة. Policy v2 تتحقق وتنشر snapshot كاملة.

<a id="boot-properties"></a>
## Boot Properties

Core userspace property view يقلل كشف مؤشرات unlocked/debug/warranty/verified boot/recovery الشائعة. مجموعة properties ثابتة وتطبق قبل Zygote وتبقى فعالة بشكل مستقل عن optional identity.

`boot_props_mode` يتحكم فقط في Build Identity compatibility الاختياري (`auto`, `force`, `disable`) ولا يوقف core protection. لا يعيد قفل bootloader فعليا ولا يصلح verified boot ولا يغير TEE root of trust.

<a id="build-identity"></a>
## Build Identity

يطبق device template كامل على fingerprint وsupported app-visible Build fields. اختياري، يحتاج Spoof Engine وreboot. Arbitrary Android properties ترفض.

Auto Identity يمكنه جلب Pixel beta/canary من Google public metadata وحفظه دون تشغيل engine تلقائيا. Build Identity وSecurity Patch وRegion وTelephony وAttestation Identity مستقلة.

<a id="building"></a>
## Building

يتطلب Java 21 وSDK API 36 وNDK 27.3.13750724 وCMake 3.22.1 وstable Rust وARM64/x86-64 Android targets وCargo NDK وsubmodules. يجب نجاح Kotlin/Android checks وRust fmt/clippy/tests وunit tests.

CI يتحقق من shell وSELinux وtemplate وKotlin/Java/Rust والمعماريتين وrelease/debug ZIP وEncryptor. First-party C ممنوع و`binder_interceptor.cpp` هو استثناء C++ الوحيد. Release عبر `./gradlew zipRelease`.

<a id="certificate-safe-mode"></a>
## Certificate Safe Mode

مفهوم legacy. WebUI الحالية لا تقدم مفتاحا لإيقاف core Keystore/TEE compatibility. Scope يحدده Global Mode/Application Rules وSpoof Engine يخص الهوية فقط.

يمكن قراءة `tee_broken_mode` للمهاجرة لكنه لا يحدد core targeting. للتشخيص قلل scope أو استخدم passthrough أو أزل key material بشكل مضبوط.

<a id="diagnostics"></a>
## Diagnostics

افحص Dashboard للقيم version وEngine وprofile وkeybox count وtarget size وRKP وDRM وnative features ثم ابحث عن أول error في Logs. إذا لم تعمل WebUI افحص logcat وdaemon و`webroot` وarchitecture-specific `webui_bridge` وحالة manager.

للعزل استخدم Minimal + reboot، تحقق من genuine path ثم فعّل الميزات واحدة واحدة. Effective State يعرض rule/profile وscope وtemplate وkeybox ref وprivacy وfeatures وpatches وRKP/DRM وKeyMint/StrongBox وprovider coexistence وreboot requirement دون private keys.

<a id="drm-passthrough"></a>
## DRM Keystore Passthrough and Identifier Privacy

Passthrough يبقي تطبيقات الوسائط المحددة على genuine Android Keystore certificate path. Identifier Privacy يستبدل فقط supported stable-AIDL `deviceUniqueId` لتطبيق `privacy=isolate` باسم مستعار ثابت خاص بالتطبيق دون استخدام genuine DRM ID في الاشتقاق.

`drm_packages.txt` يدعم exact package وbounded wildcard. Hook محدود إلى `IDrmFactory` / `IDrmPlugin.getPropertyByteArray("deviceUniqueId")` ولا يغير HIDL أوsecurity level أوlicenses أوprovisioning أوkeys أوsessions أوHDCP أوstring properties. Unexpected ABI يحافظ على original response fail open.

<a id="encrypted-storage"></a>
## Encrypted Storage

CBOX يستخدم authenticated AES-256-GCM ويربط metadata بـ ciphertext. Password containers تستخدم bounded key derivation، ومفتاح local protected cache داخل private config.

Unlock يقبل فقط عبر native WebUI ولا يتجاوز keybox verification. يتم إعادة فحص key/certificate/chain/date/algorithm/revocation. Hostile root يمكنه قراءة البيانات بعد unlock.

<a id="identity-refresh"></a>
## Identity Refresh

يجهز validated identity للإقلاع التالي دون تغيير snapshot الحالية. Early boot يتحقق من staged file ثم يقوم atomic promotion كي تستخدم Build Properties والخدمة نفس الحالة.

IMEI/ICCID checksum والأطوال محدودة. Manual edit يحذف stage القديم؛ إيقاف Engine/Refresh قبل boot يمنع unwanted promotion.

<a id="installer"></a>
## Installer

يثبت full KernelSU/APatch module على Android 12-17 ARM64/x86 64. Magisk/recovery يتوقفان قبل partial install.

كل payload لديه SHA 256 وruntime يرفض symlink/non-regular/unexpected files. Internal hash ليس دليلا على الناشر، لذلك release الرسمي ينشر `SHA256SUMS` وGitHub signed build provenance.

<a id="keybox-manager"></a>
## Keybox Manager

يحمل ويتحقق ويختار ويراقب authorized attestation material بصيغ legacy/XML/CBOX. يمكن لـ Application Rule اختيار file محدد، وremote material يبقى untrusted حتى التحقق المحلي.

يجب أن يطابق private key الـ leaf certificate ويتم فحص algorithm وchain وdate وduplicate/ambiguity وrevocation. Unknown revocation لا يفعل المواد الجديدة، وbroken pool يرفض بالكامل.

<a id="native-architecture"></a>
## Native Architecture

Portable native logic في Rust. لا يوجد first-party C؛ `binder_interceptor.cpp` هو استثناء C++ الوحيد بسبب private Android libbinder object ABI. Rust Core يتحقق من Binder layouts/streams وFD وkernel-validated copies.

Rust Injector يدير files وSELinux socket وFD transfer وmaps/symbols وptrace وregisters وremote memory وloader وcleanup. Temporary stack writes تستعاد من bounded journal. لا يجب توسيع استثناء C++.

<a id="patch-levels"></a>
## Patch Levels

`security_patch.txt` يوفر System/Vendor/Boot global/per-app rules. يدعم dates و`today` و`device_default` و`prop` و`no`؛ policy v2 تدعم Device وProperty وManual وAutomatic وOmit بشكل مستقل.

Parsing محدود وinvalid input لا يطبق partial state. Automatic يستخدم calendar arithmetic. الميزة لا تثبت security update حقيقية ولا تغير firmware ولا تضمن remote verdict.

<a id="performance"></a>
## Performance and Memory

Core Keystore interception يبقى فعالا؛ إيقاف Spoof Engine يوقف optional identity/DRM/build/region/telephony work. Automatic Keybox Check له control مستقل.

Binder parser يستخدم fixed arrays وdescriptor cache من 64 slot. Controller/cache محدودة وتتجنب busy poll. Rust release يستخدم LTO وsize optimization وhardened linking.

<a id="profiles"></a>
## Profiles

تطبق optional settings في validated transaction واحدة. Daily/Default محافظان، Maximum يوسع scope للاختبار، Minimal يوقف معظم optional identity/scheduled keybox مع إبقاء core Keystore/TEE/boot.

Profile v2 يخزن assignments وtemplate/keybox refs وprivacy وindependent patches وfeature overrides وRKP/DRM دون private key. لا تنشر snapshot إلا بعد full validation مع حفظ last-known-good.

<a id="provider-coexistence"></a>
## Provider Coexistence

Automatic Build Identity يكتشف fingerprint/property providers أخرى مثل PIF و`autopif`/`auto_pif` وPlayCurl ولا يكتب فوقها.

عند conflict تبقى optional Build properties كما هي ويمكن لبقية الميزات العمل. Force يتجاوز detection عمدا؛ Automatic موصى به.

<a id="region-properties"></a>
## Region Properties

يوفر optional fixed China-region view عبر hardware/SIM/operator country وhardware level وradio marker. Arbitrary properties غير مقبولة.

يطبق قبل Zygote مع Spoof Engine. لا يغير real SIM country أوradio registration أوmodem firmware أوsecure sales region أوcarrier account.

<a id="remote-sources"></a>
## Remote Sources

يجلب authorized keybox من HTTPS صريح فقط. Host/port/path/timeout/refresh/auth/header/size محدودة، وsecrets لا تظهر في status.

يمكن فرض signature. لا تفعل البيانات قبل signature وXML/CBOX وsize وkeybox وcertificate وrevocation validation. Failed refresh لا يستبدل verified material.

<a id="rkp-protection"></a>
## RKP Protection

يبقي Android provisioning وgenerated-key response على genuine path. `rkpdapp` وlegacy remote provisioner وsystem UID محمية، وunknown package fail closed.

RKP Passthrough يبقي generated provisioning key دون تغيير من KeyMint إلى caller. الموديول لا يحاكي RKP server ولا يصنع credentials.

<a id="security-model"></a>
## Security Model

Root service وOS وKernelSU/APatch وmodule files وauthorized key material trusted. Apps وBinder وuploads وremote responses وconfig وarchives وrules وtemplates وpaths وnetwork metadata untrusted.

Config root-owned وsensitive root-only وsymlink مرفوض وwrites atomic. Binder ABI وkernel-validated copies تتحقق. Injector يقيد symbol/process/library وWebUI لا يفتح TCP ويستخدم strict native bridge. Hostile root خارج دفاع كامل.

<a id="spoof-engine"></a>
## Spoof Engine

Optional app-facing identity controller. Core Keystore/TEE وcertificate compatibility وroot of trust وboot protection تبقى حتى عند إيقافه.

عند التشغيل تعمل optional Attestation/Telephony/Build/Region/Refresh حسب controls الخاصة بها. الإيقاف لا يحذف saved values. App cache قد يحتاج restart وBuild Identity يحتاج reboot.

<a id="telephony-identity"></a>
## Telephony Identity

يمكنه عرض IMEI وMEID وIMSI وICCID وphone لشريحتي SIM عبر supported Binder APIs. Checksum وlength وsyntax وslot وinput size تتحقق.

يتم أخذ genuine Android response أولا؛ permission denial/error/null تحفظ. لا يتغير modem أوbaseband أوEFS أوphysical SIM أوcarrier identity.

<a id="web-interface"></a>
## Web Interface

Fixed ownership: `index.html` markup/base CSS، `bridge.js` native bridge/intents، `policy.js` policy/state UI، `ux.js` presentation/localization/guide/community. لا standalone runtime CSS ولا feature-specific JS bundles.

Mobile bottom navigation وtouch controls وresponsive panels وpassword visibility وprogress وaccessibility. لا TCP listener؛ native manager API وbounded Rust bridge وroot-only queues وstrict validation.

<a id="changelog"></a>
## CHANGELOG

V2.5.3 أضاف granular identity/security patch controls وprofiles وEffective State؛ عزز Attestation/KeyMint/StrongBox/DRM privacy/upgrades/Android 17؛ وحد WebUI وtranslations؛ أضاف KeyboxHub external-browser helper؛ وحسن diagnostics وcache/timing وdependency security وregression وartifact validation.

<a id="contributing"></a>
## Contributing

يجب الحفاظ على fail-closed model وAndroid 12-17 وKernelSU/APatch وعدم تقديم claims غير قابلة للتحقق عن hardware integrity. شغل Kotlin/Android/Rust checks؛ portable native additions في Rust، first-party C ممنوع و`binder_interceptor.cpp` استثناء C++ الوحيد.

Binder/XML/ZIP/CBOX/HTTP/path/PID untrusted وتحتاج bounds/failure tests. لا ترفع private keys/keyboxes/tokens/secrets/generated APK/ZIP. حدث docs عند تغيير user-visible behavior.

<a id="donate"></a>
## Development Support

يمكن دعم المشروع عبر الخيارات في `DONATE.md`: USDT TRC20 وXMR وUSDT/USDC ERC20/BEP20 وBinance User ID وPayPal وBuyMeACoffee وموقع المطور. تحقق من العناوين الحالية في الملف الإنجليزي قبل إرسال الأموال.

<a id="languages"></a>
## Language Support

WebUI يتضمن English وTürkçe و简体中文 وEspañol وDeutsch وРусский وBahasa Indonesia وहिन्दी والعربية. Runtime catalogs تبقى فقط في `ux.js` ولا يتم إنشاء locale-specific JS/CSS. User docs توفر README وهذا المرجع بنفس اللغات التسع.

أي تغيير user-facing Markdown يجب أن يحدث English canonical والأقسام المترجمة المرتبطة.

<a id="logging"></a>
## Logging and Diagnostics

Diagnostics تكتب إلى Android logcat ولا يوجد plaintext log مستقل. الأمر: `adb logcat -s cleverestricky CleveresTricky`. علامات service/bridge/Binder/TEE مفيدة لبدء التشغيل.

`TAMPER DETECTED` وBinder ABI failure وrejected keybox وinjector timeout تحتاج فحصا. راجع filenames/packages/properties/PIDs الحساسة قبل نشر log.

<a id="theme"></a>
## UI Theme

تصميم minimal monochrome hybrid بين Nothing OS وiOS: خلفية charcoal ونص light gray وaccent فضي وpanels داكنة وsuccess أخضر وdanger أحمر. System sans وmonospace للبيانات التقنية وDynamic Island وأزرار rounded وiOS toggles وmobile-first layout.

Touch targets تقريبا 44px أو أكثر، vertical flow مفضل والواجهة محسنة للاستخدام على الهاتف داخل KernelSU/APatch.

</div>
