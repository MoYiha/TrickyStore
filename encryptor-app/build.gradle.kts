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

if (releaseSigningValues.any { !it.isNullOrBlank() } && !releaseSigningConfigured) {
    throw GradleException("Encryptor release signing configuration is incomplete")
}

android {
    namespace = "cleveres.tricky.encryptor"
    compileSdk = 37

    defaultConfig {
        applicationId = "cleveres.tricky.encryptor"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

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
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.all {
            // Avoid locale-dependent native library naming issues on Windows hosts.
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
