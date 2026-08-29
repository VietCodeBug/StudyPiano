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
import com.ian.pianotrainer.core.music.BeatGridCalculator
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import com.ian.pianotrainer.core.music.VisibleNoteWindowSelector
import com.ian.pianotrainer.domain.model.ExerciseNote
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteDisplaySize
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.SongTempoInfo
import com.ian.pianotrainer.domain.model.SongTimeSignature
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
    tempos: List<SongTempoInfo> = emptyList(),
    timeSignatures: List<SongTimeSignature> = emptyList(),
    modifier: Modifier = Modifier
) {
    val sortedNotes = remember(notes) {
        notes.sortedWith(compareBy({ it.startMs }, { it.midiNote }))
    }

    val windowSelector = remember(sortedNotes) {
        VisibleNoteWindowSelector(sortedNotes)
    }

    val gridCalculator = remember { BeatGridCalculator() }

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
            val lookAheadEndMs = currentPositionMs + lookAhead.lookAheadMs

            // 1. Efficient upcoming MIDI notes slice for AUTO range calculator
            val upcomingRange = windowSelector.getVisibleNoteRange(currentPositionMs, lookAheadEndMs)
            val upcomingMidiNotes = if (!upcomingRange.isEmpty()) {
                val list = ArrayList<Int>(upcomingRange.last - upcomingRange.first + 1)
                for (i in upcomingRange) {
                    val note = sortedNotes[i]
                    if (note.startMs in currentPositionMs..lookAheadEndMs) {
                        list.add(note.midiNote)
                    }
                }
                list
            } else {
                emptyList()
            }

            val rangeResult = PianoGeometryCalculator.computeRangeForMode(
                mode = rangeMode,
                baseOctave = startOctave,
                totalWidth = canvasWidth,
                upcomingMidiNotes = upcomingMidiNotes
            )

            val keyGeometries = rangeResult.geometries
            val isDenseMode = (rangeMode == KeyboardRangeMode.SIX_OCTAVES || rangeMode == KeyboardRangeMode.FULL_88_KEYS)

            // 2. Draw Beat / Measure Timing Guide Lines based on BeatGridCalculator
            val gridLines = gridCalculator.calculate(
                windowStartMs = currentPositionMs,
                windowEndMs = lookAheadEndMs,
                ppq = 480,
                tempos = tempos,
                timeSignatures = timeSignatures
            )

            for (gridLine in gridLines) {
                val timeDiff = gridLine.timeMs - currentPositionMs
                val lineY = hitLineY - (timeDiff * pixelsPerMs)
                if (lineY in 0f..canvasHeight) {
                    val isMeasure = gridLine.isMeasureStart
                    drawLine(
                        color = if (isMeasure) Color(0x3338BDF8) else Color(0x1038BDF8),
                        start = Offset(0f, lineY),
                        end = Offset(canvasWidth, lineY),
                        strokeWidth = if (isMeasure) 1.5.dp.toPx() else 1f
                    )
                }
            }

            // 3. Draw Background Lanes
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

            // 4. Draw Hit Reception Line at bottom with neon gradient glow
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
                        PianoPrimary.copy(alpha = 0.12f)
                    ),
                    startY = hitLineY - 35.dp.toPx(),
                    endY = hitLineY
                ),
                topLeft = Offset(0f, hitLineY - 35.dp.toPx()),
                size = Size(canvasWidth, 35.dp.toPx())
            )

            // 5. Select visible notes using interval binary search
            val minVisibleMs = currentPositionMs - 800L
            val maxVisibleMs = currentPositionMs + lookAhead.lookAheadMs + 1000L
            val visibleRange = windowSelector.getVisibleNoteRange(minVisibleMs, maxVisibleMs)

            val marginRatio = noteDisplaySize.laneMarginRatio
            val activeExpectedPitches = expectedNotes.map { it.midiNote }.toSet()

            if (!visibleRange.isEmpty()) {
                for (i in visibleRange) {
                    val note = sortedNotes[i]
                    val noteEndMs = note.startMs + note.durationMs
                    if (noteEndMs < minVisibleMs) continue
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
                        note.hand == HandMode.LEFT -> Color(0xFFF97316) // Vibrant Warm Orange (Left Hand)
                        else -> Color(0xFF00E5FF) // Electric Cyan/Blue (Right Hand)
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
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    // Target expected highlight stroke
                    if (isCurrentExpected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(blockX - 1f, noteTopY - 1f),
                            size = Size(blockW + 2f, rawHeight + 2f),
                            cornerRadius = CornerRadius(5.dp.toPx(), 5.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    // 6. Draw note text label inside note block if space permits (hide on dense tracks)
                    if (!isDenseMode && blockW >= 18.dp.toPx() && rawHeight >= 16.dp.toPx()) {
                        val noteName = NoteHelper.midiToNoteName(note.midiNote, namingMode)
                        val textPaint = android.graphics.Paint().apply {
                            color = android.graphics.Color.WHITE
                            textSize = (blockW * 0.42f).coerceIn(9.dp.toPx(), 13.dp.toPx())
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            isFakeBoldText = true
                            setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
                        }

                        val textX = blockX + blockW / 2f
                        val textY = noteBottomY - 5.dp.toPx()

                        if (textY in 0f..canvasHeight) {
                            drawContext.canvas.nativeCanvas.drawText(
                                noteName,
                                textX,
                                textY,
                                textPaint
                            )
                        }
                    }
                }
            }
        }
    }
}
