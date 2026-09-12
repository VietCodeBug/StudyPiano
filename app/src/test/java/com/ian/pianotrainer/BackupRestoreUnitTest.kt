package com.ian.pianotrainer

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.entity.ImportedSongEntity
import com.ian.pianotrainer.data.local.database.entity.SongPracticePresetEntity
import com.ian.pianotrainer.data.local.database.entity.SongAssetEntity
import com.ian.pianotrainer.data.repository.BackupRepositoryImpl
import com.ian.pianotrainer.data.repository.RestoreCheckpoint
import com.ian.pianotrainer.data.repository.RestoreFaultInjector
import com.ian.pianotrainer.data.repository.RestoreOperationRecovery
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
class BackupRestoreUnitTest {

    private lateinit var context: Context
    private lateinit var db: PianoTrainerDatabase
    private lateinit var backupRepository: BackupRepositoryImpl
    private lateinit var dbFile: File
    private lateinit var filesRoot: File

    @Before
    fun setUp() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        filesRoot = File(base.cacheDir, "backup-files-${System.nanoTime()}").apply { mkdirs() }
        context = object : ContextWrapper(base) { override fun getFilesDir(): File = filesRoot }
        dbFile = File(filesRoot, "backup.db")
        db = Room.databaseBuilder(context, PianoTrainerDatabase::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
        backupRepository = BackupRepositoryImpl(context, db)
    }

    @After
    fun tearDown() {
        db.close()
        dbFile.delete()
        filesRoot.deleteRecursively()
    }

    @Test
    fun createBackup_and_restoreBackup_roundtrip_restoresData() = runBlocking {
        // Insert sample song and preset
        val song = ImportedSongEntity(
            id = "song_backup_1",
            displayName = "Bach Invention 1",
            originalFileName = "bach.mid",
            localFilePath = File(context.filesDir, "songs/song_backup_1/source.mid").absolutePath,
            fileSizeBytes = 4L,
            fileHashSha256 = sha256("MIDI".toByteArray()),
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
        val songDir = File(context.filesDir, "songs/song_backup_1").apply { mkdirs() }
        val midi = File(songDir, "source.mid").apply { writeText("MIDI") }
        val xml = File(songDir, "sheet.musicxml").apply { writeText("<score-partwise/>") }
        val audio = File(songDir, "reference.mp3").apply { writeBytes(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0)) }
        db.songAssetDao().insertAssets(listOf(
            SongAssetEntity("a-midi", song.id, "MIDI", "bach.mid", midi.absolutePath, midi.length(), "audio/midi"),
            SongAssetEntity("a-xml", song.id, "MUSICXML", "bach.musicxml", xml.absolutePath, xml.length(), "application/vnd.recordare.musicxml+xml"),
            SongAssetEntity("a-audio", song.id, "REFERENCE_AUDIO", "bach.mp3", audio.absolutePath, audio.length(), "audio/mpeg")
        ))
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
        val targetRoot = File(context.cacheDir, "restore-target-${System.nanoTime()}").apply { mkdirs() }
        val targetContext = object : ContextWrapper(context) { override fun getFilesDir(): File = targetRoot }
        val restoreResult = BackupRepositoryImpl(targetContext, db).restoreBackupZip(input)
        assertTrue(restoreResult.isSuccess)

        // Verify restored data
        val restoredSong = db.importedSongDao().getSongById("song_backup_1")
        assertNotNull(restoredSong)
        assertEquals("Bach Invention 1", restoredSong?.displayName)
        assertTrue(restoredSong?.localFilePath?.let { File(it).isFile && it.startsWith(targetRoot.absolutePath) } == true)
        val restoredAssets = db.songAssetDao().getAssetsForSong(song.id)
        assertEquals(setOf("MIDI", "MUSICXML", "REFERENCE_AUDIO"), restoredAssets.map { it.type }.toSet())
        assertTrue(restoredAssets.all { File(it.localFilePath).isFile && it.localFilePath.startsWith(targetRoot.absolutePath) })
        assertEquals(sha256(File(restoredSong!!.localFilePath!!).readBytes()), restoredSong.fileHashSha256)
        targetRoot.deleteRecursively()

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

    @Test fun `v2 missing asset metadata or declared file fails before replacing current data`() = runBlocking {
        val backup = createV2Backup("song_guard", "NEW")
        val oldFile = File(context.filesDir, "songs/song_guard/source.mid").apply { parentFile?.mkdirs(); writeText("OLD") }
        db.importedSongDao().insertSong(songEntity("song_guard", "OLD", oldFile))

        val noMetadata = rewriteZip(backup) { name -> name != "data/song_assets.json" }
        assertTrue(backupRepository.restoreBackupZip(ByteArrayInputStream(noMetadata)).isFailure)
        assertEquals("OLD", oldFile.readText())
        assertEquals(sha256("OLD".toByteArray()), db.importedSongDao().getSongById("song_guard")?.fileHashSha256)

        val noFile = rewriteZip(backup) { name -> name != "files/songs/song_guard/source.mid" }
        assertTrue(backupRepository.restoreBackupZip(ByteArrayInputStream(noFile)).isFailure)
        assertEquals("OLD", oldFile.readText())
        assertEquals(sha256("OLD".toByteArray()), db.importedSongDao().getSongById("song_guard")?.fileHashSha256)
        File(context.filesDir, "songs/song_guard").deleteRecursively()
        Unit
    }

    @Test fun `restore overwrite fault before DB commit rolls back old DB and file hash`() = runBlocking {
        val backup = createV2Backup("song_overwrite", "NEW")
        val oldFile = File(context.filesDir, "songs/song_overwrite/source.mid").apply { parentFile?.mkdirs(); writeText("OLD") }
        db.importedSongDao().insertSong(songEntity("song_overwrite", "OLD", oldFile))
        val faulty = BackupRepositoryImpl(context, db, RestoreFaultInjector { checkpoint ->
            if (checkpoint == RestoreCheckpoint.BEFORE_DB_TRANSACTION) error("injected before DB")
        })
        val result = faulty.restoreBackupZip(ByteArrayInputStream(backup))
        assertTrue(result.isFailure)
        assertEquals(sha256("OLD".toByteArray()), db.importedSongDao().getSongById("song_overwrite")?.fileHashSha256)
        assertEquals("OLD", oldFile.readText())
        assertTrue(File(context.filesDir, "song_operations").listFiles().orEmpty().none { it.name.startsWith("restore_") })
        File(context.filesDir, "songs/song_overwrite").deleteRecursively()
        Unit
    }

    @Test fun `restore cancellation after DB commit keeps committed DB and new file`() = runBlocking {
        val backup = createV2Backup("song_restore_cancel", "NEW")
        val oldFile = File(context.filesDir, "songs/song_restore_cancel/source.mid").apply { writeText("OLD") }
        db.importedSongDao().insertSong(songEntity("song_restore_cancel", "OLD", oldFile))
        val cancelling = BackupRepositoryImpl(context, db, RestoreFaultInjector { checkpoint ->
            if (checkpoint == RestoreCheckpoint.AFTER_DB_COMMIT) throw CancellationException("after restore commit")
        })
        try { cancelling.restoreBackupZip(ByteArrayInputStream(backup)); throw AssertionError("Expected cancellation") }
        catch (_: CancellationException) { }
        assertEquals(sha256("NEW".toByteArray()), db.importedSongDao().getSongById("song_restore_cancel")?.fileHashSha256)
        assertEquals("NEW", oldFile.readText())
        assertTrue(File(context.filesDir, "song_operations").listFiles().orEmpty().none { it.name.startsWith("restore_") })
        File(context.filesDir, "songs/song_restore_cancel").deleteRecursively()
        Unit
    }

    @Test fun `interruptions after every preserve and promote rename restore all old hashes`() = runBlocking {
        val checkpoints = listOf(
            RestoreCheckpoint.AFTER_PRESERVE_SONGS_RENAME,
            RestoreCheckpoint.AFTER_PRESERVE_RECORDINGS_RENAME,
            RestoreCheckpoint.AFTER_PROMOTE_SONGS_RENAME,
            RestoreCheckpoint.AFTER_PROMOTE_RECORDINGS_RENAME
        )
        checkpoints.forEachIndexed { index, stopAt ->
            File(context.filesDir, "songs").deleteRecursively(); File(context.filesDir, "recordings").deleteRecursively()
            val id = "same_hash_$index"
            val backup = createV2FullBackup(id, "SAME_MIDI", "NEW_SHEET", "NEW_AUDIO")
            val songDir = File(context.filesDir, "songs/$id")
            File(songDir, "sheet.musicxml").writeText("OLD_SHEET")
            File(songDir, "reference.mp3").writeText("OLD_AUDIO")
            val oldRecording = File(context.filesDir, "recordings/old/performance.mid").apply { parentFile?.mkdirs(); writeText("OLD_RECORDING") }
            val expected = listOf(File(songDir, "source.mid"), File(songDir, "sheet.musicxml"), File(songDir, "reference.mp3"), oldRecording)
                .associateWith { sha256(it.readBytes()) }
            val interrupted = BackupRepositoryImpl(context, db, RestoreFaultInjector { if (it == stopAt) error("simulated process interruption") })
            assertTrue(interrupted.restoreBackupZip(ByteArrayInputStream(backup)).isFailure)
            expected.forEach { (file, hash) -> assertTrue(file.path, file.isFile); assertEquals(hash, sha256(file.readBytes())) }
            assertTrue(File(context.filesDir, "song_operations").listFiles().orEmpty().none { it.name.startsWith("restore_") })
        }
    }

    @Test fun `next restore resolves PREPARED journal before starting`() = runBlocking {
        val backup = createV2Backup("next_restore", "NEW_FINAL")
        val active = File(context.filesDir, "songs/next_restore/source.mid").apply { writeText("OLD_ACTIVE") }
        db.importedSongDao().insertSong(songEntity("next_restore", "OLD_ACTIVE", active))
        val op = File(context.filesDir, "song_operations/restore_pending").apply { mkdirs() }
        val stagedSongs = File(op, "newSongs/pending").apply { mkdirs() }; File(stagedSongs, "source.mid").writeText("UNCOMMITTED")
        val stagedRecordings = File(op, "newRecordings").apply { mkdirs() }
        RestoreOperationRecovery(context, db).create(op, "pending", true,
            RestoreOperationRecovery.treeDigest(File(context.filesDir, "songs")), RestoreOperationRecovery.treeDigest(File(op, "newSongs")),
            false, "ABSENT", RestoreOperationRecovery.treeDigest(stagedRecordings))

        assertTrue(backupRepository.restoreBackupZip(ByteArrayInputStream(backup)).isSuccess)
        assertFalse(op.exists())
        assertEquals("NEW_FINAL", File(context.filesDir, "songs/next_restore/source.mid").readText())
    }

    private suspend fun createV2Backup(id: String, content: String): ByteArray {
        db.importedSongDao().clearAll()
        val file = File(context.filesDir, "songs/$id/source.mid").apply { parentFile?.mkdirs(); writeText(content) }
        val song = songEntity(id, content, file)
        db.importedSongDao().insertSong(song)
        db.songAssetDao().insertAssets(listOf(SongAssetEntity("${id}_midi", id, "MIDI", "$id.mid", file.absolutePath, file.length(), "audio/midi")))
        val output = ByteArrayOutputStream(); backupRepository.createBackupZip(output, true).getOrThrow()
        return output.toByteArray()
    }

    private suspend fun createV2FullBackup(id: String, midiContent: String, sheetContent: String, audioContent: String): ByteArray {
        db.importedSongDao().clearAll()
        val dir = File(context.filesDir, "songs/$id").apply { mkdirs() }
        val midi = File(dir, "source.mid").apply { writeText(midiContent) }
        val sheet = File(dir, "sheet.musicxml").apply { writeText(sheetContent) }
        val audio = File(dir, "reference.mp3").apply { writeText(audioContent) }
        db.importedSongDao().insertSong(songEntity(id, midiContent, midi))
        db.songAssetDao().insertAssets(listOf(
            SongAssetEntity("${id}_midi", id, "MIDI", "$id.mid", midi.absolutePath, midi.length(), "audio/midi"),
            SongAssetEntity("${id}_xml", id, "MUSICXML", "$id.musicxml", sheet.absolutePath, sheet.length(), "application/xml"),
            SongAssetEntity("${id}_audio", id, "REFERENCE_AUDIO", "$id.mp3", audio.absolutePath, audio.length(), "audio/mpeg")
        ))
        val output = ByteArrayOutputStream(); backupRepository.createBackupZip(output, true).getOrThrow()
        return output.toByteArray()
    }

    private fun songEntity(id: String, content: String, file: File) = ImportedSongEntity(
        id = id, displayName = id, originalFileName = "$id.mid", localFilePath = file.absolutePath,
        fileSizeBytes = file.length(), fileHashSha256 = sha256(content.toByteArray()), durationMs = 1L,
        defaultBpm = 120, difficulty = "TEST", importedAt = 1L, lastPracticedAt = null, isFavorite = false
    )

    private fun rewriteZip(input: ByteArray, keep: (String) -> Boolean): ByteArray {
        val output = ByteArrayOutputStream()
        ZipInputStream(ByteArrayInputStream(input)).use { zipIn -> ZipOutputStream(output).use { zipOut ->
            while (true) {
                val entry = zipIn.nextEntry ?: break
                if (keep(entry.name)) {
                    zipOut.putNextEntry(ZipEntry(entry.name)); zipIn.copyTo(zipOut); zipOut.closeEntry()
                }
                zipIn.closeEntry()
            }
        } }
        return output.toByteArray()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
