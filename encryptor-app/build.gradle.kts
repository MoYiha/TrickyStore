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
val generatedRustJni = file("build/generated/rust/jniLibs")

if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException("Encryptor release signing configuration is incomplete")
}

android {
    namespace = "cleveres.tricky.encryptor"
    compileSdk = rootProject.extra["androidCompileSdkVersion"] as Int
    ndkVersion = rootProject.extra["androidCompileNdkVersion"] as String

    defaultConfig {
        applicationId = "cleveres.tricky.encryptor"
        minSdk = encryptorMinSdk
        targetSdk = rootProject.extra["androidTargetSdkVersion"] as Int
        versionCode = moduleVersionCode
        versionName = moduleVersionName
    }

    signingConfigs {
        create("release") {
            if (releaseSigningConfigured) {
                storeFile = file(checkNotNull(releaseKeystore))
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
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
        // Current Compose/AndroidX artifacts contain JVM 11 bytecode. Keeping Java and Kotlin on
        // the same target avoids higher-platform inlining failures while D8 still supports minSdk 26.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    sourceSets {
        getByName("main").jniLibs.directories.add(generatedRustJni.path)
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

val rustTargetByAbi =
    mapOf(
        "arm64-v8a" to "aarch64-linux-android",
        "x86_64" to "x86_64-linux-android",
    )

val cargoNdk = providers.gradleProperty("cargoNdkExecutable").orElse("cargo")
val cargoProfile = providers.gradleProperty("cargoProfile").orElse("release")
val cargoManifest = rootProject.file("rust/encryptor-core/Cargo.toml")
val cargoTargetDir = rootProject.file("rust/target")

val buildRustLibraries =
    tasks.register("buildRustLibraries") {
        group = "build"
        description = "Builds encryptor Rust JNI libraries for Android ABIs"
        inputs.file(cargoManifest)
        inputs.file(rootProject.file("rust/Cargo.lock"))
        inputs.dir(rootProject.file("rust/encryptor-core/src"))
        outputs.dir(generatedRustJni)

        doLast {
            generatedRustJni.deleteRecursively()
            rustTargetByAbi.forEach { (abi, target) ->
                val toolchainFile = file("${android.ndkDirectory}/build/cmake/android.toolchain.cmake")
                if (!toolchainFile.exists()) {
                    throw GradleException("Android NDK toolchain file not found: $toolchainFile")
                }

                val api = encryptorMinSdk
                val hostTag =
                    when {
                        System.getProperty("os.name").startsWith("Mac", ignoreCase = true) -> "darwin-x86_64"
                        System.getProperty("os.name").startsWith("Windows", ignoreCase = true) -> "windows-x86_64"
                        else -> "linux-x86_64"
                    }
                val clang =
                    file(
                        "${android.ndkDirectory}/toolchains/llvm/prebuilt/$hostTag/bin/" +
                            when (target) {
                                "aarch64-linux-android" -> "aarch64-linux-android${api}-clang"
                                "x86_64-linux-android" -> "x86_64-linux-android${api}-clang"
                                else -> throw GradleException("Unsupported Rust Android target: $target")
                            },
                    )
                if (!clang.exists()) {
                    throw GradleException("Android NDK clang not found: $clang")
                }

                val command =
                    listOf(
                        cargoNdk.get(),
                        "build",
                        "--manifest-path",
                        cargoManifest.absolutePath,
                        "--target",
                        target,
                        "--profile",
                        cargoProfile.get(),
                        "--locked",
                    )
                val process =
                    ProcessBuilder(command)
                        .directory(rootProject.projectDir)
                        .redirectErrorStream(true)
                        .apply {
                            environment()["CARGO_TARGET_DIR"] = cargoTargetDir.absolutePath
                            environment()["CC_${target.replace('-', '_')}"] = clang.absolutePath
                        }.start()
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach(::println)
                }
                val exit = process.waitFor()
                if (exit != 0) {
                    throw GradleException("Rust JNI build failed for $target with exit code $exit")
                }

                val library = file("${cargoTargetDir.absolutePath}/$target/${cargoProfile.get()}/libcleveres_encryptor_crypto.so")
                if (!library.exists()) {
                    throw GradleException("Rust JNI library missing after build: $library")
                }
                val destination = file("${generatedRustJni.absolutePath}/$abi")
                destination.mkdirs()
                library.copyTo(file("${destination.absolutePath}/libcleveres_encryptor_crypto.so"), overwrite = true)
            }
        }
    }

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("JniLibFolders") }.configureEach {
    dependsOn(buildRustLibraries)
}
