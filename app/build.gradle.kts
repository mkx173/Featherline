import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.ksp)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

val gitCommitHash = providers.exec {
    commandLine("git", "rev-parse", "--short=6", "HEAD")
}.standardOutput.asText.get().trim()

val gitFullCommitHash = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.get().trim()

val keystoreProperties = Properties()
val keystorePropertiesFile = rootProject.file("keystore.properties")

if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(keystorePropertiesFile.inputStream())
}

fun signingValue(name: String): String? {
    return keystoreProperties.getProperty(name)
        ?: System.getenv(name)
}

android {
    namespace = "com.mkx.hrttracker"
    compileSdk = 37
    flavorDimensions += "distribution"

    signingConfigs {
        create("release") {
            val storeFilePath = signingValue("RELEASE_KEYSTORE_PATH")
                ?: signingValue("storeFile")

            if (storeFilePath != null) {
                storeFile = File(storeFilePath)
                storePassword = signingValue("RELEASE_KEYSTORE_PASSWORD")
                    ?: signingValue("storePassword")
                keyAlias = signingValue("RELEASE_KEY_ALIAS")
                    ?: signingValue("keyAlias")
                keyPassword = signingValue("RELEASE_KEY_PASSWORD")
                    ?: signingValue("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.mkx.hrttracker"
        minSdk = 26
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "1.3.2"

        buildConfigField(
            "String",
            "THIRD_PARTY_NOTICES_URL",
            "\"https://github.com/mkx173/Featherline/blob/$gitFullCommitHash/docs/third-party-notices.md\""
        )

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    productFlavors {
        create("play") {
            dimension = "distribution"
            // No ABI filter: App Bundle contains all ABIs
        }

        create("arm64") {
            dimension = "distribution"

            ndk {
                abiFilters += listOf("arm64-v8a")
            }
        }

        create("x64") {
            dimension = "distribution"

            ndk {
                abiFilters += listOf("x86_64")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-$gitCommitHash"
            signingConfig = signingConfigs.getByName("debug")
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }

        // Release-equivalent build for :benchmark macrobenchmarks: debug-signed so it
        // installs without the release keystore, profileable so the benchmark harness
        // can trace it, and id-suffixed so it can never collide with (or overwrite the
        // data of) an installed production build on a personal device.
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isProfileable = true
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources{
        generateLocaleConfig = true
    }

    bundle {
        language {
            enableSplit = false
        }
    }

    packaging {
        resources {
            // Bundled Apache license/notice copies and per-artifact version stamps
            // aren't needed at runtime (third-party attribution is served via
            // THIRD_PARTY_NOTICES_URL). DebugProbesKt.bin is the kotlinx-coroutines
            // debug-agent probe, unused in release.
            excludes += listOf(
                "META-INF/**/LICENSE.txt",
                "META-INF/NOTICE.md",
                "META-INF/*.version",
                "DebugProbesKt.bin",
            )
        }
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.isIncludeAndroidResources = true
    }
}


androidComponents {
    onVariants(selector().all()) { variant ->
        variant.outputs.forEach { output ->
            val flavor = variant.productFlavors
                .find { it.first == "distribution" }
                ?.second ?: "universal"

            val abiName = when (flavor) {
                "arm64" -> "arm64-v8a"
                "x64" -> "x86_64"
                "play" -> "all-abis"
                else -> flavor
            }

            output.outputFileName.set(
                "${rootProject.name.lowercase()}-${abiName}-${output.versionName.get()}-${output.versionCode.get()}.apk"
            )
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.constraintlayout.compose)
    implementation(libs.androidx.material3)
    implementation(libs.reorderable)
    implementation(libs.androidx.material3.adaptive.navigation.suite)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.biometric)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.hilt.android)
    implementation(libs.moshi)
    implementation(libs.sqlcipher.android)
    implementation(libs.argon2kt)
    implementation(libs.haze)
    implementation(libs.haze.blur)
    implementation(libs.haze.blur.materials)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.kizitonwose.calendar)
    implementation(libs.vico.compose.m3)
    implementation(libs.material.kolor)
    implementation(libs.swmansion.kmp.wheel.picker)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.glance.appwidget.preview)
    implementation(libs.androidx.glance.preview)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.ui.test.junit4)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.mockk.android)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    // createComposeRule() relies on the test-only ComponentActivity from
    // ui-test-manifest being merged into the app-under-test manifest.
    debugImplementation(libs.androidx.ui.test.manifest)
}
