import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val secretsFile = rootProject.file("secrets.properties")
val secrets = Properties().apply {
    if (secretsFile.exists()) load(secretsFile.inputStream())
}

fun secret(key: String, fallback: String = ""): String =
    secrets.getProperty(key, fallback)

android {
    namespace = "com.tutu.meowhub"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.tutu.meowhub"
        minSdk = 28
        targetSdk = 28
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "DOUBAO_API_KEY", "\"${secret("DOUBAO_API_KEY")}\"")
        buildConfigField("String", "DOUBAO_BASE_URL", "\"${secret("DOUBAO_BASE_URL", "https://ark.cn-beijing.volces.com/api/v3")}\"")
        buildConfigField("String", "DOUBAO_MODEL_ID", "\"${secret("DOUBAO_MODEL_ID", "doubao-seed-2-0-lite-260215")}\"")
        buildConfigField("String", "TUTU_APP_ID", "\"${secret("TUTU_APP_ID")}\"")
        buildConfigField("String", "TUTU_APP_SECRET", "\"${secret("TUTU_APP_SECRET")}\"")
    }

    buildTypes {
        debug {
            // Compose strong-skipping is on by default since Kotlin 2.0.20
            // but extra Compose compiler metrics help identify slow composables:
            // composeCompiler { enableStrongSkippingMode = true } // already default
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        compose = true
        prefab = true
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    lint {
        // 不上架 Google Play，禁用 targetSdk 版本检查
        disable += "ExpiredTargetSdkVersion"
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.bouncycastle)
    implementation(libs.boringssl)
    implementation(libs.hiddenapibypass)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(project(":terminal-view"))
    implementation(project(":termux-shared"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:1.1.5")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}