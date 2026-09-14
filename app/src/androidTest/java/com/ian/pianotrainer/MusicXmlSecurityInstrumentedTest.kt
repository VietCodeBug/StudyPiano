package com.ian.pianotrainer

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ian.pianotrainer.core.contentpack.MusicXmlValidator
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MusicXmlSecurityInstrumentedTest {
    @Test fun readsWholeDocumentAndRejectsTruncatedTail() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val file = File(context.cacheDir, "truncated.musicxml").apply {
            writeText("<score-partwise><part-list/></score-partwise><broken")
        }
        assertFalse(MusicXmlValidator.validate(file).isSuccess)
        file.delete()
    }

    @Test fun acceptsDoctypeWithoutReadingExternalFile() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val sentinel = File(context.cacheDir, "external-secret.dtd").apply { writeText("THIS IS NOT A DTD") }
        val score = File(context.cacheDir, "doctype.musicxml").apply {
            writeText("""<?xml version="1.0"?><!DOCTYPE score-partwise SYSTEM "${sentinel.toURI()}"><score-partwise><part-list/></score-partwise>""")
        }
        val result = MusicXmlValidator.validate(score)
        assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
        score.delete(); sentinel.delete()
    }

    @Test fun acceptsStandardEscapesAndNumericReferences() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val score = File(context.cacheDir, "escapes.musicxml").apply {
            writeText("""<score-partwise title="A &amp; B &quot;Q&quot; &apos;P&apos; &#65; &#x42;"><part-list>&lt;Piano&gt;</part-list></score-partwise>""")
        }
        val result = MusicXmlValidator.validate(score)
        assertTrue(result.exceptionOrNull()?.toString().orEmpty(), result.isSuccess)
        score.delete()
    }

    @Test fun rejectsCustomEntityWithoutEnablingDoctypeProcessing() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val score = File(context.cacheDir, "custom-entity.musicxml").apply {
            writeText("""<!DOCTYPE score-partwise [<!ENTITY custom "unsafe">]><score-partwise><part-list>&custom;</part-list></score-partwise>""")
        }
        assertFalse(MusicXmlValidator.validate(score).isSuccess)
        score.delete()
    }
}
