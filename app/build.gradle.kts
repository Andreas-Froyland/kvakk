import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

// Release signing key, created by scripts/create-keystore.sh. Kept out of git.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// CI passes -PkvakkVersion=1.2.3 from the release tag. versionCode must grow with every
// release, or Android refuses to install the update.
val kvakkVersion = (findProperty("kvakkVersion") as String?) ?: "0.1.0"
// Where installed phones look for updates. Forks can point this at their own releases.
val updateUrl = (findProperty("kvakkUpdateUrl") as String?)
    ?: "https://github.com/Andreas-Froyland/kvakk/releases/latest/download/kvakk.apk"
val kvakkVersionCode = kvakkVersion.split(".").map(String::toInt).let { (major, minor, patch) ->
    major * 10_000 + minor * 100 + patch
}

android {
    namespace = "app.kvakk"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.kvakk"
        // Lock task features (hiding notifications, keeping the power menu) need API 28.
        minSdk = 28
        targetSdk = 36
        versionCode = kvakkVersionCode
        versionName = kvakkVersion
        buildConfigField("String", "UPDATE_URL", "\"$updateUrl\"")
    }

    signingConfigs {
        if (!keystoreProperties.isEmpty) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.09.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.palette:palette-ktx:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
}
