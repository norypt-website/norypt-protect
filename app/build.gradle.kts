import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

// Resolved via File rather than Gradle's file(), which parses its argument as a URI and
// throws on an absolute Windows path when the build runs under Linux/WSL.
val releaseKeystore: File? =
    (keystoreProps.getProperty("storeFile") ?: System.getenv("RELEASE_KEYSTORE_PATH"))
        ?.let(::File)
        ?.takeIf { it.isFile }

// Escape hatch for locally testing a minified build without the production key.
val allowDebugSignedRelease = providers.gradleProperty("allowDebugSignedRelease")
    .map { it.toBoolean() }.getOrElse(false)

android {
    signingConfigs {
        create("release") {
            keyAlias = keystoreProps.getProperty("keyAlias") ?: System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = keystoreProps.getProperty("keyPassword") ?: System.getenv("RELEASE_KEY_PASSWORD")
            storeFile = releaseKeystore
            storePassword = keystoreProps.getProperty("storePassword") ?: System.getenv("RELEASE_KEYSTORE_PASSWORD")
            enableV3Signing = true
            enableV4Signing = true
        }
    }
    namespace = "com.norypt.protect"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.norypt.protect"
        minSdk = 33
        targetSdk = 35
        // The v1.0.0 release shipped with these still at 1 / "0.1.0-mvp", so the published
        // APK reports a version that contradicts its own release notes. Android orders
        // updates by versionCode, so it must increase on every release from here.
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")
    }

    buildTypes {
        debug {
            isDebuggable = true
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Never fall back to the debug key silently: a debug-signed "release" APK fails
            // the cert pin in SelfVerification, so it looks like a legitimate build right up
            // until it refuses to start on a user's device. Left unsigned instead, unless
            // the debug key is opted into explicitly for local testing.
            signingConfig = when {
                releaseKeystore != null -> signingConfigs.getByName("release")
                allowDebugSignedRelease -> signingConfigs.getByName("debug")
                else -> null
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs.useLegacyPackaging = false
    }

    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
        variant.packaging.resources.excludes.add("**/kotlin/**")
        variant.packaging.resources.excludes.add("META-INF/*.version")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.security.crypto)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
}
