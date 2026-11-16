// :core — pure Kotlin/JVM library holding the app's rules (SPEC 4.1).
// No Android plugin on purpose: Android classes cannot be imported here, so the compiler
// enforces the "core has no Android" rule. Tests run with `./gradlew :core:test`.
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

// Match :app's Java 11 bytecode so :app can consume this module.
java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    testImplementation(libs.junit)
}
