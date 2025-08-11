plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.meruscrap"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.meruscrap"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Signing configs - Using environment variables for security
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("KEYSTORE_FILE") ?: "meruscrap-release-key.keystore")
            storePassword = System.getenv("KEYSTORE_PASSWORD")
            keyAlias = System.getenv("KEY_ALIAS") ?: "meruscrap-key"
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            // Add these for security
            isDebuggable = false
            isJniDebuggable = false
            isRenderscriptDebuggable = false
            isZipAlignEnabled = true

            // Sign with your release key
            signingConfig = signingConfigs.getByName("release")

            // Ensure BuildConfig is generated
            buildConfigField("boolean", "IS_PRODUCTION", "true")
        }

        debug {
            // Keep debug settings for development
            isDebuggable = true
            isMinifyEnabled = false

            // Ensure BuildConfig is generated for debug too
            buildConfigField("boolean", "IS_PRODUCTION", "false")
        }
    }

    // Explicitly enable BuildConfig generation
    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.preference)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    // Add these for enhanced security (optional)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("androidx.biometric:biometric:1.1.0")
}