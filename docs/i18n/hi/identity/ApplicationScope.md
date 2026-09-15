# Application Scope

**भाषा:** [English](../../../identity/ApplicationScope.md) | [Türkçe](../../tr/identity/ApplicationScope.md) | [简体中文](../../zh-CN/identity/ApplicationScope.md) | [Español](../../es/identity/ApplicationScope.md) | [Deutsch](../../de/identity/ApplicationScope.md) | [Русский](../../ru/identity/ApplicationScope.md) | [Bahasa Indonesia](../../id/identity/ApplicationScope.md) | **हिन्दी** | [العربية](../../ar/identity/ApplicationScope.md)

तय करता है कि कौन से Android app UID प्रमाणपत्र/Keybox या पहचान (Identity) अनुकूलता पाएंगे। मॉड्यूल दो अलग लक्ष्य फ़ाइलें और दो ग्लोबल मोड प्रदान करता है:

- **Keybox लक्ष्य (`target.txt`)**: ग्लोबल Keybox मोड बंद होने पर कस्टम Keybox और TEE अटेस्टेशन प्राप्त करने वाले पैकेज निर्दिष्ट करता है।
- **Identity लक्ष्य (`identity_target.txt`)**: ग्लोबल Identity मोड बंद होने पर प्रति-ऐप पहचान गुण (Build, Telephony, Region) पाने वाले पैकेज निर्दिष्ट करता है।
- **ग्लोबल Keybox मोड**: बिना `target.txt` के सभी उपयोगकर्ता ऐप्स पर कस्टम Keybox लागू करता है। सिस्टम और इंफ्रास्ट्रक्चर UID सुरक्षित रहते हैं।
- **ग्लोबल Identity मोड**: पूरे डिवाइस में सिस्टम स्तर पर Build गुण लागू करता है। बंद होने पर यह केवल `identity_target.txt` और निर्दिष्ट प्रोफाइल पर असर डालता है।
- **स्वतंत्र सुरक्षा पैच मॉड्यूल**: सुरक्षा पैच को डैशबोर्ड से पहचान इंजन से स्वतंत्र रूप से प्रबंधित किया जा सकता है।

Shared UID वाले पैकेज Binder पहचान साझा करते हैं। अमान्य अपडेट fail-closed होते हैं।
