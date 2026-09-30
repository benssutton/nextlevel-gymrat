package com.nextlevel.gymrat

/**
 * Build-time configuration injected per build type (app/build.gradle.kts → BuildConfig).
 * Mirrors `AppConfiguration` on iOS, which reads the Config xcconfig files via Info.plist.
 */
object AppConfiguration {
    val apiBaseUrl: String = BuildConfig.API_BASE_URL
}
