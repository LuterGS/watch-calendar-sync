plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "dev.lutergs.watchcalsync.mobile"
    compileSdk = 37

    defaultConfig {
        // Must match :wear exactly — the Data Layer pairs the phone and watch apps
        // by applicationId + signing key.
        applicationId = "dev.lutergs.watchcalsync"
        minSdk = 30
        targetSdk = 37
        versionCode = 1
        versionName = "0.1"
    }

    buildTypes {
        release {
            // Personal sideload build: signed with the debug key so release APKs
            // still pair with the watch. No Play Store distribution.
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("debug")
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
    // inferred unstable and defeat skipping in the agenda and calendar lists.
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
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.play.services.wearable)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
