package cleveres.tricky.cleverestech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Tests that validate the WebUI label fix for renderResourceTable.
 *
 * The t() translation function must always be called with a default value
 * in the resource table data-label attributes, preventing "undefined" from
 * appearing when no language file is loaded.
 */
class WebUiLabelFixTest {

    private lateinit var webServerContent: String

    @Before
    fun setup() {
        webServerContent = serviceMainFile("WebServer.kt").readText()
    }

    @Test
    fun testTranslationFunctionHasDefaultForFeature() {
        assertTrue(
            "renderResourceTable must call t('col_feature', 'Feature') with default value to prevent undefined label",
            webServerContent.contains("t('col_feature', 'Feature')")
        )
    }

    @Test
    fun testTranslationFunctionHasDefaultForStatus() {
        assertTrue(
            "renderResourceTable must call t('col_status', 'Status') with default value to prevent undefined label",
            webServerContent.contains("t('col_status', 'Status')")
        )
    }

    @Test
    fun testTranslationFunctionHasDefaultForRam() {
        assertTrue(
            "renderResourceTable must call t('col_ram', 'Est. RAM') with default value to prevent undefined label",
            webServerContent.contains("t('col_ram', 'Est. RAM')")
        )
    }

    @Test
    fun testTranslationFunctionHasDefaultForCpu() {
        assertTrue(
            "renderResourceTable must call t('col_cpu', 'Est. CPU') with default value to prevent undefined label",
            webServerContent.contains("t('col_cpu', 'Est. CPU')")
        )
    }

    @Test
    fun testTranslationFunctionHasDefaultForSecurity() {
        assertTrue(
            "renderResourceTable must call t('col_security', 'Security Impact') with default value to prevent undefined label",
            webServerContent.contains("t('col_security', 'Security Impact')")
        )
    }

    @Test
    fun testNoUndefinedProneTranslationCalls() {
        // Ensure no bare t('col_xxx') calls without defaults remain in the resource table rendering
        val resourceTableSection = webServerContent.substringAfter("function renderResourceTable")
            .substringBefore("function downloadLangTemplate")
        assertFalse(
            "renderResourceTable must not have t() calls without a default value (causes undefined labels)",
            resourceTableSection.contains(Regex("""t\('col_\w+'\)"""))
        )
    }

    @Test
    fun testLanguageSupportInGuideTab() {
        val guideSection = webServerContent.substringAfter("id=\"guide\"")
            .substringBefore("id=\"editor\"")
        assertTrue(
            "Language Support section must be in the Guide tab for better UX layout",
            guideSection.contains("Language Support")
        )
    }

    @Test
    fun testLanguageSupportNotInInfoTab() {
        val infoSection = webServerContent.substringAfter("id=\"info\"")
            .substringBefore("id=\"guide\"")
        assertFalse(
            "Language Support section must NOT be in the Info tab (clutters resource monitor area)",
            infoSection.contains("<h3>Language Support</h3>")
        )
    }

    @Test
    fun testNotificationIconsUseTextLabels() {
        assertTrue(
            "Success notification icon should use text label for cross-WebView compatibility",
            webServerContent.contains("\">OK</div>") || webServerContent.contains("\">OK</span>")
        )
        assertTrue(
            "Error notification icon should use text label for cross-WebView compatibility",
            webServerContent.contains("\">!</div>") || webServerContent.contains("\">!</span>")
        )
    }

    @Test
    fun testFrontendDebugLogging() {
        assertTrue(
            "init() must log config loading for debugging",
            webServerContent.contains("[CleveresTricky] init: loading config")
        )
        assertTrue(
            "loadLanguage() must log language fetch for debugging",
            webServerContent.contains("[CleveresTricky] loadLanguage:")
        )
        assertTrue(
            "loadFile() must log file loading for debugging",
            webServerContent.contains("[CleveresTricky] loadFile:")
        )
    }
}
