package com.nextlevel.gymrat.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextlevel.gymrat.core.HealthChecking
import com.nextlevel.gymrat.core.Readiness
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Mirrors `HomeViewModel` on iOS: same states, same transitions. */
class HomeViewModel(private val health: HealthChecking) : ViewModel() {
    sealed interface State {
        data object Idle : State

        data object Loading : State

        data class Loaded(val readiness: Readiness) : State

        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Fire-and-forget refresh for UI events (button, pull-to-refresh, first appearance). */
    fun refresh() {
        viewModelScope.launch { load() }
    }

    /** Suspends until the refresh completes; tests call this directly. */
    suspend fun load() {
        _state.value = State.Loading
        _state.value = try {
            State.Loaded(health.readiness())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            State.Failed(e.message ?: e::class.java.simpleName)
        }
    }
}
