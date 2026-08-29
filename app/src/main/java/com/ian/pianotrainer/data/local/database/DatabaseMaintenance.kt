package com.ian.pianotrainer.data.local.database

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DatabaseMaintenance(
    private val database: PianoTrainerDatabase,
    private val context: Context? = null
) {

    suspend fun cleanLegacyDemoDataIfNeeded() = withContext(Dispatchers.IO) {
        // Clean any legacy demo artifacts from previous versions
        database.importedSongDao().deleteDemoSongs()
        database.practiceSessionDao().deleteDemoSessions()
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.practiceNoteResultDao().clearAll()
        database.practiceSessionDao().clearAll()
        database.songPracticePresetDao().clearAll()
        database.freePlayRecordingDao().clearAll()
        database.importedSongDao().clearAll()
        database.lessonProgressDao().clearAll()

        // Clean physical storage
        context?.let { ctx ->
            try {
                File(ctx.filesDir, "songs").deleteRecursively()
                File(ctx.filesDir, "recordings").deleteRecursively()
                File(ctx.filesDir, "pending_recordings").deleteRecursively()
                File(ctx.cacheDir, "temp_imports").deleteRecursively()
                File(ctx.cacheDir, "temp_restore").deleteRecursively()
            } catch (_: Exception) {
                // Best effort cleanup
            }
        }
    }
}
