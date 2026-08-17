plugins {
    alias(libs.plugins.agp.app)
    alias(libs.plugins.compose.compiler)
}

val releaseKeystore = providers.gradleProperty("ENCRYPTOR_RELEASE_KEYSTORE").orNull
val releaseStorePassword = providers.gradleProperty("ENCRYPTOR_RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("ENCRYPTOR_RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("ENCRYPTOR_RELEASE_KEY_PASSWORD").orNull
val releaseSigningValues =
    listOf(
        releaseKeystore,
        releaseStorePassword,
        releaseKeyAlias,
        releaseKeyPassword,
    )
val releaseSigningConfigured = releaseSigningValues.all { !it.isNullOrBlank() }
val useDebugSigningForPullRequest = System.getenv("GITHUB_EVENT_NAME") == "pull_request"
val moduleVersionCode = rootProject.extra["verCode"] as Int
val moduleVersionName = rootProject.extra["verName"] as String
val encryptorMinSdk = 26
val generatedRustJni = layout.buildDirectory.dir("generated/rust/jniLibs")

if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException("Encryptor release signing configuration is incomplete")
}

android {
    namespace = "cleveres.tricky.encryptor"
    compileSdk = 37

    defaultConfig {
        applicationId = "cleveres.tricky.encryptor"
        minSdk = encryptorMinSdk
        targetSdk = 37
        versionCode = moduleVersionCode
        versionName = moduleVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (releaseSigningConfigured) {
            create("release") {
                storeFile = file(requireNotNull(releaseKeystore))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningConfigured) {
                signingConfig = signingConfigs.getByName("release")
            } else if (useDebugSigningForPullRequest) {
                // PR artifacts are installable for contributor testing, but are never published as releases.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main").jniLibs.srcDir(generatedRustJni)
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            useLegacyPackaging = false
        }
    }
    testOptions {
        unitTests.all {
            it.systemProperty("user.language", "en")
            it.systemProperty("user.country", "US")
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget("1.8"))
    }
}

val buildEncryptorRust =
    tasks.register<Exec>("buildEncryptorRust") {
        group = "rust"
        description = "Builds the Rust CBOX/vault JNI bridge for supported Android ABIs"
        dependsOn(":module:installRustTargets")
        workingDir = file("../rust")
        environment("RUSTFLAGS", "-D warnings")
        doFirst {
            generatedRustJni.get().asFile.deleteRecursively()
            generatedRustJni.get().asFile.mkdirs()
        }
        commandLine(
            "cargo",
            "ndk",
            "--platform",
            encryptorMinSdk.toString(),
            "-t",
            "arm64-v8a",
            "-t",
            "x86_64",
            "-o",
            generatedRustJni.get().asFile.absolutePath,
            "build",
            "--release",
            "-p",
            "cleverestricky-encryptor-native",
        )
    }

tasks.named("preBuild") {
    dependsOn(buildEncryptorRust)
}

tasks.register("verifyModuleVersionParity") {
    group = "verification"
    doLast {
        check(android.defaultConfig.versionCode == moduleVersionCode) {
            "Encryptor versionCode must match module verCode"
        }
        check(android.defaultConfig.versionName == moduleVersionName) {
            "Encryptor versionName must match module verName"
        }
    }
}

tasks.named("check") {
    dependsOn("verifyModuleVersionParity")
}

dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    implementation(libs.annotation)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.robolectric)
}
