import java.io.File

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

// Optional release signing. The GitHub workflow decodes the keystore from a secret and exports
// these variables; without them the release APK is signed with the debug key so it still installs.
val releaseKeystorePath: String? = providers.environmentVariable("BYTECODE_KEYSTORE_FILE").orNull
val hasReleaseKey: Boolean = releaseKeystorePath != null && File(releaseKeystorePath).isFile

android {
    namespace = "dev.bytecode.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.bytecode.ide"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = File(releaseKeystorePath!!)
                storePassword = System.getenv("BYTECODE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("BYTECODE_KEY_ALIAS")
                keyPassword = System.getenv("BYTECODE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(project(":editor"))
    implementation(project(":luau"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.material)
    implementation(libs.kotlinx.coroutines.android)
}
