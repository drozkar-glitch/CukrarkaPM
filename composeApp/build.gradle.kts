import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.application")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach { cilIos ->
        cilIos.binaries.framework {
            baseName = "ComposeApp"
            isStatic = true
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            implementation("androidx.activity:activity-compose:1.9.2")
            implementation("androidx.core:core-ktx:1.13.1")
            implementation("androidx.camera:camera-core:1.3.4")
            implementation("androidx.camera:camera-camera2:1.3.4")
            implementation("androidx.camera:camera-lifecycle:1.3.4")
            implementation("androidx.camera:camera-view:1.3.4")
            implementation("com.google.zxing:core:3.5.3")
        }
    }
}

val cisloBehuCi = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1) + 100
val hlavniVerzeAplikace = System.getenv("APP_VERSION")?.takeIf { it.isNotBlank() } ?: "1.0"
val cestaKeKeystoru = System.getenv("CM_KEYSTORE_PATH")
val maProdukcniPodpis = !cestaKeKeystoru.isNullOrBlank() && File(cestaKeKeystoru).exists()

android {
    namespace = "cz.mares.cukrarkapm"
    compileSdk = 36

    defaultConfig {
        applicationId = "cz.mares.cukrarkapm"
        minSdk = 24
        targetSdk = 36
        versionCode = cisloBehuCi
        versionName = hlavniVerzeAplikace
    }

    signingConfigs {
        if (maProdukcniPodpis) {
            create("cukrarkaSign") {
                storeFile = File(cestaKeKeystoru!!)
                storePassword = System.getenv("CM_KEYSTORE_PASSWORD") ?: ""
                keyAlias = System.getenv("CM_KEY_ALIAS") ?: ""
                keyPassword = System.getenv("CM_KEY_PASSWORD") ?: ""
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            if (maProdukcniPodpis) {
                signingConfig = signingConfigs.getByName("cukrarkaSign")
            }
        }
        getByName("debug") {
            if (maProdukcniPodpis) {
                signingConfig = signingConfigs.getByName("cukrarkaSign")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}
