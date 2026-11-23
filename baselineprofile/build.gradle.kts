// Generates app/src/main/generated/baselineProfiles: run ./gradlew :app:generateBaselineProfile with a phone
// emulator connected and the login passed as -Pandroid.testInstrumentationRunnerArguments.server/user/pass=…
plugins {
    id("com.android.test")
    id("androidx.baselineprofile")
}

android {
    namespace = "com.avetero.relay.baselineprofile"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig {
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    targetProjectPath = ":app"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

baselineProfile { useConnectedDevices = true }

dependencies {
    implementation("androidx.test.ext:junit:1.3.0")
    implementation("androidx.test:runner:1.7.0")
    implementation("androidx.test.uiautomator:uiautomator:2.4.1")
    implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}
