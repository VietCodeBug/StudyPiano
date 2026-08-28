package com.ian.pianotrainer.data.assets

import android.content.Context
import com.ian.pianotrainer.domain.model.FingerExercise
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetExerciseDataSource(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(FingerExercisesJsonResponse::class.java)

    private var cachedExercises: List<FingerExercise>? = null

    suspend fun getExercises(): Result<List<FingerExercise>> = withContext(Dispatchers.IO) {
        cachedExercises?.let { return@withContext Result.success(it) }

        try {
            val jsonString = context.assets.open("finger_exercises_vi.json").bufferedReader().use { it.readText() }
            val parsedResponse = adapter.fromJson(jsonString)
                ?: return@withContext Result.failure(IllegalStateException("Không thể đọc định dạng bài tập luyện ngón"))

            val exercises = parsedResponse.exercises.map { it.toDomain() }

            // Validate requirements:
            val seenIds = mutableSetOf<String>()
            for (ex in exercises) {
                if (!seenIds.add(ex.id)) {
                    return@withContext Result.failure(IllegalArgumentException("Trùng lặp mã bài tập: ${ex.id}"))
                }
                if (ex.notes.isEmpty()) {
                    return@withContext Result.failure(IllegalArgumentException("Bài tập ${ex.title} không có nốt nhạc"))
                }
                for (note in ex.notes) {
                    if (note.midiNote !in 21..108) {
                        return@withContext Result.failure(IllegalArgumentException("Mã nốt MIDI không hợp lệ (${note.midiNote}) trong bài ${ex.title}"))
                    }
                    if (note.fingerNumber !in 1..5) {
                        return@withContext Result.failure(IllegalArgumentException("Số ngón tay không hợp lệ (${note.fingerNumber}) trong bài ${ex.title}"))
                    }
                }
            }

            cachedExercises = exercises
            Result.success(exercises)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
