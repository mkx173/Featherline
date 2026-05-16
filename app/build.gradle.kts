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
        minSdk = 31
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "1.0.0"

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
    }

    buildTypes {
        debug {
            versionNameSuffix = "-$gitCommitHash"
            signingConfig = signingConfigs.getByName("release")
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

        create("benchmark") {
            initWith(getByName("release"))
            applicationIdSuffix = ".benchmark"
            versionNameSuffix = "-benchmark"
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            matchingFallbacks += listOf("release")
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

    testOptions {
        unitTests.isReturnDefaultValues = true
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
    implementation(libs.material)
    implementation(libs.androidx.material3)
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
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.room.compiler)
    ksp(libs.moshi.kotlin.codegen)

    implementation(libs.kizitonwose.calendar)
    implementation(libs.vico.compose.m3)
    implementation(libs.material.kolor)
    implementation(libs.swmansion.kmp.wheel.picker)

    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)

    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
