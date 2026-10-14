import java.util.Properties
import com.android.build.gradle.api.ApkVariantOutput

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// One checked-in application version; builds never increment it.
val appVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionName = requireNotNull(appVersion.getProperty("versionName"))
val appVersionCodeText = requireNotNull(appVersion.getProperty("versionCode"))
require(appVersionName.matches(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)"))) {
    "versionName must be major.minor.patch without leading zeros"
}
val appVersionCode = requireNotNull(appVersionCodeText.toIntOrNull())
require(appVersionCodeText.matches(Regex("[1-9][0-9]*")) && appVersionCode in 1..2_100_000_000) {
    "versionCode must be in 1..2100000000"
}

// Local release signing. Without the untracked keystore config, release stays unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.han1997.vipledger"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "fun.han1997.vipledger"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = appVersionCode
        versionName = appVersionName
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // ML Kit OCR 原生库单架构就有 7~11MB，四架构齐打会让 APK 膨到 45MB。
    // 按 ABI 拆包：真机 arm64-v8a / armeabi-v7a，模拟器 x86_64；不含无用的 x86。
    // 每个产物只带自己的原生库，安装时按设备 ABI 选对应包即可。
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }
}

// AGP 8.13's public APK output API also keeps output-metadata.json in sync.
// ABI 拆包后同一 variant 有多个输出，文件名在 outputs.all 里逐个拼（带 ABI 段）。
android.applicationVariants.all {
    val variantName = name
    val signed = isSigningReady
    outputs.all {
        val output = this as ApkVariantOutput
        val abi = output.filters.firstOrNull { it.filterType == "ABI" }?.identifier
        output.outputFileName = buildString {
            append("vipledger-v$versionName")
            if (abi != null) append("-$abi")
            append("-$variantName")
            if (!signed) append("-unsigned")
            append(".apk")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:database"))
    implementation(project(":core:data"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:ledger"))
    implementation(project(":feature:stats"))
    implementation(project(":feature:sync"))
    implementation(project(":feature:ai"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
}
