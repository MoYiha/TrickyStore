from pathlib import Path

web = Path('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt')
s = web.read_text()
old = '''                    if (saveFile(filename, content)) {
                        return secureResponse(Response.Status.OK, "text/plain", "Saved")
                    }
'''
new = '''                    if (saveFile(filename, content)) {
                        if (filename == "templates.json") {
                            DeviceTemplateManager.initialize(configDir)
                        }
                        return secureResponse(Response.Status.OK, "text/plain", "Saved")
                    }
'''
if old not in s:
    raise SystemExit('WebServer save marker missing')
web.write_text(s.replace(old, new, 1))

policy = Path('module/template/webroot/policy.js')
p = policy.read_text()
insert_before = 'function installAppsProfileCard() {'
feature = r'''
const BUILT_IN_TEMPLATE_IDS = new Set(['pixel8pro','pixel8','pixel7pro','pixel6pro','s24ultra','s23ultra','xiaomi14','oneplus11','nothing2']);
const CUSTOM_TEMPLATE_FIELDS = [
  ['id','Template ID'],['manufacturer','Manufacturer'],['model','Model'],['fingerprint','Fingerprint'],
  ['brand','Brand'],['product','Product'],['device','Device'],['release','Android release'],
  ['buildId','Build ID'],['incremental','Incremental'],['type','Build type'],['tags','Build tags'],
  ['securityPatch','Security patch']
];

function installCustomTemplateBuilder() {
  const spoof = document.getElementById('spoof');
  if (!spoof || document.getElementById('ct_custom_template_panel')) return;
  const identityPanel = [...spoof.querySelectorAll('.panel')].find(item => /^Identity Manager$/i.test((item.querySelector('h3')?.textContent || '').trim()));
  if (!identityPanel) return;
  const panel = document.createElement('div');
  panel.id = 'ct_custom_template_panel';
  panel.className = 'panel';
  const fields = CUSTOM_TEMPLATE_FIELDS.map(([key,label]) => {
    const value = key === 'type' ? 'user' : (key === 'tags' ? 'release-keys' : '');
    return `<div><label for="ct_template_${key}">${escapeHtml(label)}</label><input id="ct_template_${key}" type="text" maxlength="512" value="${escapeHtml(value)}" autocomplete="off" spellcheck="false"></div>`;
  }).join('');
  panel.innerHTML = `<details id="ct_custom_template_details"><summary><strong>Custom Templates</strong></summary><div class="scope-note" style="margin-top:12px">Create a reusable device identity template. The form stays collapsed until you open it.</div><div class="ct-choice-grid">${fields}</div><button id="ct_template_save" type="button" class="primary" style="width:100%;margin-top:14px">Save custom template</button></details>`;
  identityPanel.insertAdjacentElement('afterend',panel);
  panel.querySelector('#ct_template_save').onclick = () => saveCustomTemplate().catch(error => notify(error.message || 'Could not save custom template','error'));
}

async function saveCustomTemplate() {
  const template = {};
  for (const [key] of CUSTOM_TEMPLATE_FIELDS) {
    const input = document.getElementById(`ct_template_${key}`);
    template[key] = input ? input.value.trim() : '';
  }
  template.id = template.id.toLowerCase();
  if (!/^[a-z0-9_-]{1,64}$/.test(template.id)) throw new Error('Template ID is invalid');
  if (BUILT_IN_TEMPLATE_IDS.has(template.id)) throw new Error('Built-in template IDs cannot be replaced');
  if (Object.entries(template).some(([key,value]) => key !== 'id' && (!value || value.length > 512 || /[\u0000-\u001f\u007f]/.test(value)))) throw new Error('All template fields are required');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(template.securityPatch)) throw new Error('Security patch must be YYYY-MM-DD');

  const currentResponse = await bridge.fetch('/api/file?filename=templates.json');
  if (!currentResponse.ok) throw new Error('Template catalog is unavailable');
  let current;
  try { current = JSON.parse(await currentResponse.text()); } catch (_) { throw new Error('Template catalog is unavailable'); }
  if (!Array.isArray(current)) throw new Error('Template catalog is unavailable');
  const next = current.filter(item => String(item && item.id || '').toLowerCase() !== template.id);
  next.push(template);
  const body = new URLSearchParams();
  body.set('filename','templates.json');
  body.set('content',JSON.stringify(next,null,2));
  const save = await bridge.fetch('/api/save',{method:'POST',body});
  if (!save.ok) throw new Error((await save.text()) || 'Could not save custom template');
  await loadReferenceData();
  notify('Custom template saved');
  global.setTimeout(() => global.location.reload(),500);
}

'''
if 'function installCustomTemplateBuilder() {' not in p:
    if insert_before not in p:
        raise SystemExit('policy insertion marker missing')
    p = p.replace(insert_before, feature + insert_before, 1)
init_old = '  installConfigurationActions();\n  installAppsProfileCard();'
init_new = '  installConfigurationActions();\n  installCustomTemplateBuilder();\n  installAppsProfileCard();'
if init_new not in p:
    if init_old not in p:
        raise SystemExit('policy init marker missing')
    p = p.replace(init_old, init_new, 1)
policy.write_text(p)

ux = Path('module/template/webroot/ux.js')
u = ux.read_text()
if "'Custom Templates': 'Özel Şablonlar'" not in u:
    marker = "    // Complete catalogs share one source key per row to keep all built-in\n"
    if marker not in u:
        raise SystemExit('translation marker missing')
    tr = '''    Object.assign(TRANSLATIONS.tr, {
        'Custom Templates': 'Özel Şablonlar',
        'Create a reusable device identity template. The form stays collapsed until you open it.': 'Yeniden kullanılabilir bir cihaz kimliği şablonu oluşturun. Form siz açana kadar kapalı kalır.',
        'Template ID': 'Şablon Kimliği', 'Manufacturer': 'Üretici', 'Model': 'Model', 'Fingerprint': 'Parmak izi',
        'Brand': 'Marka', 'Product': 'Ürün', 'Device': 'Cihaz', 'Android release': 'Android sürümü',
        'Build ID': 'Derleme Kimliği', 'Incremental': 'Artımlı derleme', 'Build type': 'Derleme türü', 'Build tags': 'Derleme etiketleri',
        'Security patch': 'Güvenlik yaması', 'Save custom template': 'Özel şablonu kaydet',
        'Custom template saved': 'Özel şablon kaydedildi', 'Template ID is invalid': 'Şablon kimliği geçersiz',
        'Built-in template IDs cannot be replaced': 'Yerleşik şablon kimlikleri değiştirilemez',
        'All template fields are required': 'Tüm şablon alanları zorunludur',
        'Security patch must be YYYY-MM-DD': 'Güvenlik yaması YYYY-AA-GG biçiminde olmalıdır',
        'Template catalog is unavailable': 'Şablon kataloğu kullanılamıyor',
        'Could not save custom template': 'Özel şablon kaydedilemedi'
    });

'''
    u = u.replace(marker, tr + marker, 1)
    end_marker = '    ];\n\n    for (const row of COMPLETE_CATALOG_ROWS) {'
    if end_marker not in u:
        raise SystemExit('catalog end marker missing')
    rows = '''        ["Custom Templates", "自定义模板", "Plantillas personalizadas", "Benutzerdefinierte Vorlagen", "Пользовательские шаблоны", "Template Kustom", "कस्टम टेम्पलेट", "قوالب مخصصة"],
        ["Create a reusable device identity template. The form stays collapsed until you open it.", "创建可重复使用的设备身份模板。表单在打开前保持折叠。", "Crea una plantilla reutilizable de identidad del dispositivo. El formulario permanece contraído hasta que lo abras.", "Erstellt eine wiederverwendbare Geräteidentitätsvorlage. Das Formular bleibt bis zum Öffnen eingeklappt.", "Создайте многоразовый шаблон идентичности устройства. Форма остаётся свёрнутой, пока вы её не откроете.", "Buat template identitas perangkat yang dapat digunakan kembali. Form tetap tertutup sampai dibuka.", "दोबारा उपयोग योग्य डिवाइस पहचान टेम्पलेट बनाएँ। खोलने तक फ़ॉर्म बंद रहता है।", "أنشئ قالب هوية جهاز قابل لإعادة الاستخدام. يبقى النموذج مطويا حتى تفتحه."],
        ["Template ID", "模板 ID", "ID de plantilla", "Vorlagen-ID", "ID шаблона", "ID Template", "टेम्पलेट आईडी", "معرف القالب"],
        ["Manufacturer", "制造商", "Fabricante", "Hersteller", "Производитель", "Produsen", "निर्माता", "الشركة المصنعة"],
        ["Model", "型号", "Modelo", "Modell", "Модель", "Model", "मॉडल", "الطراز"],
        ["Fingerprint", "指纹", "Huella digital", "Fingerprint", "Отпечаток", "Fingerprint", "फिंगरप्रिंट", "البصمة"],
        ["Brand", "品牌", "Marca", "Marke", "Бренд", "Merek", "ब्रांड", "العلامة التجارية"],
        ["Product", "产品", "Producto", "Produkt", "Продукт", "Produk", "उत्पाद", "المنتج"],
        ["Device", "设备", "Dispositivo", "Gerät", "Устройство", "Perangkat", "डिवाइस", "الجهاز"],
        ["Android release", "Android 版本", "Versión de Android", "Android-Version", "Версия Android", "Rilis Android", "Android रिलीज़", "إصدار Android"],
        ["Build ID", "构建 ID", "ID de compilación", "Build-ID", "ID сборки", "ID Build", "बिल्ड आईडी", "معرف البناء"],
        ["Incremental", "增量版本", "Incremental", "Inkrementell", "Инкремент", "Inkremental", "इन्क्रिमेंटल", "تزايدي"],
        ["Build type", "构建类型", "Tipo de compilación", "Build-Typ", "Тип сборки", "Tipe build", "बिल्ड प्रकार", "نوع البناء"],
        ["Build tags", "构建标签", "Etiquetas de compilación", "Build-Tags", "Теги сборки", "Tag build", "बिल्ड टैग", "وسوم البناء"],
        ["Security patch", "安全补丁", "Parche de seguridad", "Sicherheitspatch", "Патч безопасности", "Patch keamanan", "सुरक्षा पैच", "تصحيح الأمان"],
        ["Save custom template", "保存自定义模板", "Guardar plantilla personalizada", "Benutzerdefinierte Vorlage speichern", "Сохранить пользовательский шаблон", "Simpan template kustom", "कस्टम टेम्पलेट सहेजें", "حفظ القالب المخصص"],
        ["Custom template saved", "自定义模板已保存", "Plantilla personalizada guardada", "Benutzerdefinierte Vorlage gespeichert", "Пользовательский шаблон сохранён", "Template kustom disimpan", "कस्टम टेम्पलेट सहेजा गया", "تم حفظ القالب المخصص"],
        ["Template ID is invalid", "模板 ID 无效", "El ID de plantilla no es válido", "Vorlagen-ID ist ungültig", "Недопустимый ID шаблона", "ID template tidak valid", "टेम्पलेट आईडी अमान्य है", "معرف القالب غير صالح"],
        ["Built-in template IDs cannot be replaced", "不能替换内置模板 ID", "No se pueden reemplazar los ID de plantillas integradas", "Integrierte Vorlagen-IDs können nicht ersetzt werden", "Встроенные ID шаблонов нельзя заменять", "ID template bawaan tidak dapat diganti", "अंतर्निहित टेम्पलेट आईडी बदली नहीं जा सकती", "لا يمكن استبدال معرفات القوالب المدمجة"],
        ["All template fields are required", "所有模板字段均为必填", "Todos los campos de la plantilla son obligatorios", "Alle Vorlagenfelder sind erforderlich", "Все поля шаблона обязательны", "Semua kolom template wajib diisi", "सभी टेम्पलेट फ़ील्ड आवश्यक हैं", "جميع حقول القالب مطلوبة"],
        ["Security patch must be YYYY-MM-DD", "安全补丁必须为 YYYY-MM-DD", "El parche de seguridad debe tener formato AAAA-MM-DD", "Sicherheitspatch muss JJJJ-MM-TT sein", "Патч безопасности должен быть YYYY-MM-DD", "Patch keamanan harus YYYY-MM-DD", "सुरक्षा पैच YYYY-MM-DD होना चाहिए", "يجب أن يكون تصحيح الأمان بالصيغة YYYY-MM-DD"],
        ["Template catalog is unavailable", "模板目录不可用", "El catálogo de plantillas no está disponible", "Vorlagenkatalog ist nicht verfügbar", "Каталог шаблонов недоступен", "Katalog template tidak tersedia", "टेम्पलेट कैटलॉग उपलब्ध नहीं है", "كتالوج القوالب غير متاح"],
        ["Could not save custom template", "无法保存自定义模板", "No se pudo guardar la plantilla personalizada", "Benutzerdefinierte Vorlage konnte nicht gespeichert werden", "Не удалось сохранить пользовательский шаблон", "Tidak dapat menyimpan template kustom", "कस्टम टेम्पलेट सहेजा नहीं जा सका", "تعذر حفظ القالب المخصص"],
'''
    u = u.replace(end_marker, rows + end_marker, 1)
ux.write_text(u)

test = Path('module/webui-tests/custom-template.test.js')
test.write_text(r'''const fs = require('fs');
const assert = require('assert');
const policy = fs.readFileSync('module/template/webroot/policy.js','utf8');
const web = fs.readFileSync('service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt','utf8');
assert(policy.includes('id="ct_custom_template_details"'), 'custom template editor must be collapsible');
assert(policy.includes("bridge.fetch('/api/file?filename=templates.json')"), 'builder must preserve complete template records');
assert(policy.includes("body.set('filename','templates.json')"), 'builder must save validated template catalog');
assert(policy.includes("fillSelect(document.getElementById('ct_profile_template'),templates"), 'Profiles must consume shared template catalog');
for (const field of ['id','manufacturer','model','fingerprint','brand','product','device','release','buildId','incremental','type','tags','securityPatch']) assert(policy.includes(`['${field}'`), `missing template field ${field}`);
assert(web.includes('if (filename == "templates.json") {\n                            DeviceTemplateManager.initialize(configDir)'), 'saving templates must refresh runtime catalog');
console.log('custom-template regression checks passed');
''')

changelog = Path('CHANGELOG.md')
c = changelog.read_text().rstrip() + '\n'
bullet = '- Added a localized, collapsible Identity custom-template builder whose saved templates are immediately available in Profiles.\n'
if bullet not in c:
    c += bullet
changelog.write_text(c)

Path('docs/.trusted-write-check').unlink(missing_ok=True)
Path('.agent/apply_custom_template.py').unlink(missing_ok=True)
Path('.github/workflows/apply-custom-template-ui.yml').unlink(missing_ok=True)
