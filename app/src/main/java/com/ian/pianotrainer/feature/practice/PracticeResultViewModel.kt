package com.ian.pianotrainer.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.WeakMeasureStat
import com.ian.pianotrainer.domain.repository.ProgressRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PracticeResultUiState(
    val session: PracticeSession? = null,
    val noteResults: List<PracticeNoteResult> = emptyList(),
    val pitchAccuracyPercent: Int = 0,
    val rhythmAccuracyPercent: Int = 0,
    val averageEarlyMs: Long = 0L,
    val averageLateMs: Long = 0L,
    val weakMeasures: List<WeakMeasureStat> = emptyList(),
    val isLoading: Boolean = true
)

class PracticeResultViewModel(
    private val sessionId: String,
    private val progressRepository: ProgressRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PracticeResultUiState())
    val uiState: StateFlow<PracticeResultUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.value = PracticeResultUiState(isLoading = true)
            val session = progressRepository.getSessionById(sessionId)
            val noteResults = progressRepository.getSessionNoteResults(sessionId).firstOrNull() ?: emptyList()

            if (session != null && noteResults.isNotEmpty()) {
                val total = noteResults.size
                val correctCount = noteResults.count { it.resultType == NoteResultType.CORRECT || it.resultType == NoteResultType.EARLY || it.resultType == NoteResultType.LATE }
                val pitchAcc = ((correctCount.toFloat() / total) * 100f).toInt().coerceIn(0, 100)

                val onTimeCount = noteResults.count { it.resultType == NoteResultType.CORRECT }
                val rhythmAcc = ((onTimeCount.toFloat() / total) * 100f).toInt().coerceIn(0, 100)

                val earlyOffsets = noteResults.filter { it.resultType == NoteResultType.EARLY }.mapNotNull { it.timingOffsetMs?.let { offset -> abs(offset) } }
                val lateOffsets = noteResults.filter { it.resultType == NoteResultType.LATE }.mapNotNull { it.timingOffsetMs?.let { offset -> abs(offset) } }
                val avgEarly = if (earlyOffsets.isNotEmpty()) (earlyOffsets.sum() / earlyOffsets.size) else 0L
                val avgLate = if (lateOffsets.isNotEmpty()) (lateOffsets.sum() / lateOffsets.size) else 0L

                // Estimate measures based on session duration & bpm (assuming 4/4)
                val bpm = session.bpm.coerceAtLeast(30)
                val msPerMeasure = (4 * 60_000L) / bpm
                val weakMeasureMap = mutableMapOf<Int, Int>() // measureNum -> errorCount
                val measureTotalMap = mutableMapOf<Int, Int>()

                noteResults.forEach { result ->
                    val measureNum = (result.occurredAtOffsetMs / msPerMeasure).toInt() + 1
                    measureTotalMap[measureNum] = (measureTotalMap[measureNum] ?: 0) + 1
                    if (result.resultType == NoteResultType.WRONG || result.resultType == NoteResultType.MISSED) {
                        weakMeasureMap[measureNum] = (weakMeasureMap[measureNum] ?: 0) + 1
                    }
                }

                val weakList = weakMeasureMap.entries
                    .filter { it.value > 0 }
                    .sortedByDescending { it.value }
                    .take(3)
                    .map { (measureNum, errors) ->
                        val totalInMeasure = measureTotalMap[measureNum] ?: errors
                        val acc = (((totalInMeasure - errors).toFloat() / totalInMeasure) * 100f).toInt().coerceIn(0, 100)
                        WeakMeasureStat(
                            measureNumber = measureNum,
                            startMs = (measureNum - 1) * msPerMeasure,
                            endMs = measureNum * msPerMeasure,
                            errorCount = errors,
                            accuracyPercent = acc
                        )
                    }

                _uiState.value = PracticeResultUiState(
                    session = session,
                    noteResults = noteResults,
                    pitchAccuracyPercent = pitchAcc,
                    rhythmAccuracyPercent = rhythmAcc,
                    averageEarlyMs = avgEarly,
                    averageLateMs = avgLate,
                    weakMeasures = weakList,
                    isLoading = false
                )
            } else {
                _uiState.value = PracticeResultUiState(session = session, isLoading = false)
            }
        }
    }

    class Factory(
        private val sessionId: String,
        private val progressRepository: ProgressRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PracticeResultViewModel(sessionId, progressRepository) as T
        }
    }
}
