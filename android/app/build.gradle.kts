import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Valori personali fuori da git (es. la chiave della sincronizzazione supplenze)
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.lk024.orario"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.lk024.orario"
        minSdk = 35
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SHEET_CSV_URL", "\"${property("orario.sheetCsvUrl")}\"")
        buildConfigField("String", "SHEET_EDIT_URL", "\"${property("orario.sheetEditUrl")}\"")
        buildConfigField("String", "SYNC_URL", "\"${localProperties.getProperty("orario.syncUrl", "")}\"")
        buildConfigField("String", "SYNC_TOKEN", "\"${localProperties.getProperty("orario.syncToken", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Uso personale: firmata con la chiave di debug, così si installa direttamente
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.material.kolor)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    testImplementation(libs.junit)
}
