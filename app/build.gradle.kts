plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.d10ng.voice.app"
    compileSdk = Project.compile_sdk

    defaultConfig {
        applicationId = "com.d10ng.voice.app"
        minSdk = Project.min_sdk
        targetSdk = Project.target_sdk
        versionCode = 1
        versionName = "0.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlin {
        jvmToolchain(8)
    }
    composeOptions {
        kotlinCompilerExtensionVersion = compose_compiler_ver
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    // Android
    implementation("androidx.core:core-ktx:1.10.0")

    // 单元测试（可选）
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")

    // jetpack compose 框架
    implementation("com.github.D10NGYANG:DLJetpackComposeUtil:1.3.2")

    implementation(project(":library"))
    // 内存泄漏检查
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.10")
}