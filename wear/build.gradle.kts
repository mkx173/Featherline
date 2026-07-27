import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val gitCommitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
}.standardOutput.asText.get().trim().toInt()

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
    namespace = "com.mkx.hrttracker.wear"
    compileSdk = 37

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
        minSdk = 30
        targetSdk = 37
        versionCode = gitCommitCount
        versionName = "1.3.3"
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
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

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":wear-protocol"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    implementation(libs.play.services.wearable)
    implementation(libs.guava)

    testImplementation(libs.junit)
    debugImplementation(libs.androidx.ui.tooling)
}
