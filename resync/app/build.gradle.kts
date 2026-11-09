import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "io.github.radixtrator.resync"
    // Compose's alpha BOM needs 37.1+.
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.radixtrator.resync"
        minSdk = 26
        targetSdk = 37
        // Release builds take these from the tag (see .github/workflows/release.yml): -PversionCode=… -PversionName=…
        versionCode = providers.gradleProperty("versionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("versionName").orNull ?: "0.1.0"
    }

    // The release key comes from the environment (CI secrets), so it never lives in the repo. Without it, release
    // builds stay unsigned. Every published release must use the same key, or Obtainium/Android can't update in place.
    val releaseKeystore = System.getenv("RESYNC_KEYSTORE")?.let(::file)?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RESYNC_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RESYNC_KEY_ALIAS")
                keyPassword = System.getenv("RESYNC_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseKeystore != null) signingConfig = signingConfigs.getByName("release")
            // The bundled demo feed (Setup's "browse demo data") is for checking the UI, not for users.
            buildConfigField("boolean", "DEMO", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            buildConfigField("boolean", "DEMO", "true")
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// The baseline profile is recorded on release-like builds (:baselineprofile drives the demo feed), so those builds get
// the demo data and Setup's demo button too.
androidComponents {
    onVariants { variant ->
        if (variant.buildType in setOf("nonMinifiedRelease", "benchmarkRelease")) {
            variant.buildConfigFields?.put("DEMO", com.android.build.api.variant.BuildConfigField("boolean", "true", null))
            variant.sources.assets?.addStaticSourceDirectory("src/debug/assets")
        }
    }
}

baselineProfile {
    // Regenerated on request (./gradlew :app:generateBaselineProfile), not on every release build.
    automaticGenerationDuringBuild = false
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.core)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.ui)
    implementation(libs.androidx.browser)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(libs.kotlin.test.junit)
}
