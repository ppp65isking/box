plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ppp65isking.box"
    // compileSdk 37: the material3 1.5.0 line (Material 3 Expressive) needs
    // compileSdk 37. The android-CLI trial 2026-11-09 PASSED (the new
    // `android` CLI from a downloaded cmdline-tools 15859902 installs
    // platforms/android-37.0@2.0.0 --beta; see workflow). The platform
    // lands at android-37.0, so compileSdkMinor = 0 is required for AGP
    // to resolve it.
    // targetSdk stays 36 (Play floor); minSdk stays 26.
    compileSdk = 37
    compileSdkMinor = 0

    defaultConfig {
        applicationId = "com.ppp65isking.box"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        // Google's Desktop OAuth client needs its client_secret sent on
        // the token exchange AND the refresh, alongside the PKCE verifier
        // (Google's endpoint enforces it even though the docs call it
        // "optional"). The secret is injected by CI from the
        // GOOGLE_CLIENT_SECRET repo secret and never lives in this repo;
        // local builds without the env var get an empty string and still
        // compile (Google sign-in just fails with the provider's error).
        buildConfigField(
            "String",
            "GOOGLE_CLIENT_SECRET",
            "\"${System.getenv("GOOGLE_CLIENT_SECRET") ?: ""}\"",
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    // AGP 9.1 bundles Kotlin 2.2.10 — no kotlin-android plugin; configure the
    // compiler through the kotlin extension instead of kotlinOptions.
    kotlin {
        compilerOptions {
            jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    // Compose BOM 2026.09.00 (Compose 1.12.1): material3 1.5.0-rc01
    // (Material 3 Expressive) was built against Compose 1.12.0, so
    // the stack has to be 1.12.x — the old 2026.06.01 BOM pinned
    // 1.11.4, which material3 1.5.0 is not binary-compatible with.
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Material 3 Expressive (1.5.0-rc01, the current RC per the
    // Android Developers release page, 2026-11-04) — pinned
    // explicitly to override the BOM's stable 1.4.0. Requires
    // compileSdk 37, which CI now has (android-CLI trial 2026-11-09
    // installed platforms/android-37.0@2.0.0 --beta; see workflow).
    // 1.5.0-rc01 was built against Compose 1.12.0, which the
    // 2026.09.00 BOM provides as 1.12.1.
    implementation("androidx.compose.material3:material3:1.5.0-rc01")
    // Icons are not transitive since 1.4.0 — declare both explicitly.
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Media3 — audio-only playback (ExoPlayer + HLS + MediaSession).
    implementation("androidx.media3:media3-exoplayer:1.11.1")
    implementation("androidx.media3:media3-session:1.11.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.11.1")

    // Network + JSON: OkHttp directly + kotlinx-serialization-json tree
    // parsing (JsonElement, no @Serializable plugin needed).
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Thumbnails / avatars.
    implementation("io.coil-kt:coil-compose:2.7.0")

    // Health Connect (steps card on Home) — read-only. 1.1.0 stable:
    // the 1.1.0-alpha10 permission-request contract silently did
    // nothing on some phones (no popup, no grant).
    implementation("androidx.health.connect:connect-client:1.1.0")

    // Custom Tabs — OAuth sign-in pages open in-app and redirect back
    // to box://oauth/<service>. Small, stable, no extra setup.
    implementation("androidx.browser:browser:1.8.0")

    // Jetpack WindowManager — foldable detection (FoldingFeature) for
    // the vertical nav rail on foldables.
    implementation("androidx.window:window:1.4.0")

    // Haze 1.7.3 — real backdrop blur for the floating bottom bar
    // (replaces the fake translucent fill). PINNED at 1.7.3 on
    // purpose: Haze 2.x (2.0.0–2.1.0) is compiled with Kotlin 2.4
    // metadata, which the bundled Kotlin 2.2.10 compiler cannot read
    // (build 42677324517 failed on exactly that). AGP 9.1.1 bundles
    // Kotlin 2.2.10 and applying a newer Kotlin plugin is
    // incompatible with AGP 9.x, so 2.x is unreachable until AGP
    // ships a newer Kotlin. 1.7.3 was compiled with Kotlin 2.3.20
    // (metadata 2.3.0), which 2.2.10 can read. Single artifact in
    // 1.x — no haze-blur / haze-blur-material3 split yet — and the
    // 1.x API is hazeSource + hazeEffect(state, style).
    implementation("dev.chrisbanes.haze:haze:1.7.3")

    // AI in Box, Phase 1 (see ai-in-box-research.md §9): the router
    // tries Gemini Nano first, then a downloaded LiteRT-LM model, then
    // free cloud legs via the user's own key, then ML Kit classic.
    // ML Kit GenAI (Gemini Nano via AICore) — needs minSdk 26, which
    // Box already is. 1.0.0-beta4 is the version the official docs
    // print; its Kotlin metadata is 2.3.0, which the bundled Kotlin
    // 2.2.10 can still read (2.2 reads up to 2.3.0).
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")
    // ML Kit classic: Smart Reply (bundled model, no download) and
    // Entity Extraction (model downloads on first use via Play
    // Services). Both work on essentially every phone, no key.
    implementation("com.google.mlkit:smart-reply:17.0.4")
    implementation("com.google.mlkit:entity-extraction:16.0.0-beta6")
    // LiteRT-LM — the downloaded-model runtime (MediaPipe LLM
    // Inference is maintenance-only; new code starts here). PINNED at
    // 0.16.1: it carries Kotlin metadata 2.3.0 and builds with the
    // bundled Kotlin 2.2.10, while 0.17.0+ carry metadata 2.4.0 and
    // need Kotlin 2.3+. Only arm64-v8a + x86_64 native libs ship —
    // the code checks Build.SUPPORTED_ABIS before loading.
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.16.1")
    // Coroutines pin the LiteRT-LM recipe requires: the AAR's POM
    // asks for an older coroutines version than its bytecode calls,
    // and without this pin streaming replies crash with
    // NoSuchMethodError when the stream completes.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
}
