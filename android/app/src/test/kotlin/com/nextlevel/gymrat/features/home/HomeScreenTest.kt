package com.nextlevel.gymrat.features.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nextlevel.gymrat.core.Readiness
import com.nextlevel.gymrat.ui.theme.GymRatTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the real Home screen on the JVM with Robolectric (no emulator) for each state.
 * The emulator test in src/androidTest covers the running app end to end.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(state: HomeViewModel.State, onRefresh: () -> Unit = {}) {
        compose.setContent { GymRatTheme { HomeContent(state = state, onRefresh = onRefresh) } }
    }

    @Test
    fun showsTitleSectionAndLoadingIndicator() {
        show(HomeViewModel.State.Loading)
        assertTrue(compose.onAllNodesWithText("GymRat").fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Backend").assertIsDisplayed()
        compose.onNodeWithTag("loadingIndicator").assertExists()
    }

    @Test
    fun showsReadyStatusAndEachDependency() {
        show(
            HomeViewModel.State.Loaded(
                Readiness(
                    status = "ready",
                    checks = listOf(Readiness.Check(name = "postgres", status = "up"))
                )
            )
        )
        compose.onNodeWithTag("backendStatus").assertIsDisplayed()
        compose.onNodeWithText("Ready").assertIsDisplayed()
        compose.onNodeWithText("postgres").assertIsDisplayed()
        compose.onNodeWithText("up").assertIsDisplayed()
    }

    @Test
    fun showsNotReadyStatus() {
        show(HomeViewModel.State.Loaded(Readiness(status = "not_ready", checks = emptyList())))
        compose.onNodeWithText("Not ready").assertIsDisplayed()
    }

    @Test
    fun showsFailureMessage() {
        show(HomeViewModel.State.Failed("Failed to connect"))
        compose.onNodeWithTag("backendStatus").assertIsDisplayed()
        compose.onNodeWithText("Failed to connect").assertIsDisplayed()
    }

    @Test
    fun refreshButtonTriggersRefresh() {
        var refreshed = false
        show(HomeViewModel.State.Idle, onRefresh = { refreshed = true })
        compose.onNodeWithTag("refreshButton").performClick()
        assertTrue(refreshed)
    }
}
