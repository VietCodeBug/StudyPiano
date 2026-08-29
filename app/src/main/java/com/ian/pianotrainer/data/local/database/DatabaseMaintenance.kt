package com.ian.pianotrainer.data.local.database

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DatabaseMaintenance(private val database: PianoTrainerDatabase) {

    suspend fun cleanLegacyDemoDataIfNeeded() = withContext(Dispatchers.IO) {
        // Clean any legacy demo artifacts from previous versions
        database.importedSongDao().deleteDemoSongs()
        database.practiceSessionDao().deleteDemoSessions()
    }

    suspend fun clearAllData() = withContext(Dispatchers.IO) {
        database.practiceSessionDao().clearAll()
        database.importedSongDao().clearAll()
        database.lessonProgressDao().clearAll()
    }
}
