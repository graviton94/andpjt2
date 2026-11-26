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
        // store id follows the other apps (io.github.graviton94.<app>); namespace stays the Kotlin package
        applicationId = "io.github.graviton94.donotdisturb"
        minSdk = 26
        targetSdk = 36
        // Play versionCode = 1000 + AAB workflow run number (always above the old test builds); local/test builds stay 4
        versionCode = System.getenv("DND_VERSION_CODE")?.toIntOrNull()?.let { 1000 + it } ?: 4
        versionName = System.getenv("DND_VERSION_NAME")?.takeIf { it.isNotBlank() } ?: "0.4.0-web"
        buildConfigField("boolean", "FIREBASE", hasFirebase.toString())
        // Kakao native app key (public, ships in the APK; secret KAKAO_NATIVE_APP_KEY in CI). Empty = Kakao sign-in hidden.
        val kakaoKey = prop("KAKAO_NATIVE_APP_KEY", "")
        buildConfigField("String", "KAKAO_KEY", "\"$kakaoKey\"")
        manifestPlaceholders["kakaoScheme"] = if (kakaoKey.isNotBlank()) "kakao$kakaoKey" else "kakao-disabled"
    }

    // Signing keys like the other apps (keystore/README.md): CI unlocks keystore/*.jks.gpg into a temp file and passes
    // paths and passwords as env. Test APKs use the sideload key (fixed SHA for Firebase); Play bundles the upload key.
    signingConfigs {
        val sideloadStore = System.getenv("DND_SIDELOAD_STORE")?.takeIf { it.isNotBlank() && file(it).exists() }
        val sideloadPass = System.getenv("DND_SIDELOAD_PASSWORD").orEmpty()
        if (sideloadStore != null && sideloadPass.isNotBlank()) {
            create("sideload") {
                storeFile = file(sideloadStore); storePassword = sideloadPass; keyAlias = "sideload"; keyPassword = sideloadPass
            }
        }
        val uploadStore = System.getenv("ANDROID_UPLOAD_KEYSTORE")?.takeIf { it.isNotBlank() && file(it).exists() }
        val uploadVars = listOf("ANDROID_UPLOAD_STORE_PASSWORD", "ANDROID_UPLOAD_KEY_ALIAS", "ANDROID_UPLOAD_KEY_PASSWORD").map { System.getenv(it).orEmpty() }
        if (uploadStore != null && uploadVars.all { it.isNotBlank() }) {
            create("upload") {
                storeFile = file(uploadStore); storePassword = uploadVars[0]; keyAlias = uploadVars[1]; keyPassword = uploadVars[2]
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
            // debug builds never show live ads
            manifestPlaceholders["admobAppId"] = admobTestApp
            buildConfigField("String", "ADMOB_REWARDED", "\"$admobTestRewarded\"")
        }
        release {
            signingConfig = signingConfigs.findByName("upload") ?: signingConfigs.findByName("sideload")
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
    // sign-in (Google via Credential Manager, Kakao SDK → Cloud Function custom token) and Play Billing
    implementation("com.google.firebase:firebase-functions")
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("com.kakao.sdk:v2-user:2.21.4")
    implementation("com.android.billingclient:billing-ktx:8.0.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    testImplementation("junit:junit:4.13.2")
}
