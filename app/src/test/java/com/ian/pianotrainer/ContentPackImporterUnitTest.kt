package com.ian.pianotrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.core.contentpack.ContentPackImporter
import com.ian.pianotrainer.core.contentpack.AudioHeaderSniffer
import com.ian.pianotrainer.core.contentpack.DetectedAudioFormat
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.ImportedSong
import com.ian.pianotrainer.domain.model.PendingSongAsset
import com.ian.pianotrainer.domain.model.SongAsset
import com.ian.pianotrainer.domain.model.SongAssetType
import com.ian.pianotrainer.domain.model.SongPlaybackData
import com.ian.pianotrainer.domain.model.SongPracticePreset
import com.ian.pianotrainer.domain.model.SongTimeSignature
import com.ian.pianotrainer.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.File
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
        override suspend fun importSongPackage(
            inputStream: InputStream,
            originalFileName: String,
            fileSize: Long,
            customTitle: String?,
            additionalAssets: List<PendingSongAsset>
        ): Result<ImportedSong> {
            val id = UUID.randomUUID().toString()
            val assets = buildList {
                add(SongAsset("${id}_midi", id, SongAssetType.MIDI, originalFileName, "midi", fileSize))
                additionalAssets.forEach { add(SongAsset("${id}_${it.type}", id, it.type, it.originalFileName, it.stagedFilePath, it.fileSizeBytes, it.mimeType)) }
            }
            val song = ImportedSong(id = id, displayName = customTitle ?: originalFileName, originalFileName = originalFileName, noteCount = 42, assets = assets)
            importedSongs.add(song)
            return Result.success(song)
        }
        override suspend fun getSongAssets(songId: String): List<SongAsset> =
            importedSongs.firstOrNull { it.id == songId }?.assets.orEmpty()
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

    @Test
    fun `pack assets share repository song id and staging is cleaned`() = runBlocking {
        val bytes = zipOf(
            "manifest.json" to """{"id":"manifest-id","title":"Bundle","midiFileName":"music/main.mid","musicXmlFileName":"score/main.musicxml","audioFileName":"audio/ref.ogg"}""".toByteArray(),
            "music/main.mid" to sampleMidi(),
            "score/main.musicxml" to """<?xml version="1.0"?><score-partwise version="4.0"><part-list/></score-partwise>""".toByteArray(),
            "audio/ref.ogg" to "OggSfake".toByteArray()
        )
        val result = importer.importPack(ByteArrayInputStream(bytes))
        assertTrue(result.errorMessage.orEmpty(), result.isSuccess)
        val song = fakeSongRepository.getSongById(result.songId!!)!!
        assertEquals(setOf(SongAssetType.MIDI, SongAssetType.MUSICXML, SongAssetType.REFERENCE_AUDIO), song.assets.map { it.type }.toSet())
        assertTrue(song.assets.all { it.songId == song.id })
        assertTrue(File(context.cacheDir, "song_import_staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `invalid manifest and missing declared resource fail clearly`() = runBlocking {
        val invalid = importer.importPack(ByteArrayInputStream(zipOf("manifest.json" to "{".toByteArray(), "song.mid" to sampleMidi())))
        assertFalse(invalid.isSuccess)
        assertTrue(invalid.errorMessage.orEmpty().contains("manifest.json"))

        val missing = importer.importPack(ByteArrayInputStream(zipOf(
            "manifest.json" to """{"id":"x","title":"X","midiFileName":"missing.mid"}""".toByteArray(),
            "other.mid" to sampleMidi()
        )))
        assertFalse(missing.isSuccess)
        assertTrue(missing.errorMessage.orEmpty().contains("không tồn tại"))
    }

    @Test
    fun `zip traversal is rejected and leaves no staged files`() = runBlocking {
        val result = importer.importPack(ByteArrayInputStream(zipOf("../escape.mid" to sampleMidi())))
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage.orEmpty().contains("không an toàn"))
        assertTrue(File(context.cacheDir, "song_import_staging").listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `declared mxl is expanded and validated as MusicXML`() = runBlocking {
        val mxl = zipOf(
            "META-INF/container.xml" to """<?xml version="1.0"?><container><rootfiles><rootfile full-path="score.xml"/></rootfiles></container>""".toByteArray(),
            "score.xml" to """<?xml version="1.0"?><score-partwise version="4.0"><part-list/></score-partwise>""".toByteArray()
        )
        val pack = zipOf(
            "manifest.json" to """{"id":"x","title":"MXL","midiFileName":"song.mid","musicXmlFileName":"sheet.mxl"}""".toByteArray(),
            "song.mid" to sampleMidi(),
            "sheet.mxl" to mxl
        )
        val result = importer.importPack(ByteArrayInputStream(pack))
        assertTrue(result.errorMessage.orEmpty(), result.isSuccess)
        assertTrue(result.hasSheetMusic)
    }

    @Test fun `valid MusicXML root followed by truncated XML is rejected`() = runBlocking {
        val pack = zipOf(
            "manifest.json" to """{"id":"x","title":"Broken","midiFileName":"song.mid","musicXmlFileName":"score.xml"}""".toByteArray(),
            "song.mid" to sampleMidi(),
            "score.xml" to "<score-partwise><part-list/></score-partwise><broken".toByteArray()
        )
        val result = importer.importPack(ByteArrayInputStream(pack))
        assertFalse(result.isSuccess)
        assertTrue(result.errorMessage.orEmpty().contains("hoàn chỉnh"))
    }

    @Test fun `MusicXML DOCTYPE is accepted without resolving external entity`() = runBlocking {
        val sentinel = File(context.cacheDir, "xxe-secret.txt").apply { writeText("SECRET_MUST_NOT_BE_READ") }
        val xml = """<?xml version="1.0"?><!DOCTYPE score-partwise SYSTEM "${sentinel.toURI()}"><score-partwise><part-list/></score-partwise>"""
        val pack = zipOf(
            "manifest.json" to """{"id":"x","title":"Doctype","midiFileName":"song.mid","musicXmlFileName":"score.xml"}""".toByteArray(),
            "song.mid" to sampleMidi(), "score.xml" to xml.toByteArray()
        )
        val result = importer.importPack(ByteArrayInputStream(pack))
        sentinel.delete()
        assertTrue(result.errorMessage.orEmpty(), result.isSuccess)
    }

    @Test fun `audio sniffer supports ID3v23 ID3v24 and frame-only MP3 but rejects broken headers`() {
        fun id3(version: Int) = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), version.toByte(), 0, 0, 0, 0, 0, 0)
        assertEquals(DetectedAudioFormat.MP3, AudioHeaderSniffer.detect(id3(3)))
        assertEquals(DetectedAudioFormat.MP3, AudioHeaderSniffer.detect(id3(4)))
        assertEquals(DetectedAudioFormat.MP3, AudioHeaderSniffer.detect(byteArrayOf(0xff.toByte(), 0xfb.toByte(), 0x90.toByte(), 0)))
        assertEquals(null, AudioHeaderSniffer.detect(byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 9)))
        assertEquals(null, AudioHeaderSniffer.detect(byteArrayOf(0xff.toByte(), 0, 0, 0)))
    }

    @Test fun `standard XML escapes and numeric references are accepted`() = runBlocking {
        val xml = """<score-partwise title="A &amp; B &quot;Q&quot; &apos;P&apos; &#65; &#x42;"><part-list><part-name>&lt;Piano&gt;</part-name></part-list></score-partwise>"""
        val result = importer.importPack(ByteArrayInputStream(zipOf(
            "manifest.json" to """{"id":"x","title":"Escapes","midiFileName":"song.mid","musicXmlFileName":"score.xml"}""".toByteArray(),
            "song.mid" to sampleMidi(), "score.xml" to xml.toByteArray()
        )))
        assertTrue(result.errorMessage.orEmpty(), result.isSuccess)
    }

    @Test fun `custom entity is rejected while entity processing stays disabled`() = runBlocking {
        val xml = """<!DOCTYPE score-partwise [<!ENTITY custom "unsafe">]><score-partwise><part-list>&custom;</part-list></score-partwise>"""
        val result = importer.importPack(ByteArrayInputStream(zipOf(
            "manifest.json" to """{"id":"x","title":"Entity","midiFileName":"song.mid","musicXmlFileName":"score.xml"}""".toByteArray(),
            "song.mid" to sampleMidi(), "score.xml" to xml.toByteArray()
        )))
        assertFalse(result.isSuccess)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
            }
        }
        return output.toByteArray()
    }

    private fun sampleMidi(): ByteArray = byteArrayOf(
        0x4D, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 1, 0,
        0x4D, 0x54, 0x72, 0x6B, 0, 0, 0, 4, 0, 0xFF.toByte(), 0x2F, 0
    )
}
