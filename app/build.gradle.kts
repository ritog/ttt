// :app — the Android module: data and ui layers on top of :core (SPEC 4.1).
// Kotlin is compiled by AGP's built-in Kotlin support (AGP 9+), so no kotlin-android plugin.
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp) // runs Room's code generator
    alias(libs.plugins.room) // exports the schema JSON used by migration tests
}

android {
    namespace = "com.ritoghosh.ttt"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        // Never change after the first release: Android treats a new ID as a different app.
        applicationId = "com.ritoghosh.ttt"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true // for BuildConfig.VERSION_NAME on the About screen
    }
}

// One JSON file per schema version, committed to git. Migration tests read them (docs/CHANGING.md).
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.room.testing)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // Version floor, not a new dependency. Navigation brings kotlinx-serialization 1.7.x;
    // room-testing needs 1.8+ and AGP makes tests use the app's versions, so without this
    // MigrationTest crashes with AbstractMethodError (typeParametersSerializers). DECISIONS.md.
    constraints {
        implementation(libs.kotlinx.serialization.core) {
            because("room-testing needs kotlinx-serialization 1.8+; navigation brings 1.7.x")
        }
    }
}
