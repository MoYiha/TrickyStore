# Magisk सपोर्ट

**भाषा:** [English](../../../system/Magisk.md) | [Türkçe](../../tr/system/Magisk.md) | [简体中文](../../zh-CN/system/Magisk.md) | [Español](../../es/system/Magisk.md) | [Deutsch](../../de/system/Magisk.md) | [Русский](../../ru/system/Magisk.md) | [Bahasa Indonesia](../../id/system/Magisk.md) | **हिन्दी** | [العربية](../../ar/system/Magisk.md)

## समर्थन घोषणा

CleveresTricky तीनों रूट वातावरणों को आधिकारिक रूप से समर्थन करता है, और प्रत्येक में सामान्य WebUI उपलब्ध है:

* **[KernelSU](https://kernelsu.org)** - प्रबंधक ऐप के मॉड्यूल बटन से WebUI खुलता है।
* **[APatch](https://apatch.dev)** - प्रबंधक ऐप के मॉड्यूल बटन से WebUI खुलता है।
* **Magisk** - WebUI होस्ट ऐप के माध्यम से मॉड्यूल **Action** बटन से WebUI खुलता है (नीचे देखें)।

> [!NOTE]
> आधुनिक डिटेक्शन फ्रेमवर्क और Google Play Integrity यूज़रस्पेस माउंट और रूट बाइनरी की सक्रिय जांच करते हैं, इसलिए कर्नेल-स्तरीय समाधान लंबे समय तक मज़बूत छिपाव दे सकते हैं। आर्किटेक्चर तुलना:
> 👉 **[Advanced Android Root Guide: KernelSU, APatch & Concealment | Yiğit - tryigit.dev](https://tryigit.dev/advanced-android-root-architecture-concealment/)**

---

## Magisk पर WebUI

Magisk स्वयं मॉड्यूल WebUI इंटरफ़ेस लागू नहीं करता, इसलिए Magisk पर Action बटन CleveresTricky WebUI को **[स्टैंडअलोन WebUI होस्ट ऐप](https://github.com/adivenxnataly/KsuWebUI)** के अंदर खोलता है:

1. Magisk ऐप में CleveresTricky मॉड्यूल खोलें और **Action** पर टैप करें।
2. पहली बार उपयोग पर, लॉन्चर WebUI होस्ट APK को उसके GitHub releases से डाउनलोड करके इंस्टॉल करता है। डाउनलोड केवल एक बार होता है।
3. फिर लॉन्चर होस्ट को मॉड्यूल id `cleverestricky` के साथ खोलता है, जो `/data/adb/modules/cleverestricky/webroot` (`index.html` तथा मौजूदा `bridge.js`, `policy.js` और `ux.js`) पर resolve होता है।

शब्दावली, क्योंकि नाम भ्रमित करते हैं:

* **WebUI होस्ट ऐप** एक स्टैंडअलोन WebUI होस्ट/कंटेनर ऐप है। यह KernelSU **नहीं** है और कोई रूट प्रबंधक **इंस्टॉल नहीं** करता; डिवाइस पर एकमात्र रूट समाधान Magisk ही रहता है।
* **CleveresTricky WebUI**, `/data/adb/modules/cleverestricky/webroot` में मॉड्यूल का अपना इंटरफ़ेस है।
* **रूट बैकएंड** CleveresTricky का अपना नेटिव/Rust रनटाइम है (`cleverestrickyd`, बैकएंड, `webui_bridge`), जो तीनों वातावरणों में अपरिवर्तित है। होस्ट ऐप केवल रूट शेल एक्सेस वाला WebView कंटेनर है; मौजूदा `bridge.js` से `webui_bridge` संचार पथ अपरिवर्तित है।

---

## मैन्युअल कॉन्फ़िगरेशन

फ़ाइलें हाथ से संपादित करना पसंद करते हैं या WebUI उपलब्ध नहीं है? स्वतंत्र [मैन्युअल कॉन्फ़िगरेशन](ManualConfiguration.md) गाइड देखें। तैयार रिपोर्ट संग्रह:
```sh
su -c "/data/adb/modules/cleverestricky/emergency-report.sh"
```
तैयार रिपोर्ट `/data/adb/cleverestricky/bugreports/` में सहेजी जाएगी।
