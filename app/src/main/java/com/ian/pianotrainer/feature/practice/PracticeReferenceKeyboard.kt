package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.NoteResultType
import com.ian.pianotrainer.domain.model.VisualNoteFeedback



@Composable
fun PracticeReferenceKeyboard(
    modifier: Modifier = Modifier,
    rangeMode: KeyboardRangeMode = KeyboardRangeMode.FULL_88_KEYS,
    baseOctave: Int = 3,
    activePressedNotes: Set<Int> = emptySet(),
    targetNotes: List<KeyHighlight> = emptyList(),
    namingMode: NoteNamingMode = NoteNamingMode.CDE,
    activeFeedback: VisualNoteFeedback? = null,
    keyHeight: Dp = 56.dp,
    enableInteraction: Boolean = false,
    onKeyPressed: ((Int, Int) -> Unit)? = null,
    onKeyReleased: ((Int) -> Unit)? = null
) {
    val targetNotesMap = remember(targetNotes) {
        targetNotes.associateBy { it.midiNote }
    }

    val labelPaint = remember {
        android.graphics.Paint().apply {
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(keyHeight)
            .background(Color(0xFF0F172A))
            .testTag("practice_reference_keyboard")
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (enableInteraction && onKeyPressed != null && onKeyReleased != null) {
                        Modifier.pointerInput(rangeMode, baseOctave) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                val range = PianoGeometryCalculator.computeRangeForMode(
                                    rangeMode,
                                    baseOctave,
                                    size.width.toFloat()
                                )
                                val blackKeyHeight = size.height * 0.62f
                                var activeMidi: Int? = null

                                if (down.position.y <= blackKeyHeight) {
                                    for ((midi, geom) in range.geometries) {
                                        if (geom.isBlack && down.position.x in geom.left..geom.right) {
                                            activeMidi = midi
                                            break
                                        }
                                    }
                                }
                                if (activeMidi == null) {
                                    for ((midi, geom) in range.geometries) {
                                        if (!geom.isBlack && down.position.x in geom.left..geom.right) {
                                            activeMidi = midi
                                            break
                                        }
                                    }
                                }

                                activeMidi?.let { midi ->
                                    onKeyPressed(midi, 80)
                                    // Wait until pointer up or gesture cancellation
                                    do {
                                        val event = awaitPointerEvent()
                                        val isUpOrCancelled = event.changes.all { !it.pressed }
                                    } while (!isUpOrCancelled)
                                    onKeyReleased(midi)
                                }
                            }
                        }
                    } else Modifier
                )
        ) {
            val totalWidth = size.width
            val totalHeight = size.height
            val blackKeyHeight = totalHeight * 0.62f

            val range = PianoGeometryCalculator.computeRangeForMode(
                rangeMode,
                baseOctave,
                totalWidth
            )

            // 1. Draw White Keys Backgrounds
            for ((midiNote, geom) in range.geometries) {
                if (geom.isBlack) continue

                val isPressed = activePressedNotes.contains(midiNote)
                val targetHighlight = targetNotesMap[midiNote]
                val isTarget = targetHighlight != null
                val isEvaluated = activeFeedback != null && activeFeedback.midiNote == midiNote

                val fillColor = when {
                    isEvaluated && activeFeedback?.result == NoteResultType.CORRECT -> PianoSuccess
                    isEvaluated && (activeFeedback?.result == NoteResultType.WRONG || activeFeedback?.result == NoteResultType.MISSED) -> PianoError
                    isPressed && targetHighlight?.hand == HandMode.LEFT -> Color(0xFFF97316) // Orange for Left hand
                    isPressed -> Color(0xFF00E5FF) // Cyan/Blue for Right hand
                    isTarget -> Color(0xFFE2E8F0) // Clean off-white
                    else -> Color(0xFFF8FAFC) // Off-white key
                }

                // White key fill
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(geom.left + 0.5f, 0f),
                    size = Size(geom.width - 1f, totalHeight),
                    cornerRadius = CornerRadius(0f, 0f)
                )

                // White key divider line
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(geom.right, 0f),
                    end = Offset(geom.right, totalHeight),
                    strokeWidth = 1f
                )

                // Target indicator on key
                if (isTarget && !isPressed) {
                    val targetColor = if (targetHighlight?.hand == HandMode.LEFT) Color(0xFFF97316) else Color(0xFF00E5FF)
                    drawRect(
                        color = targetColor.copy(alpha = 0.25f),
                        topLeft = Offset(geom.left, 0f),
                        size = Size(geom.width, totalHeight)
                    )
                    drawRect(
                        color = targetColor,
                        topLeft = Offset(geom.left, totalHeight - 4.dp.toPx()),
                        size = Size(geom.width, 4.dp.toPx())
                    )
                }

                // Subtle C octave markers (e.g. C1..C8)
                val isCKey = (midiNote % 12) == 0
                if (isCKey) {
                    val octaveNum = (midiNote / 12) - 1
                    val label = "C$octaveNum"
                    labelPaint.color = if (isPressed) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#64748B")
                    labelPaint.textSize = (geom.width * 0.45f).coerceIn(8.dp.toPx(), 10.dp.toPx())
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        geom.centerX,
                        totalHeight - 4.dp.toPx(),
                        labelPaint
                    )
                }
            }

            // 2. Draw Black Keys on top
            for ((midiNote, geom) in range.geometries) {
                if (!geom.isBlack) continue

                val isPressed = activePressedNotes.contains(midiNote)
                val targetHighlight = targetNotesMap[midiNote]
                val isTarget = targetHighlight != null
                val isEvaluated = activeFeedback != null && activeFeedback.midiNote == midiNote

                val fillColor = when {
                    isEvaluated && activeFeedback?.result == NoteResultType.CORRECT -> PianoSuccess
                    isEvaluated && (activeFeedback?.result == NoteResultType.WRONG || activeFeedback?.result == NoteResultType.MISSED) -> PianoError
                    isPressed && targetHighlight?.hand == HandMode.LEFT -> Color(0xFFF97316) // Orange for Left hand
                    isPressed -> Color(0xFF00E5FF) // Cyan/Blue for Right hand
                    else -> Color(0xFF0F172A) // Sleek slate black
                }

                // Black key body
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(geom.left, 0f),
                    size = Size(geom.width, blackKeyHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // 3D subtle bottom edge
                if (!isPressed && !isEvaluated) {
                    drawRect(
                        color = Color(0xFF334155),
                        topLeft = Offset(geom.left, blackKeyHeight - 3.dp.toPx()),
                        size = Size(geom.width, 3.dp.toPx())
                    )
                }

                // Target indicator on black key
                if (isTarget && !isPressed) {
                    val targetColor = if (targetHighlight?.hand == HandMode.LEFT) Color(0xFFF97316) else Color(0xFF00E5FF)
                    drawRect(
                        color = targetColor,
                        topLeft = Offset(geom.left, blackKeyHeight - 4.dp.toPx()),
                        size = Size(geom.width, 4.dp.toPx())
                    )
                }
            }

            // 3. Top red/accent keyboard felt strip
            drawLine(
                color = Color(0xFFDC2626), // Classic piano red felt strip
                start = Offset(0f, 0f),
                end = Offset(totalWidth, 0f),
                strokeWidth = 2.dp.toPx()
            )
        }
    }
}
