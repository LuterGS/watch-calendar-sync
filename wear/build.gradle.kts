plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.lutergs.watchcalsync.wear"
    compileSdk = 37

    defaultConfig {
        // Must match :mobile exactly — the Data Layer pairs the phone and watch apps
        // by applicationId + signing key.
        applicationId = "dev.lutergs.watchcalsync"
        // Wear OS 3 and up. Pixel Watch 4 runs Wear OS 7 (API 36).
        minSdk = 30
        targetSdk = 37
        // Overridable so a tagged CI build stamps the tag onto the APK instead of
        // leaving whatever was last committed here.
        versionCode = (findProperty("appVersionCode") as String?)?.toInt() ?: 1
        versionName = (findProperty("appVersionName") as String?) ?: "0.1"
    }


    // Release signing comes from the environment so CI can sign with a stable key.
    // :mobile and :wear MUST end up with the same key — the Data Layer pairs the two
    // apps on applicationId + signing certificate, so a mismatch silently stops the
    // watch from ever receiving anything.
    //
    // With no keystore configured this falls back to the debug key, which keeps
    // `./gradlew assembleRelease` working locally with zero setup. Those builds are
    // fine to sideload but cannot upgrade a CI-signed install, and vice versa.
    signingConfigs {
        create("releaseEnv") {
            val keystore = System.getenv("RELEASE_KEYSTORE_PATH")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (System.getenv("RELEASE_KEYSTORE_PATH") != null) {
                signingConfigs.getByName("releaseEnv")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    buildFeatures {
        compose = true
    }
}

composeCompiler {
    // :shared has no Compose compiler, so its model classes would otherwise be
    // inferred unstable and defeat skipping in the agenda list.
    stabilityConfigurationFiles.add(
        rootProject.layout.projectDirectory.file("compose_stability.conf")
    )
    reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
    metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
}

dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    // Compose runtime/UI still come from the phone BOM; only the *material* layer
    // is Wear-specific.
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    implementation(libs.androidx.wear.compose.material3)
    implementation(libs.androidx.wear.compose.foundation)
    implementation(libs.androidx.wear.tooling.preview)

    // Tiles run in the system's Tile renderer, not in this app's Compose tree,
    // so they use ProtoLayout instead of Compose.
    implementation(libs.androidx.wear.tiles)
    implementation(libs.androidx.wear.protolayout)
    implementation(libs.androidx.wear.protolayout.material3)
    implementation(libs.androidx.wear.protolayout.expression)
    // TileService returns ListenableFuture and is called on the main thread, so the
    // snapshot read has to be bridged off it rather than blocked on.
    implementation(libs.kotlinx.coroutines.guava)
    implementation(libs.androidx.wear.complications.datasource.ktx)
    debugImplementation(libs.androidx.wear.tiles.tooling)
    implementation(libs.androidx.wear.tiles.tooling.preview)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
