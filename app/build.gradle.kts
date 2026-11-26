plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Firebase config is per project and not in the repo by default: put the console's google-services.json in app/
// (or the GOOGLE_SERVICES_JSON secret in CI). Without it the app builds and runs with Firebase switched off.
val hasFirebase = file("google-services.json").exists()
if (hasFirebase) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}
// AdMob ids: Google's public test ids unless the real ones are given (gradle.properties, -P or env).
fun prop(name: String, fallback: String) = (findProperty(name) as String?) ?: System.getenv(name) ?: fallback
val admobTestApp = "ca-app-pub-3940256099942544~3347511713"
val admobTestRewarded = "ca-app-pub-3940256099942544/5224354917"

android {
    namespace = "com.graviton94.notificationcleaner"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.graviton94.notificationcleaner"
        minSdk = 26
        targetSdk = 36
        versionCode = 4
        versionName = "0.4.0-web"
        buildConfigField("boolean", "FIREBASE", hasFirebase.toString())
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".playtest"
            // debug builds never show live ads
            manifestPlaceholders["admobAppId"] = admobTestApp
            buildConfigField("String", "ADMOB_REWARDED", "\"$admobTestRewarded\"")
        }
        release {
            manifestPlaceholders["admobAppId"] = prop("ADMOB_APP_ID", admobTestApp)
            buildConfigField("String", "ADMOB_REWARDED", "\"${prop("ADMOB_REWARDED_ID", admobTestRewarded)}\"")
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("com.graviton94.notificationcleaner:notification-cleaner-core")
    implementation("androidx.webkit:webkit:1.14.0")
    // 21 platform: Firebase (analytics, crash reports, login, cloud save, remote rules) and AdMob rewarded ads + consent
    implementation(platform("com.google.firebase:firebase-bom:34.0.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-config")
    implementation("com.google.android.gms:play-services-ads:24.4.0")
    implementation("com.google.android.ump:user-messaging-platform:3.2.0")
    testImplementation("junit:junit:4.13.2")
}
