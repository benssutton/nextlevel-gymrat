import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // AGP 9 has built-in Kotlin support, so there is no separate kotlin-android plugin.
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
}

android {
    namespace = "com.nextlevel.gymrat"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // Change to an application ID registered in your Play Console (mirrors
        // APP_BUNDLE_IDENTIFIER in ios/Config/Shared.xcconfig).
        applicationId = "com.nextlevel.gymrat"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        // CI passes -Pgymrat.versionCode=<run number> for releases (mirrors CURRENT_PROJECT_VERSION).
        versionCode = providers.gradleProperty("gymrat.versionCode").orNull?.toInt() ?: 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // Upload-key signing for Play, configured only when CI provides the keystore
    // (see .github/workflows/android-release.yml). Local and PR builds are unsigned/debug.
    val uploadKeystore = providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_PATH").orNull
    signingConfigs {
        if (uploadKeystore != null) {
            create("upload") {
                storeFile = file(uploadKeystore)
                storePassword =
                    providers.environmentVariable("ANDROID_UPLOAD_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("ANDROID_UPLOAD_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("ANDROID_UPLOAD_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        // Backend URL per build type, mirroring ios/Config/{Debug,Release}.xcconfig.
        // 10.0.2.2 is the host machine as seen from the Android emulator.
        // Override locally with -Pgymrat.apiBaseUrl=... (like ios/Config/Local.xcconfig).
        debug {
            val url =
                providers.gradleProperty("gymrat.apiBaseUrl").orNull ?: "http://10.0.2.2:8000/"
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
        }
        release {
            val url =
                providers.gradleProperty("gymrat.apiBaseUrl").orNull ?: "https://api.example.com/"
            buildConfigField("String", "API_BASE_URL", "\"$url\"")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.findByName("upload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget = JvmTarget.JVM_17
            // Mirrors SWIFT_TREAT_WARNINGS_AS_ERRORS on iOS.
            allWarningsAsErrors = true
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        // Robolectric needs merged resources to render Compose UI in JVM tests.
        unitTests.isIncludeAndroidResources = true
        // Robolectric's FileDescriptor interceptor reflects into JDK internals, which
        // JDK 17+ only allows when the packages are exported/opened to test code.
        unitTests.all {
            it.jvmArgs(
                "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED",
                "--add-opens=java.base/java.io=ALL-UNNAMED"
            )
        }
        animationsDisabled = true
    }

    lint {
        // Lint errors fail the build; warnings are reported (build/reports/lint-results-*.html).
        abortOnError = true
        checkDependencies = true
        // Dependency versions are Dependabot's job, not lint's.
        disable +=
            setOf(
                "GradleDependency",
                "NewerVersionAvailable",
                "AndroidGradlePluginVersion",
                "OldTargetApi"
            )
    }
}

dependencies {
    implementation(project(":core"))

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // JVM tests: view-model logic + Compose UI rendered by Robolectric (fast, Linux, no emulator).
    testImplementation(composeBom)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)

    // Instrumented UI tests on an emulator (mirrors the iOS XCUITest target).
    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
