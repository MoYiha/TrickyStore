(function (global) {
    'use strict';

    const MAX_SUPPORTED_FILES = 10000;
    const MAX_ARCHIVE_ENTRIES = 20000;
    const MAX_FILE_BYTES = 10 * 1024 * 1024;
    const MAX_COMPRESSED_FILE_BYTES = MAX_FILE_BYTES + 64 * 1024;
    const MAX_NAME_BYTES = 4096;
    const MAX_CENTRAL_DIRECTORY_BYTES = 64 * 1024 * 1024;
    const STORAGE_KEY = 'cleverestricky.language.v1';
    const SUPPORTED_LOCALES = new Set(['en', 'tr', 'zh-CN', 'es', 'de', 'ru', 'id', 'hi', 'ar']);

    const COPY = {
        en: {
            prompt: 'Or click to select .xml, .cbox or .zip',
            ready: 'ZIP ready: {name}',
            summary: '{count} supported XML/CBOX files · {size}',
            confirm: 'I understand that every supported XML/CBOX file in this ZIP will be imported individually.',
            import: 'Import ZIP files',
            importing: 'Importing ZIP files… {done}/{total}',
            success: 'ZIP import complete: {ok}/{total} files imported.',
            partial: 'ZIP import finished: {ok} imported, {failed} failed.',
            select: 'Select a non-empty XML, CBOX or ZIP file.',
            entryLimit: 'ZIP contains too many entries.',
            fileCountLimit: 'ZIP contains more than 10000 supported XML/CBOX files.',
            fileLimit: 'A supported XML/CBOX file is empty or larger than 10 MiB.',
            noSupported: 'ZIP does not contain supported .xml or .cbox files.',
            encrypted: 'Encrypted ZIP entries are not supported.',
            compression: 'ZIP uses an unsupported compression method.',
            malformed: 'ZIP is malformed or uses unsupported ZIP64/multi-disk features.',
            decompressor: 'This Android WebView cannot decompress standard ZIP files. Update Android System WebView and try again.',
            changed: 'ZIP selection changed. Review the files and confirm again.',
            busy: 'A ZIP import is already running.'
        },
        tr: {
            prompt: '.xml, .cbox veya .zip seçmek için dokunun',
            ready: 'ZIP hazır: {name}',
            summary: '{count} desteklenen XML/CBOX dosyası · {size}',
            confirm: 'Bu ZIP içindeki desteklenen tüm XML/CBOX dosyalarının tek tek içe aktarılacağını anlıyorum.',
            import: 'ZIP dosyalarını içe aktar',
            importing: 'ZIP dosyaları içe aktarılıyor… {done}/{total}',
            success: 'ZIP içe aktarma tamamlandı: {ok}/{total} dosya eklendi.',
            partial: 'ZIP içe aktarma bitti: {ok} eklendi, {failed} başarısız.',
            select: 'Boş olmayan bir XML, CBOX veya ZIP dosyası seçin.',
            entryLimit: 'ZIP çok fazla girdi içeriyor.',
            fileCountLimit: 'ZIP 10000 adetten fazla desteklenen XML/CBOX dosyası içeriyor.',
            fileLimit: 'Desteklenen bir XML/CBOX dosyası boş veya 10 MiB sınırını aşıyor.',
            noSupported: 'ZIP desteklenen .xml veya .cbox dosyası içermiyor.',
            encrypted: 'Şifreli ZIP girdileri desteklenmiyor.',
            compression: 'ZIP desteklenmeyen bir sıkıştırma yöntemi kullanıyor.',
            malformed: 'ZIP bozuk veya desteklenmeyen ZIP64/çoklu disk özellikleri kullanıyor.',
            decompressor: 'Bu Android WebView standart ZIP dosyalarını açamıyor. Android System WebView uygulamasını güncelleyip yeniden deneyin.',
            changed: 'ZIP seçimi değişti. Dosyaları yeniden kontrol edip onaylayın.',
            busy: 'Bir ZIP içe aktarma işlemi zaten çalışıyor.'
        },
        'zh-CN': {
            prompt: '点击选择 .xml、.cbox 或 .zip', ready: 'ZIP 已就绪：{name}', summary: '{count} 个支持的 XML/CBOX 文件 · {size}',
            confirm: '我了解此 ZIP 中所有受支持的 XML/CBOX 文件都会逐个导入。', import: '导入 ZIP 文件', importing: '正在导入 ZIP 文件… {done}/{total}',
            success: 'ZIP 导入完成：已导入 {ok}/{total} 个文件。', partial: 'ZIP 导入结束：成功 {ok} 个，失败 {failed} 个。',
            select: '请选择非空的 XML、CBOX 或 ZIP 文件。', entryLimit: 'ZIP 中的条目过多。', fileCountLimit: 'ZIP 中受支持的 XML/CBOX 文件超过 10000 个。',
            fileLimit: '受支持的 XML/CBOX 文件为空或超过 10 MiB。', noSupported: 'ZIP 中没有受支持的 .xml 或 .cbox 文件。', encrypted: '不支持加密的 ZIP 条目。',
            compression: 'ZIP 使用了不支持的压缩方式。', malformed: 'ZIP 已损坏，或使用了不支持的 ZIP64/多磁盘功能。',
            decompressor: '此 Android WebView 无法解压标准 ZIP。请更新 Android System WebView 后重试。', changed: 'ZIP 选择已更改。请重新检查文件并确认。', busy: '已有 ZIP 导入任务正在运行。'
        },
        es: {
            prompt: 'Toca para elegir .xml, .cbox o .zip', ready: 'ZIP listo: {name}', summary: '{count} archivos XML/CBOX compatibles · {size}',
            confirm: 'Entiendo que todos los archivos XML/CBOX compatibles de este ZIP se importarán individualmente.', import: 'Importar archivos ZIP', importing: 'Importando archivos ZIP… {done}/{total}',
            success: 'Importación ZIP completada: {ok}/{total} archivos importados.', partial: 'Importación ZIP finalizada: {ok} importados, {failed} fallidos.',
            select: 'Selecciona un archivo XML, CBOX o ZIP no vacío.', entryLimit: 'El ZIP contiene demasiadas entradas.', fileCountLimit: 'El ZIP contiene más de 10000 archivos XML/CBOX compatibles.',
            fileLimit: 'Un XML/CBOX compatible está vacío o supera 10 MiB.', noSupported: 'El ZIP no contiene archivos .xml o .cbox compatibles.', encrypted: 'No se admiten entradas ZIP cifradas.',
            compression: 'El ZIP usa un método de compresión no compatible.', malformed: 'El ZIP está dañado o usa ZIP64/múltiples discos no compatibles.',
            decompressor: 'Este Android WebView no puede descomprimir ZIP estándar. Actualiza Android System WebView y vuelve a intentarlo.', changed: 'La selección ZIP cambió. Revisa los archivos y confirma de nuevo.', busy: 'Ya hay una importación ZIP en curso.'
        },
        de: {
            prompt: 'Tippen, um .xml, .cbox oder .zip auszuwählen', ready: 'ZIP bereit: {name}', summary: '{count} unterstützte XML/CBOX-Dateien · {size}',
            confirm: 'Ich verstehe, dass alle unterstützten XML/CBOX-Dateien in diesem ZIP einzeln importiert werden.', import: 'ZIP-Dateien importieren', importing: 'ZIP-Dateien werden importiert… {done}/{total}',
            success: 'ZIP-Import abgeschlossen: {ok}/{total} Dateien importiert.', partial: 'ZIP-Import beendet: {ok} importiert, {failed} fehlgeschlagen.',
            select: 'Wähle eine nicht leere XML-, CBOX- oder ZIP-Datei.', entryLimit: 'Das ZIP enthält zu viele Einträge.', fileCountLimit: 'Das ZIP enthält mehr als 10000 unterstützte XML/CBOX-Dateien.',
            fileLimit: 'Eine unterstützte XML/CBOX-Datei ist leer oder größer als 10 MiB.', noSupported: 'Das ZIP enthält keine unterstützten .xml- oder .cbox-Dateien.', encrypted: 'Verschlüsselte ZIP-Einträge werden nicht unterstützt.',
            compression: 'Das ZIP verwendet eine nicht unterstützte Komprimierung.', malformed: 'Das ZIP ist beschädigt oder verwendet nicht unterstützte ZIP64-/Multi-Disk-Funktionen.',
            decompressor: 'Dieses Android WebView kann Standard-ZIP-Dateien nicht entpacken. Aktualisiere Android System WebView und versuche es erneut.', changed: 'Die ZIP-Auswahl wurde geändert. Prüfe die Dateien erneut und bestätige noch einmal.', busy: 'Ein ZIP-Import läuft bereits.'
        },
        ru: {
            prompt: 'Нажмите, чтобы выбрать .xml, .cbox или .zip', ready: 'ZIP готов: {name}', summary: '{count} поддерживаемых XML/CBOX · {size}',
            confirm: 'Я понимаю, что все поддерживаемые XML/CBOX-файлы из этого ZIP будут импортированы по отдельности.', import: 'Импортировать файлы ZIP', importing: 'Импорт файлов ZIP… {done}/{total}',
            success: 'Импорт ZIP завершён: импортировано {ok}/{total}.', partial: 'Импорт ZIP завершён: {ok} успешно, {failed} с ошибкой.',
            select: 'Выберите непустой XML, CBOX или ZIP.', entryLimit: 'В ZIP слишком много записей.', fileCountLimit: 'В ZIP больше 10000 поддерживаемых XML/CBOX-файлов.',
            fileLimit: 'Поддерживаемый XML/CBOX пуст или больше 10 MiB.', noSupported: 'В ZIP нет поддерживаемых .xml или .cbox.', encrypted: 'Зашифрованные записи ZIP не поддерживаются.',
            compression: 'ZIP использует неподдерживаемый метод сжатия.', malformed: 'ZIP повреждён или использует неподдерживаемые ZIP64/многодисковые функции.',
            decompressor: 'Этот Android WebView не может распаковать стандартный ZIP. Обновите Android System WebView и повторите попытку.', changed: 'Выбран другой ZIP. Снова проверьте файлы и подтвердите импорт.', busy: 'Импорт ZIP уже выполняется.'
        },
        id: {
            prompt: 'Ketuk untuk memilih .xml, .cbox, atau .zip', ready: 'ZIP siap: {name}', summary: '{count} file XML/CBOX yang didukung · {size}',
            confirm: 'Saya memahami bahwa semua file XML/CBOX yang didukung di ZIP ini akan diimpor satu per satu.', import: 'Impor file ZIP', importing: 'Mengimpor file ZIP… {done}/{total}',
            success: 'Impor ZIP selesai: {ok}/{total} file diimpor.', partial: 'Impor ZIP selesai: {ok} berhasil, {failed} gagal.',
            select: 'Pilih file XML, CBOX, atau ZIP yang tidak kosong.', entryLimit: 'ZIP berisi terlalu banyak entri.', fileCountLimit: 'ZIP berisi lebih dari 10000 file XML/CBOX yang didukung.',
            fileLimit: 'File XML/CBOX yang didukung kosong atau lebih dari 10 MiB.', noSupported: 'ZIP tidak berisi file .xml atau .cbox yang didukung.', encrypted: 'Entri ZIP terenkripsi tidak didukung.',
            compression: 'ZIP memakai metode kompresi yang tidak didukung.', malformed: 'ZIP rusak atau memakai fitur ZIP64/multi-disk yang tidak didukung.',
            decompressor: 'Android WebView ini tidak dapat mengekstrak ZIP standar. Perbarui Android System WebView lalu coba lagi.', changed: 'Pilihan ZIP berubah. Tinjau file dan konfirmasi lagi.', busy: 'Impor ZIP sedang berjalan.'
        },
        hi: {
            prompt: '.xml, .cbox या .zip चुनने के लिए टैप करें', ready: 'ZIP तैयार: {name}', summary: '{count} समर्थित XML/CBOX फ़ाइलें · {size}',
            confirm: 'मैं समझता/समझती हूँ कि इस ZIP की हर समर्थित XML/CBOX फ़ाइल अलग-अलग आयात होगी।', import: 'ZIP फ़ाइलें आयात करें', importing: 'ZIP फ़ाइलें आयात हो रही हैं… {done}/{total}',
            success: 'ZIP आयात पूरा: {ok}/{total} फ़ाइलें आयात हुईं।', partial: 'ZIP आयात समाप्त: {ok} आयात हुईं, {failed} विफल।',
            select: 'कोई खाली न होने वाली XML, CBOX या ZIP फ़ाइल चुनें।', entryLimit: 'ZIP में बहुत अधिक प्रविष्टियाँ हैं।', fileCountLimit: 'ZIP में 10000 से अधिक समर्थित XML/CBOX फ़ाइलें हैं।',
            fileLimit: 'कोई समर्थित XML/CBOX फ़ाइल खाली है या 10 MiB से बड़ी है।', noSupported: 'ZIP में समर्थित .xml या .cbox फ़ाइल नहीं है।', encrypted: 'एन्क्रिप्टेड ZIP प्रविष्टियाँ समर्थित नहीं हैं।',
            compression: 'ZIP असमर्थित संपीड़न विधि उपयोग करता है।', malformed: 'ZIP खराब है या असमर्थित ZIP64/मल्टी-डिस्क सुविधा उपयोग करता है।',
            decompressor: 'यह Android WebView सामान्य ZIP फ़ाइल नहीं खोल सकता। Android System WebView अपडेट करके फिर कोशिश करें।', changed: 'ZIP चयन बदल गया। फ़ाइलों की दोबारा जाँच करके फिर पुष्टि करें।', busy: 'ZIP आयात पहले से चल रहा है।'
        },
        ar: {
            prompt: 'اضغط لاختيار .xml أو .cbox أو .zip', ready: 'ZIP جاهز: {name}', summary: '{count} من ملفات XML/CBOX المدعومة · {size}',
            confirm: 'أفهم أن كل ملفات XML/CBOX المدعومة داخل ZIP ستُستورد واحداً تلو الآخر.', import: 'استيراد ملفات ZIP', importing: 'جار استيراد ملفات ZIP… {done}/{total}',
            success: 'اكتمل استيراد ZIP: تم استيراد {ok}/{total} ملفاً.', partial: 'انتهى استيراد ZIP: نجح {ok} وفشل {failed}.',
            select: 'اختر ملف XML أو CBOX أو ZIP غير فارغ.', entryLimit: 'يحتوي ZIP على عدد كبير جداً من العناصر.', fileCountLimit: 'يحتوي ZIP على أكثر من 10000 ملف XML/CBOX مدعوماً.',
            fileLimit: 'أحد ملفات XML/CBOX المدعومة فارغ أو أكبر من 10 MiB.', noSupported: 'لا يحتوي ZIP على ملفات .xml أو .cbox مدعومة.', encrypted: 'عناصر ZIP المشفرة غير مدعومة.',
            compression: 'يستخدم ZIP طريقة ضغط غير مدعومة.', malformed: 'ملف ZIP تالف أو يستخدم ZIP64/أقراصاً متعددة غير مدعومة.',
            decompressor: 'لا يستطيع Android WebView هذا فك ZIP القياسي. حدّث Android System WebView ثم أعد المحاولة.', changed: 'تغير ملف ZIP المحدد. راجع الملفات وأكّد مرة أخرى.', busy: 'هناك عملية استيراد ZIP قيد التشغيل بالفعل.'
        }
    };

    class ZipImportError extends Error {
        constructor(code) {
            super(code);
            this.name = 'ZipImportError';
            this.code = code;
        }
    }

    function fail(code) {
        throw new ZipImportError(code);
    }

    function readLocale() {
        try {
            const value = global.localStorage && global.localStorage.getItem(STORAGE_KEY);
            return SUPPORTED_LOCALES.has(value) ? value : 'en';
        } catch (_) {
            return 'en';
        }
    }

    function text(key, values) {
        const locale = readLocale();
        let value = (COPY[locale] && COPY[locale][key]) || COPY.en[key] || key;
        Object.entries(values || {}).forEach(([name, replacement]) => {
            value = value.split('{' + name + '}').join(String(replacement));
        });
        return value;
    }

    function formatBytes(size) {
        if (size < 1024) return size + ' B';
        if (size < 1024 * 1024) return Math.ceil(size / 1024) + ' KiB';
        if (size < 1024 * 1024 * 1024) return (size / (1024 * 1024)).toFixed(size % (1024 * 1024) === 0 ? 0 : 1) + ' MiB';
        return (size / (1024 * 1024 * 1024)).toFixed(1) + ' GiB';
    }

    function u16(view, offset) {
        if (offset < 0 || offset + 2 > view.byteLength) fail('malformed');
        return view.getUint16(offset, true);
    }

    function u32(view, offset) {
        if (offset < 0 || offset + 4 > view.byteLength) fail('malformed');
        return view.getUint32(offset, true);
    }

    function decodeName(bytes, utf8) {
        if (bytes.length === 0 || bytes.length > MAX_NAME_BYTES) fail('malformed');
        let value;
        try {
            value = utf8
                ? new TextDecoder('utf-8', { fatal: true }).decode(bytes)
                : Array.from(bytes, byte => String.fromCharCode(byte)).join('');
        } catch (_) {
            fail('malformed');
        }
        if (!value || value.indexOf('\u0000') >= 0 || /[\u0000-\u001f\u007f]/.test(value)) fail('malformed');
        return value;
    }

    function isSupportedName(name) {
        const lower = name.toLowerCase();
        return lower.endsWith('.xml') || lower.endsWith('.cbox');
    }

    function safeBasename(name) {
        let base = name.replace(/\\/g, '/').split('/').pop() || 'keybox.xml';
        base = base.replace(/[\u0000-\u001f\u007f]/g, '_').trim();
        if (!base) base = 'keybox.xml';
        if (base.length > 120) {
            const dot = base.lastIndexOf('.');
            const ext = dot >= 0 ? base.slice(dot).slice(0, 8) : '';
            base = base.slice(0, Math.max(1, 120 - ext.length)) + ext;
        }
        return base;
    }

    function allocateUploadNames(entries) {
        const used = new Set();
        return entries.map(entry => {
            const base = safeBasename(entry.name);
            const dot = base.lastIndexOf('.');
            const stem = dot > 0 ? base.slice(0, dot) : base;
            const ext = dot > 0 ? base.slice(dot) : '';
            let candidate = base;
            let suffix = 2;
            while (used.has(candidate.toLowerCase())) {
                const suffixText = '-' + suffix++;
                candidate = stem.slice(0, Math.max(1, 120 - ext.length - suffixText.length)) + suffixText + ext;
            }
            used.add(candidate.toLowerCase());
            return Object.assign({}, entry, { uploadName: candidate });
        });
    }

    async function readRange(blob, start, length) {
        if (!(blob instanceof Blob) || !Number.isSafeInteger(start) || !Number.isSafeInteger(length) || start < 0 || length < 0 || start > blob.size || length > blob.size - start) fail('malformed');
        return new Uint8Array(await blob.slice(start, start + length).arrayBuffer());
    }

    async function findEocd(file) {
        if (!(file instanceof Blob) || !Number.isSafeInteger(file.size) || file.size < 22) fail('malformed');
        const tailStart = Math.max(0, file.size - 65557);
        const tail = await readRange(file, tailStart, file.size - tailStart);
        try {
            const view = new DataView(tail.buffer, tail.byteOffset, tail.byteLength);
            for (let offset = tail.length - 22; offset >= 0; offset--) {
                if (u32(view, offset) !== 0x06054b50) continue;
                const commentLength = u16(view, offset + 20);
                if (offset + 22 + commentLength !== tail.length) continue;
                return {
                    absoluteOffset: tailStart + offset,
                    disk: u16(view, offset + 4),
                    centralDisk: u16(view, offset + 6),
                    diskEntries: u16(view, offset + 8),
                    totalEntries: u16(view, offset + 10),
                    centralSize: u32(view, offset + 12),
                    centralOffset: u32(view, offset + 16)
                };
            }
            fail('malformed');
        } finally {
            tail.fill(0);
        }
    }

    async function parseZipFile(file) {
        if (!(file instanceof Blob) || file.size <= 0) fail('select');
        const eocd = await findEocd(file);
        if (eocd.disk !== 0 || eocd.centralDisk !== 0 || eocd.diskEntries !== eocd.totalEntries || eocd.totalEntries === 0xffff || eocd.centralSize === 0xffffffff || eocd.centralOffset === 0xffffffff) fail('malformed');
        if (eocd.totalEntries > MAX_ARCHIVE_ENTRIES) fail('entryLimit');
        if (eocd.centralSize > MAX_CENTRAL_DIRECTORY_BYTES || eocd.centralOffset > eocd.absoluteOffset || eocd.centralSize > eocd.absoluteOffset - eocd.centralOffset) fail('malformed');

        const central = await readRange(file, eocd.centralOffset, eocd.centralSize);
        try {
            const view = new DataView(central.buffer, central.byteOffset, central.byteLength);
            const entries = [];
            let cursor = 0;
            for (let index = 0; index < eocd.totalEntries; index++) {
                if (cursor + 46 > central.length || u32(view, cursor) !== 0x02014b50) fail('malformed');
                const flags = u16(view, cursor + 8);
                const method = u16(view, cursor + 10);
                const crc = u32(view, cursor + 16);
                const compressedSize = u32(view, cursor + 20);
                const uncompressedSize = u32(view, cursor + 24);
                const nameLength = u16(view, cursor + 28);
                const extraLength = u16(view, cursor + 30);
                const commentLength = u16(view, cursor + 32);
                const diskStart = u16(view, cursor + 34);
                const localOffset = u32(view, cursor + 42);
                const end = cursor + 46 + nameLength + extraLength + commentLength;
                if (end > central.length || diskStart !== 0 || compressedSize === 0xffffffff || uncompressedSize === 0xffffffff || localOffset === 0xffffffff) fail('malformed');
                if ((flags & 0x0041) !== 0) fail('encrypted');
                if (method !== 0 && method !== 8) fail('compression');
                const name = decodeName(central.subarray(cursor + 46, cursor + 46 + nameLength), (flags & 0x0800) !== 0);
                if (!name.endsWith('/') && isSupportedName(name)) {
                    if (uncompressedSize <= 0 || uncompressedSize > MAX_FILE_BYTES || compressedSize <= 0 || compressedSize > MAX_COMPRESSED_FILE_BYTES) fail('fileLimit');
                    entries.push({ name, flags, method, crc, compressedSize, uncompressedSize, localOffset });
                    if (entries.length > MAX_SUPPORTED_FILES) fail('fileCountLimit');
                }
                cursor = end;
            }
            if (cursor !== central.length) fail('malformed');
            if (entries.length === 0) fail('noSupported');
            const named = allocateUploadNames(entries);
            const totalBytes = named.reduce((sum, entry) => sum + entry.uncompressedSize, 0);
            return { entries: named, centralOffset: eocd.centralOffset, totalBytes };
        } finally {
            central.fill(0);
        }
    }

    let crcTable = null;
    function crc32(bytes) {
        if (!crcTable) {
            crcTable = new Uint32Array(256);
            for (let n = 0; n < 256; n++) {
                let value = n;
                for (let k = 0; k < 8; k++) value = (value & 1) ? (0xedb88320 ^ (value >>> 1)) : (value >>> 1);
                crcTable[n] = value >>> 0;
            }
        }
        let crc = 0xffffffff;
        for (let index = 0; index < bytes.length; index++) crc = crcTable[(crc ^ bytes[index]) & 0xff] ^ (crc >>> 8);
        return (crc ^ 0xffffffff) >>> 0;
    }

    async function inflateRawBounded(compressed, expectedSize) {
        if (typeof global.DecompressionStream !== 'function') fail('decompressor');
        let stream;
        try {
            stream = new Blob([compressed]).stream().pipeThrough(new global.DecompressionStream('deflate-raw'));
        } catch (_) {
            fail('decompressor');
        }
        const reader = stream.getReader();
        const chunks = [];
        let total = 0;
        try {
            while (true) {
                const result = await reader.read();
                if (result.done) break;
                const chunk = result.value instanceof Uint8Array ? result.value : new Uint8Array(result.value);
                if (chunk.length > expectedSize - total || total + chunk.length > MAX_FILE_BYTES) fail('fileLimit');
                chunks.push(chunk);
                total += chunk.length;
            }
        } catch (error) {
            try { await reader.cancel(); } catch (_) {}
            chunks.forEach(chunk => chunk.fill(0));
            if (error instanceof ZipImportError) throw error;
            fail('malformed');
        }
        if (total !== expectedSize) {
            chunks.forEach(chunk => chunk.fill(0));
            fail('malformed');
        }
        const output = new Uint8Array(total);
        let offset = 0;
        chunks.forEach(chunk => {
            output.set(chunk, offset);
            offset += chunk.length;
            chunk.fill(0);
        });
        return output;
    }

    async function extractEntry(file, entry, centralOffset) {
        const header = await readRange(file, entry.localOffset, 30);
        let compressed = null;
        try {
            const view = new DataView(header.buffer, header.byteOffset, header.byteLength);
            if (u32(view, 0) !== 0x04034b50) fail('malformed');
            const flags = u16(view, 6);
            const method = u16(view, 8);
            const nameLength = u16(view, 26);
            const extraLength = u16(view, 28);
            if ((flags & 0x0041) !== 0) fail('encrypted');
            if (method !== entry.method || (flags & 0x0809) !== (entry.flags & 0x0809) || nameLength <= 0 || nameLength > MAX_NAME_BYTES) fail('malformed');
            const dataOffset = entry.localOffset + 30 + nameLength + extraLength;
            if (!Number.isSafeInteger(dataOffset) || dataOffset > centralOffset || entry.compressedSize > centralOffset - dataOffset) fail('malformed');
            const localNameBytes = await readRange(file, entry.localOffset + 30, nameLength);
            try {
                if (decodeName(localNameBytes, (flags & 0x0800) !== 0) !== entry.name) fail('malformed');
            } finally {
                localNameBytes.fill(0);
            }
            compressed = await readRange(file, dataOffset, entry.compressedSize);
            let output;
            if (entry.method === 0) {
                if (entry.compressedSize !== entry.uncompressedSize) fail('malformed');
                output = compressed;
                compressed = null;
            } else {
                output = await inflateRawBounded(compressed, entry.uncompressedSize);
            }
            if (output.length !== entry.uncompressedSize || crc32(output) !== entry.crc) {
                output.fill(0);
                fail('malformed');
            }
            return output;
        } finally {
            header.fill(0);
            if (compressed) compressed.fill(0);
        }
    }

    function notify(message, type) {
        if (typeof global.notify === 'function') global.notify(message, type);
    }

    function errorMessage(error) {
        if (error instanceof ZipImportError) return text(error.code);
        return text('malformed');
    }

    let pendingZip = null;
    let busy = false;
    let ui = null;
    let originalLoadFileContent = null;
    let originalResetDropZone = null;

    function clearPending() {
        pendingZip = null;
        if (!ui) return;
        ui.confirm.checked = false;
        ui.button.disabled = true;
        ui.panel.hidden = true;
        ui.summary.textContent = '';
    }

    function updatePrompt() {
        if (!ui) return;
        const content = document.getElementById('dropZoneContent');
        if (content && !pendingZip && !busy) {
            content.innerHTML = '';
            const drag = document.createElement('div');
            drag.style.cssText = 'font-size:1.5em;margin-bottom:10px;color:#888;';
            drag.textContent = '[ Drag & Drop ]';
            const prompt = document.createElement('div');
            prompt.style.cssText = 'font-size:0.9em;color:#888;';
            prompt.textContent = text('prompt');
            content.appendChild(drag);
            content.appendChild(prompt);
        }
        ui.confirmLabel.textContent = text('confirm');
        ui.button.textContent = text('import');
        if (pendingZip) ui.summary.textContent = text('summary', { count: pendingZip.count, size: formatBytes(pendingZip.totalBytes) });
    }

    async function prepareZip(file) {
        if (busy) {
            notify(text('busy'), 'error');
            return;
        }
        clearPending();
        try {
            const parsed = await parseZipFile(file);
            pendingZip = {
                file,
                count: parsed.entries.length,
                totalBytes: parsed.totalBytes,
                identity: file.name + ':' + file.size + ':' + file.lastModified
            };
            ui.panel.hidden = false;
            ui.confirm.checked = false;
            ui.button.disabled = true;
            const content = document.getElementById('dropZoneContent');
            if (content) {
                content.innerHTML = '';
                const ready = document.createElement('div');
                ready.style.cssText = 'font-size:1.05em;color:var(--accent);font-weight:600;overflow-wrap:anywhere;';
                ready.textContent = text('ready', { name: file.name });
                content.appendChild(ready);
            }
            updatePrompt();
        } catch (error) {
            clearPending();
            notify(errorMessage(error), 'error');
            updatePrompt();
        }
    }

    async function uploadEntry(entry, bytes) {
        if (typeof global.fetchAuth !== 'function') throw new Error('upload unavailable');
        const formData = new FormData();
        const type = entry.uploadName.toLowerCase().endsWith('.cbox') ? 'application/octet-stream' : 'application/xml';
        const file = new File([bytes], entry.uploadName, { type });
        formData.append('file', file);
        formData.append('filename', entry.uploadName);
        const response = await global.fetchAuth('/api/upload_keybox', { method: 'POST', body: formData, timeoutMs: 120000 });
        if (response.ok) return { ok: true, name: entry.uploadName };
        let detail = '';
        try { detail = (await response.text()).replace(/[\r\n]+/g, ' ').trim().slice(0, 160); } catch (_) {}
        return { ok: false, name: entry.uploadName, detail };
    }

    async function importPendingZip() {
        if (busy || !pendingZip) return;
        if (!ui.confirm.checked) {
            ui.button.disabled = true;
            return;
        }
        const selection = pendingZip;
        busy = true;
        ui.button.disabled = true;
        const picker = document.getElementById('kbFilePicker');
        if (picker) picker.disabled = true;
        const results = [];
        try {
            const parsed = await parseZipFile(selection.file);
            const identity = selection.file.name + ':' + selection.file.size + ':' + selection.file.lastModified;
            if (identity !== selection.identity || parsed.entries.length !== selection.count || parsed.totalBytes !== selection.totalBytes) fail('changed');
            for (let index = 0; index < parsed.entries.length; index++) {
                const entry = parsed.entries[index];
                const progress = text('importing', { done: index, total: parsed.entries.length });
                ui.summary.textContent = progress;
                notify(progress, 'working');
                let bytes = null;
                try {
                    bytes = await extractEntry(selection.file, entry, parsed.centralOffset);
                    results.push(await uploadEntry(entry, bytes));
                } catch (error) {
                    results.push({ ok: false, name: entry.uploadName, detail: errorMessage(error) });
                } finally {
                    if (bytes) bytes.fill(0);
                }
                ui.summary.textContent = text('importing', { done: index + 1, total: parsed.entries.length });
            }
            const ok = results.filter(result => result.ok).length;
            const failed = results.length - ok;
            const message = failed === 0
                ? text('success', { ok, total: results.length })
                : text('partial', { ok, failed });
            const failures = results.filter(result => !result.ok).slice(0, 4).map(result => result.name + (result.detail ? ': ' + result.detail : '')).join(' | ');
            notify(failures ? message + ' ' + failures : message, failed === 0 ? 'normal' : 'error');
            if (typeof global.loadKeyInfo === 'function') global.loadKeyInfo();
            if (typeof global.loadKeyboxes === 'function') global.loadKeyboxes();
        } catch (error) {
            notify(errorMessage(error), 'error');
        } finally {
            busy = false;
            if (picker) picker.disabled = false;
            clearPending();
            if (typeof originalResetDropZone === 'function') originalResetDropZone();
            updatePrompt();
        }
    }

    function createUi(dropZone) {
        const panel = document.createElement('div');
        panel.id = 'ct_zip_confirmation';
        panel.hidden = true;
        panel.style.cssText = 'grid-column:1 / -1;border:1px solid var(--border);border-radius:8px;padding:12px;margin:0 0 10px 0;';
        const summary = document.createElement('div');
        summary.id = 'ct_zip_summary';
        summary.style.cssText = 'font-size:0.85em;color:#aaa;margin-bottom:10px;overflow-wrap:anywhere;';
        const row = document.createElement('label');
        row.style.cssText = 'display:flex;gap:10px;align-items:flex-start;line-height:1.45;cursor:pointer;margin-bottom:10px;';
        const confirm = document.createElement('input');
        confirm.type = 'checkbox';
        confirm.id = 'ct_zip_confirm';
        confirm.style.cssText = 'width:20px;height:20px;min-width:20px;margin-top:1px;';
        const confirmLabel = document.createElement('span');
        confirmLabel.id = 'ct_zip_confirm_label';
        row.appendChild(confirm);
        row.appendChild(confirmLabel);
        const button = document.createElement('button');
        button.id = 'ct_zip_import_btn';
        button.className = 'primary';
        button.type = 'button';
        button.disabled = true;
        button.style.width = '100%';
        panel.appendChild(summary);
        panel.appendChild(row);
        panel.appendChild(button);
        dropZone.parentNode.appendChild(panel);
        confirm.addEventListener('change', function () { button.disabled = !confirm.checked || busy || !pendingZip; });
        button.addEventListener('click', function (event) { event.preventDefault(); event.stopPropagation(); importPendingZip(); });
        panel.addEventListener('click', function (event) { event.stopPropagation(); });
        return { panel, summary, confirm, confirmLabel, button };
    }

    function install() {
        if (ui || typeof document === 'undefined') return;
        const picker = document.getElementById('kbFilePicker');
        const dropZone = document.getElementById('dropZone');
        if (!picker || !dropZone || typeof global.loadFileContent !== 'function') {
            global.setTimeout(install, 50);
            return;
        }
        originalLoadFileContent = global.loadFileContent;
        originalResetDropZone = typeof global.resetDropZone === 'function' ? global.resetDropZone : null;
        picker.accept = '.xml,.cbox,.zip';
        ui = createUi(dropZone);

        global.loadFileContent = async function (input) {
            const file = input instanceof File ? input : (input && input.files ? input.files[0] : null);
            if (!file) return originalLoadFileContent(input);
            if (busy) {
                notify(text('busy'), 'error');
                return;
            }
            if (file.name.toLowerCase().endsWith('.zip')) {
                await prepareZip(file);
                return;
            }
            clearPending();
            updatePrompt();
            return originalLoadFileContent(input);
        };

        if (originalResetDropZone) {
            global.resetDropZone = function () {
                const result = originalResetDropZone.apply(this, arguments);
                if (!busy) clearPending();
                updatePrompt();
                return result;
            };
        }

        const selector = document.getElementById('ct_language_selector');
        if (selector) selector.addEventListener('change', function () { global.setTimeout(updatePrompt, 0); });
        updatePrompt();
    }

    global.CleveresZipImport = Object.freeze({
        limits: Object.freeze({ MAX_SUPPORTED_FILES, MAX_ARCHIVE_ENTRIES, MAX_FILE_BYTES }),
        parseZipFile,
        extractEntry,
        allocateUploadNames,
        translations: COPY
    });

    if (typeof document !== 'undefined') {
        if (document.readyState === 'complete') global.setTimeout(install, 0);
        else global.addEventListener('load', install, { once: true });
    }
})(window);
