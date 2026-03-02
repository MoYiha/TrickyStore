import android.databinding.tool.ext.capitalizeUS
import org.jetbrains.kotlin.daemon.common.toHexString
import java.security.MessageDigest

plugins {
    alias(libs.plugins.agp.app)
}

val moduleId: String by rootProject.extra
val moduleName: String by rootProject.extra
val verCode: Int by rootProject.extra
val verName: String by rootProject.extra
val commitHash: String by rootProject.extra
val author: String by rootProject.extra
val description: String by rootProject.extra
val moduleDescription = description

fun calculateChecksum(variantLowered: String): String {
    return MessageDigest.getInstance("SHA-256").run {
        update(moduleId.toByteArray(Charsets.UTF_8))
        update(moduleName.toByteArray(Charsets.UTF_8))
        update("$verName ($verCode-$commitHash-$variantLowered)".toByteArray(Charsets.UTF_8))
        update(verCode.toString().toByteArray(Charsets.UTF_8))
        update(author.toByteArray(Charsets.UTF_8))
        update(description.toByteArray(Charsets.UTF_8))
        digest().toHexString()
    }
}

android {
    namespace = "cleveres.tricky.cleverestech"
    compileSdk = 36

    defaultConfig {
        applicationId = "cleveres.tricky.cleverestech"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        forEach {
            val checksum = calculateChecksum(it.name)
            it.buildConfigField("String", "CHECKSUM", "\"$checksum\"")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs["debug"]
        }
    }

    packaging {
        resources {
            excludes += "META-INF/versions/**"
            excludes += "META-INF/DEPENDENCIES"
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
    }

    buildFeatures {
        buildConfig = true
    }

    testOptions {
        unitTests.all {
            it.testLogging {
                events = setOf(
                    org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_OUT,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.STANDARD_ERROR,
                    org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
                )
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("17"))
    }
}

dependencies {
    compileOnly(project(":stub"))
    implementation(libs.annotation)
    implementation(libs.bcpkix.jdk18on)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation(libs.junit)
    testImplementation(project(":stub"))
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    testImplementation("org.json:json:20240303")
    testImplementation("org.mockito:mockito-core:5.22.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("net.bytebuddy:byte-buddy:1.18.7")
    testImplementation("net.bytebuddy:byte-buddy-agent:1.18.7")
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.androidx.test.runner)
    implementation(libs.nanohttpd)
}

afterEvaluate {
    android.buildTypes.forEach { buildType ->
        val variantLowered = buildType.name.lowercase()
        val variantCapped = buildType.name.capitalizeUS()
        val pushTask = tasks.register<Task>("pushService$variantCapped") {
            group = "Service"
            dependsOn("assemble$variantCapped")
            doLast {
                project.exec {
                    commandLine(
                        "adb",
                        "push",
                        layout.buildDirectory.file("outputs/apk/$variantLowered/service-$variantLowered.apk")
                            .get().asFile.absolutePath,
                        "/data/local/tmp/service.apk"
                    )
                }
                project.exec {
                    commandLine(
                        "adb",
                        "shell",
                        "su -c 'rm /data/adb/modules/cleverestricky/service.apk; mv /data/local/tmp/service.apk /data/adb/modules/cleverestricky/'"
                    )
                }
            }
        }

        tasks.register<Task>("pushAndRestartService$variantCapped") {
            group = "Service"
            dependsOn(pushTask)
            doLast {
                project.exec {
                    commandLine("adb", "shell", "su", "-c", "setprop ctl.restart keystore2")
                }
            }
        }
    }
}
