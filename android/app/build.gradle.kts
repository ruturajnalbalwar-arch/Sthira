plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.serialization")
}

/** Single source of truth: the model, params.json and norms.json come from the web project, never copied by hand. */
val sharedAssets = layout.buildDirectory.dir("generated/sharedAssets")
val copySharedAssets by tasks.registering(Copy::class) {
    from(rootProject.file("../web/public/models/pose_landmarker_full.task"))
    from(rootProject.file("../web/src/lib/sthira/params/params.json"))
    from(rootProject.file("../web/src/lib/sthira/norms/norms.json"))
    into(sharedAssets)
}

android {
    namespace = "app.sthira"
    compileSdk = 34

    defaultConfig {
        applicationId = "app.sthira"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        // The keystore lives outside the repo and OneDrive; passwords come from the environment only.
        create("release") {
            val keystore = System.getenv("STHIRA_KEYSTORE")
            if (keystore != null) {
                storeFile = file(keystore)
                storePassword = System.getenv("STHIRA_KS_PASS")
                keyAlias = System.getenv("STHIRA_KEY_ALIAS") ?: "sthira"
                keyPassword = System.getenv("STHIRA_KEY_PASS") ?: System.getenv("STHIRA_KS_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (System.getenv("STHIRA_KEYSTORE") != null) signingConfig = signingConfigs.getByName("release")
        }
    }

    sourceSets["main"].assets.srcDir(sharedAssets)

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.8" }
    androidResources { noCompress += "task" }
    // Unit tests read the shared engine fixtures (test-only; nothing is packaged).
    testOptions {
        unitTests.all {
            it.systemProperty("sthira.golden", rootProject.file("../shared/golden/chair-stand.golden.json").absolutePath)
            it.systemProperty("sthira.norms", rootProject.file("../web/src/lib/sthira/norms/norms.json").absolutePath)
        }
    }
    // AGP 8.5.0 does not 16 KB-align uncompressed native libraries; legacy packaging avoids the issue.
    packaging { jniLibs { useLegacyPackaging = true } }
}

tasks.named("preBuild") { dependsOn(copySharedAssets) }

dependencies {
    implementation(project(":engine"))

    implementation(platform("androidx.compose:compose-bom:2023.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.activity:activity-compose:1.8.1")
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

    val camerax = "1.4.2"
    implementation("androidx.camera:camera-core:$camerax")
    implementation("androidx.camera:camera-camera2:$camerax")
    implementation("androidx.camera:camera-lifecycle:$camerax")
    implementation("androidx.camera:camera-video:$camerax")
    implementation("androidx.camera:camera-view:$camerax")

    implementation("com.google.mediapipe:tasks-vision:1.1.1")

    testImplementation("junit:junit:4.13.2")
}
