plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "in.dalc.pkdmobile"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "in.dalc.pkdmobile"
        minSdk = 31
        targetSdk = 35
        // Release builds take the version from the git tag (v0.1.2 → "0.1.2") and a
        // versionCode that always grows (GitHub Actions run number). Obtainium compares
        // the installed versionName with the tag; a fixed value makes it offer the update forever.
        versionCode = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1
        versionName = System.getenv("GITHUB_REF_NAME")?.takeIf { it.startsWith("v") }?.removePrefix("v") ?: "0.0.0-dev"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Assinatura de release via variáveis de ambiente (GitHub Secrets no CI).
    // Sem elas, o build de release cai para a assinatura de debug (funciona localmente).
    val hasReleaseKeystore = !System.getenv("PKD_KEYSTORE_PATH").isNullOrBlank()
    if (hasReleaseKeystore) {
        signingConfigs {
            create("release") {
                storeFile = file(System.getenv("PKD_KEYSTORE_PATH")!!)
                storePassword = System.getenv("PKD_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("PKD_KEY_ALIAS")
                keyPassword = System.getenv("PKD_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (hasReleaseKeystore) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true // BuildConfig.DEBUG: debug builds also accept http:// (local PKD)
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    debugImplementation(libs.androidx.ui.tooling)
}
