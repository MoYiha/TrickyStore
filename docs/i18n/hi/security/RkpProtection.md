# RKP Protection

**भाषा:** [English](../../../security/RkpProtection.md) | [Türkçe](../../tr/security/RkpProtection.md) | [简体中文](../../zh-CN/security/RkpProtection.md) | [Español](../../es/security/RkpProtection.md) | [Deutsch](../../de/security/RkpProtection.md) | [Русский](../../ru/security/RkpProtection.md) | [Bahasa Indonesia](../../id/security/RkpProtection.md) | **हिन्दी** | [العربية](../../ar/security/RkpProtection.md)

Remote Key Provisioning protection Android provisioning infrastructure को genuine platform path पर रखती है। Android/Google RKP और legacy Remote Provisioner packages substitution scope से बाहर रहते हैं; system UID और unknown package resolution fail closed रहते हैं।

RKP infrastructure callers कभी modify नहीं किए जाते। Target app UID के लिए `generateKey` और बाद के `getKeyEntry` certificate responses unified compatibility path का उपयोग करते हैं, जिससे एक alias दो अलग attestation leaf नहीं दिखाता।

पुराना `rkp_passthrough` switch retired है। Marker पुराने config/backup में रह सकता है, लेकिन अब generated-key behavior को control नहीं करता और WebUI runtime toggle के रूप में expose नहीं होता। Built-in Profiles RKP behavior नहीं बदलते; infrastructure protection हमेशा active है।

CleveresTricky RKP server simulate नहीं करता, provisioning credentials नहीं बनाता और hardware provisioning root नहीं बदलता।
