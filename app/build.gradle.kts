plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")  // ✅ Google Services plugin
}

android {
    namespace = "com.vplan.rxtprob"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.vplan.rxtprob"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        viewBinding = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // ✅ Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:34.6.0"))
    implementation("com.google.firebase:firebase-database-ktx")
    implementation("com.google.firebase:firebase-auth-ktx")

    // ✅ Gmail OAuth + API
    implementation("com.google.android.gms:play-services-auth:21.4.0")
    implementation("com.google.api-client:google-api-client-android:2.8.1")
    implementation("com.google.oauth-client:google-oauth-client-jetty:1.39.0")
    implementation("com.google.apis:google-api-services-gmail:v1-rev110-1.25.0")

    // ✅ Test dependencies
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}
