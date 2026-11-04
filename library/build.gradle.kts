import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.library)
    id("maven-publish")
}

group = "com.github.D10NGYANG"
version = "0.3.0"

kotlin {
    android {
        namespace = "com.d10ng.voice"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()
        compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
        withHostTest {}
    }
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // 协程
            api(libs.kotlinx.coroutines.core)
            // 通用计算
            implementation(libs.dl.common)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        androidMain.dependencies {
            // Android
            implementation(libs.androidx.annotation)
            // 协程 Android
            implementation(libs.kotlinx.coroutines.android)
        }
    }
}

val bds100MavenUsername = providers.gradleProperty("bds100MavenUsername")
val bds100MavenPassword = providers.gradleProperty("bds100MavenPassword")

val javadocJar = tasks.register<Jar>("javadocJar") {
    archiveClassifier.set("javadoc")
}

afterEvaluate {
    publishing {
        publications {
            withType(MavenPublication::class) {
                //artifactId = artifactId.replace(project.name, rootProject.name)
                artifact(javadocJar)
            }
        }
        repositories {
            maven {
                url = uri(providers.gradleProperty("localMavenRepository").getOrElse(layout.buildDirectory.dir("repository").get().asFile.path))
            }
            maven {
                credentials {
                    username = bds100MavenUsername.orNull
                    password = bds100MavenPassword.orNull
                }
                setUrl("https://nexus.bds100.com/repository/maven-releases/")
            }
        }
    }
}

// Allow acceptance runs to use a dedicated simulator without changing developer devices.
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    providers.gradleProperty("iosSimulatorId").orNull?.let { device.set(it) }
}
