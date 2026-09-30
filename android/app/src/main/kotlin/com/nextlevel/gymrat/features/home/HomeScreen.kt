package com.nextlevel.gymrat.features.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nextlevel.gymrat.R
import com.nextlevel.gymrat.core.Readiness
import com.nextlevel.gymrat.ui.theme.GymRatColors
import com.nextlevel.gymrat.ui.theme.GymRatTheme

/**
 * Home screen. Mirrors `HomeView` on iOS: large "GymRat" title, a Refresh action,
 * pull-to-refresh, and a "Backend" section showing readiness and each dependency.
 * Test tags match the iOS accessibility identifiers (checked by CI: check_ui_parity.py).
 */
@Composable
fun HomeScreen(model: HomeViewModel) {
    val state by model.state.collectAsStateWithLifecycle()
    // Like `.task` on iOS: refresh when the screen first appears.
    LaunchedEffect(model) { model.refresh() }
    HomeContent(state = state, onRefresh = model::refresh)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(state: HomeViewModel.State, onRefresh: () -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.home_title)) },
                actions = {
                    IconButton(onClick = onRefresh, modifier = Modifier.testTag("refreshButton")) {
                        Icon(
                            painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(R.string.action_refresh)
                        )
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state is HomeViewModel.State.Loading,
            onRefresh = onRefresh,
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        text = stringResource(R.string.section_backend),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = 32.dp,
                            end = 32.dp,
                            top = 16.dp,
                            bottom = 8.dp
                        )
                    )
                }
                item {
                    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                        BackendSection(state)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackendSection(state: HomeViewModel.State) {
    when (state) {
        HomeViewModel.State.Idle, HomeViewModel.State.Loading -> Box(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(modifier = Modifier.testTag("loadingIndicator"))
        }

        is HomeViewModel.State.Loaded -> Column {
            val ready = state.readiness.isReady
            StatusRow(
                iconRes = if (ready) R.drawable.ic_check_circle else R.drawable.ic_warning,
                text = stringResource(
                    if (ready) R.string.status_ready else R.string.status_not_ready
                ),
                color = if (ready) GymRatColors.StatusReady else GymRatColors.StatusNotReady
            )
            state.readiness.checks.forEach { check ->
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                LabeledRow(label = check.name, value = check.status)
            }
        }

        is HomeViewModel.State.Failed -> StatusRow(
            iconRes = R.drawable.ic_wifi_off,
            text = state.message,
            color = GymRatColors.StatusError
        )
    }
}

/** Icon + text row; the `backendStatus` tag mirrors the iOS accessibility identifier. */
@Composable
private fun StatusRow(@DrawableRes iconRes: Int, text: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .semantics(mergeDescendants = true) {}
            .testTag("backendStatus"),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(iconRes), contentDescription = null, tint = color)
        Text(text = text, color = color)
    }
}

/** Label on the left, value on the right; mirrors SwiftUI `LabeledContent`. */
@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label)
        Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(name = "Ready")
@Composable
private fun HomeReadyPreview() {
    GymRatTheme {
        HomeContent(
            state = HomeViewModel.State.Loaded(
                Readiness(
                    status = "ready",
                    checks = listOf(
                        Readiness.Check(name = "postgres", status = "up", latencyMs = 1.2)
                    )
                )
            ),
            onRefresh = {}
        )
    }
}
