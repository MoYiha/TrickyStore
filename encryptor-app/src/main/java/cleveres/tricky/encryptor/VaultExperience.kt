package cleveres.tricky.encryptor

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import java.util.Locale

internal val CleveresVaultColors =
    darkColorScheme(
        background = Color(0xFF080808),
        surface = Color(0xFF121212),
        surfaceVariant = Color(0xFF181818),
        primary = Color(0xFFF5F5F5),
        onPrimary = Color(0xFF090909),
        primaryContainer = Color(0xFFF5F5F5),
        onPrimaryContainer = Color(0xFF090909),
        secondary = Color(0xFFD4D4D4),
        onSecondary = Color(0xFF090909),
        secondaryContainer = Color(0xFF242424),
        onSecondaryContainer = Color(0xFFF5F5F5),
        onBackground = Color(0xFFF5F5F5),
        onSurface = Color(0xFFF5F5F5),
        onSurfaceVariant = Color(0xFFAAAAAA),
        outline = Color(0xFF3A3A3A),
        outlineVariant = Color(0xFF252525),
        error = Color(0xFFF5F5F5),
        onError = Color(0xFF090909),
        errorContainer = Color(0xFF242424),
        onErrorContainer = Color(0xFFF5F5F5),
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
    val languageLabel = stringResource(R.string.language)
    var open by remember { mutableStateOf(false) }

    Box {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            tonalElevation = 0.dp,
        ) {
            IconButton(
                onClick = { open = true },
                modifier = Modifier.size(44.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = languageLabel,
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.widthIn(min = 220.dp),
            containerColor = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            LocaleController.languages.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = if (language.tag.isBlank()) systemDefault else language.label,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        context.findActivity()?.let { LocaleController.apply(it, language.tag) }
                    },
                    trailingIcon = {
                        if (language.tag == selected.tag) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    },
                )
            }
        }
    }
}