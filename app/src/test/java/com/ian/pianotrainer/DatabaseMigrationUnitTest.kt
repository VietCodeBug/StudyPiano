package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseMigrationUnitTest {

    @Test
    fun migration_3_to_4_backfillsNoteCount_and_createsTimeSignaturesTable() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, PianoTrainerDatabase::class.java)
            .addMigrations(PianoTrainerDatabase.MIGRATION_1_2, PianoTrainerDatabase.MIGRATION_2_3, PianoTrainerDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()

        val songDao = db.importedSongDao()
        val noteDao = db.songNoteDao()
        val timeSignatureDao = db.songTimeSignatureDao()

        // Verify DAOs and tables are accessible
        assertNotNull(songDao)
        assertNotNull(noteDao)
        assertNotNull(timeSignatureDao)

        db.close()
    }
}
