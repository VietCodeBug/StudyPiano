package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.PracticeNoteResult
import com.ian.pianotrainer.domain.model.PracticeSession
import com.ian.pianotrainer.domain.model.ProgressSummary
import kotlinx.coroutines.flow.Flow

interface ProgressRepository {
    fun getProgressSummary(daysFilter: Int? = null): Flow<ProgressSummary>
    fun getRecentSessions(limit: Int = 10): Flow<List<PracticeSession>>
    fun getSessionNoteResults(sessionId: String): Flow<List<PracticeNoteResult>>
    suspend fun getAllSessionsList(): List<PracticeSession>
    suspend fun getAllNoteResultsList(): List<PracticeNoteResult>
    suspend fun getSessionById(sessionId: String): PracticeSession?
    suspend fun savePracticeSession(session: PracticeSession, noteResults: List<PracticeNoteResult> = emptyList())
    suspend fun deletePracticeSession(sessionId: String)
    suspend fun clearAllProgress()
}
