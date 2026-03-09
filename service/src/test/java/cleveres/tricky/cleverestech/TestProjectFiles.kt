package cleveres.tricky.cleverestech

import java.io.File

internal fun moduleTemplateFile(name: String): File {
    val workingDir = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(workingDir) { it.parentFile }
        .map { File(it, "module/template/$name") }
        .firstOrNull(File::exists)
        ?: error("Could not locate module/template/$name from ${workingDir.absolutePath}")
}

internal fun moduleCppFile(relativePath: String): File {
    val workingDir = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(workingDir) { it.parentFile }
        .map { File(it, "module/src/main/cpp/$relativePath") }
        .firstOrNull(File::exists)
        ?: error("Could not locate module/src/main/cpp/$relativePath from ${workingDir.absolutePath}")
}

internal fun serviceMainFile(relativePath: String): File {
    val workingDir = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(workingDir) { it.parentFile }
        .map { File(it, "service/src/main/java/cleveres/tricky/cleverestech/$relativePath") }
        .firstOrNull(File::exists)
        ?: error("Could not locate service/src/main/java/cleveres/tricky/cleverestech/$relativePath from ${workingDir.absolutePath}")
}
