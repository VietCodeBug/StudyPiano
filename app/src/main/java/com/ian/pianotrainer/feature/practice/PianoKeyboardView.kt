package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ian.pianotrainer.core.designsystem.PianoActiveKey
import com.ian.pianotrainer.core.designsystem.PianoBlackKey
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoWhiteKey
import com.ian.pianotrainer.core.music.MidiConstants
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.KeyboardRangeMode
import com.ian.pianotrainer.domain.model.NoteNamingMode

data class KeyHighlight(
    val midiNote: Int,
    val color: Color = PianoPrimary,
    val label: String? = null,
    val fingerNumber: Int? = null,
    val hand: HandMode? = null
)

@Composable
fun PianoKeyboardView(
    onKeyPressed: (Int) -> Unit,
    onKeyReleased: (Int) -> Unit,
    modifier: Modifier = Modifier,
    activeNotes: Set<Int> = emptySet(),
    targetNotes: List<KeyHighlight> = emptyList(),
    noteNamingMode: NoteNamingMode = NoteNamingMode.CDE,
    showNoteLabels: Boolean = true,
    initialOctaveOffset: Int = 3,
    rangeMode: KeyboardRangeMode = KeyboardRangeMode.TWO_OCTAVES,
    onOctaveChange: ((Int) -> Unit)? = null,
    onRangeModeChange: ((KeyboardRangeMode) -> Unit)? = null,
    upcomingNotes: List<Int> = emptyList(),
    showRangeBar: Boolean = false,
    keyHeight: Dp = 120.dp
) {
    val targetNotesMap = remember(targetNotes) {
        targetNotes.associateBy { it.midiNote }
    }

    val density = LocalDensity.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("piano_keyboard_view")
    ) {
        // Optional quick range / octave bar
        if (showRangeBar) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            if (initialOctaveOffset > 1) {
                                onOctaveChange?.invoke(initialOctaveOffset - 1)
                            }
                        },
                        enabled = initialOctaveOffset > 1,
                        modifier = Modifier.size(32.dp).testTag("octave_down_button")
                    ) {
                        Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Quãng 8 thấp hơn")
                    }
                    Text(
                        text = "Quãng C$initialOctaveOffset",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = PianoPrimaryDark
                    )
                    IconButton(
                        onClick = {
                            if (initialOctaveOffset < 6) {
                                onOctaveChange?.invoke(initialOctaveOffset + 1)
                            }
                        },
                        enabled = initialOctaveOffset < 6,
                        modifier = Modifier.size(32.dp).testTag("octave_up_button")
                    ) {
                        Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Quãng 8 cao hơn")
                    }
                }

                // Middle C focus
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PianoSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .pointerInput(Unit) {
                            detectTapGestures { onOctaveChange?.invoke(3) }
                        }
                        .testTag("middle_c_focus_button")
                ) {
                    Text(
                        text = "Đô 4 (C4)",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoPrimaryDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Keys Canvas/Layout Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight)
                .background(Color(0xFF0F172A), RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .padding(horizontal = 2.dp, vertical = 2.dp)
        ) {
            val totalWidthPx = with(density) { maxWidth.toPx() }
            val rangeResult = remember(rangeMode, initialOctaveOffset, totalWidthPx, upcomingNotes) {
                PianoGeometryCalculator.computeRangeForMode(
                    mode = rangeMode,
                    baseOctave = initialOctaveOffset,
                    totalWidth = totalWidthPx,
                    upcomingMidiNotes = upcomingNotes
                )
            }

            val geometries = rangeResult.geometries
            val blackKeyHeightPx = with(density) { keyHeight.toPx() } * 0.62f
            val isDenseMode = (rangeMode == KeyboardRangeMode.SIX_OCTAVES || rangeMode == KeyboardRangeMode.FULL_88_KEYS)

            // 1. Draw White Keys
            Box(modifier = Modifier.fillMaxSize()) {
                rangeResult.whiteNotes.forEach { midiNote ->
                    val geom = geometries[midiNote] ?: return@forEach
                    val isPressed = midiNote in activeNotes
                    val targetHighlight = targetNotesMap[midiNote]
                    val isMiddleC = (midiNote == MidiConstants.MIDDLE_C_MIDI_NOTE)
                    val isCNote = (midiNote % 12 == 0)

                    val keyBg = when {
                        isPressed -> PianoActiveKey
                        targetHighlight != null -> targetHighlight.color.copy(alpha = 0.4f)
                        isMiddleC -> Color(0xFFEFF6FF)
                        else -> PianoWhiteKey
                    }

                    val leftDp = with(density) { geom.left.toDp() }
                    val widthDp = with(density) { geom.width.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = leftDp)
                            .width(widthDp)
                            .fillMaxHeight()
                            .padding(horizontal = 0.5.dp)
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(keyBg)
                            .border(
                                width = if (isMiddleC || targetHighlight != null) 2.dp else 1.dp,
                                color = if (targetHighlight != null) targetHighlight.color else if (isMiddleC) PianoPrimary else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp)
                            )
                            .pointerInput(midiNote) {
                                detectTapGestures(
                                    onPress = {
                                        onKeyPressed(midiNote)
                                        tryAwaitRelease()
                                        onKeyReleased(midiNote)
                                    }
                                )
                            }
                            .testTag("piano_key_$midiNote"),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 3.dp)
                        ) {
                            if (targetHighlight?.fingerNumber != null) {
                                val circleSize = if (isDenseMode) 14.dp else 18.dp
                                Box(
                                    modifier = Modifier
                                        .size(circleSize)
                                        .background(
                                            if (targetHighlight.hand == HandMode.LEFT) Color(0xFFF97316) else PianoPrimary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${targetHighlight.fingerNumber}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = if (isDenseMode) 8.sp else 10.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(1.dp))
                            }

                            // Show label if enabled and fits
                            val shouldShowLabel = showNoteLabels && (
                                !isDenseMode || isCNote || isPressed || targetHighlight != null
                            )
                            if (shouldShowLabel) {
                                val label = NoteHelper.formatNoteName(midiNote, noteNamingMode)
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = if (isDenseMode) 7.sp else 9.sp,
                                        fontWeight = if (isMiddleC || isPressed) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isMiddleC) PianoPrimaryDark else if (isPressed) Color.White else Color(0xFF475569),
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            // 2. Draw Black Keys
            Box(modifier = Modifier.fillMaxSize()) {
                geometries.values.filter { it.isBlack }.forEach { geom ->
                    val midiNote = geom.midiNote
                    val isPressed = midiNote in activeNotes
                    val targetHighlight = targetNotesMap[midiNote]

                    val keyBg = when {
                        isPressed -> PianoActiveKey
                        targetHighlight != null -> targetHighlight.color
                        else -> PianoBlackKey
                    }

                    val leftDp = with(density) { geom.left.toDp() }
                    val widthDp = with(density) { geom.width.toDp() }
                    val heightDp = with(density) { blackKeyHeightPx.toDp() }

                    Box(
                        modifier = Modifier
                            .offset(x = leftDp)
                            .width(widthDp)
                            .height(heightDp)
                            .zIndex(2f)
                            .shadow(3.dp, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            .background(keyBg)
                            .border(
                                width = if (targetHighlight != null) 2.dp else 1.dp,
                                color = if (targetHighlight != null) targetHighlight.color else Color(0xFF0F172A),
                                shape = RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)
                            )
                            .pointerInput(midiNote) {
                                detectTapGestures(
                                    onPress = {
                                        onKeyPressed(midiNote)
                                        tryAwaitRelease()
                                        onKeyReleased(midiNote)
                                    }
                                )
                            }
                            .testTag("piano_key_$midiNote"),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        if (targetHighlight?.fingerNumber != null) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 3.dp)
                                    .size(if (isDenseMode) 12.dp else 16.dp)
                                    .background(
                                        if (targetHighlight.hand == HandMode.LEFT) Color(0xFFF97316) else PianoPrimary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${targetHighlight.fingerNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = if (isDenseMode) 7.sp else 9.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
