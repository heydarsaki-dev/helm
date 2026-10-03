import java.io.File
import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing is opt-in: the keystore only exists as a CI secret, so a
// plain clone still builds — it just produces an unsigned release APK.
val keystoreB64 = System.getenv("ANDROID_KEYSTORE_BASE64")
val keystoreAlias = System.getenv("ANDROID_KEY_ALIAS")
val keystorePassword = System.getenv("ANDROID_KEY_PASSWORD")
val signingReady = listOf(keystoreB64, keystoreAlias, keystorePassword).all { !it.isNullOrBlank() }
val keystoreFile = rootProject.layout.buildDirectory.file("ci-upload.jks")

android {
    namespace = "dev.helm.hermes"
    compileSdk = 35

    if (signingReady) {
        // Materialise the keystore from the CI secret into the build dir. With
        // no secrets present this whole branch is skipped and nothing on disk
        // changes, so a plain clone still produces an unsigned release APK.
        val decoded = keystoreFile.get().asFile
        decoded.parentFile?.mkdirs()
        decoded.writeBytes(Base64.getDecoder().decode(keystoreB64!!))
        signingConfigs {
            create("ci") {
                storeFile = decoded
                storePassword = keystorePassword
                keyAlias = keystoreAlias
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    defaultConfig {
        applicationId = "dev.helm.hermes"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (signingReady) {
                signingConfig = signingConfigs.getByName("ci")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs = freeCompilerArgs + listOf(
            // Variable-font axes: Font(resId, weight, style, variationSettings).
            "-opt-in=androidx.compose.ui.text.ExperimentalTextApi",
            // ModalBottomSheet and friends.
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            // Remember/derived-state APIs Compose still marks experimental.
            "-opt-in=androidx.compose.runtime.ExperimentalComposeApi",
        )
    }
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
