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
import androidx.core.content.edit
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
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit {
                if (tag.isBlank()) remove(KEY) else putString(KEY, tag)
            }
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
