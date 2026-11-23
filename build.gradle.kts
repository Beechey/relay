plugins {
    // AGP 9 compiles Kotlin itself (no kotlin-android plugin); Material 3 1.5 needs AGP 9.1+ and compileSdk 37
    id("com.android.application") version "9.5.2" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.21" apply false
    id("com.android.test") version "9.5.2" apply false
    id("androidx.baselineprofile") version "1.5.0" apply false
}
