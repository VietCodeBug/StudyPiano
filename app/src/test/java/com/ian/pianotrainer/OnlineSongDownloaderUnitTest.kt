package com.ian.pianotrainer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.ian.pianotrainer.core.contentpack.ContentPackImporter
import com.ian.pianotrainer.core.contentpack.OnlineSongDownloader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnlineSongDownloaderUnitTest {

    private lateinit var context: Context
    private lateinit var fakeRepo: ContentPackImporterUnitTest.FakeSongRepository
    private lateinit var importer: ContentPackImporter
    private lateinit var downloader: OnlineSongDownloader

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        fakeRepo = ContentPackImporterUnitTest.FakeSongRepository()
        importer = ContentPackImporter(context, fakeRepo)
        downloader = OnlineSongDownloader(context, fakeRepo, importer)
    }

    @Test
    fun `extractSequenceId parses numeric id and url formats correctly`() {
        assertEquals("3134103", downloader.extractSequenceId("3134103"))
        assertEquals("3813152", downloader.extractSequenceId("https://onlinesequencer.net/3813152#t=0"))
        assertEquals("2749811", downloader.extractSequenceId("https://onlinesequencer.net/app/midi/2749811"))
        assertEquals("3309995", downloader.extractSequenceId("onlinesequencer.net/3309995"))
    }

    @Test
    fun `extractSequenceId returns null for non-sequencer string`() {
        assertNull(downloader.extractSequenceId("invalid_url_without_digits"))
    }
}
