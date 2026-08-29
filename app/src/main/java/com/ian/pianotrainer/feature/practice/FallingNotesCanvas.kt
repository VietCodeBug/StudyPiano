package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.VisualLookAhead

@Composable
fun FallingNotesCanvas(
    notes: List<ExerciseNote>,
    currentPositionMs: Long,
    currentNoteIndex: Int,
    namingMode: NoteNamingMode,
    startOctave: Int = 3,
    rangeMode: KeyboardRangeMode = KeyboardRangeMode.TWO_OCTAVES,
    noteDisplaySize: NoteDisplaySize = NoteDisplaySize.AUTO,
    lookAhead: VisualLookAhead = VisualLookAhead.MEDIUM,
    lastResult: NoteResultType? = null,
    lastPlayedMidi: Int? = null,
    expectedNotes: List<ExerciseNote> = emptyList(),
    modifier: Modifier = Modifier
) {
    val sortedNotes = remember(notes) {
        notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .testTag("falling_notes_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val hitLineY = canvasHeight - 10.dp.toPx()

            val lookAheadMs = lookAhead.lookAheadMs.toFloat()
            val pixelsPerMs = (hitLineY - 8.dp.toPx()) / lookAheadMs

            // Compute visible upcoming notes to assist AUTO range calculator
            val lookAheadEndMs = currentPositionMs + lookAhead.lookAheadMs
            val upcomingMidiNotes = sortedNotes
                .filter { it.startMs in currentPositionMs..lookAheadEndMs }
                .map { it.midiNote }

            val rangeResult = PianoGeometryCalculator.computeRangeForMode(
                mode = rangeMode,
                baseOctave = startOctave,
                totalWidth = canvasWidth,
                upcomingMidiNotes = upcomingMidiNotes
            )

            val keyGeometries = rangeResult.geometries
            val isDenseMode = (rangeMode == KeyboardRangeMode.SIX_OCTAVES || rangeMode == KeyboardRangeMode.FULL_88_KEYS)

            // 1. Draw Subtle Beat / Timing Guide Lines (Horizontal DAW-like grid)
            val beatIntervalMs = 500L // ~120 BPM beat interval guide
            val firstBeatMs = (currentPositionMs / beatIntervalMs) * beatIntervalMs
            var beatMs = firstBeatMs
            while (beatMs <= lookAheadEndMs + 500L) {
                val timeDiff = beatMs - currentPositionMs
                val lineY = hitLineY - (timeDiff * pixelsPerMs)
                if (lineY in 0f..canvasHeight) {
                    val isMeasure = (beatMs % (beatIntervalMs * 4) == 0L)
                    drawLine(
                        color = if (isMeasure) Color(0x2238BDF8) else Color(0x0F38BDF8),
                        start = Offset(0f, lineY),
                        end = Offset(canvasWidth, lineY),
                        strokeWidth = if (isMeasure) 1.5.dp.toPx() else 1f
                    )
                }
                beatMs += beatIntervalMs
            }

            // 2. Draw Background Lanes
            // White key lane dividers
            rangeResult.whiteNotes.forEach { midiNote ->
                val geom = keyGeometries[midiNote] ?: return@forEach
                drawLine(
                    color = Color(0xFF141D2D).copy(alpha = 0.5f),
                    start = Offset(geom.left, 0f),
                    end = Offset(geom.left, canvasHeight),
                    strokeWidth = 1f
                )
            }

            // Darker shaded columns for black keys
            keyGeometries.values.filter { it.isBlack }.forEach { geom ->
                drawRect(
                    color = Color(0xFF04070E).copy(alpha = 0.65f),
                    topLeft = Offset(geom.left, 0f),
                    size = Size(geom.width, canvasHeight)
                )
            }

            // 3. Draw Hit Reception Line at bottom with neon gradient glow
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        PianoPrimary.copy(alpha = 0.45f),
                        PianoAccent.copy(alpha = 0.95f),
                        PianoPrimary.copy(alpha = 0.45f)
                    )
                ),
                start = Offset(0f, hitLineY),
                end = Offset(canvasWidth, hitLineY),
                strokeWidth = 2.5.dp.toPx()
            )

            // Soft glow zone above hitline
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        PianoAccent.copy(alpha = 0.14f)
                    ),
                    startY = hitLineY - 28.dp.toPx(),
                    endY = hitLineY
                ),
                topLeft = Offset(0f, hitLineY - 28.dp.toPx()),
                size = Size(canvasWidth, 28.dp.toPx())
            )

            // 4. Filter notes in visible time window using binary search
            val minVisibleMs = currentPositionMs - 800L
            val maxVisibleMs = currentPositionMs + lookAhead.lookAheadMs + 1000L

            var low = 0
            var high = sortedNotes.size - 1
            var startIndex = 0
            while (low <= high) {
                val mid = (low + high) ushr 1
                val endMs = sortedNotes[mid].startMs + sortedNotes[mid].durationMs
                if (endMs < minVisibleMs) {
                    low = mid + 1
                } else {
                    startIndex = mid
                    high = mid - 1
                }
            }

            val marginRatio = noteDisplaySize.laneMarginRatio
            val activeExpectedPitches = expectedNotes.map { it.midiNote }.toSet()

            for (i in startIndex until sortedNotes.size) {
                val note = sortedNotes[i]
                if (note.startMs > maxVisibleMs) break

                val geom = keyGeometries[note.midiNote] ?: continue

                val timeUntilHitMs = note.startMs - currentPositionMs
                val rawHeight = (note.durationMs * pixelsPerMs).coerceIn(12.dp.toPx(), 280.dp.toPx())

                val noteBottomY = hitLineY - (timeUntilHitMs * pixelsPerMs)
                val noteTopY = noteBottomY - rawHeight

                if (noteBottomY < 0f || noteTopY > canvasHeight) continue

                val isCurrentExpected = if (activeExpectedPitches.isNotEmpty()) {
                    note.midiNote in activeExpectedPitches && kotlin.math.abs(note.startMs - currentPositionMs) < 2000L
                } else {
                    currentNoteIndex in notes.indices && notes[currentNoteIndex] == note
                }
                val isRecentlyPlayed = (lastPlayedMidi == note.midiNote)

                val baseColor = when {
                    isRecentlyPlayed && lastResult == NoteResultType.CORRECT -> PianoSuccess
                    isRecentlyPlayed && lastResult == NoteResultType.WRONG -> PianoError
                    note.hand == HandMode.LEFT -> Color(0xFFF97316) // Vibrant Warm Orange
                    else -> Color(0xFF00E5FF) // Electric Cyan
                }

                val laneMargin = (geom.width * marginRatio).coerceIn(1f, 3.dp.toPx())
                val blockX = geom.left + laneMargin
                val blockW = (geom.width - laneMargin * 2).coerceAtLeast(3.dp.toPx())

                // Draw Note Bar with sleek gradient
                val gradientBrush = Brush.verticalGradient(
                    listOf(
                        baseColor.copy(alpha = 0.8f),
                        baseColor.copy(alpha = 1.0f)
                    ),
                    startY = noteTopY,
                    endY = noteBottomY
                )

                drawRoundRect(
                    brush = gradientBrush,
                    topLeft = Offset(blockX, noteTopY),
                    size = Size(blockW, rawHeight),
                    cornerRadius = CornerRadius(3.5.dp.toPx(), 3.5.dp.toPx())
                )

                // Top highlight shine bar
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.5f),
                    topLeft = Offset(blockX, noteTopY),
                    size = Size(blockW, 2.5.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Highlight border for expected active note / chord
                if (isCurrentExpected) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(blockX - 1.dp.toPx(), noteTopY - 1.dp.toPx()),
                        size = Size(blockW + 2.dp.toPx(), rawHeight + 2.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Draw Note Label & Finger Number if block is wide enough
                if (blockW > 15.dp.toPx() && rawHeight > 14.dp.toPx()) {
                    val label = NoteHelper.formatNoteName(note.midiNote, namingMode)
                    val displayText = if (note.fingerNumber > 0 && blockW > 24.dp.toPx()) {
                        "$label • ${note.fingerNumber}"
                    } else {
                        label
                    }
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = if (isDenseMode) 8.dp.toPx() else 9.5.dp.toPx()
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        displayText,
                        blockX + blockW / 2f,
                        noteBottomY - 4.dp.toPx(),
                        paint
                    )
                }
            }
        }
    }
}
