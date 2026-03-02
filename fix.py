import re

with open('service/src/main/java/cleveres/tricky/cleverestech/Config.kt', 'r') as f:
    content = f.read()

# Add APPLY_PROFILE_FILE constant
content = content.replace(
    'private const val RANDOM_DRM_ON_BOOT_FILE = "random_drm_on_boot"',
    'private const val RANDOM_DRM_ON_BOOT_FILE = "random_drm_on_boot"\n    private const val APPLY_PROFILE_FILE = "apply_profile"'
)

# Add applyProfile function
apply_profile_func = """
    private fun applyProfileFromFile(f: File?) = runCatching {
        if (f == null || !f.exists()) return@runCatching
        val profileName = f.readText().trim()
        if (profileName.isNotEmpty()) {
            applyProfile(profileName)
        }
        f.delete() // One-shot trigger
    }.onFailure {
        Logger.e("failed to apply profile from file", it)
    }

    fun applyProfile(profileName: String) {
        Logger.i("Applying profile: $profileName")
        when (profileName.lowercase()) {
            "godprofile" -> {
                SecureFile.touch(File(root, GLOBAL_MODE_FILE), 384)
                SecureFile.touch(File(root, RKP_BYPASS_FILE), 384)
                File(root, TEE_BROKEN_MODE_FILE).delete()
                SecureFile.touch(File(root, RANDOM_ON_BOOT_FILE), 384)
                SecureFile.touch(File(root, HIDE_SENSITIVE_PROPS_FILE), 384)

                // Set DRM fix content
                val drmContent = "ro.netflix.bsp_rev=0\\ndrm.service.enabled=true\\nro.com.google.widevine.level=1\\nro.crypto.state=encrypted\\n"
                SecureFile.writeText(File(root, DRM_FIX_FILE), drmContent)
            }
            "dailyuse" -> {
                File(root, GLOBAL_MODE_FILE).delete()
                SecureFile.touch(File(root, RKP_BYPASS_FILE), 384)
                File(root, TEE_BROKEN_MODE_FILE).delete()
                File(root, RANDOM_ON_BOOT_FILE).delete()
                SecureFile.touch(File(root, HIDE_SENSITIVE_PROPS_FILE), 384)
                File(root, DRM_FIX_FILE).delete()
            }
            "minimal" -> {
                File(root, GLOBAL_MODE_FILE).delete()
                File(root, RKP_BYPASS_FILE).delete()
                File(root, TEE_BROKEN_MODE_FILE).delete()
                File(root, RANDOM_ON_BOOT_FILE).delete()
                File(root, HIDE_SENSITIVE_PROPS_FILE).delete()
                File(root, DRM_FIX_FILE).delete()
            }
            "default" -> {
                // Same as dailyuse for now, could be customized
                File(root, GLOBAL_MODE_FILE).delete()
                SecureFile.touch(File(root, RKP_BYPASS_FILE), 384)
                File(root, TEE_BROKEN_MODE_FILE).delete()
                File(root, RANDOM_ON_BOOT_FILE).delete()
                File(root, HIDE_SENSITIVE_PROPS_FILE).delete()
                File(root, DRM_FIX_FILE).delete()
            }
            else -> {
                Logger.e("Unknown profile: $profileName")
            }
        }
    }
"""

content = content.replace(
    'private fun checkRandomDrm() {',
    apply_profile_func + '\n    private fun checkRandomDrm() {'
)

# Add HIDE_SENSITIVE_PROPS_FILE if not exists
if 'private const val HIDE_SENSITIVE_PROPS_FILE' not in content:
    content = content.replace(
        'private const val RANDOM_DRM_ON_BOOT_FILE = "random_drm_on_boot"',
        'private const val RANDOM_DRM_ON_BOOT_FILE = "random_drm_on_boot"\n    private const val HIDE_SENSITIVE_PROPS_FILE = "hide_sensitive_props"'
    )

# Update ConfigObserver
content = content.replace(
    'KEYBOX_SOURCE_FILE -> updateKeyboxSource(f)',
    'KEYBOX_SOURCE_FILE -> updateKeyboxSource(f)\n                APPLY_PROFILE_FILE -> applyProfileFromFile(f)'
)

with open('service/src/main/java/cleveres/tricky/cleverestech/Config.kt', 'w') as f:
    f.write(content)
