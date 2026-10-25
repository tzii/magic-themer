plugins {
    id("com.android.application")
}

android {
    namespace = "dev.magicthemer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.magicthemer"
        minSdk = 26
        targetSdk = 36
        versionCode = 6
        versionName = "0.6.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    testImplementation("junit:junit:4.13.2")
}
