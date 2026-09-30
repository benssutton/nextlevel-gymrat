import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        allWarningsAsErrors = true
    }
}

dependencies {
    // `api`: OkHttp and serialization types appear in ApiClient's and the models' public API,
    // so consumers (:app) must see them on their compile classpath.
    api(libs.okhttp)
    api(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.test {
    // Contract tests run only when a live backend URL is supplied (see BackendContractTest).
    environment("GYMRAT_API_BASE_URL", System.getenv("GYMRAT_API_BASE_URL") ?: "")
    testLogging { events("passed", "skipped", "failed") }
}
