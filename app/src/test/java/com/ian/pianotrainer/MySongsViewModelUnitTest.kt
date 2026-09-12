package com.ian.pianotrainer

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.data.local.database.entity.SongNoteEntity
import com.ian.pianotrainer.data.local.database.entity.SongTrackEntity
import com.ian.pianotrainer.domain.model.*
import com.ian.pianotrainer.domain.repository.SongRepository
import com.ian.pianotrainer.feature.mysongs.MySongsViewModel
import com.ian.pianotrainer.feature.mysongs.SongPendingAction
import com.ian.pianotrainer.core.contentpack.ContentPackImportResult
import java.io.File
import java.io.InputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MySongsViewModelUnitTest {
    private val dispatcher = UnconfinedTestDispatcher()
    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test fun allTracksCanBeReselectedAndExactPracticeConfigIsSaved() = runTest {
        val repo = FakeRepo()
        val song = song("a")
        repo.songs.value = listOf(song)
        repo.tracks["a"] = listOf(track("a", 0, false, "LEFT"), track("a", 1, false, "RIGHT"))
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.openSongPreparation(song)
        await { vm.uiState.value.prepState?.tracks?.size == 2 }
        vm.toggleTrackSelection(1)
        vm.updateTrackHand(1, "LEFT")
        vm.setPrepPracticeMode(PracticeMode.RHYTHM)
        vm.setPrepBpm(88)
        var started: List<Any>? = null
        vm.saveTrackConfigAndStart { title, id, hand, mode, bpm -> started = listOf(title, id, hand, mode, bpm) }
        advanceUntilIdle()
        assertEquals(listOf(false, true), repo.savedTracks.map { it.isSelectedForPractice })
        assertEquals("LEFT", repo.savedTracks[1].assignedHand)
        assertEquals(listOf("Song a", "a", "LEFT", PracticeMode.RHYTHM, 88), started)
    }

    @Test fun favoriteUpdatesOpenDetailAfterRepositorySucceeds() = runTest {
        val repo = FakeRepo()
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.openSongPreparation(song("a"))
        advanceUntilIdle()
        vm.toggleFavorite("a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.prepState!!.song.isFavorite)
    }

    @Test fun slowAResultNeverOverwritesBAndCloseDoesNotReopen() = runTest {
        val repo = FakeRepo()
        val gateA = CompletableDeferred<Unit>()
        repo.trackGates["a"] = gateA
        repo.tracks["a"] = listOf(track("a", 0))
        repo.tracks["b"] = listOf(track("b", 7))
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.openSongPreparation(song("a"))
        vm.openSongPreparation(song("b"))
        await { vm.uiState.value.prepState?.tracks?.singleOrNull()?.trackIndex == 7 }
        assertEquals("b", vm.uiState.value.prepState!!.song.id)
        assertEquals(7, vm.uiState.value.prepState!!.tracks.single().trackIndex)
        gateA.complete(Unit)
        advanceUntilIdle()
        assertEquals("b", vm.uiState.value.prepState!!.song.id)
        repo.trackGates["a"] = CompletableDeferred()
        vm.openSongPreparation(song("a"))
        vm.closeSongPreparation()
        repo.trackGates["a"]!!.complete(Unit)
        advanceUntilIdle()
        assertNull(vm.uiState.value.prepState)
    }

    @Test fun libraryErrorIsShownAndRetryResubscribesSuccessfully() = runTest {
        val repo = FakeRepo()
        repo.failLibraryOnce = true
        repo.songs.value = listOf(song("ok"))
        val vm = MySongsViewModel(repo)
        collect(vm)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.libraryErrorMessage.orEmpty().contains("DB unavailable"))
        vm.retryLibrary()
        advanceUntilIdle()
        assertEquals(listOf("ok"), vm.uiState.value.songs.map { it.id })
        assertNull(vm.uiState.value.libraryErrorMessage)
    }

    @Test fun urlRetryUsesTheExactPreviousRequest() = runTest {
        val calls = mutableListOf<Pair<String, String?>>()
        val vm = MySongsViewModel(FakeRepo(), urlDownloadAction = { url, title ->
            calls += url to title
            ContentPackImportResult(isSuccess = false, errorMessage = "offline")
        })
        collect(vm)
        vm.downloadSong("https://example.test/song.mid", "Tên riêng")
        advanceUntilIdle()
        assertEquals("offline", vm.uiState.value.operation.urlError)
        vm.retryUrlImport()
        advanceUntilIdle()
        assertEquals(listOf("https://example.test/song.mid" to "Tên riêng", "https://example.test/song.mid" to "Tên riêng"), calls)
    }

    @Test fun detailRetryReloadsTheSameSong() = runTest {
        val repo = FakeRepo().apply { failTrackLoads = 1 }
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.openSongPreparation(song("detail"))
        await { vm.uiState.value.prepState?.errorMessage != null }
        assertNotNull(vm.uiState.value.prepState?.errorMessage)
        vm.retrySongPreparation()
        await { vm.uiState.value.prepState?.isLoadingTracks == false && vm.uiState.value.prepState?.errorMessage == null }
        assertEquals("detail", vm.uiState.value.prepState?.song?.id)
        assertNull(vm.uiState.value.prepState?.errorMessage)
        assertEquals(2, repo.trackLoadCalls)
    }

    @Test fun renameAndDeleteFailuresStayScopedAndKeepActionAvailable() = runTest {
        val repo = FakeRepo().apply { failRename = true; failDelete = true }
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.renameSong("a", "Tên đang sửa")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.operation.renameError.orEmpty().contains("rename failed"))
        assertNull(vm.uiState.value.operation.pendingAction)
        vm.deleteSong("a")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.operation.deleteError.orEmpty().contains("delete failed"))
        assertNull(vm.uiState.value.operation.pendingAction)
    }

    @Test fun duplicateRenameIsIgnoredWhileFirstRequestIsRunning() = runTest {
        val repo = FakeRepo().apply { renameGate = CompletableDeferred() }
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.renameSong("a", "Một")
        vm.renameSong("a", "Hai")
        assertEquals(SongPendingAction.RENAME, vm.uiState.value.operation.pendingAction)
        assertEquals(1, repo.renameCalls)
        repo.renameGate!!.complete(Unit)
        advanceUntilIdle()
        assertEquals(1, repo.renameCalls)
    }

    @Test fun importLoadingRemainsTrueUntilRepositoryCompletes() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val midi = File(context.cacheDir, "loading.mid")
        midi.writeBytes(sampleMidi())
        val repo = FakeRepo()
        repo.importGate = CompletableDeferred()
        val vm = MySongsViewModel(repo)
        collect(vm)
        vm.importFromUri(Uri.fromFile(midi), context)
        repo.importStarted.await()
        assertTrue(vm.uiState.value.isImporting)
        repo.importGate!!.complete(Unit)
        await { !vm.uiState.value.isImporting }
        assertFalse(vm.uiState.value.isImporting)
        assertTrue(vm.uiState.value.feedbackMessage.orEmpty().contains("Nhập thành công"))
    }

    private fun TestScope.collect(vm: MySongsViewModel) =
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    private suspend fun await(condition: () -> Boolean) {
        withTimeout(5_000) { while (!condition()) delay(10) }
    }
    private fun song(id: String) = ImportedSong(id, "Song " + id, id + ".mid", defaultBpm = 70)
    private fun track(id: String, index: Int, selected: Boolean = true, hand: String = "BOTH") =
        SongTrackEntity(id, index, "Track " + index, "Piano", null, 5, 60, 72, selected, hand)
    private fun sampleMidi() = byteArrayOf(0x4D,0x54,0x68,0x64,0,0,0,6,0,0,0,1,0,96,0x4D,0x54,0x72,0x6B,0,0,0,4,0,0xFF.toByte(),0x2F,0)

    private class FakeRepo : SongRepository {
        val songs = MutableStateFlow<List<ImportedSong>>(emptyList())
        val tracks = mutableMapOf<String, List<SongTrackEntity>>()
        val trackGates = mutableMapOf<String, CompletableDeferred<Unit>>()
        var savedTracks = emptyList<SongTrackEntity>()
        var failLibraryOnce = false
        var importGate: CompletableDeferred<Unit>? = null
        var renameGate: CompletableDeferred<Unit>? = null
        var failRename = false
        var failDelete = false
        var failTrackLoads = 0
        var trackLoadCalls = 0
        var renameCalls = 0
        val importStarted = CompletableDeferred<Unit>()
        private var libraryCalls = 0
        override fun getAllSongs(): Flow<List<ImportedSong>> = flow {
            libraryCalls++
            if (failLibraryOnce && libraryCalls == 1) throw IllegalStateException("DB unavailable")
            songs.collect { emit(it) }
        }
        override suspend fun getAllSongsList() = songs.value
        override fun getFavoriteSongs() = flowOf(songs.value.filter { it.isFavorite })
        override suspend fun getSongById(id: String) = songs.value.find { it.id == id }
        override suspend fun getSongPlaybackData(id: String): SongPlaybackData? = null
        override suspend fun getSongTracks(songId: String): List<SongTrackEntity> { trackLoadCalls++; if (failTrackLoads-- > 0) error("detail failed"); trackGates[songId]?.await(); return tracks[songId].orEmpty() }
        override suspend fun getSongNotes(songId: String) = emptyList<SongNoteEntity>()
        override suspend fun getSongTimeSignatures(songId: String) = emptyList<SongTimeSignature>()
        override suspend fun importMidiFile(inputStream: InputStream, originalFileName: String, fileSize: Long, customTitle: String?): Result<ImportedSong> {
            importStarted.complete(Unit); importGate?.await(); return Result.success(ImportedSong("imported", "Imported", originalFileName))
        }
        override suspend fun updateTrackConfigurations(songId: String, tracks: List<SongTrackEntity>) { savedTracks = tracks }
        override suspend fun renameSong(id: String, newName: String) { renameCalls++; renameGate?.await(); if (failRename) error("rename failed"); songs.value = songs.value.map { if (it.id == id) it.copy(displayName = newName) else it } }
        override suspend fun toggleFavorite(id: String) { songs.value = songs.value.map { if (it.id == id) it.copy(isFavorite = !it.isFavorite) else it } }
        override suspend fun deleteSong(id: String) { if (failDelete) error("delete failed"); songs.value = songs.value.filterNot { it.id == id } }
        override suspend fun updateLastPracticed(id: String) {}
        override suspend fun seedCurriculumRepertoire() = 0
        override fun getPracticePresets(songId: String) = flowOf(emptyList<SongPracticePreset>())
        override suspend fun getAllPresetsList() = emptyList<SongPracticePreset>()
        override suspend fun savePracticePreset(preset: SongPracticePreset) {}
        override suspend fun deletePracticePreset(id: String) {}
    }
}
