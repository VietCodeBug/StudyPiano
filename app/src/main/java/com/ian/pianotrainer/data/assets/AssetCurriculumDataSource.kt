package com.ian.pianotrainer.data.assets

import android.content.Context
import com.ian.pianotrainer.domain.model.Course
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AssetCurriculumDataSource(private val context: Context) {

    private val moshi: Moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val adapter = moshi.adapter(CurriculumJsonResponse::class.java)

    private var cachedCourses: List<Course>? = null

    suspend fun getCurriculum(): Result<List<Course>> = withContext(Dispatchers.IO) {
        cachedCourses?.let { return@withContext Result.success(it) }

        try {
            val jsonString = context.assets.open("curriculum_vi.json").bufferedReader().use { it.readText() }
            val parsedResponse = adapter.fromJson(jsonString)
                ?: return@withContext Result.failure(IllegalStateException("Không thể đọc định dạng dữ liệu giáo trình"))

            val courses = parsedResponse.courses.map { it.toDomain() }
            cachedCourses = courses
            Result.success(courses)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
