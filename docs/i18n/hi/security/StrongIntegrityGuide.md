# Strong Integrity Guide

**भाषा:** [English](../../../security/StrongIntegrityGuide.md) | [Türkçe](../../tr/security/StrongIntegrityGuide.md) | [简体中文](../../zh-CN/security/StrongIntegrityGuide.md) | [Español](../../es/security/StrongIntegrityGuide.md) | [Deutsch](../../de/security/StrongIntegrityGuide.md) | [Русский](../../ru/security/StrongIntegrityGuide.md) | [Bahasa Indonesia](../../id/security/StrongIntegrityGuide.md) | **हिन्दी** | [العربية](../../ar/security/StrongIntegrityGuide.md)

CleveresTricky के साथ ROM प्रकार के अनुसार Google Play Integrity (`MEETS_STRONG_INTEGRITY`) पास करने की त्वरित गाइड:

- **Official Stock ROM**: CleveresTricky इंस्टॉल करें और एक मान्य Keybox जोड़ें। आमतौर पर केवल यही आवश्यक होता है।
- **बहुत पुराने सुरक्षा पैच वाला Official ROM**: WebUI Dashboard में `Security Patch` सक्षम करें और इसे `Auto` पर सेट करें।
- **AOSP ROM**: WebUI Dashboard में `Identity` सक्षम करें और फ़िंगरप्रिंट स्पूफ़ करें (Auto Pixel Identity या प्रमाणित डिवाइस टेम्पलेट का उपयोग कर सकते हैं)। *(नोट: डायनामिक प्रॉपर्टी मूल्यांकन के कारण Identity और स्वचालित स्पूफिंग से RAM उपयोग थोड़ा बढ़ सकता है)।*
- **Custom ROM**: Custom ROMs आधिकारिक तौर पर समर्थित नहीं हैं। यदि आपका Keystore टूटा हुआ है या मूल हार्डवेयर सत्यापन काम नहीं करता है, तो पुरानी विधि का उपयोग करें।

**Keybox टूल्स और आयात:**
- ऑनलाइन Keybox चेकर: https://keybox.tryigit.dev/checker
- Keybox डाउनलोड और जानकारी: https://keybox.tryigit.dev/
- आयात करने का तरीका: CleveresTricky WebUI खोलें → Keybox Manager में जाकर फ़ाइल अपलोड करें। पुरानी TrickyStore निर्देशिका में मैन्युअल कॉपी करने की आवश्यकता नहीं है।

**कानूनी अस्वीकरण और समुदाय सूचना:** CleveresTricky समुदाय द्वारा समर्थित एक स्वतंत्र ओपन-सोर्स प्रोजेक्ट है और यह Google LLC से संबद्ध नहीं है। Google Play Integrity नीतियां और सर्वर-साइड डिटेक्शन बिना किसी पूर्व सूचना के कभी भी बदल सकते हैं; स्थायी परिणाम की कोई गारंटी नहीं दी जा सकती। केवल अधिकृत कुंजियों का उपयोग करें।
