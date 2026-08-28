package com.ian.pianotrainer.data.mock

import com.ian.pianotrainer.domain.service.MetronomeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockMetronomeController : MetronomeController {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var metronomeJob: Job? = null

    private val _isRunning = MutableStateFlow(false)
    override val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _currentBeat = MutableStateFlow(1)
    override val currentBeat: StateFlow<Int> = _currentBeat.asStateFlow()

    private val _bpm = MutableStateFlow(60)
    override val bpm: StateFlow<Int> = _bpm.asStateFlow()

    override fun start(bpm: Int) {
        setBpm(bpm)
        _isRunning.value = true
        metronomeJob?.cancel()
        metronomeJob = scope.launch {
            var beat = 1
            while (isActive) {
                _currentBeat.value = beat
                beat = if (beat >= 4) 1 else beat + 1
                val intervalMs = (60000L / _bpm.value.coerceIn(40, 240))
                delay(intervalMs)
            }
        }
    }

    override fun stop() {
        _isRunning.value = false
        metronomeJob?.cancel()
        _currentBeat.value = 1
    }

    override fun setBpm(bpm: Int) {
        _bpm.value = bpm.coerceIn(40, 240)
    }
}
