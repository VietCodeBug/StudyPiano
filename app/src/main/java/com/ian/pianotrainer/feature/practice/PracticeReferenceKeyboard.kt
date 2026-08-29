package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.music.MidiConstants
import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteNamingMode
import com.ian.pianotrainer.domain.model.NoteResultType

@Composable
fun PracticeReferenceKeyboard(
    modifier: Modifier = Modifier,
    rangeMode: KeyboardRangeMode = KeyboardRangeMode.FULL_88_KEYS,
    baseOctave: Int = 3,
    activePressedNotes: Set<Int> = emptySet(),
    targetNotes: List<KeyHighlight> = emptyList(),
    namingMode: NoteNamingMode = NoteNamingMode.CDE,
    lastResult: NoteResultType? = null,
    lastPlayedMidi: Int? = null,
    keyHeight: Dp = 68.dp,
    onKeyPressed: ((Int) -> Unit)? = null
) {
    val targetNotesMap = remember(targetNotes) {
        targetNotes.associateBy { it.midiNote }
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
                .pointerInput(onKeyPressed, rangeMode, baseOctave) {
                    if (onKeyPressed != null) {
                        detectTapGestures(
                            onPress = { offset ->
                                val range = PianoGeometryCalculator.computeRangeForMode(
                                    rangeMode,
                                    baseOctave,
                                    size.width.toFloat()
                                )
                                // Check black keys first (top 60% of height)
                                val blackKeyHeight = size.height * 0.62f
                                var tappedNote: Int? = null
                                if (offset.y <= blackKeyHeight) {
                                    for ((midi, geom) in range.geometries) {
                                        if (geom.isBlack && offset.x in geom.left..geom.right) {
                                            tappedNote = midi
                                            break
                                        }
                                    }
                                }
                                if (tappedNote == null) {
                                    for ((midi, geom) in range.geometries) {
                                        if (!geom.isBlack && offset.x in geom.left..geom.right) {
                                            tappedNote = midi
                                            break
                                        }
                                    }
                                }
                                tappedNote?.let { onKeyPressed(it) }
                            }
                        )
                    }
                }
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
                val isEvaluated = lastPlayedMidi == midiNote

                val fillColor = when {
                    isEvaluated && lastResult == NoteResultType.CORRECT -> PianoSuccess
                    isEvaluated && lastResult == NoteResultType.WRONG -> PianoError
                    isPressed && targetHighlight?.hand == HandMode.LEFT -> PianoAccent // Orange for Left hand
                    isPressed -> PianoPrimary // Cyan/Blue for Right hand
                    isTarget -> Color(0xFFE2E8F0) // Slight warm glow for target
                    else -> Color(0xFFF8FAFC) // Off-white key
                }

                // White key fill
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(geom.left + 0.5f, 0f),
                    size = Size(geom.width - 1f, totalHeight),
                    cornerRadius = CornerRadius(0f, 0f)
                )

                // White key outline/divider
                drawLine(
                    color = Color(0xFFCBD5E1),
                    start = Offset(geom.right, 0f),
                    end = Offset(geom.right, totalHeight),
                    strokeWidth = 1f
                )

                // Target outline glow if target
                if (isTarget && !isPressed) {
                    val glowColor = if (targetHighlight?.hand == HandMode.LEFT) PianoAccent else PianoPrimary
                    drawRect(
                        color = glowColor.copy(alpha = 0.35f),
                        topLeft = Offset(geom.left, 0f),
                        size = Size(geom.width, totalHeight)
                    )
                    drawRect(
                        color = glowColor,
                        topLeft = Offset(geom.left, totalHeight - 6.dp.toPx()),
                        size = Size(geom.width, 6.dp.toPx())
                    )
                }

                // Note label for C keys or when lane is wide enough
                val isCKey = (midiNote % 12) == 0
                if ((isCKey || geom.width > 22.dp.toPx()) && geom.width > 12.dp.toPx()) {
                    val label = when {
                        isCKey -> "C${(midiNote / 12) - 1}"
                        else -> com.ian.pianotrainer.core.music.NoteHelper.formatNoteName(midiNote, namingMode)
                    }
                    val textColor = if (isPressed || isEvaluated) android.graphics.Color.WHITE else android.graphics.Color.DKGRAY
                    val paint = android.graphics.Paint().apply {
                        color = textColor
                        textSize = (geom.width * 0.38f).coerceIn(8.dp.toPx(), 11.dp.toPx())
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                        typeface = android.graphics.Typeface.DEFAULT_BOLD
                    }
                    drawContext.canvas.nativeCanvas.drawText(
                        label,
                        geom.centerX,
                        totalHeight - 4.dp.toPx(),
                        paint
                    )
                }
            }

            // 2. Draw Black Keys on top
            for ((midiNote, geom) in range.geometries) {
                if (!geom.isBlack) continue

                val isPressed = activePressedNotes.contains(midiNote)
                val targetHighlight = targetNotesMap[midiNote]
                val isTarget = targetHighlight != null
                val isEvaluated = lastPlayedMidi == midiNote

                val fillColor = when {
                    isEvaluated && lastResult == NoteResultType.CORRECT -> PianoSuccess
                    isEvaluated && lastResult == NoteResultType.WRONG -> PianoError
                    isPressed && targetHighlight?.hand == HandMode.LEFT -> PianoAccent
                    isPressed -> PianoPrimary
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
                    val glowColor = if (targetHighlight?.hand == HandMode.LEFT) PianoAccent else PianoPrimary
                    drawRect(
                        color = glowColor,
                        topLeft = Offset(geom.left, blackKeyHeight - 5.dp.toPx()),
                        size = Size(geom.width, 5.dp.toPx())
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
