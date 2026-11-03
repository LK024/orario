import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

// Valori personali fuori da git, gli stessi dell'app per telefono
val localProperties = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.lk024.orario.wear"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Stesso pacchetto dell'app per telefono, come raccomandato per le app Wear OS
        applicationId = "io.github.lk024.orario"
        minSdk = 34
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        buildConfigField("String", "SHEET_CSV_URL", "\"${property("orario.sheetCsvUrl")}\"")
        buildConfigField("String", "SYNC_URL", "\"${localProperties.getProperty("orario.syncUrl", "")}\"")
        buildConfigField("String", "SYNC_TOKEN", "\"${localProperties.getProperty("orario.syncToken", "")}\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Uso personale: stessa chiave di debug dell'app per telefono
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
    implementation(project(":core"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.work.runtime)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material.icons)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.foundation)

    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.wear.protolayout.expression)
    implementation(libs.wear.protolayout.material3)
    implementation(libs.wear.complications)
    implementation(libs.concurrent.futures)

    testImplementation(libs.junit)
}
