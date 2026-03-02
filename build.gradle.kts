import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.GradleException
import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.android.library) apply false
}

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
val moduleName by extra("CleveresTricky Beta")
val author by extra("tryigitx")
val description by extra("AI Powered trick of keystore. See GitHub for changelog details.")
val verName by extra("V2.1.9")
val verCode by extra(gitCommitCount)
val commitHash by extra(gitCommitHash)
val abiList by extra(listOf("arm64-v8a", "x86_64"))

val androidMinSdkVersion by extra(31)
val androidTargetSdkVersion by extra(36)
val androidCompileSdkVersion by extra(36)
val androidBuildToolsVersion by extra("34.0.0")
val androidCompileNdkVersion by extra("27.0.12077973")
val androidSourceCompatibility by extra(JavaVersion.VERSION_17)
val androidTargetCompatibility by extra(JavaVersion.VERSION_17)

tasks.register("Delete", Delete::class) {
    delete(layout.buildDirectory)
}

fun Project.configureBaseExtension() {
    extensions.findByType(ApplicationExtension::class)?.run {
        namespace = "cleveres.tricky.cleverestech"
        compileSdk = androidCompileSdkVersion
        ndkVersion = androidCompileNdkVersion
        buildToolsVersion = androidBuildToolsVersion

        defaultConfig {
            minSdk = androidMinSdkVersion
            targetSdk = androidCompileSdkVersion
            versionCode = verCode
            versionName = verName
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }

    extensions.findByType(LibraryExtension::class)?.run {
        namespace = "cleveres.tricky.cleverestech"
        compileSdk = androidCompileSdkVersion
        ndkVersion = androidCompileNdkVersion
        buildToolsVersion = androidBuildToolsVersion

        defaultConfig {
            minSdk = androidMinSdkVersion
        }

        lint {
            checkReleaseBuilds = false
            abortOnError = true
        }

        compileOptions {
            sourceCompatibility = androidSourceCompatibility
            targetCompatibility = androidTargetCompatibility
        }
    }
}

subprojects {
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
}
