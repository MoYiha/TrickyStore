pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            url = uri("https://downloads.bouncycastle.org/java/maven")
            content {
                includeGroup("org.bouncycastle")
            }
        }
    }
}

rootProject.name = "CleveresTricky"
include(":module")
include(":service")
include(":stub")
include(":encryptor-app")

gradle.rootProject {
    allprojects {
        buildscript.configurations.all {
            resolutionStrategy {
                force("io.netty:netty-codec-http:4.2.18.Final")
                force("io.netty:netty-codec-http2:4.2.18.Final")
                force("io.netty:netty-codec:4.2.18.Final")
                force("io.netty:netty-handler-proxy:4.2.18.Final")
                force("ch.qos.logback:logback-core:1.6.3")
                force("ch.qos.logback:logback-classic:1.6.3")
                // AGP/lint pull a vulnerable bcprov into the plugin classpath;
                // pin it to the patched version used by the direct dependency.
                force("org.bouncycastle:bcprov-jdk18on:1.86")
            }
        }
        configurations.all {
            resolutionStrategy {
                force("io.netty:netty-codec-http:4.2.18.Final")
                force("io.netty:netty-codec-http2:4.2.18.Final")
                force("io.netty:netty-codec:4.2.18.Final")
                force("io.netty:netty-handler-proxy:4.2.18.Final")
                force("ch.qos.logback:logback-core:1.6.3")
                force("ch.qos.logback:logback-classic:1.6.3")
                force("org.bouncycastle:bcprov-jdk18on:1.86")
            }
        }
    }
}
