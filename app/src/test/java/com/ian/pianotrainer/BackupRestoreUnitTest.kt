package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.SongPracticePresetEntity
import com.ian.pianotrainer.data.repository.BackupRepositoryImpl
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class BackupRestoreUnitTest {

    private lateinit var context: Context
    private lateinit var db: PianoTrainerDatabase
    private lateinit var backupRepository: BackupRepositoryImpl

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PianoTrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        backupRepository = BackupRepositoryImpl(context, db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun createBackup_and_restoreBackup_roundtrip_restoresData() = runBlocking {
        // Insert sample song and preset
        val song = ImportedSongEntity(
            id = "song_backup_1",
            displayName = "Bach Invention 1",
            originalFileName = "bach.mid",
            localFilePath = null,
            fileSizeBytes = 2048L,
            durationMs = 45000L,
            defaultBpm = 90,
            difficulty = "EASY",
            importedAt = System.currentTimeMillis(),
            lastPracticedAt = null,
            isFavorite = false
        )
        val preset = SongPracticePresetEntity(
            id = "preset_1",
            songId = "song_backup_1",
            name = "Tricky Bars 5-8",
            loopStartMs = 12000L,
            loopEndMs = 24000L,
            targetBpm = 80,
            speedMultiplier = 0.85f,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        db.importedSongDao().insertSong(song)
        db.songPracticePresetDao().insertOrUpdate(preset)

        val output = ByteArrayOutputStream()
        val backupResult = backupRepository.createBackupZip(output, includeAudio = true)
        assertTrue(backupResult.isSuccess)
        val manifest = backupResult.getOrThrow()
        assertEquals(1, manifest.songCount)

        // Clear DB
        db.importedSongDao().clearAll()
        db.songPracticePresetDao().clearAll()
        assertEquals(0, db.importedSongDao().getSongCount())

        // Restore
        val input = ByteArrayInputStream(output.toByteArray())
        val restoreResult = backupRepository.restoreBackupZip(input)
        assertTrue(restoreResult.isSuccess)

        // Verify restored data
        val restoredSong = db.importedSongDao().getSongById("song_backup_1")
        assertNotNull(restoredSong)
        assertEquals("Bach Invention 1", restoredSong?.displayName)

        val restoredPresets = db.songPracticePresetDao().getPresetsForSong("song_backup_1")
        val restoredPreset = db.songPracticePresetDao().getAllPresets().firstOrNull()
        assertNotNull(restoredPreset)
        assertEquals("Tricky Bars 5-8", restoredPreset?.name)
        assertEquals(12000L, restoredPreset?.loopStartMs)
    }

    @Test
    fun restoreBackup_rejectsZipSlipMaliciousPath() = runBlocking {
        val badZip = ByteArrayOutputStream()
        ZipOutputStream(badZip).use { zos ->
            zos.putNextEntry(ZipEntry("../../../evil.sh"))
            zos.write("echo evil".toByteArray())
            zos.closeEntry()
        }

        val input = ByteArrayInputStream(badZip.toByteArray())
        val result = backupRepository.restoreBackupZip(input)
        assertTrue("Malicious zip path must fail restore", result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException || result.exceptionOrNull()?.message?.contains("ZipSlip") == true || result.exceptionOrNull()?.message?.contains("rejected") == true)
    }
}
