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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
import com.ian.pianotrainer.domain.model.VisualNoteFeedback
import kotlin.math.abs

@Composable
fun FallingNotesCanvas(
    notes: List<ExerciseNote>,
    currentPositionMs: Long,
    currentNoteIndex: Int,
    namingMode: NoteNamingMode,
    startOctave: Int = 3,
    rangeMode: KeyboardRangeMode = KeyboardRangeMode.FULL_88_KEYS,
    noteDisplaySize: NoteDisplaySize = NoteDisplaySize.AUTO,
    lookAhead: VisualLookAhead = VisualLookAhead.MEDIUM,
    activeFeedback: VisualNoteFeedback? = null,
    expectedNotes: List<ExerciseNote> = emptyList(),
    showNoteNames: Boolean = false,
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

    val textPaint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            isFakeBoldText = true
            setShadowLayer(3f, 0f, 1f, android.graphics.Color.BLACK)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F1D)) // Clean neutral dark background
            .testTag("falling_notes_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val hitLineY = canvasHeight - 2.dp.toPx() // Hit reception line right above reference keyboard

            val lookAheadMs = lookAhead.lookAheadMs.toFloat()
            val pixelsPerMs = (hitLineY - 8.dp.toPx()) / lookAheadMs
            val lookAheadEndMs = currentPositionMs + lookAhead.lookAheadMs

            // 1. Efficient upcoming MIDI notes slice for AUTO range calculator (zero per-frame collection allocations)
            val rangeResult = PianoGeometryCalculator.computeRangeForMode(
                mode = rangeMode,
                baseOctave = startOctave,
                totalWidth = canvasWidth
            )

            val keyGeometries = rangeResult.geometries
            val isDenseMode = (rangeMode == KeyboardRangeMode.SIX_OCTAVES || rangeMode == KeyboardRangeMode.FULL_88_KEYS)

            // 2. Draw Beat / Measure Timing Guide Lines
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
                        color = if (isMeasure) Color(0x3338BDF8) else Color(0x0C38BDF8),
                        start = Offset(0f, lineY),
                        end = Offset(canvasWidth, lineY),
                        strokeWidth = if (isMeasure) 1.5.dp.toPx() else 0.8f
                    )
                }
            }

            // 3. Draw Background Lanes
            rangeResult.whiteNotes.forEach { midiNote ->
                val geom = keyGeometries[midiNote] ?: return@forEach
                drawLine(
                    color = Color(0xFF141D2D).copy(alpha = 0.35f),
                    start = Offset(geom.left, 0f),
                    end = Offset(geom.left, canvasHeight),
                    strokeWidth = 1f
                )
            }

            // Darker shaded columns for black keys
            for (geom in keyGeometries.values) {
                if (!geom.isBlack) continue
                drawRect(
                    color = Color(0xFF060913).copy(alpha = 0.6f),
                    topLeft = Offset(geom.left, 0f),
                    size = Size(geom.width, canvasHeight)
                )
            }

            // 4. Draw Hit Reception Line at bottom (clean 2dp line)
            drawLine(
                color = PianoPrimary.copy(alpha = 0.85f),
                start = Offset(0f, hitLineY),
                end = Offset(canvasWidth, hitLineY),
                strokeWidth = 2.dp.toPx()
            )

            // 5. Select visible notes using interval binary search
            val minVisibleMs = currentPositionMs - 800L
            val maxVisibleMs = currentPositionMs + lookAhead.lookAheadMs + 1000L
            val visibleRange = windowSelector.getVisibleNoteRange(minVisibleMs, maxVisibleMs)

            if (!visibleRange.isEmpty()) {
                for (i in visibleRange) {
                    val note = sortedNotes[i]
                    val noteEndMs = note.startMs + note.durationMs
                    if (noteEndMs < minVisibleMs) continue
                    if (note.startMs > maxVisibleMs) break

                    val geom = keyGeometries[note.midiNote] ?: continue

                    val timeUntilHitMs = note.startMs - currentPositionMs
                    val headHeight = 12.dp.toPx()
                    val sustainHeight = (note.durationMs * pixelsPerMs).coerceAtLeast(0f)

                    val noteBottomY = hitLineY - (timeUntilHitMs * pixelsPerMs)
                    val noteTopY = noteBottomY - headHeight - sustainHeight

                    if (noteBottomY < 0f || noteTopY > canvasHeight) continue

                    // Identity-based feedback: ONLY the specific note/chord event that was evaluated turns green/red!
                    val isFeedbackTarget = (activeFeedback != null &&
                            activeFeedback.midiNote == note.midiNote &&
                            abs(activeFeedback.startMs - note.startMs) < 100L)

                    val baseColor = when {
                        isFeedbackTarget && activeFeedback!!.result == NoteResultType.CORRECT -> PianoSuccess
                        isFeedbackTarget && activeFeedback!!.result == NoteResultType.WRONG -> PianoError
                        isFeedbackTarget && activeFeedback!!.result == NoteResultType.MISSED -> PianoError.copy(alpha = 0.5f)
                        note.hand == HandMode.LEFT -> Color(0xFFF97316)
                        note.hand == HandMode.RIGHT -> Color(0xFF00B8D9)
                        else -> Color(0xFF94A3B8)
                    }

                    val isCurrentExpected = abs(note.startMs - currentPositionMs) < 600L && currentNoteIndex in notes.indices && notes[currentNoteIndex].startMs == note.startMs

                    val blockW = (geom.width * 0.62f).coerceIn(3.dp.toPx(), 14.dp.toPx())
                    val blockX = geom.left + (geom.width - blockW) / 2f

                    // Distance-based alpha: approaching notes are fully solid, distant notes slightly softer
                    val distanceAlpha = (1.0f - (timeUntilHitMs.toFloat() / lookAhead.lookAheadMs.toFloat()).coerceIn(0f, 0.35f)).coerceIn(0.7f, 1.0f)

                    if (sustainHeight > 1.dp.toPx()) {
                        drawRoundRect(
                            color = baseColor.copy(alpha = 0.32f * distanceAlpha),
                            topLeft = Offset(blockX + blockW * 0.22f, noteTopY),
                            size = Size(blockW * 0.56f, sustainHeight + 2.dp.toPx()),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                        )
                    }
                    val headTopY = noteBottomY - headHeight
                    drawRoundRect(
                        color = baseColor.copy(alpha = distanceAlpha),
                        topLeft = Offset(blockX, headTopY),
                        size = Size(blockW, headHeight),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )

                    // Target expected highlight stroke (soft white border on nearest upcoming chord)
                    if (isCurrentExpected) {
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.9f),
                            topLeft = Offset(blockX - 1f, headTopY - 1f),
                            size = Size(blockW + 2f, headHeight + 2f),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // 6. Note text label: only show when showNoteNames is enabled
                    if (showNoteNames && !isDenseMode && blockW >= 18.dp.toPx()) {
                        val noteName = NoteHelper.midiToNoteName(note.midiNote, namingMode)
                        textPaint.textSize = (blockW * 0.42f).coerceIn(9.dp.toPx(), 13.dp.toPx())

                        val textX = blockX + blockW / 2f
                        val textY = noteBottomY - 4.dp.toPx()

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
