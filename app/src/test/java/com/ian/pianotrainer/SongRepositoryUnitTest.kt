package com.ian.pianotrainer

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.PianoTrainerDatabase
import com.ian.pianotrainer.data.repository.DuplicateMidiException
import com.ian.pianotrainer.data.repository.InvalidMidiFileException
import com.ian.pianotrainer.data.repository.MidiFileTooLargeException
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
import java.io.File
import java.io.InputStream

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
            database = db,
            importedSongDao = db.importedSongDao(),
            songTrackDao = db.songTrackDao(),
            songNoteDao = db.songNoteDao(),
            songTempoDao = db.songTempoDao(),
            songTimeSignatureDao = db.songTimeSignatureDao()
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

        // Time Signature: Delta 0, FF 58 04 04 02 18 08 (4/4)
        trackDos.writeByte(0x00)
        trackDos.writeByte(0xFF)
        trackDos.writeByte(0x58)
        trackDos.writeByte(0x04)
        trackDos.writeByte(0x04)
        trackDos.writeByte(0x02) // 2^2 = 4
        trackDos.writeByte(0x18)
        trackDos.writeByte(0x08)

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
        assertEquals(1, imported?.noteCount)

        // Check getAllSongs
        val allSongs = repository.getAllSongs().first()
        assertEquals(1, allSongs.size)
        assertEquals("My First Song", allSongs[0].displayName)
        assertEquals(1, allSongs[0].noteCount)

        // Check getSongById
        val retrieved = repository.getSongById(imported!!.id)
        assertNotNull(retrieved)
        assertEquals(1, retrieved?.notes?.size)
        assertEquals(60, retrieved?.notes?.get(0)?.midiNote)

        // Check playback data
        val playbackData = repository.getSongPlaybackData(imported.id)
        assertNotNull(playbackData)
        assertEquals(1, playbackData?.timeSignatures?.size)
        assertEquals(4, playbackData?.timeSignatures?.get(0)?.numerator)
        assertEquals(4, playbackData?.timeSignatures?.get(0)?.denominator)
    }

    @Test
    fun importMidiFile_providerReportsOver20MB_failsImmediately() = runTest {
        val rawBytes = createSampleMidiBytes(60)
        val result = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "large.mid",
            fileSize = 25L * 1024L * 1024L // 25 MB reported
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MidiFileTooLargeException)
    }

    @Test
    fun importMidiFile_streamExceeds20MB_fails() = runTest {
        // Create an infinite or 21MB stream
        val stream = object : InputStream() {
            var count = 0L
            override fun read(): Int {
                if (count > 21L * 1024L * 1024L) return -1
                count++
                return 0
            }
            override fun read(b: ByteArray, off: Int, len: Int): Int {
                if (count > 21L * 1024L * 1024L) return -1
                val toRead = minOf(len.toLong(), 21L * 1024L * 1024L - count + 1).toInt()
                count += toRead
                return toRead
            }
        }

        val result = repository.importMidiFile(
            inputStream = stream,
            originalFileName = "too_large_stream.mid",
            fileSize = 0L // provider reported unknown size
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is MidiFileTooLargeException)
    }

    @Test
    fun importMidiFile_invalidHeader_failsWithInvalidMidiFileException() = runTest {
        val corruptedBytes = "Not a MIDI file content at all".toByteArray()
        val result = repository.importMidiFile(
            inputStream = ByteArrayInputStream(corruptedBytes),
            originalFileName = "corrupted.mid",
            fileSize = corruptedBytes.size.toLong()
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is InvalidMidiFileException)
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
    fun deleteSong_cascadesAndCleansUpAllChildRowsAndFiles() = runTest {
        val rawBytes = createSampleMidiBytes(64)
        val imported = repository.importMidiFile(
            inputStream = ByteArrayInputStream(rawBytes),
            originalFileName = "to_delete.mid",
            fileSize = rawBytes.size.toLong()
        ).getOrThrow()

        val songId = imported.id
        assertNotNull(repository.getSongById(songId))
        assertEquals(1, db.songTrackDao().getTracksForSong(songId).size)
        assertEquals(1, db.songNoteDao().getNotesForSong(songId).size)
        assertEquals(1, db.songTempoDao().getTemposForSong(songId).size)
        assertEquals(1, db.songTimeSignatureDao().getTimeSignaturesForSong(songId).size)

        val localFile = File(context.filesDir, "songs/$songId/source.mid")
        assertTrue(localFile.exists())

        // Delete
        repository.deleteSong(songId)

        // Assert parent and all children cascaded
        assertNull(repository.getSongById(songId))
        assertTrue(db.songTrackDao().getTracksForSong(songId).isEmpty())
        assertTrue(db.songNoteDao().getNotesForSong(songId).isEmpty())
        assertTrue(db.songTempoDao().getTemposForSong(songId).isEmpty())
        assertTrue(db.songTimeSignatureDao().getTimeSignaturesForSong(songId).isEmpty())
        assertFalse(localFile.exists())
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

    @Test
    fun seedCurriculumRepertoire_seedsLessonsIntoDatabase() = runTest {
        val seededCount = repository.seedCurriculumRepertoire()
        assertTrue(seededCount > 0)
        val allSongs = repository.getAllSongsList()
        assertTrue(allSongs.isNotEmpty())
    }
}
