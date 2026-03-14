import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream

//plugins {
//    alias(libs.plugins.agp.app) apply false
//    alias(libs.plugins.jetbrains.kotlin.android) apply false
//    alias(libs.plugins.android.library) apply false
//}

fun String.execute(currentWorkingDir: File = file("./")): String {
    val byteOut = ByteArrayOutputStream()
    val process = ProcessBuilder(trim().split("\\s+".toRegex()))
        .directory(currentWorkingDir)
        .redirectErrorStream(true)
        .start()
    process.inputStream.copyTo(byteOut)
    val output = byteOut.toString().trim()
    if (process.waitFor() != 0) {
        throw GradleException("Command failed: $this\n$output")
    }
    return output
}

val gitCommitCount = "git rev-list HEAD --count".execute().toInt()
val gitCommitHash = "git rev-parse --verify --short HEAD".execute()

// also the soname
val moduleId by extra("cleverestricky")
val moduleName by extra("CleveresTricky")
val author by extra("tryigitx")
val description by extra("AI Powered trick of keystore. See GitHub for changelog details.")
val verName by extra("V2.2.7")
val verCode by extra(gitCommitCount)
val commitHash by extra(gitCommitHash)
val abiList by extra(listOf("arm64-v8a", "x86_64"))

val androidMinSdkVersion by extra(31)
val androidTargetSdkVersion by extra(36)
val androidCompileSdkVersion by extra(36)
val androidBuildToolsVersion by extra("36.0.0")
val androidCompileNdkVersion by extra("27.3.13750724")
val androidSourceCompatibility by extra(JavaVersion.VERSION_17)
val androidTargetCompatibility by extra(JavaVersion.VERSION_17)

tasks.register("Delete", Delete::class) {
    delete(layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    // Disabled due to AGP plugin resolution issues
    // extensions.findByType(ApplicationExtension::class)?.run {
    //     ...
    // }
    // extensions.findByType(LibraryExtension::class)?.run {
    //     ...
    // }
}


subprojects {
    // Configuration disabled due to AGP plugin resolution issues
    // Will be re-enabled once AGP plugins are properly resolved
    /*
    plugins.withId("com.android.application") {
        configureBaseExtension()
    }
    plugins.withId("com.android.library") {
        configureBaseExtension()
    }
    plugins.withType(JavaPlugin::class.java) {
        extensions.configure(JavaPluginExtension::class.java) {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions {
            allWarningsAsErrors.set(!name.contains("UnitTest"))
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.addAll(listOf("-Werror", "-Xlint:all", "-Xlint:-options", "-Xlint:-path", "-Xlint:-rawtypes", "-Xlint:-unchecked", "-Xlint:-this-escape"))
    }
    */
}
