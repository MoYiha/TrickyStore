package cleveres.tricky.cleverestech

import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class NullablePolicyJsonContractTest {
    @Test
    fun `nullable policy strings never use coercing optString reads`() {
        val sourceRoot = Path.of("src", "main", "java")
        val policyState = sourceRoot.resolve("cleveres/tricky/cleverestech/PolicyState.kt")
        val policySource = Files.readString(policyState)
        val nullableStringFields =
            Regex("""nullableString\(\s*[^,]+,\s*"([^"]+)"\s*\)""")
                .findAll(policySource)
                .map { it.groupValues[1] }
                .toSortedSet()

        if (nullableStringFields.isEmpty()) {
            fail("Could not derive nullable policy string fields from PolicyState parser")
        }

        val violations = mutableListOf<String>()
        Files.walk(sourceRoot).use { paths ->
            paths
                .filter { Files.isRegularFile(it) && it.toString().endsWith(".kt") }
                .forEach { path ->
                    val source = Files.readString(path)
                    nullableStringFields.forEach { field ->
                        val pattern = Regex("\\.optString\\(\\s*\"${Regex.escape(field)}\"")
                        if (pattern.containsMatchIn(source)) {
                            violations += "$path uses optString(\"$field\") for a nullable policy string"
                        }
                    }
                }
        }

        if (violations.isNotEmpty()) {
            fail(
                buildString {
                    appendLine("Nullable policy strings must preserve JSON null as absence before string conversion.")
                    appendLine("Derived nullable fields: ${nullableStringFields.joinToString()}")
                    appendLine("Use has/isNull plus a strict String type check; optString can collapse platform sentinels into valid domain strings.")
                    violations.sorted().forEach(::appendLine)
                },
            )
        }
    }
}
