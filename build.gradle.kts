buildscript {
    dependencies {
        // AGP supports using a newer KGP runtime while keeping built-in Kotlin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.3.21")
    }
}

plugins {
    id("com.android.application") version "9.1.0" apply false
}
