package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ian.pianotrainer.core.designsystem.PianoActiveKey
import com.ian.pianotrainer.core.designsystem.PianoBlackKey
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoWhiteKey
import com.ian.pianotrainer.core.music.MidiConstants
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.domain.model.HandMode
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
    initialOctaveOffset: Int = 3, // Start at Octave 3 (C3 = 48)
    keyHeight: Dp = 160.dp
) {
    var startOctave by remember { mutableIntStateOf(initialOctaveOffset) } // Octaves: 1..7 (3 = C3 to B4 or C5)
    val octavesToShow = 2
    val startMidiNote = (startOctave + 1) * 12 // e.g. Octave 3 starts at C3 (48)
    val endMidiNote = (startOctave + 1 + octavesToShow) * 12 // e.g. C5 (72)

    val targetNotesMap = remember(targetNotes) {
        targetNotes.associateBy { it.midiNote }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("piano_keyboard_view")
    ) {
        // Octave range shift controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { if (startOctave > 1) startOctave-- },
                    enabled = startOctave > 1,
                    modifier = Modifier.size(36.dp).testTag("octave_down_button")
                ) {
                    Icon(imageVector = Icons.Default.ChevronLeft, contentDescription = "Quãng 8 thấp hơn")
                }
                Text(
                    text = "Quãng: C$startOctave - C${startOctave + octavesToShow}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = PianoPrimaryDark
                )
                IconButton(
                    onClick = { if (startOctave < 6) startOctave++ },
                    enabled = startOctave < 6,
                    modifier = Modifier.size(36.dp).testTag("octave_up_button")
                ) {
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = "Quãng 8 cao hơn")
                }
            }

            // Middle C reset button
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = PianoSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures { startOctave = 3 }
                    }
                    .testTag("middle_c_focus_button")
            ) {
                Text(
                    text = "Về Đô 4 (C4)",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = PianoPrimaryDark,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        // Keys Container
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(keyHeight)
                .background(Color(0xFF0F172A), RoundedCornerShape(12.dp))
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            val totalWhiteKeys = (startMidiNote..endMidiNote).count { !MidiConstants.isBlackKey(it) }
            val whiteKeyWidth = (maxWidth / totalWhiteKeys.coerceAtLeast(1))
            val blackKeyWidth = whiteKeyWidth * 0.65f
            val blackKeyHeight = keyHeight * 0.60f

            val whiteNotes = (startMidiNote..endMidiNote).filter { !MidiConstants.isBlackKey(it) }

            // 1. Draw White Keys
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                whiteNotes.forEach { midiNote ->
                    val isPressed = midiNote in activeNotes
                    val targetHighlight = targetNotesMap[midiNote]
                    val isMiddleC = (midiNote == MidiConstants.MIDDLE_C_MIDI_NOTE)

                    val keyBg = when {
                        isPressed -> PianoActiveKey
                        targetHighlight != null -> targetHighlight.color.copy(alpha = 0.35f)
                        isMiddleC -> Color(0xFFEFF6FF)
                        else -> PianoWhiteKey
                    }

                    Box(
                        modifier = Modifier
                            .width(whiteKeyWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 1.dp)
                            .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                            .background(keyBg)
                            .border(
                                width = if (isMiddleC || targetHighlight != null) 2.dp else 1.dp,
                                color = if (targetHighlight != null) targetHighlight.color else if (isMiddleC) PianoPrimary else Color(0xFFCBD5E1),
                                shape = RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp)
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
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            if (targetHighlight?.fingerNumber != null) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .background(
                                            if (targetHighlight.hand == HandMode.LEFT) Color(0xFF8E44AD) else PianoPrimary,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "${targetHighlight.fingerNumber}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                            }

                            if (showNoteLabels) {
                                val label = NoteHelper.formatNoteName(midiNote, noteNamingMode)
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = if (isMiddleC) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = if (isMiddleC) PianoPrimaryDark else Color(0xFF475569)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Draw Black Keys positioned accurately above white keys
            var currentWhiteIndex = 0
            (startMidiNote..endMidiNote).forEach { midiNote ->
                if (MidiConstants.isBlackKey(midiNote)) {
                    val isPressed = midiNote in activeNotes
                    val targetHighlight = targetNotesMap[midiNote]

                    // Calculate offset: it sits on boundary between white key (currentWhiteIndex - 1) and (currentWhiteIndex)
                    val offsetX = (whiteKeyWidth * currentWhiteIndex) - (blackKeyWidth / 2)

                    val keyBg = when {
                        isPressed -> PianoActiveKey
                        targetHighlight != null -> targetHighlight.color
                        else -> PianoBlackKey
                    }

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX)
                            .width(blackKeyWidth)
                            .height(blackKeyHeight)
                            .zIndex(2f)
                            .shadow(4.dp, RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .clip(RoundedCornerShape(bottomStart = 4.dp, bottomEnd = 4.dp))
                            .background(keyBg)
                            .border(
                                width = if (targetHighlight != null) 2.dp else 1.dp,
                                color = if (targetHighlight != null) targetHighlight.color else Color(0xFF0F172A),
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
                        if (targetHighlight?.fingerNumber != null) {
                            Box(
                                modifier = Modifier
                                    .padding(bottom = 6.dp)
                                    .size(18.dp)
                                    .background(
                                        if (targetHighlight.hand == HandMode.LEFT) Color(0xFF8E44AD) else PianoPrimary,
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${targetHighlight.fingerNumber}",
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    currentWhiteIndex++
                }
            }
        }
    }
}
