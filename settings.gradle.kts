import java.util.concurrent.TimeUnit

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
                // Re-resolve floating versions hourly so newest releases apply.
                cacheDynamicVersionsFor(1, TimeUnit.HOURS)
                cacheChangingModulesFor(1, TimeUnit.HOURS)
                force("io.netty:netty-codec-http:latest.release")
                force("io.netty:netty-codec-http2:latest.release")
                force("io.netty:netty-codec:latest.release")
                force("io.netty:netty-handler-proxy:latest.release")
                force("ch.qos.logback:logback-core:latest.release")
                force("ch.qos.logback:logback-classic:latest.release")
                // AGP/lint drag a stale bcprov into the plugin classpath that
                // Dependabot cannot bump directly; always follow the newest
                // release instead of locking. The 1.85 CVE floor is enforced
                // by the security workflow baseline, not by this selector.
                force("org.bouncycastle:bcprov-jdk18on:latest.release")
            }
        }
        configurations.all {
            resolutionStrategy {
                cacheDynamicVersionsFor(1, TimeUnit.HOURS)
                cacheChangingModulesFor(1, TimeUnit.HOURS)
                force("io.netty:netty-codec-http:latest.release")
                force("io.netty:netty-codec-http2:latest.release")
                force("io.netty:netty-codec:latest.release")
                force("io.netty:netty-handler-proxy:latest.release")
                force("ch.qos.logback:logback-core:latest.release")
                force("ch.qos.logback:logback-classic:latest.release")
                force("org.bouncycastle:bcprov-jdk18on:latest.release")
            }
        }
    }
}
