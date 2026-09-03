package com.ian.pianotrainer.feature.practice

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ToolbarAutoHideController(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 3000L,
    private val setVisible: (Boolean) -> Unit
) {
    private var job: Job? = null
    fun showAndSchedule(running: Boolean) {
        job?.cancel()
        setVisible(true)
        if (running) job = scope.launch {
            delay(timeoutMs)
            setVisible(false)
        }
    }
    fun cancelAndShow() {
        job?.cancel()
        job = null
        setVisible(true)
    }
    fun cancel() { job?.cancel(); job = null }
}