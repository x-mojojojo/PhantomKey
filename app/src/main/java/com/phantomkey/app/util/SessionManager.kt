package com.phantomkey.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Tracks user activity and auto-locks after [timeoutMinutes] of inactivity.
 */
class SessionManager(
    private val scope: CoroutineScope,
) {
    private val _locked = MutableStateFlow(true)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private var timeoutMinutes: Int = 5
    private var lockJob: Job? = null

    fun setTimeoutMinutes(minutes: Int) {
        timeoutMinutes = minutes.coerceIn(1, 60)
        if (!_locked.value) {
            touch()
        }
    }

    fun unlock() {
        _locked.value = false
        touch()
    }

    fun lock() {
        lockJob?.cancel()
        _locked.value = true
    }

    fun touch() {
        if (_locked.value) return
        lockJob?.cancel()
        lockJob = scope.launch {
            delay(timeoutMinutes * 60_000L)
            _locked.value = true
        }
    }
}
