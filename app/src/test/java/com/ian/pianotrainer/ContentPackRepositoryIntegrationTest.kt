package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.core.contentpack.ContentPackImporter
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.repository.SongRepositoryImpl
import com.ian.pianotrainer.data.repository.SongFileRenamer
import com.ian.pianotrainer.data.repository.SongOperationCheckpoint
import com.ian.pianotrainer.data.repository.SongOperationFaultInjector
import com.ian.pianotrainer.domain.model.SongAssetType
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.CancellationException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ContentPackRepositoryIntegrationTest {
    private lateinit var context: Context
    private lateinit var db: PianoTrainerDatabase
    private lateinit var repository: SongRepositoryImpl
    private lateinit var importer: ContentPackImporter
    private lateinit var dbFile: File
    private var importedSongId: String? = null

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = File(context.cacheDir, "phase11_${System.nanoTime()}.db")
        db = Room.databaseBuilder(context, PianoTrainerDatabase::class.java, dbFile.absolutePath).allowMainThreadQueries().build()
        repository = SongRepositoryImpl(context, db, db.importedSongDao(), db.songTrackDao(), db.songNoteDao(), db.songTempoDao(), db.songTimeSignatureDao())
        importer = ContentPackImporter(context, repository)
    }

    @After fun teardown() {
        importedSongId?.let { File(context.filesDir, "songs/$it").deleteRecursively() }
        db.close()
        dbFile.delete()
    }

    @Test fun `simulated process death recovery removes uncommitted import files`() = runTest {
        val songId = "song_orphan_fixture"
        val finalDir = File(context.filesDir, "songs/$songId").apply { mkdirs() }
        File(finalDir, "source.mid").writeBytes(sampleMidi())
        val op = File(context.filesDir, "song_operations/import_fixture").apply { mkdirs() }
        File(op, "operation.properties").writeText("kind=IMPORT\nsongId=$songId\n")
        repository.getAllSongs().first()
        assertFalse(finalDir.exists())
        assertFalse(op.exists())
    }

    @Test fun `simulated cancellation after commit keeps committed files`() = runTest {
        val imported = repository.importMidiFile(ByteArrayInputStream(sampleMidi()), "committed.mid", sampleMidi().size.toLong()).getOrThrow()
        importedSongId = imported.id
        val finalDir = File(context.filesDir, "songs/${imported.id}")
        val op = File(context.filesDir, "song_operations/import_committed_fixture").apply { mkdirs() }
        File(op, "operation.properties").writeText("kind=IMPORT\nsongId=${imported.id}\n")
        repository.getAllSongs().first()
        assertTrue(finalDir.isDirectory)
        assertFalse(op.exists())
    }

    @Test fun `simulated interrupted delete restores files while DB row exists`() = runTest {
        val imported = repository.importMidiFile(ByteArrayInputStream(sampleMidi()), "delete.mid", sampleMidi().size.toLong()).getOrThrow()
        importedSongId = imported.id
        val finalDir = File(context.filesDir, "songs/${imported.id}")
        val op = File(context.filesDir, "song_operations/delete_fixture").apply { mkdirs() }
        File(op, "operation.properties").writeText("kind=DELETE\nsongId=${imported.id}\n")
        assertTrue(finalDir.renameTo(File(op, "payload")))
        repository.getAllSongs().first()
        assertTrue(finalDir.isDirectory)
        assertFalse(op.exists())
    }

    @Test fun `delete rollback rename failure retains payload and journal then recovery retries`() = runTest {
        val imported = repository.importMidiFile(ByteArrayInputStream(sampleMidi()), "rollback.mid", sampleMidi().size.toLong()).getOrThrow()
        importedSongId = imported.id
        var failRollback = true
        val faulty = SongRepositoryImpl(context, db, db.importedSongDao(), db.songTrackDao(), db.songNoteDao(), db.songTempoDao(), db.songTimeSignatureDao(),
            SongOperationFaultInjector { checkpoint, _ -> if (checkpoint == SongOperationCheckpoint.DELETE_FILES_STAGED) error("DB boundary fault") },
            SongFileRenamer { source, target -> if (failRollback && target.name == imported.id) false else source.renameTo(target) })
        val failure = runCatching { faulty.deleteSong(imported.id) }
        assertTrue(failure.exceptionOrNull()?.message.orEmpty().contains("journal"))
        val journal = File(context.filesDir, "song_operations").listFiles().orEmpty().single { it.name.startsWith("delete_${imported.id}") }
        assertTrue(File(journal, "payload/source.mid").isFile)
        assertTrue(db.importedSongDao().getSongById(imported.id) != null)

        failRollback = false
        repository.getAllSongs().first()
        assertTrue(File(context.filesDir, "songs/${imported.id}/source.mid").isFile)
        assertFalse(journal.exists())
        repository.deleteSong(imported.id); importedSongId = null
    }

    @Test fun `real cancellation before delete commit rolls back files and row`() = runTest {
        val imported = repository.importMidiFile(ByteArrayInputStream(sampleMidi()), "cancel-before.mid", sampleMidi().size.toLong()).getOrThrow()
        importedSongId = imported.id
        val cancelling = SongRepositoryImpl(context, db, db.importedSongDao(), db.songTrackDao(), db.songNoteDao(), db.songTempoDao(), db.songTimeSignatureDao(),
            SongOperationFaultInjector { checkpoint, _ -> if (checkpoint == SongOperationCheckpoint.DELETE_FILES_STAGED) throw CancellationException("before commit") })
        try { cancelling.deleteSong(imported.id); throw AssertionError("Expected cancellation") } catch (_: CancellationException) { }
        assertTrue(db.importedSongDao().getSongById(imported.id) != null)
        assertTrue(File(context.filesDir, "songs/${imported.id}/source.mid").isFile)
    }

    @Test fun `real cancellation after delete commit finalizes deletion`() = runTest {
        val imported = repository.importMidiFile(ByteArrayInputStream(sampleMidi()), "cancel-after.mid", sampleMidi().size.toLong()).getOrThrow()
        val cancelling = SongRepositoryImpl(context, db, db.importedSongDao(), db.songTrackDao(), db.songNoteDao(), db.songTempoDao(), db.songTimeSignatureDao(),
            SongOperationFaultInjector { checkpoint, _ -> if (checkpoint == SongOperationCheckpoint.DELETE_DB_COMMITTED) throw CancellationException("after commit") })
        try { cancelling.deleteSong(imported.id); throw AssertionError("Expected cancellation") } catch (_: CancellationException) { }
        assertTrue(db.importedSongDao().getSongById(imported.id) == null)
        assertFalse(File(context.filesDir, "songs/${imported.id}").exists())
        assertTrue(File(context.filesDir, "song_operations").listFiles().orEmpty().none { it.name.contains(imported.id) })
    }

    @Test fun `real importer repository and Room persist one song with all assets and reject duplicate`() = runTest {
        val pack = zipOf(
            "manifest.json" to """{"id":"pack","title":"Integrated Pack","midiFileName":"midi/song.mid","musicXmlFileName":"score/song.musicxml","audioFileName":"audio/ref.ogg"}""".toByteArray(),
            "midi/song.mid" to sampleMidi(),
            "score/song.musicxml" to """<score-partwise version="4.0"><part-list/></score-partwise>""".toByteArray(),
            "audio/ref.ogg" to "OggSfixture".toByteArray()
        )
        val first = importer.importPack(ByteArrayInputStream(pack))
        assertTrue(first.errorMessage.orEmpty(), first.isSuccess)
        importedSongId = first.songId
        val persisted = repository.getSongById(first.songId!!)!!
        assertEquals(setOf(SongAssetType.MIDI, SongAssetType.MUSICXML, SongAssetType.REFERENCE_AUDIO), persisted.assets.map { it.type }.toSet())
        assertTrue(persisted.assets.all { it.songId == persisted.id && File(it.localFilePath).isFile })

        val ownedPaths = persisted.assets.map { it.localFilePath }
        val duplicate = importer.importPack(ByteArrayInputStream(pack))
        assertFalse(duplicate.isSuccess)
        assertTrue(duplicate.errorMessage.orEmpty().contains("đã tồn tại"))
        assertEquals(1, repository.getAllSongs().first().size)
        assertTrue(ownedPaths.all { File(it).isFile })

        repository.deleteSong(persisted.id)
        importedSongId = null
        assertTrue(ownedPaths.none { File(it).exists() })
        assertTrue(repository.getAllSongs().first().isEmpty())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip -> entries.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
        } }
        return output.toByteArray()
    }

    private fun sampleMidi(): ByteArray {
        val output = ByteArrayOutputStream(); val data = DataOutputStream(output)
        data.writeBytes("MThd"); data.writeInt(6); data.writeShort(0); data.writeShort(1); data.writeShort(256)
        val track = byteArrayOf(0, 0x90.toByte(), 71, 80, 0x82.toByte(), 0, 0x80.toByte(), 71, 0, 0, 0xFF.toByte(), 0x2F, 0)
        data.writeBytes("MTrk"); data.writeInt(track.size); data.write(track)
        return output.toByteArray()
    }
}
