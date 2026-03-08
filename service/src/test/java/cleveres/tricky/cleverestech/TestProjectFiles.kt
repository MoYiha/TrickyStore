package cleveres.tricky.cleverestech

import java.io.File

internal fun moduleTemplateFile(name: String): File {
    val workingDir = File(System.getProperty("user.dir")).absoluteFile
    return generateSequence(workingDir) { it.parentFile }
        .map { File(it, "module/template/$name") }
        .firstOrNull(File::exists)
        ?: error("Could not locate module/template/$name from ${workingDir.absolutePath}")
}
