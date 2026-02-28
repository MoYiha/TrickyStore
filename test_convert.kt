fun main() {
    fun String.convertPatchLevel(long: Boolean) = kotlin.runCatching {
        if (contains("-")) {
            val l = split("-")
            if (long) l[0].toInt() * 10000 + l[1].toInt() * 100 + l[2].toInt()
            else l[0].toInt() * 100 + l[1].toInt()
        } else {
            val year = substring(0, 4).toInt()
            val month = substring(4, 6).toInt()
            if (long) {
                val day = if (length >= 8) substring(6, 8).toInt() else 1
                year * 10000 + month * 100 + day
            } else {
                year * 100 + month
            }
        }
    }.getOrDefault(202404)

    println("2023-12-05".convertPatchLevel(false))
    println(kotlin.runCatching { "2023-12-05".convertPatchLevel(false) }.getOrNull())
}
