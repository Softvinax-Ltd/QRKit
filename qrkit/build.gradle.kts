import org.gradle.api.publish.maven.MavenPublication
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidMultiplatformLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    `maven-publish`
}

group = "com.softvinax"
version = providers.environmentVariable("LIBRARY_VERSION").orNull
    ?.takeIf { it.isNotBlank() }
    ?.removePrefix("v")
    ?: "1.0.0"

kotlin {
    iosArm64()
    iosSimulatorArm64()

    android {
        namespace = "ltd.softvinax.qrkit"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget = JvmTarget.JVM_11
        }
        withHostTest {
            isIncludeAndroidResources = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        getByName("androidHostTest").dependencies {
            implementation(libs.kotlin.testJunit)
            implementation(libs.junit)
            implementation(libs.zxing.core)
        }
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("QRKit")
            description.set(
                "Dependency-free QR code generator and scanner for Kotlin Multiplatform (Android and iOS)."
            )
            url.set("https://github.com/Softvinax-Ltd/QRKit")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                }
            }
            developers {
                developer {
                    id.set("softvinax-ltd")
                    name.set("Softvinax Ltd")
                }
            }
            scm {
                url.set("https://github.com/Softvinax-Ltd/QRKit")
                connection.set("scm:git:git://github.com/Softvinax-Ltd/QRKit.git")
                developerConnection.set("scm:git:ssh://git@github.com/Softvinax-Ltd/QRKit.git")
            }
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Softvinax-Ltd/QRKit")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
