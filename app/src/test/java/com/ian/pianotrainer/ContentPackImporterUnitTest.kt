package com.ian.pianotrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.core.contentpack.ContentPackImporter
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongPracticePreset
import com.ian.pianotrainer.domain.model.SongTimeSignature
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
class ContentPackImporterUnitTest {

    private lateinit var context: Context
    private lateinit var fakeSongRepository: FakeSongRepository
    private lateinit var importer: ContentPackImporter

    class FakeSongRepository : SongRepository {
        var importedSongs = mutableListOf<ImportedSong>()
        override fun getAllSongs(): Flow<List<ImportedSong>> = flowOf(importedSongs)
        override suspend fun getAllSongsList(): List<ImportedSong> = importedSongs
        override fun getFavoriteSongs(): Flow<List<ImportedSong>> = flowOf(emptyList())
        override suspend fun getSongById(id: String): ImportedSong? = importedSongs.find { it.id == id }
        override suspend fun getSongPlaybackData(id: String): SongPlaybackData? = null
        override suspend fun getSongTracks(songId: String): List<SongTrackEntity> = emptyList()
        override suspend fun getSongNotes(songId: String): List<SongNoteEntity> = emptyList()
        override suspend fun getSongTimeSignatures(songId: String): List<SongTimeSignature> = emptyList()
        override suspend fun importMidiFile(
            inputStream: InputStream,
            originalFileName: String,
            fileSize: Long,
            customTitle: String?
        ): Result<ImportedSong> {
            val song = ImportedSong(
                id = UUID.randomUUID().toString(),
                displayName = customTitle ?: originalFileName,
                originalFileName = originalFileName,
                noteCount = 42
            )
            importedSongs.add(song)
            return Result.success(song)
        }
        override suspend fun updateTrackConfigurations(songId: String, tracks: List<SongTrackEntity>) {}
        override suspend fun renameSong(id: String, newName: String) {}
        override suspend fun toggleFavorite(id: String) {}
        override suspend fun deleteSong(id: String) {}
        override suspend fun updateLastPracticed(id: String) {}
        override suspend fun seedCurriculumRepertoire(): Int = 0
        override fun getPracticePresets(songId: String): Flow<List<SongPracticePreset>> = flowOf(emptyList())
        override suspend fun getAllPresetsList(): List<SongPracticePreset> = emptyList()
        override suspend fun savePracticePreset(preset: SongPracticePreset) {}
        override suspend fun deletePracticePreset(id: String) {}
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeSongRepository = FakeSongRepository()
        importer = ContentPackImporter(context, fakeSongRepository)
    }

    @Test
    fun `empty stream returns error`() = runBlocking {
        val emptyZip = ByteArrayInputStream(ByteArray(0))
        val result = importer.importPack(emptyZip)
        assertFalse("Empty zip must fail", result.isSuccess)
    }

    @Test
    fun `zip without midi file returns error`() = runBlocking {
        val baos = ByteArrayOutputStream()
        ZipOutputStream(baos).use { zos ->
            zos.putNextEntry(ZipEntry("manifest.json"))
            zos.write("""{"id":"test","title":"Test Song"}""".toByteArray())
            zos.closeEntry()
        }

        val result = importer.importPack(ByteArrayInputStream(baos.toByteArray()))
        assertFalse("Zip without MIDI must fail", result.isSuccess)
        assertTrue("Error message should mention MIDI", result.errorMessage?.contains("MIDI") == true)
    }
}
