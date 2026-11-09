plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.ppp65isking.box"
    // compileSdk 37 TRIAL (2026-11-09): the API-37 platform is installed
    // from the SDK beta channel in CI (see build-apk.yml). If that install
    // fails, revert to 36. targetSdk stays 36 (Play floor); minSdk stays 26.
    compileSdk = 37

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
    // Compose BOM 2026.06.01 for everything except material3 (see below).
    val composeBom = platform("androidx.compose:compose-bom:2026.06.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.2")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")

    // Material 3 comes from the BOM (stable 1.4.x line). Do NOT pin the
    // 1.5.0 line (Material 3 Expressive) yet: it drags the whole Compose
    // stack to 1.12.0, which requires compileSdk 37. The beta-channel
    // API-37 install is being trialled (2026-11-09); the Expressive
    // migration is a separate build once the trial goes green. NOTE: the
    // BOM only pins versions — the artifact still needs declaring (no
    // version here).
    implementation("androidx.compose.material3:material3")
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
}
