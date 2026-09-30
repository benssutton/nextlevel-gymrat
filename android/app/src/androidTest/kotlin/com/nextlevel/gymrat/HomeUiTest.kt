package com.nextlevel.gymrat

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Launches the real app on an emulator. Mirrors ios/GymRatUITests/HomeUITests.swift. */
@RunWith(AndroidJUnit4::class)
class HomeUiTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun homeScreenShowsBackendStatus() {
        assertTrue(compose.onAllNodesWithText("GymRat").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithTag("refreshButton").assertExists()
        // Resolves to Ready/Not ready with a backend running, or an error without one.
        compose.waitUntilAtLeastOneExists(hasTestTag("backendStatus"), timeoutMillis = 15_000)
    }
}
