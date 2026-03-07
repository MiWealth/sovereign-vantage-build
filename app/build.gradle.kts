/**
 * SOVEREIGN VANTAGE V5.17.0 "ARTHUR EDITION"
 * App Build Configuration — DEBUG ONLY (no obfuscation)
 *
 * © 2025-2026 MiWealth Pty Ltd
 * Creator & Founder: Mike Stahl
 * Co-Founder & CTO (In Memoriam): Arthur Iain McManus (1966-2025)
 */

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.dagger.hilt.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.miwealth.sovereignvantage"
    compileSdk = 35

    // ── Stable debug signing — same signature every CI build ──
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    defaultConfig {
        applicationId = "com.miwealth.sovereignvantage"
        minSdk = 26
        targetSdk = 35
        versionCode = 519133
        versionName = "5.19.133-arthur"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Room schema export for migration tracking
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false    // No obfuscation — debug/test only
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = false    // Obfuscation OFF until production
            isDebuggable = false
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
        viewBinding = true         // AnalyticsDashboardActivity + BacktestingActivity use XML layouts
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/DEPENDENCIES"
        }
    }

    // ── APK naming: SovereignVantage-debug.apk / SovereignVantage-release.apk ──
    applicationVariants.all {
        val variant = this
        outputs.all {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            output.outputFileName = "SovereignVantage-${variant.buildType.name}.apk"
        }
    }
}

// ══════════════════════════════════════════════════════════════
// DEPENDENCY VERSIONS — single source of truth
// ══════════════════════════════════════════════════════════════

val composeBom      = "2024.09.03"
val hiltVersion     = "2.51.1"
val roomVersion     = "2.6.1"
val okHttpVersion   = "4.12.0"
val retrofitVersion = "2.9.0"
val gsonVersion     = "2.10.1"
val coroutinesVer   = "1.8.1"
val navVersion      = "2.8.3"
val lifecycleVer    = "2.8.6"
val workVersion     = "2.9.1"
val sqlcipherVer    = "4.6.1"   // V5.6.1: Migrated from deprecated android-database-sqlcipher
val biometricVer    = "1.1.0"
val securityVer     = "1.1.0-alpha06"
val splashVer       = "1.0.1"
val zxingVersion    = "3.5.3"

dependencies {

    // ── Core Android ──────────────────────────────────────────
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")  // V5.6.1: XML themes (Theme.Material3.DayNight)
    implementation("androidx.activity:activity-compose:1.9.2")
    implementation("androidx.fragment:fragment-ktx:1.8.4")
    implementation("androidx.core:core-splashscreen:$splashVer")

    // ── Jetpack Compose (BOM) ─────────────────────────────────
    implementation(platform("androidx.compose:compose-bom:$composeBom"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.animation:animation")
    implementation("androidx.compose.runtime:runtime")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // ── Navigation ────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:$navVersion")
    implementation("androidx.hilt:hilt-navigation-compose:1.2.0")

    // ── Lifecycle ─────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycleVer")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:$lifecycleVer")

    // ── Hilt (Dependency Injection) ───────────────────────────
    implementation("com.google.dagger:hilt-android:$hiltVersion")
    ksp("com.google.dagger:hilt-android-compiler:$hiltVersion")

    // ── Room + SQLCipher (Encrypted Local DB) ─────────────────
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")
    implementation("net.zetetic:sqlcipher-android:$sqlcipherVer")  // V5.7.0: Removed @aar to bundle native .so libs
    implementation("androidx.sqlite:sqlite-ktx:2.4.0")

    // ── Networking ────────────────────────────────────────────
    implementation("com.squareup.okhttp3:okhttp:$okHttpVersion")
    implementation("com.squareup.okhttp3:logging-interceptor:$okHttpVersion")
    implementation("com.squareup.okhttp3:okhttp-sse:$okHttpVersion")   // Server-Sent Events if needed
    implementation("com.squareup.retrofit2:retrofit:$retrofitVersion")
    implementation("com.squareup.retrofit2:converter-gson:$retrofitVersion")

    // ── JSON ──────────────────────────────────────────────────
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    // ── Coroutines ────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:$coroutinesVer")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVer")

    // ── WorkManager (EconomicCalendarService) ─────────────────
    implementation("androidx.work:work-runtime-ktx:$workVersion")

    // ── Security ──────────────────────────────────────────────
    implementation("androidx.security:security-crypto:$securityVer")  // EncryptedSharedPreferences
    implementation("androidx.biometric:biometric:$biometricVer")

    // ── QR Code (ZXing — used for licence key activation) ─────
    implementation("com.google.zxing:core:$zxingVersion")

    // ── Testing (minimal for now) ─────────────────────────────
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
}
