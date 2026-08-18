from pathlib import Path
import re
import textwrap


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one match, found {count}")
    return text.replace(old, new, 1)


def regex_once(text: str, pattern: str, replacement: str, label: str) -> str:
    updated, count = re.subn(pattern, replacement, text, count=1, flags=re.S)
    if count != 1:
        raise SystemExit(f"{label}: expected exactly one regex match, found {count}")
    return updated


# Backend: authenticate the inherited root-only broker before dropping credentials,
# but bind/listen only after the permanent nobody uid/gid drop. Android validates
# the backend with SO_PEERCRED, so listener creation must observe the final identity.
backend_path = Path("rust/backend/src/main.rs")
backend = backend_path.read_text()
backend = replace_once(
    backend,
    "fn run() -> io::Result<()> {\n"
    "    let adapter_pid = parse_adapter_pid()?;\n"
    "    backend_instance::initialize()?;\n"
    "    let mut broker = take_broker_stream()?;\n"
    "    let listener = bind_abstract(BACKEND_SOCKET_NAME)?;\n"
    "    harden_process()?;\n"
    "    serve(listener, adapter_pid, &mut broker)\n"
    "}",
    "fn run() -> io::Result<()> {\n"
    "    let adapter_pid = parse_adapter_pid()?;\n"
    "    // Authenticate the inherited privileged broker before permanently dropping\n"
    "    // credentials and exposing the backend listener to Android clients.\n"
    "    let mut broker = take_broker_stream()?;\n"
    "    harden_process()?;\n"
    "    backend_instance::initialize()?;\n"
    "    let listener = bind_abstract(BACKEND_SOCKET_NAME)?;\n"
    "    serve(listener, adapter_pid, &mut broker)\n"
    "}",
    "backend startup order",
)
backend_path.write_text(backend)


# Android service: register the native WebUI adapter before waiting for Rust backend
# readiness. That keeps diagnostics/resource/file requests reachable during backend
# recovery and prevents the adapter-unavailable deadlock seen on device.
main_path = Path("service/src/main/java/cleveres/tricky/cleverestech/Main.kt")
main = main_path.read_text()
anchor = "private const val BACKEND_STARTUP_TIMEOUT_MS = 30_000L\n"
helper = textwrap.dedent(
    '''\
    private const val WEB_UI_START_ATTEMPTS = 12
    private const val WEB_UI_START_INITIAL_DELAY_MS = 50L
    private const val WEB_UI_START_MAX_DELAY_MS = 1_000L

    private fun startWebUiBridge(
        configDir: File,
        isTampered: Boolean,
    ): WebUiBridge? {
        val bridge = WebUiBridge(WebServer(0, configDir, isTampered), configDir)
        var retryDelayMs = WEB_UI_START_INITIAL_DELAY_MS
        repeat(WEB_UI_START_ATTEMPTS) { attempt ->
            try {
                bridge.start()
                if (attempt > 0) {
                    Logger.i("Native WebUI adapter registered after ${attempt + 1} attempts")
                }
                return bridge
            } catch (error: Exception) {
                Logger.e("Native WebUI adapter registration attempt ${attempt + 1} failed", error)
            }
            if (attempt + 1 < WEB_UI_START_ATTEMPTS) {
                try {
                    Thread.sleep(retryDelayMs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return null
                }
                retryDelayMs = minOf(retryDelayMs * 2, WEB_UI_START_MAX_DELAY_MS)
            }
        }
        return null
    }

    '''
)
main = replace_once(main, anchor, anchor + helper, "WebUI helper insertion")
main = regex_once(
    main,
    r'''        if \(isTampered\) \{\n            runCatching \{ WebUiBridge\(WebServer\(0, configDir, true\), configDir\)\.start\(\) \}\n                \.onFailure \{ Logger\.e\("Failed to start native WebUI lockdown endpoint", it\) \}\n            Logger\.e\("Main: Running in tamper lockdown; native interceptors will not be registered"\)\n            while \(true\) \{\n                delay\(60000\)\n            \}\n        \}\n\n        while \(!NativeBackend\.awaitReady\(BACKEND_STARTUP_TIMEOUT_MS\)\) \{''',
    textwrap.dedent(
        '''\
                val webUiBridge = startWebUiBridge(configDir, isTampered)
                if (webUiBridge == null) {
                    Logger.e("Main: Native WebUI adapter could not register; exiting for supervisor retry")
                    return@runBlocking
                }

                if (isTampered) {
                    Logger.e("Main: Running in tamper lockdown; native interceptors will not be registered")
                    while (true) {
                        delay(60000)
                    }
                }

                while (!NativeBackend.awaitReady(BACKEND_STARTUP_TIMEOUT_MS)) {
        '''
    ),
    "early WebUI registration",
)
# The replacement above was dedented for readability; restore runBlocking indentation.
early = textwrap.dedent(
    '''\
    val webUiBridge = startWebUiBridge(configDir, isTampered)
    if (webUiBridge == null) {
        Logger.e("Main: Native WebUI adapter could not register; exiting for supervisor retry")
        return@runBlocking
    }

    if (isTampered) {
        Logger.e("Main: Running in tamper lockdown; native interceptors will not be registered")
        while (true) {
            delay(60000)
        }
    }

    while (!NativeBackend.awaitReady(BACKEND_STARTUP_TIMEOUT_MS)) {
    '''
)
# re-indent only the newly inserted block; the exact first occurrence is unique.
main = replace_once(main, early, textwrap.indent(early, "        "), "WebUI block indentation")
main = regex_once(
    main,
    r'''        try \{\n            WebUiBridge\(WebServer\(0, configDir\), configDir\)\.start\(\)\n        \} catch \(e: Exception\) \{\n            KeyboxDirectoryRefreshWatcher\.stop\(\)\n            CertificatePolicyWatcher\.stop\(\)\n            Logger\.e\("Failed to start native WebUI bridge", e\)\n            Logger\.e\("Main: Exiting so the module supervisor can restore native WebUI service"\)\n            return@runBlocking\n        \}\n\n''',
    "",
    "remove late WebUI registration",
)
main_path.write_text(main)


# Encryptor: retain FLAG_SECURE, Rust crypto and noBackup storage, while restoring the
# black/off-white visual identity and an explicit in-app language chooser.
activity_path = Path("encryptor-app/src/main/java/cleveres/tricky/encryptor/SecureMainActivity.kt")
activity = activity_path.read_text()
activity = replace_once(
    activity,
    "class SecureMainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {",
    "class SecureMainActivity : ComponentActivity() {\n"
    "    override fun attachBaseContext(newBase: Context) {\n"
    "        super.attachBaseContext(LocaleController.wrap(newBase))\n"
    "    }\n\n"
    "    override fun onCreate(savedInstanceState: Bundle?) {",
    "pre-33 locale context",
)
activity = replace_once(
    activity,
    "MaterialTheme(colorScheme = darkColorScheme()) {",
    "MaterialTheme(colorScheme = CleveresVaultColors) {",
    "monochrome Compose theme",
)
activity = replace_once(
    activity,
    "                Text(text = stringResource(R.string.vault_summary, files.size, formatBytes(files.sumOf { it.length() })))\n",
    "                Column(horizontalAlignment = Alignment.End) {\n"
    "                    LanguagePicker()\n"
    "                    Text(\n"
    "                        text = stringResource(R.string.vault_summary, files.size, formatBytes(files.sumOf { it.length() })),\n"
    "                        style = MaterialTheme.typography.bodySmall,\n"
    "                    )\n"
    "                }\n",
    "in-app language picker",
)
activity_path.write_text(activity)


experience_path = Path("encryptor-app/src/main/java/cleveres/tricky/encryptor/VaultExperience.kt")
experience_path.write_text(
    textwrap.dedent(
        '''\
        package cleveres.tricky.encryptor

        import android.app.Activity
        import android.app.LocaleManager
        import android.content.Context
        import android.content.ContextWrapper
        import android.content.res.Configuration
        import android.os.Build
        import android.os.LocaleList
        import androidx.compose.foundation.layout.Row
        import androidx.compose.foundation.layout.fillMaxWidth
        import androidx.compose.foundation.layout.heightIn
        import androidx.compose.foundation.layout.padding
        import androidx.compose.foundation.lazy.LazyColumn
        import androidx.compose.foundation.lazy.items
        import androidx.compose.material.icons.Icons
        import androidx.compose.material.icons.filled.Check
        import androidx.compose.material.icons.filled.Language
        import androidx.compose.material3.AlertDialog
        import androidx.compose.material3.Icon
        import androidx.compose.material3.OutlinedButton
        import androidx.compose.material3.Text
        import androidx.compose.material3.TextButton
        import androidx.compose.material3.darkColorScheme
        import androidx.compose.runtime.Composable
        import androidx.compose.runtime.getValue
        import androidx.compose.runtime.mutableStateOf
        import androidx.compose.runtime.remember
        import androidx.compose.runtime.setValue
        import androidx.compose.ui.Alignment
        import androidx.compose.ui.Modifier
        import androidx.compose.ui.graphics.Color
        import androidx.compose.ui.platform.LocalContext
        import androidx.compose.ui.res.stringResource
        import androidx.compose.ui.unit.dp
        import java.util.Locale

        internal val CleveresVaultColors =
            darkColorScheme(
                background = Color(0xFF0A0A0B),
                surface = Color(0xFF151516),
                surfaceVariant = Color(0xFF111113),
                primary = Color(0xFFF4F4F5),
                onPrimary = Color(0xFF0A0A0B),
                primaryContainer = Color(0xFFE7E5E4),
                onPrimaryContainer = Color(0xFF0A0A0B),
                secondary = Color(0xFFE7E5E4),
                onSecondary = Color(0xFF0A0A0B),
                onBackground = Color(0xFFF4F4F5),
                onSurface = Color(0xFFF4F4F5),
                onSurfaceVariant = Color(0xFF9CA3AF),
                outline = Color(0xFF303033),
                error = Color(0xFFFB7185),
            )

        internal object LocaleController {
            internal data class Language(
                val tag: String,
                val label: String,
            )

            internal val languages =
                listOf(
                    Language("", "System"),
                    Language("en", "English"),
                    Language("tr", "Türkçe"),
                    Language("de", "Deutsch"),
                    Language("es", "Español"),
                    Language("ru", "Русский"),
                    Language("ar", "العربية"),
                    Language("hi", "हिन्दी"),
                    Language("in", "Bahasa Indonesia"),
                    Language("zh-CN", "简体中文"),
                )

            private const val PREFS = "cleveres_locale"
            private const val KEY = "language_tag"

            private fun storedTag(context: Context): String =
                context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "").orEmpty()

            private fun normalizeTag(tag: String): String = if (tag == "id") "in" else tag

            internal fun selectedTag(context: Context): String =
                if (Build.VERSION.SDK_INT >= 33) {
                    normalizeTag(
                        context.getSystemService(LocaleManager::class.java)
                            .applicationLocales
                            .toLanguageTags()
                            .substringBefore(','),
                    )
                } else {
                    normalizeTag(storedTag(context))
                }

            internal fun apply(
                activity: Activity,
                tag: String,
            ) {
                require(languages.any { it.tag == tag }) { "Unsupported locale" }
                if (Build.VERSION.SDK_INT >= 33) {
                    activity.getSystemService(LocaleManager::class.java).applicationLocales =
                        if (tag.isBlank()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
                } else {
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                        .edit()
                        .apply {
                            if (tag.isBlank()) remove(KEY) else putString(KEY, tag)
                        }
                        .apply()
                    activity.recreate()
                }
            }

            internal fun wrap(context: Context): Context {
                if (Build.VERSION.SDK_INT >= 33) return context
                val tag = storedTag(context)
                if (tag.isBlank()) return context
                val locale = Locale.forLanguageTag(tag)
                val configuration = Configuration(context.resources.configuration)
                configuration.setLocale(locale)
                configuration.setLayoutDirection(locale)
                return context.createConfigurationContext(configuration)
            }
        }

        private fun Context.findActivity(): Activity? {
            var current: Context = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return current as? Activity
        }

        @Composable
        internal fun LanguagePicker() {
            val context = LocalContext.current
            val selectedTag = LocaleController.selectedTag(context)
            val selected = LocaleController.languages.firstOrNull { it.tag == selectedTag } ?: LocaleController.languages.first()
            val systemDefault = stringResource(R.string.system_default)
            var open by remember { mutableStateOf(false) }

            OutlinedButton(onClick = { open = true }) {
                Icon(Icons.Default.Language, contentDescription = stringResource(R.string.language))
                Text(
                    text = if (selected.tag.isBlank()) systemDefault else selected.label,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            if (open) {
                AlertDialog(
                    onDismissRequest = { open = false },
                    title = { Text(stringResource(R.string.language)) },
                    text = {
                        LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                            items(LocaleController.languages, key = { it.tag }) { language ->
                                TextButton(
                                    onClick = {
                                        open = false
                                        context.findActivity()?.let { LocaleController.apply(it, language.tag) }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = if (language.tag.isBlank()) systemDefault else language.label,
                                            modifier = Modifier.weight(1f),
                                        )
                                        if (language.tag == selected.tag) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { open = false }) {
                            Text(stringResource(R.string.cancel))
                        }
                    },
                )
            }
        }
        '''
    )
)


translations = {
    "values/strings.xml": ("Language", "System default"),
    "values-tr/strings.xml": ("Dil", "Sistem varsayılanı"),
    "values-de/strings.xml": ("Sprache", "Systemstandard"),
    "values-es/strings.xml": ("Idioma", "Predeterminado del sistema"),
    "values-ru/strings.xml": ("Язык", "Системный язык"),
    "values-in/strings.xml": ("Bahasa", "Default sistem"),
    "values-hi/strings.xml": ("भाषा", "सिस्टम डिफ़ॉल्ट"),
    "values-ar/strings.xml": ("اللغة", "إعداد النظام الافتراضي"),
    "values-zh-rCN/strings.xml": ("语言", "系统默认"),
}
res_root = Path("encryptor-app/src/main/res")
for relative, (language, system_default) in translations.items():
    path = res_root / relative
    text = path.read_text()
    if 'name="language"' in text or 'name="system_default"' in text:
        raise SystemExit(f"locale labels already present in {path}")
    path.write_text(
        text.replace(
            "</resources>",
            f'    <string name="language">{language}</string>\n'
            f'    <string name="system_default">{system_default}</string>\n'
            "</resources>",
        )
    )


# Permanent regression gates for the two device-only startup contracts.
startup_test_path = Path("service/src/test/java/cleveres/tricky/cleverestech/MainStartupContractTest.kt")
startup_test_path.write_text(
    textwrap.dedent(
        '''\
        package cleveres.tricky.cleverestech

        import java.io.File
        import org.junit.Assert.assertTrue
        import org.junit.Test

        class MainStartupContractTest {
            @Test
            fun `web ui adapter registers before backend readiness gate`() {
                val root = locateRoot()
                val source = File(root, "service/src/main/java/cleveres/tricky/cleverestech/Main.kt").readText()
                val entry = source.indexOf("fun main(args: Array<String>)")
                val registration = source.indexOf("startWebUiBridge(configDir, isTampered)", entry)
                val backendWait = source.indexOf("NativeBackend.awaitReady(BACKEND_STARTUP_TIMEOUT_MS)", entry)
                assertTrue(entry >= 0)
                assertTrue(registration > entry)
                assertTrue(backendWait > registration)
            }

            private fun locateRoot(): File {
                var current = File(requireNotNull(System.getProperty("user.dir"))).canonicalFile
                repeat(6) {
                    if (File(current, "service").isDirectory && File(current, "rust").isDirectory) return current
                    current = current.parentFile ?: return@repeat
                }
                error("Repository root not found")
            }
        }
        '''
    )
)

rust_test_path = Path("rust/backend/tests/startup_privilege_order.rs")
rust_test_path.parent.mkdir(parents=True, exist_ok=True)
rust_test_path.write_text(
    textwrap.dedent(
        '''\
        #[test]
        fn backend_listener_is_created_only_after_privilege_drop() {
            let source = include_str!("../src/main.rs");
            let start = source.find("fn run() -> io::Result<()> {").expect("run function");
            let end = source[start..]
                .find("\\n}\\n\\nfn parse_adapter_pid")
                .map(|offset| start + offset)
                .expect("run function end");
            let run = &source[start..end];
            let broker = run.find("take_broker_stream()?").expect("broker authentication");
            let harden = run.find("harden_process()?").expect("privilege drop");
            let listener = run.find("bind_abstract(BACKEND_SOCKET_NAME)?").expect("backend listener");
            assert!(broker < harden);
            assert!(harden < listener);
        }
        '''
    )
)

mobile_test_path = Path("encryptor-app/src/test/java/cleveres/tricky/encryptor/MobileSecurityContractTest.kt")
mobile_test = mobile_test_path.read_text()
marker = '        assertTrue(activity.contains("WindowManager.LayoutParams.FLAG_SECURE"))\n'
mobile_test = replace_once(
    mobile_test,
    marker,
    marker
    + '        assertTrue(activity.contains("CleveresVaultColors"))\n'
    + '        assertTrue(activity.contains("LanguagePicker()"))\n',
    "mobile UX contract",
)
mobile_test_path.write_text(mobile_test)
