import java.io.File
import java.util.Properties
import java.util.zip.ZipFile

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
    // libnorypt_shutdown and the norypt_watcher executable (src/main/jni): C9's orderly-reboot mark. NDK r27d (LTS),
    // CMake 3.22.1 (the CI workflows and the Dockerfile install both).
    ndkVersion = "27.3.13750724"

    defaultConfig {
        applicationId = "com.norypt.protect"
        minSdk = 33
        targetSdk = 35
        // The v1.0.0 release shipped with these still at 1 / "0.1.0-mvp", so the published
        // APK reports a version that contradicts its own release notes. Android orders
        // updates by versionCode, so it must increase on every release from here.
        versionCode = 14
        versionName = "1.3.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += listOf("en")

        ndk {
            // Phones only (no emulator ABIs); must match copyNoryptWatcher below.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
        externalNativeBuild {
            cmake {
                // No C++ runtime (plain C); 16 KB page devices (Android 15+).
                arguments += listOf("-DANDROID_STL=none", "-DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/jni/CMakeLists.txt")
            version = "3.22.1"
        }
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

    lint {
        abortOnError = true
        warningsAsErrors = false
        checkReleaseBuilds = true
        sarifReport = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        // Compressed in the APK and extracted at install: libnorypt_watcher.so is executed from the native library
        // directory, which only holds files when they are extracted (C9's orderly-reboot mark, 1.3.5).
        jniLibs.useLegacyPackaging = true
    }

    androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
        variant.packaging.resources.excludes.add("**/kotlin/**")
        variant.packaging.resources.excludes.add("META-INF/*.version")
    }
}

// C9's orderly-reboot watcher (src/main/jni/shutdown_watcher.c) is an executable: CMake builds it as
// `norypt_watcher`, but AGP only packages libraries, so each variant copies it in as libnorypt_watcher.so (the native
// library directory is the only place an app may execute its own files). Runs every build (cheap) and fails the build
// when the binary is missing or older than any of its sources: a stale watcher never ships silently. The release
// build strips it with the libraries.
abstract class CopyNoryptWatcher : DefaultTask() {
    @get:Internal abstract val cxxDir: DirectoryProperty
    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) abstract val sources: ConfigurableFileCollection
    @get:Input abstract val debugBuild: Property<Boolean>
    @get:Input abstract val abis: ListProperty<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val newestSource = sources.files.maxOfOrNull { it.lastModified() }
            ?: throw GradleException("norypt_watcher sources missing")
        for (abi in abis.get()) {
            // intermediates/cxx/<Debug|RelWithDebInfo>/<hash>/obj/<abi>/norypt_watcher; the newest of this type.
            val found = cxxDir.get().asFile.listFiles().orEmpty()
                .filter { (it.name == "Debug") == debugBuild.get() }
                .flatMap { it.listFiles().orEmpty().toList() }
                .map { File(it, "obj/$abi/norypt_watcher") }
                .filter { it.isFile }
                .maxByOrNull { it.lastModified() }
                ?: throw GradleException("norypt_watcher not built for $abi")
            if (found.lastModified() < newestSource) {
                throw GradleException("norypt_watcher for $abi is older than its sources: clean the native build")
            }
            found.copyTo(File(out, "$abi/libnorypt_watcher.so"), overwrite = true)
        }
    }
}

androidComponents {
    onVariants { variant ->
        val cap = variant.name.replaceFirstChar { it.uppercase() }
        val debug = variant.buildType == "debug"
        val copy = tasks.register<CopyNoryptWatcher>("copyNoryptWatcher$cap") {
            dependsOn("externalNativeBuild$cap")
            cxxDir.set(layout.buildDirectory.dir("intermediates/cxx"))
            sources.from(
                "src/main/jni/shutdown_watcher.c", "src/main/jni/shutdown_record.h", "src/main/jni/CMakeLists.txt",
            )
            doNotTrackState("picks the newest ndk-build output; always re-checked")
            debugBuild.set(debug)
            // Same as android.defaultConfig.ndk.abiFilters.
            abis.set(listOf("arm64-v8a", "armeabi-v7a"))
        }
        variant.sources.jniLibs?.addGeneratedSourceDirectory(copy, CopyNoryptWatcher::outputDir)
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

// ---------------------------------------------------------------------------
// Build-time security gates.
//
// Each of these encodes a property that was verified by hand once and would
// otherwise silently regress. They are Gradle tasks rather than CI-only shell so
// they fail on a developer machine too.
// ---------------------------------------------------------------------------

/**
 * The app must never gain network access. Matches the permission element rather than the
 * bare string, because the manifest carries a "DELIBERATELY NOT DECLARED: ...INTERNET"
 * comment that a substring search would trip on — and deleting that comment to satisfy a
 * naive check would make the check pass vacuously.
 */
val verifyNoNetworkPermission by tasks.registering {
    group = "verification"
    description = "Fails if the merged release manifest declares INTERNET."
    dependsOn("processReleaseMainManifest")
    doLast {
        val manifest = layout.buildDirectory
            .file("intermediates/merged_manifest/release/processReleaseMainManifest/AndroidManifest.xml")
            .get().asFile
        require(manifest.isFile) { "merged manifest not found at $manifest" }
        val text = manifest.readText()
        val declared = Regex("""<uses-permission[^>]*android:name\s*=\s*"android\.permission\.INTERNET"""")
            .containsMatchIn(text)
        if (declared) {
            throw GradleException(
                "SECURITY GATE: the merged release manifest declares android.permission.INTERNET. " +
                    "This app is local-only and must never be able to exfiltrate anything.",
            )
        }
        logger.lifecycle("✓ no INTERNET permission in the merged release manifest")
    }
}

/**
 * Debug telemetry writes the device's whole protection posture to a plaintext prefs file.
 * It is gated on BuildConfig.DEBUG, so R8 should strip both the writes and their key
 * strings from release. Asserting on the dex catches a future ungated call site.
 */
val verifyNoTelemetryInReleaseDex by tasks.registering {
    group = "verification"
    description = "Fails if debug-telemetry key strings survive into the release dex."
    dependsOn("assembleRelease")
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        require(apk.isFile) { "release APK not found at $apk" }

        // Keys only ever written by DebugTelemetry call sites. The prefs file name itself is
        // excluded: purgeInReleaseBuilds legitimately needs it to delete stale files.
        val forbidden = listOf(
            "panic_total", "wipe_last_call", "sos_click_count", "fgs_ticks_total",
            "c3_screen_on", "c4_alarm_fired", "a5_intents_total", "a7_receiver_invocations",
            "b5_tick_entered", "dump_dry_run", "a9_usb_state_total", "a9_panic_fired",
        )
        val dex = StringBuilder()
        ZipFile(apk).use { zip ->
            zip.entries().asSequence()
                .filter { it.name.matches(Regex("""classes\d*\.dex""")) }
                .forEach { entry ->
                    zip.getInputStream(entry).use { dex.append(String(it.readBytes(), Charsets.ISO_8859_1)) }
                }
        }
        val found = forbidden.filter { dex.contains(it) }
        if (found.isNotEmpty()) {
            throw GradleException(
                "SECURITY GATE: debug-telemetry strings present in the release dex: $found. " +
                    "A call site is not gated on BuildConfig.DEBUG.",
            )
        }
        logger.lifecycle("✓ no debug-telemetry strings in the release dex")
    }
}

/**
 * A release APK signed with the debug key passes every local check and then refuses to
 * start on a user's device, because SelfVerification pins the release certificate. Fail at
 * build time instead, unless the debug key was opted into explicitly.
 */
val verifyReleaseSigning by tasks.registering {
    group = "verification"
    description = "Fails if a release build is unsigned or falls back to the debug key."
    doLast {
        if (releaseKeystore == null && !allowDebugSignedRelease) {
            throw GradleException(
                "SECURITY GATE: no release keystore configured. Set keystore.properties or the " +
                    "RELEASE_KEYSTORE_* environment variables. To build a throwaway minified APK " +
                    "locally, pass -PallowDebugSignedRelease=true and do not distribute it.",
            )
        }
        if (releaseKeystore == null) {
            logger.warn("⚠ release build is DEBUG-SIGNED (allowDebugSignedRelease=true) — do not distribute")
        } else {
            logger.lifecycle("✓ release signing uses the configured release keystore")
        }
    }
}

/**
 * setPermissionPolicy(AUTO_GRANT) reads like "grant my own permissions" but is device-wide:
 * every app's runtime permission requests, camera and microphone included, are granted
 * silently. Versions up to 1.1.1 shipped it. Own permissions go through setPermissionGrantState.
 */
val verifyNoDeviceWideAutoGrant by tasks.registering {
    group = "verification"
    description = "Fails if any source sets the device-wide runtime-permission auto-grant policy."
    val sources = fileTree("src") { include("**/*.kt", "**/*.java") }
    inputs.files(sources)
    doLast {
        val call = Regex("""setPermissionPolicy\s*\([^)]*AUTO_GRANT""")
        // Comments may name the call to warn against it; only code counts.
        val comments = Regex("""/\*[\s\S]*?\*/|//[^\n]*""")
        val offenders = sources.files.filter { call.containsMatchIn(it.readText().replace(comments, "")) }
        if (offenders.isNotEmpty()) {
            throw GradleException(
                "SECURITY GATE: setPermissionPolicy(...AUTO_GRANT) in ${offenders.map { it.name }}. " +
                    "It silently grants every app's permissions; grant Norypt's own one by one.",
            )
        }
        logger.lifecycle("✓ no device-wide permission auto-grant")
    }
}

/** One entry point for CI and for humans. */
val securityGates by tasks.registering {
    group = "verification"
    description = "Runs every build-time security gate."
    dependsOn(
        verifyReleaseSigning,
        verifyNoNetworkPermission,
        verifyNoTelemetryInReleaseDex,
        verifyNoDeviceWideAutoGrant,
    )
}
