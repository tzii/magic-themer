buildscript {
    dependencies {
        // AGP supports using a newer KGP runtime while keeping built-in Kotlin.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.3" apply false
}
