package cleveres.tricky.cleverestech.util

class PackageTrie<T> {
    private class Node<T> {
        val children = HashMap<Char, Node<T>>()
        var value: T? = null
        var isWildcard = false
        var wildcardValue: T? = null
    }

    private val root = Node<T>()
    var size = 0
        private set

    fun add(rule: String, value: T) {
        size++
        var current = root
        var effectiveRule = rule
        var isWildcardRule = false
        if (rule.endsWith("*")) {
            effectiveRule = rule.dropLast(1)
            isWildcardRule = true
        }

        for (char in effectiveRule) {
            current = current.children.computeIfAbsent(char) { Node() }
        }

        if (isWildcardRule) {
            current.isWildcard = true
            current.wildcardValue = value
        } else {
            current.value = value
        }
    }

    fun clear() {
        root.children.clear()
        root.value = null
        root.isWildcard = false
        root.wildcardValue = null
        size = 0
    }

    fun get(pkgName: String): T? {
        var current = root
        // Keep track of the most specific wildcard match found so far
        var bestMatch: T? = if (current.isWildcard) current.wildcardValue else null

        for (i in pkgName.indices) {
            val char = pkgName[i]
            val next = current.children[char] ?: return bestMatch
            current = next
            if (current.isWildcard) {
                bestMatch = current.wildcardValue
            }
        }
        // If we reached the end of the string, check for exact match value first, then fallback to wildcard
        return current.value ?: bestMatch
    }

    fun matches(pkgName: String): Boolean {
        return get(pkgName) != null
    }

    fun isEmpty() = size == 0
}
