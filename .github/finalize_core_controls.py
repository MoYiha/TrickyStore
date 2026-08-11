from pathlib import Path


def replace(path: str, old: str, new: str, count: int = 1) -> None:
    file = Path(path)
    text = file.read_text()
    found = text.count(old)
    if found != count:
        raise RuntimeError(f"{path}: expected {count}, found {found}: {old[:120]!r}")
    file.write_text(text.replace(old, new, count))


replace(
    "service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt",
    '''        private val WEB_UI_SETTINGS =
            linkedSetOf(
                "spoof_enabled",
                "spoof_build_identity",
                "global_mode",
                "tee_broken_mode",
                "auto_keybox_check",
                "random_on_boot",
                "hide_sensitive_props",
                "spoof_region_cn",
                "telephony",
                "rkp_passthrough",
                "drm_passthrough",
            )
''',
    '''        private val WEB_UI_SETTINGS =
            linkedSetOf(
                "spoof_enabled",
                "spoof_build_identity",
                "global_mode",
                "auto_keybox_check",
                "random_on_boot",
                "spoof_region_cn",
                "telephony",
                "rkp_passthrough",
                "drm_passthrough",
            )
''',
)

replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''                removeConfigFiles(
                    TEE_BROKEN_MODE_FILE,
                    BootLogic.FILE_SPOOF_CN,
                    RKP_PASSTHROUGH_FILE,
                    DRM_PASSTHROUGH_FILE,
                )
                SecureFile.touch(File(root, RANDOM_ON_BOOT_FILE), 384)
                SecureFile.touch(File(root, BootLogic.FILE_HIDE_PROPS), 384)
''',
    '''                removeConfigFiles(
                    TEE_BROKEN_MODE_FILE,
                    BootLogic.FILE_HIDE_PROPS,
                    BootLogic.FILE_SPOOF_CN,
                    RKP_PASSTHROUGH_FILE,
                    DRM_PASSTHROUGH_FILE,
                )
                SecureFile.touch(File(root, RANDOM_ON_BOOT_FILE), 384)
''',
)

replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''                removeConfigFiles(
                    GLOBAL_MODE_FILE,
                    TEE_BROKEN_MODE_FILE,
                    RANDOM_ON_BOOT_FILE,
                    BootLogic.FILE_SPOOF_CN,
                    TELEPHONY_FILE,
                    BUILD_IDENTITY_FILE,
                )
                SecureFile.touch(File(root, BootLogic.FILE_HIDE_PROPS), 384)
''',
    '''                removeConfigFiles(
                    GLOBAL_MODE_FILE,
                    TEE_BROKEN_MODE_FILE,
                    RANDOM_ON_BOOT_FILE,
                    BootLogic.FILE_HIDE_PROPS,
                    BootLogic.FILE_SPOOF_CN,
                    TELEPHONY_FILE,
                    BUILD_IDENTITY_FILE,
                )
''',
)

replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''            "minimal" -> {
                SecureFile.touch(File(root, TEE_BROKEN_MODE_FILE), 384)
                removeConfigFiles(
                    SPOOF_ENABLED_FILE,
                    BUILD_IDENTITY_FILE,
                    GLOBAL_MODE_FILE,
                    RANDOM_ON_BOOT_FILE,
                    BootLogic.FILE_HIDE_PROPS,
''',
    '''            "minimal" -> {
                removeConfigFiles(
                    SPOOF_ENABLED_FILE,
                    BUILD_IDENTITY_FILE,
                    GLOBAL_MODE_FILE,
                    TEE_BROKEN_MODE_FILE,
                    RANDOM_ON_BOOT_FILE,
                    BootLogic.FILE_HIDE_PROPS,
''',
)

replace(
    "service/src/main/java/cleveres/tricky/cleverestech/Config.kt",
    '''        Logger.i("TEE broken mode is ${if (isTeeBrokenMode) "enabled" else "disabled"}")
''',
    '''        Logger.i("Legacy TEE safe mode flag is ${if (isTeeBrokenMode) "present" else "absent"}; core protection is unchanged")
''',
)

# Assert that legacy core controls are not part of the toggle allowlist.
web = Path("service/src/main/java/cleveres/tricky/cleverestech/WebServer.kt").read_text()
start = web.index("private val WEB_UI_SETTINGS")
end = web.index("private val EDITABLE_CONFIG_FILES", start)
settings = web[start:end]
if '"tee_broken_mode"' in settings or '"hide_sensitive_props"' in settings:
    raise RuntimeError("legacy core settings remain remotely toggleable")

print("core control cleanup applied")
