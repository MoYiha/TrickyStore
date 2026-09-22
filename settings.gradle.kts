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
                force("io.netty:netty-codec-http:[4.2.18,4.3)")
                force("io.netty:netty-codec-http2:[4.2.18,4.3)")
                force("io.netty:netty-codec:[4.2.18,4.3)")
                force("io.netty:netty-handler-proxy:[4.2.18,4.3)")
                force("ch.qos.logback:logback-core:[1.6.3,2.0)")
                force("ch.qos.logback:logback-classic:[1.6.3,2.0)")
                // AGP/lint drag a stale bcprov into the plugin classpath that
                // Dependabot cannot bump directly; always follow the newest
                // release instead of locking. The 1.85 CVE floor is enforced
                // by the security workflow baseline, not by this selector.
                // bcpkix/bcutil ride along: IANAObjectIdentifiers exists in
                // both jars, so a stale sibling shadowing the forced copy
                // breaks provider init with NoSuchFieldError.
                force("org.bouncycastle:bcprov-jdk18on:[1.86,2.0)")
                force("org.bouncycastle:bcpkix-jdk18on:[1.86,2.0)")
                force("org.bouncycastle:bcutil-jdk18on:[1.86,2.0)")
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
                force("io.netty:netty-codec-http:[4.2.18,4.3)")
                force("io.netty:netty-codec-http2:[4.2.18,4.3)")
                force("io.netty:netty-codec:[4.2.18,4.3)")
                force("io.netty:netty-handler-proxy:[4.2.18,4.3)")
                force("ch.qos.logback:logback-core:[1.6.3,2.0)")
                force("ch.qos.logback:logback-classic:[1.6.3,2.0)")
                force("org.bouncycastle:bcprov-jdk18on:[1.86,2.0)")
                force("org.bouncycastle:bcpkix-jdk18on:[1.86,2.0)")
                force("org.bouncycastle:bcutil-jdk18on:[1.86,2.0)")
            }
        }
    }
}
