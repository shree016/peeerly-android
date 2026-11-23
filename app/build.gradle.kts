plugins {
    id("com.android.application")
}

android {
    namespace = "tech.peeerly.app"
    compileSdk {
        version = release(37) {
            minorApiLevel = 0
        }
    }

    defaultConfig {
        // Permanent: changing this later makes Android treat it as a different app.
        applicationId = "tech.peeerly.app"
        minSdk = 29 // Android 10
        targetSdk = 36 // Android 16
        // Raise versionCode by 1 for every APK you share.
        versionCode = 2
        versionName = "1.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("androidx.activity:activity:1.13.0")
    implementation("androidx.core:core:1.19.1")
    implementation("androidx.core:core-splashscreen:1.2.0")
    // Play services pulls in an old Fragment that breaks the file picker.
    implementation("androidx.fragment:fragment:1.8.9")
    implementation("androidx.webkit:webkit:1.18.0")

    // Google account picker (Google blocks its login page inside WebViews)
    implementation("androidx.credentials:credentials:1.6.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.6.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.2.1")

    testImplementation("junit:junit:4.13.2")
}
