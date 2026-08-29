package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.repository.DuplicateMidiException
import com.ian.pianotrainer.data.repository.SongRepositoryImpl
import com.ian.pianotrainer.domain.model.HandMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

@RunWith(RobolectricTestRunner::class)
class SongRepositoryUnitTest {

    private lateinit var db: PianoTrainerDatabase
    private lateinit var context: Context
    private lateinit var repository: SongRepositoryImpl

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, PianoTrainerDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        repository = SongRepositoryImpl(
            context = context,
            importedSongDao = db.importedSongDao(),
            songTrackDao = db.songTrackDao(),
            songNoteDao = db.songNoteDao(),
            songTempoDao = db.songTempoDao()
        )
    }

    @After
    fun teardown() {
        db.close()
    }

    private fun createSampleMidiBytes(noteNumber: Int = 60): ByteArray {
        val byteStream = ByteArrayOutputStream()
        val dos = DataOutputStream(byteStream)

        // 1. MThd Header
        dos.writeBytes("MThd")
        dos.writeInt(6)
        dos.writeShort(1) // format 1
        dos.writeShort(1) // 1 track
        dos.writeShort(480) // 480 TPQN

        // 2. MTrk Track
        val trackStream = ByteArrayOutputStream()
        val trackDos = DataOutputStream(trackStream)

        // Note On: Delta 0, Note 60, Vel 80
        trackDos.writeByte(0x00)
        trackDos.writeByte(0x90)
        trackDos.writeByte(noteNumber)
        trackDos.writeByte(80)

        // Note Off: Delta 480, Note 60, Vel 0
        trackDos.writeByte(0x83)
        trackDos.writeByte(0x60)
        trackDos.writeByte(0x80)
        trackDos.writeByte(noteNumber)
        trackDos.writeByte(0)

        // End of Track
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF)
        trackDos.writeByte(0x2F)
        trackDos.writeByte(0x00)

        val trackBytes = trackStream.toByteArray()
        dos.writeBytes("MTrk")
        dos.writeInt(trackBytes.size)
        dos.write(trackBytes)

        return byteStream.toByteArray()
    }

    @Test
    fun importMidiFile_successfulImportAndPersist() = runTest {
        val rawBytes = createSampleMidiBytes(60)
        val result = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "test_song.mid",
            fileSize = rawBytes.size.toLong(),
            customTitle = "My First Song"
        )

        assertTrue(result.isSuccess)
        val imported = result.getOrNull()
        assertNotNull(imported)
        assertEquals("My First Song", imported?.displayName)
        assertEquals(1, imported?.trackCount)

        // Check getAllSongs
        val allSongs = repository.getAllSongs().first()
        assertEquals(1, allSongs.size)
        assertEquals("My First Song", allSongs[0].displayName)

        // Check getSongById
        val retrieved = repository.getSongById(imported!!.id)
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.notes?.size)
        assertEquals(60, retrieved?.notes?.get(0)?.midiNote)
    }

    @Test
    fun importMidiFile_detectsDuplicateSha256() = runTest {
        val rawBytes = createSampleMidiBytes(60)

        // Import 1st time
        val firstResult = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "song1.mid",
            fileSize = rawBytes.size.toLong()
        )
        assertTrue(firstResult.isSuccess)

        // Import 2nd time with exact same content
        val secondResult = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "song1_copy.mid",
            fileSize = rawBytes.size.toLong()
        )
        assertTrue(secondResult.isFailure)
        assertTrue(secondResult.exceptionOrNull() is DuplicateMidiException)
    }

    @Test
    fun renameAndFavoriteSong_persistsCorrectly() = runTest {
        val rawBytes = createSampleMidiBytes(62)
        val imported = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "flower_dance.mid",
            fileSize = rawBytes.size.toLong(),
            customTitle = "Flower Dance"
        ).getOrThrow()

        // 1. Rename
        repository.renameSong(imported.id, "Flower Dance (Remastered)")
        val afterRename = repository.getSongById(imported.id)
        assertEquals("Flower Dance (Remastered)", afterRename?.displayName)

        // 2. Favorite
        assertFalse(afterRename!!.isFavorite)
        repository.toggleFavorite(imported.id)
        val afterFav = repository.getSongById(imported.id)
        assertTrue(afterFav!!.isFavorite)

        val favorites = repository.getFavoriteSongs().first()
        assertEquals(1, favorites.size)
        assertEquals("Flower Dance (Remastered)", favorites[0].displayName)
    }

    @Test
    fun deleteSong_cascadesAndCleansUp() = runTest {
        val rawBytes = createSampleMidiBytes(64)
        val imported = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "to_delete.mid",
            fileSize = rawBytes.size.toLong()
        ).getOrThrow()

        assertNotNull(repository.getSongById(imported.id))

        // Delete
        repository.deleteSong(imported.id)

        assertNull(repository.getSongById(imported.id))
        val allSongs = repository.getAllSongs().first()
        assertTrue(allSongs.isEmpty())
    }

    @Test
    fun updateTrackConfigurations_updatesAssignedHand() = runTest {
        val rawBytes = createSampleMidiBytes(60)
        val imported = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "hand_test.mid",
            fileSize = rawBytes.size.toLong()
        ).getOrThrow()

        val tracks = repository.getSongTracks(imported.id)
        assertEquals(1, tracks.size)

        // Switch track 0 to LEFT hand
        val updatedTracks = listOf(tracks[0].copy(assignedHand = "LEFT"))
        repository.updateTrackConfigurations(imported.id, updatedTracks)

        val song = repository.getSongById(imported.id)
        assertNotNull(song)
        assertEquals(HandMode.LEFT, song?.notes?.get(0)?.hand)
    }
}
