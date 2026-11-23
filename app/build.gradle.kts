import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.avetero.relay"
    compileSdk { version = release(37) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.avetero.relay"
        minSdk = 26
        targetSdk = 35
        versionName = "0.3.0-beta.1"
        // 1.2.3 -> 10203, so every tagged release installs over the last
        versionCode = versionName!!.substringBefore('-').split('.').map { it.toInt() }.let { (a, b, c) -> a * 10000 + b * 100 + c }
        // GitHub updates: updateRepo=owner/name in gradle.properties; updateToken only for a private repo, in local.properties (never commit it)
        val local = Properties().apply { rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) } }
        buildConfigField("String", "UPDATE_REPO", "\"${project.findProperty("updateRepo") ?: ""}\"")
        buildConfigField("String", "UPDATE_TOKEN", "\"${local.getProperty("updateToken") ?: ""}\"")
    }
    // CI signs releases from secrets (see docs/06-github-updates.md); local release builds stay unsigned
    val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
    signingConfigs {
        if (keystorePassword != null) create("release") {
            storeFile = file("release.jks")
            storePassword = keystorePassword
            keyAlias = System.getenv("KEY_ALIAS")
            keyPassword = System.getenv("KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            // R8 makes Compose markedly faster (inlining, dead code); rules for the few reflective bits are in proguard-rules.pro
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePassword != null) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi", "-opt-in=androidx.compose.ui.ExperimentalComposeUiApi", "-opt-in=androidx.compose.foundation.layout.ExperimentalLayoutApi", "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api", "-opt-in=androidx.compose.material3.ExperimentalMaterial3ExpressiveApi", "-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api")
    }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2026.11.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // Material icons (R8 keeps only the ones used)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    // Navigation 3: we own the back stacks (one per tab); keys can be any object, so screens keep passing Channel/Vod
    implementation("androidx.navigation3:navigation3-runtime:1.2.2")
    implementation("androidx.navigation3:navigation3-ui:1.2.2")
    // Material 3 for TV: drawer, cards, surfaces with TV focus
    implementation("androidx.tv:tv-material:1.1.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    // MainActivity is a FragmentActivity for the Cast route button; pinned so it agrees with activity-compose
    implementation("androidx.fragment:fragment:1.9.1")
    // the APK is sideloaded, so no Play cloud profiles: this compiles Compose's bundled baseline profiles at install
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.media3:media3-cast:1.5.1")
    implementation("androidx.mediarouter:mediarouter:1.7.0")
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.5.0+1")
    // streaming JSON reader: catalogue lists can be hundreds of MB
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")
    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests
    testImplementation("org.json:json:20240303")
    // XmlPullParser for the XMLTV tests (Android supplies it on devices)
    testImplementation("net.sf.kxml:kxml2:2.3.0")
}
