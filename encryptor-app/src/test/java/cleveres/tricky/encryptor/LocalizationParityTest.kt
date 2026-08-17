package cleveres.tricky.encryptor

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalizationParityTest {
    @Test
    fun `all supported mobile locales have identical string and placeholder contracts`() {
        val resources = locate("src/main/res", "encryptor-app/src/main/res")
        val defaultStrings = parseStrings(File(resources, "values/strings.xml"))
        assertTrue(defaultStrings.isNotEmpty())

        val localeDirectories =
            listOf(
                "values-tr",
                "values-zh-rCN",
                "values-es",
                "values-de",
                "values-ru",
                "values-in",
                "values-hi",
                "values-ar",
            )
        for (directory in localeDirectories) {
            val translated = parseStrings(File(resources, "$directory/strings.xml"))
            assertEquals("String keys differ for $directory", defaultStrings.keys, translated.keys)
            for (key in defaultStrings.keys) {
                assertEquals(
                    "Format arguments differ for $directory/$key",
                    placeholders(defaultStrings.getValue(key)),
                    placeholders(translated.getValue(key)),
                )
            }
        }
    }

    private fun parseStrings(file: File): Map<String, String> {
        assertTrue("Missing ${file.path}", file.isFile)
        val expression = Regex("<string\\s+name=\"([^\"]+)\">([\\s\\S]*?)</string>")
        return expression.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }
    }

    private fun placeholders(value: String): List<String> =
        Regex("%\\d+\\$[sd]").findAll(value).map { it.value }.toList()

    private fun locate(vararg candidates: String): File =
        candidates.map(::File).firstOrNull(File::exists)
            ?: error("Could not locate ${candidates.joinToString()}")
}
