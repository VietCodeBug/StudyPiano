package com.ian.pianotrainer.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.ian.pianotrainer.data.local.database.dao.ImportedSongDao
import com.ian.pianotrainer.data.local.database.dao.LessonProgressDao
import com.ian.pianotrainer.data.local.database.dao.PracticeNoteResultDao
import com.ian.pianotrainer.data.local.database.dao.PracticeSessionDao
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.LessonProgressEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeNoteResultEntity
import com.ian.pianotrainer.data.local.database.entity.PracticeSessionEntity

@Database(
    entities = [
        ImportedSongEntity::class,
        LessonProgressEntity::class,
        PracticeSessionEntity::class,
        PracticeNoteResultEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class PianoTrainerDatabase : RoomDatabase() {
    abstract fun importedSongDao(): ImportedSongDao
    abstract fun lessonProgressDao(): LessonProgressDao
    abstract fun practiceSessionDao(): PracticeSessionDao
    abstract fun practiceNoteResultDao(): PracticeNoteResultDao

    companion object {
        const val DATABASE_NAME = "piano_trainer.db"

        @Volatile
        private var INSTANCE: PianoTrainerDatabase? = null

        fun getInstance(context: Context): PianoTrainerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PianoTrainerDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
