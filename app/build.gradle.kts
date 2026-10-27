import java.util.Properties

plugins {
    id("com.android.application")
}

// Release signing stays outside Git: copy keystore.properties.example to keystore.properties.
val keystoreProperties = Properties().apply {
    rootProject.file("keystore.properties").takeIf { it.isFile }?.inputStream()?.use(::load)
}

android {
    namespace = "dev.magicthemer"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.magicthemer"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.7.0"
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) create("release") {
            storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
            storePassword = keystoreProperties.getProperty("storePassword")
            keyAlias = keystoreProperties.getProperty("keyAlias")
            keyPassword = keystoreProperties.getProperty("keyPassword")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Without keystore.properties the release APK is left unsigned.
            signingConfig = signingConfigs.findByName("release")
        }
    }

    // Omit Google's encrypted dependency metadata from distributed artifacts.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
