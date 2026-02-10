package cleveres.tricky.cleverestech.util

class PackageTrie<T> {
    private class Node<T> {
        // Optimization: Use parallel arrays instead of HashMap to reduce memory overhead and avoid boxing.
        // For trie nodes, the number of children is usually small (often 1), making linear scan faster than hashing.
        var keys: CharArray = CharArray(0)
        var children: Array<Node<T>?> = emptyArray()

        var value: T? = null
        var isWildcard = false
        var wildcardValue: T? = null

        fun getChild(char: Char): Node<T>? {
            val k = keys
            // Optimized linear scan
            for (i in k.indices) {
                if (k[i] == char) {
                    return children[i]
                }
            }
            return null
        }

        fun addChild(char: Char): Node<T> {
            val k = keys
            // Check if child already exists
            for (i in k.indices) {
                if (k[i] == char) {
                    return children[i]!!
                }
            }

            // Add new child
            val newSize = k.size + 1
            val newKeys = k.copyOf(newSize)
            newKeys[k.size] = char

            // Resize children array
            // If the array is empty, we must create a new one of correct type.
            // Using arrayOfNulls<Node<T>>(newSize) works as generic array creation workaround
            val newChildren = if (children.isEmpty()) {
                arrayOfNulls<Node<T>>(newSize)
            } else {
                children.copyOf(newSize)
            }

            val newNode = Node<T>()
            newChildren[children.size] = newNode

            keys = newKeys
            children = newChildren

            return newNode
        }
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
            current = current.addChild(char)
        }

        if (isWildcardRule) {
            current.isWildcard = true
            current.wildcardValue = value
        } else {
            current.value = value
        }
    }

    fun clear() {
        // Clear root children to release memory
        root.keys = CharArray(0)
        root.children = emptyArray()
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
            val next = current.getChild(char) ?: return bestMatch
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
