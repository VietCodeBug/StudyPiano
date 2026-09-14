package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.local.database.entity.RestoreCommitEntity
import com.ian.pianotrainer.data.repository.RestoreOperationRecovery
import com.ian.pianotrainer.data.repository.RestoreJournalState
import com.ian.pianotrainer.data.repository.RestoreMetadataPublishFaultInjector
import com.ian.pianotrainer.data.repository.SongFileRenamer
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class RestoreRecoveryUnitTest {
    private lateinit var context: Context
    private lateinit var db: PianoTrainerDatabase
    private lateinit var dbName: String
    private lateinit var baseContext: Context
    private lateinit var root: File

    @Before fun setup() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        baseContext = base
        root = File(base.cacheDir, "restore-recovery-${System.nanoTime()}").apply { mkdirs() }
        context = object : android.content.ContextWrapper(base) { override fun getFilesDir(): File = root }
        dbName = "restore-recovery-${System.nanoTime()}.db"
        db = Room.databaseBuilder(base, PianoTrainerDatabase::class.java, dbName).allowMainThreadQueries().build()
        db.openHelper.writableDatabase
    }

    @After fun teardown() { db.close(); baseContext.deleteDatabase(dbName); root.deleteRecursively() }

    @Test fun `simulated process death at PREPARED never moves active library`() = runBlocking {
        val activeSong = file("songs/old/source.mid", "OLD_MIDI")
        val activeRecording = file("recordings/old/performance.mid", "OLD_REC")
        val op = preparedJournal("prepared", newSong = "NEW_MIDI", newRecording = "NEW_REC")

        RestoreOperationRecovery(context, db).reconcile(op)

        assertEquals("OLD_MIDI", activeSong.readText())
        assertEquals("OLD_REC", activeRecording.readText())
        assertFalse(op.exists())
    }

    @Test fun `rename rollback failure retains journal and second recovery succeeds idempotently`() = runBlocking {
        file("songs/old/source.mid", "OLD")
        val op = preparedJournal("retry", newSong = "NEW", newRecording = "")
        val oldSongs = File(op, "oldSongs")
        assertTrue(File(root, "songs").renameTo(oldSongs)) // interruption after preserve rename
        var fail = true
        val faulty = RestoreOperationRecovery(context, db, SongFileRenamer { source, target ->
            if (fail && source == oldSongs) false else source.renameTo(target)
        })
        assertTrue(runCatching { faulty.reconcile(op) }.isFailure)
        assertTrue(op.exists()); assertTrue(File(op, "oldSongs/old/source.mid").isFile)
        fail = false
        faulty.reconcile(op)
        assertEquals("OLD", File(root, "songs/old/source.mid").readText())
        assertFalse(op.exists())
        // A second startup pass has no journal and cannot move the restored old library.
        assertEquals("OLD", File(root, "songs/old/source.mid").readText())
    }

    @Test fun `PREPARED recovery preserves library with no songs and recordings only`() = runBlocking {
        val recording = file("recordings/take/performance.mid", "ONLY_OLD_RECORDING")
        val op = preparedJournal("recordings-only", newSong = "", newRecording = "NEW_RECORDING", createActiveSongs = false)
        RestoreOperationRecovery(context, db).reconcile(op)
        assertFalse(File(root, "songs").exists())
        assertEquals("ONLY_OLD_RECORDING", recording.readText())
        assertFalse(op.exists())
    }

    @Test fun `DB marker finalizes new files after commit before cleanup`() = runBlocking {
        file("songs/old/source.mid", "OLD")
        file("recordings/old/performance.mid", "OLD_REC")
        val op = preparedJournal("committed", "NEW", "NEW_REC")
        assertTrue(File(root, "songs").renameTo(File(op, "oldSongs")))
        assertTrue(File(root, "recordings").renameTo(File(op, "oldRecordings")))
        assertTrue(File(op, "newSongs").renameTo(File(root, "songs")))
        assertTrue(File(op, "newRecordings").renameTo(File(root, "recordings")))
        db.restoreCommitDao().insert(RestoreCommitEntity("committed", 1L))

        RestoreOperationRecovery(context, db).reconcile(op)

        assertEquals("NEW", File(root, "songs/new/source.mid").readText())
        assertEquals("NEW_REC", File(root, "recordings/new/performance.mid").readText())
        assertFalse(op.exists()); assertFalse(db.restoreCommitDao().isCommitted("committed"))
    }

    @Test fun `metadata publish interruption recovers from synced temp without losing library`() = runBlocking {
        val oldSong = file("songs/old/source.mid", "OLD_METADATA_SAFE")
        val oldRecording = file("recordings/old/performance.mid", "OLD_RECORDING_SAFE")
        val op = preparedJournal("metadata-publish", "NEW", "NEW_REC")
        val interrupted = RestoreOperationRecovery(
            context, db, SongFileRenamer.DEFAULT,
            RestoreMetadataPublishFaultInjector { throw java.io.IOException("simulated death after temp fsync") }
        )
        assertTrue(runCatching { interrupted.writeState(op, RestoreJournalState.PRESERVE_SONGS_INTENT) }.isFailure)
        assertTrue(File(op, "operation.properties").isFile)
        assertTrue(File(op, "operation.properties.tmp").isFile)

        // Simulate the legacy delete-before-rename gap: scanner must recover the synced temp,
        // never silently skip the restore_* journal.
        assertTrue(File(op, "operation.properties").delete())
        RestoreOperationRecovery(context, db).reconcile(op)

        assertEquals("OLD_METADATA_SAFE", oldSong.readText())
        assertEquals("OLD_RECORDING_SAFE", oldRecording.readText())
        assertFalse(op.exists())
    }

    @Test fun `restore directory without primary or temp metadata fails loudly and retains data`() = runBlocking {
        val oldSong = file("songs/old/source.mid", "OLD_UNTOUCHED")
        val op = File(root, "song_operations/restore_missing_meta").apply { mkdirs() }
        File(op, "newSongs/new/source.mid").apply { parentFile?.mkdirs(); writeText("NEW_UNKNOWN") }
        val result = runCatching { RestoreOperationRecovery(context, db).reconcile(op) }
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("no readable"))
        assertEquals("OLD_UNTOUCHED", oldSong.readText())
        assertTrue(op.exists())
    }

    private fun preparedJournal(id: String, newSong: String, newRecording: String, createActiveSongs: Boolean = true): File {
        if (createActiveSongs && !File(root, "songs").exists()) file("songs/old/source.mid", "OLD_MIDI")
        val op = File(root, "song_operations/restore_$id").apply { mkdirs() }
        val newSongs = File(op, "newSongs").apply { mkdirs() }
        val newRecordings = File(op, "newRecordings").apply { mkdirs() }
        if (newSong.isNotEmpty()) File(newSongs, "new/source.mid").apply { parentFile?.mkdirs(); writeText(newSong) }
        if (newRecording.isNotEmpty()) File(newRecordings, "new/performance.mid").apply { parentFile?.mkdirs(); writeText(newRecording) }
        RestoreOperationRecovery(context, db).create(
            op, id, File(root, "songs").exists(), RestoreOperationRecovery.treeDigest(File(root, "songs")), RestoreOperationRecovery.treeDigest(newSongs),
            File(root, "recordings").exists(), RestoreOperationRecovery.treeDigest(File(root, "recordings")), RestoreOperationRecovery.treeDigest(newRecordings)
        )
        return op
    }

    private fun file(relative: String, text: String) = File(root, relative).apply { parentFile?.mkdirs(); writeText(text) }
}
