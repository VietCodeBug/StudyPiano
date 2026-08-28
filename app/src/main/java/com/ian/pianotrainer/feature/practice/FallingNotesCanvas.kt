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
    modifier: Modifier = Modifier
) {
    val sortedNotes = remember(notes) {
        notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090D16))
            .testTag("falling_notes_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val hitLineY = canvasHeight - 12.dp.toPx()

            val lookAheadMs = lookAhead.lookAheadMs.toFloat()
            val pixelsPerMs = (hitLineY - 10.dp.toPx()) / lookAheadMs

            // Compute visible upcoming notes to help AUTO range calculator
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

            // 1. Draw Background Lanes
            // White key lane dividers
            rangeResult.whiteNotes.forEach { midiNote ->
                val geom = keyGeometries[midiNote] ?: return@forEach
                drawLine(
                    color = Color(0xFF161F30).copy(alpha = 0.5f),
                    start = Offset(geom.left, 0f),
                    end = Offset(geom.left, canvasHeight),
                    strokeWidth = 1f
                )
            }

            // Darker shaded columns for black keys
            keyGeometries.values.filter { it.isBlack }.forEach { geom ->
                drawRect(
                    color = Color(0xFF060910).copy(alpha = 0.55f),
                    topLeft = Offset(geom.left, 0f),
                    size = Size(geom.width, canvasHeight)
                )
            }

            // 2. Draw Hit Reception Line at bottom with neon gradient glow
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        PianoPrimary.copy(alpha = 0.5f),
                        PianoAccent.copy(alpha = 0.95f),
                        PianoPrimary.copy(alpha = 0.5f)
                    )
                ),
                start = Offset(0f, hitLineY),
                end = Offset(canvasWidth, hitLineY),
                strokeWidth = 3.dp.toPx()
            )

            // Subtle glow above hitline
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(
                        Color.Transparent,
                        PianoAccent.copy(alpha = 0.12f)
                    ),
                    startY = hitLineY - 30.dp.toPx(),
                    endY = hitLineY
                ),
                topLeft = Offset(0f, hitLineY - 30.dp.toPx()),
                size = Size(canvasWidth, 30.dp.toPx())
            )

            // 3. Filter notes in visible time window using binary search / range filter
            val minVisibleMs = currentPositionMs - 800L
            val maxVisibleMs = currentPositionMs + lookAhead.lookAheadMs + 1000L

            // Quick binary search bounds
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

            for (i in startIndex until sortedNotes.size) {
                val note = sortedNotes[i]
                if (note.startMs > maxVisibleMs) break

                val geom = keyGeometries[note.midiNote] ?: continue

                val timeUntilHitMs = note.startMs - currentPositionMs
                val rawHeight = (note.durationMs * pixelsPerMs).coerceIn(12.dp.toPx(), 260.dp.toPx())

                val noteBottomY = hitLineY - (timeUntilHitMs * pixelsPerMs)
                val noteTopY = noteBottomY - rawHeight

                val isCurrentExpected = (currentNoteIndex in notes.indices && notes[currentNoteIndex] == note)
                val isRecentlyPlayed = (lastPlayedMidi == note.midiNote)

                val noteColor = when {
                    isRecentlyPlayed && lastResult == NoteResultType.CORRECT -> PianoSuccess
                    isRecentlyPlayed && lastResult == NoteResultType.WRONG -> PianoError
                    note.hand == HandMode.LEFT -> Color(0xFFF97316) // Vibrant Orange for Left Hand
                    else -> Color(0xFF00E5FF) // Bright Cyan for Right Hand
                }

                val laneMargin = (geom.width * marginRatio).coerceIn(1f, 3.dp.toPx())
                val blockX = geom.left + laneMargin
                val blockW = (geom.width - laneMargin * 2).coerceAtLeast(3.dp.toPx())

                // Draw Note Body
                drawRoundRect(
                    color = noteColor,
                    topLeft = Offset(blockX, noteTopY),
                    size = Size(blockW, rawHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Highlight border for expected active note
                if (isCurrentExpected) {
                    drawRoundRect(
                        color = Color.White,
                        topLeft = Offset(blockX - 1.dp.toPx(), noteTopY - 1.dp.toPx()),
                        size = Size(blockW + 2.dp.toPx(), rawHeight + 2.dp.toPx()),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // Draw note label if block is wide enough and not dense mode
                if (blockW > 16.dp.toPx() && rawHeight > 16.dp.toPx() && noteTopY >= 0 && noteBottomY <= canvasHeight) {
                    val label = NoteHelper.formatNoteName(note.midiNote, namingMode)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.WHITE
                        textSize = if (isDenseMode) 8.dp.toPx() else 10.dp.toPx()
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        blockX + blockW / 2f,
                        noteBottomY - 4.dp.toPx(),
                        paint
                    )
                }
            }
        }
    }
}
