from pathlib import Path

path = Path('service/src/main/java/cleveres/tricky/cleverestech/KeystoreInterceptor.kt')
text = path.read_text()
replacements = [
    (
        '''        if (!Config.isSpoofEnabled) {\n            stopKeystoreInterceptor()\n            return true\n        }\n''',
        '',
        2,
    ),
    (
        '''        if (!Config.isSpoofEnabled) {\n            parkBinderHook(bd)\n            return true\n        }\n''',
        '',
        1,
    ),
]
for old, new, expected in replacements:
    found = text.count(old)
    if found != expected:
        raise RuntimeError(f'expected {expected} occurrence(s), found {found}: {old!r}')
    text = text.replace(old, new)
if 'Config.isSpoofEnabled' in text:
    raise RuntimeError('KeystoreInterceptor still depends on Identity Spoof Engine')
path.write_text(text)
print('Keystore core lifecycle fix applied')
