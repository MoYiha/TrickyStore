package cleveres.tricky.cleverestech

import cleveres.tricky.cleverestech.util.PackageTrie

/** Keeps the bounded two-argument parser call isolated from the large Config state holder. */
internal fun parseDrmPackages(
    lines: Sequence<String>,
    maxRules: Int,
): PackageTrie<Boolean> {
    require(maxRules > 0)
    val packages = PackageTrie<Boolean>()
    var ruleCount = 0
    lines.forEach { line ->
        val packageName = line.trim()
        if (packageName.isEmpty() || packageName.startsWith("#")) return@forEach
        require(
            packageName.length <= 255 &&
                packageName.all { character ->
                    character.isLetterOrDigit() || character == '_' || character == '.' || character == '*'
                },
        ) { "Invalid DRM package rule" }
        require(++ruleCount <= maxRules) { "Too many DRM package rules" }
        packages.add(packageName, true)
    }
    return packages
}
