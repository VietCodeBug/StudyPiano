package com.ian.pianotrainer.domain.repository

import com.ian.pianotrainer.domain.model.FingerExercise
import kotlinx.coroutines.flow.Flow

interface ExerciseRepository {
    fun getFingerExercises(): Flow<List<FingerExercise>>
    suspend fun getExerciseById(id: String): FingerExercise?
}
