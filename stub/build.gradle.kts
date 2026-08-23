plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.a13e300.stub"
    defaultConfig {
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = true
        warningsAsErrors = true
    }
}

tasks.withType<JavaCompile>().configureEach {
    // The remap processor intentionally claims only @RemapMethod; do not turn unrelated
    // compile-only AndroidX nullability annotations into -Werror processing warnings.
    options.compilerArgs.add("-Xlint:-processing")
}

dependencies {
    compileOnly(libs.annotation)
    compileOnly(libs.remap.annotation)
    annotationProcessor(libs.remap.processor)
    implementation(libs.json)
}
