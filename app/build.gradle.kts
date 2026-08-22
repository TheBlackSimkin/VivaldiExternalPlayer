plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.chaquo.python")
}

android {
    namespace = "com.example.vivaldiplayer"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.vivaldiplayer"
        minSdk = 24
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0"

        /*
         * These values are visible in the local About screen. GitHub Actions
         * supplies them automatically; local Android Studio builds fall back to
         * readable development values.
         */
        val githubSha = System.getenv("GITHUB_SHA")?.take(8) ?: "local"
        val githubRun = System.getenv("GITHUB_RUN_NUMBER") ?: "local"
        buildConfigField("String", "GIT_COMMIT", "\"$githubSha\"")
        buildConfigField("String", "BUILD_RUN", "\"$githubRun\"")

        ndk {
            abiFilters += listOf("arm64-v8a")
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )

            /*
             * A persistent release signing key is provided only through CI/local
             * environment variables. Never commit a private signing key to this
             * public repository.
             */
            val storeFilePath = System.getenv("VEP_KEYSTORE_PATH")
            if (!storeFilePath.isNullOrBlank()) {
                signingConfig = signingConfigs.create("persistentRelease") {
                    storeFile = file(storeFilePath)
                    val persistentStorePassword = System.getenv("VEP_KEYSTORE_PASSWORD")
                    storePassword = persistentStorePassword
                    keyAlias = System.getenv("VEP_KEY_ALIAS")

                    /*
                     * The permanent release keystore is PKCS#12. Its private key
                     * is protected by the same password as the keystore itself,
                     * so use that single authoritative value here. Keeping a
                     * separate key-password secret would allow the two values to
                     * drift and make otherwise-valid release builds fail at the
                     * packaging/signing stage.
                     */
                    keyPassword = persistentStorePassword
                }
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
}

chaquopy {
    defaultConfig {
        version = "3.13"

        pip {
            install("yt-dlp==2026.06.09")
        }
    }
}

dependencies {
    val media3Version = "1.10.1"

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.1")
    implementation("androidx.recyclerview:recyclerview:1.4.0")

    // Reliable, Android-managed background pre-resolution. This never owns playback.
    implementation("androidx.work:work-runtime-ktx:2.11.2")

    implementation("androidx.media3:media3-common:$media3Version")
    implementation("androidx.media3:media3-datasource:$media3Version")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-inspector-frame:$media3Version")
}
