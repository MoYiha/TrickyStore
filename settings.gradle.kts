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
                // Registries point <release> at alphas (e.g. activity-compose
                // 1.14.0-alpha02); accept newest stable only.
                componentSelection {
                    all {
                        // Jetifier never left beta: 1.0.0-beta10 is its final
                        // form and AGP requires it exactly.
                        if (candidate.group == "com.android.tools.build.jetifier") return@all
                        val v = candidate.version.lowercase()
                        if (v.contains("-alpha") || v.contains("-beta") || v.contains("-rc") || v.contains("-m") || v.contains("-preview") || v.contains("-snapshot") || v.contains(".alpha") || v.contains(".beta")) {
                            reject("Pre-release versions are not accepted")
                        }
                    }
                }
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
                // bcpkix/bcutil ride along: IANAObjectIdentifiers exists in
                // both jars, so a stale sibling shadowing the forced copy
                // breaks provider init with NoSuchFieldError.
                force("org.bouncycastle:bcprov-jdk18on:latest.release")
                force("org.bouncycastle:bcpkix-jdk18on:latest.release")
                force("org.bouncycastle:bcutil-jdk18on:latest.release")
            }
        }
        configurations.all {
            resolutionStrategy {
                cacheDynamicVersionsFor(1, TimeUnit.HOURS)
                cacheChangingModulesFor(1, TimeUnit.HOURS)
                componentSelection {
                    all {
                        // Jetifier never left beta: 1.0.0-beta10 is its final
                        // form and AGP requires it exactly.
                        if (candidate.group == "com.android.tools.build.jetifier") return@all
                        val v = candidate.version.lowercase()
                        if (v.contains("-alpha") || v.contains("-beta") || v.contains("-rc") || v.contains("-m") || v.contains("-preview") || v.contains("-snapshot") || v.contains(".alpha") || v.contains(".beta")) {
                            reject("Pre-release versions are not accepted")
                        }
                    }
                }
                force("io.netty:netty-codec-http:latest.release")
                force("io.netty:netty-codec-http2:latest.release")
                force("io.netty:netty-codec:latest.release")
                force("io.netty:netty-handler-proxy:latest.release")
                force("ch.qos.logback:logback-core:latest.release")
                force("ch.qos.logback:logback-classic:latest.release")
                force("org.bouncycastle:bcprov-jdk18on:latest.release")
                force("org.bouncycastle:bcpkix-jdk18on:latest.release")
                force("org.bouncycastle:bcutil-jdk18on:latest.release")
            }
        }
    }
}
