package com.ian.pianotrainer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.music.NoteHelper
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.NoteNamingMode

@Composable
fun NoteBadge(
    midiNote: Int,
    modifier: Modifier = Modifier,
    fingerNumber: Int? = null,
    handMode: HandMode? = null,
    noteNamingMode: NoteNamingMode = NoteNamingMode.CDE,
    isHighlighted: Boolean = false,
    isCorrect: Boolean? = null
) {
    val noteName = NoteHelper.formatNoteName(midiNote, noteNamingMode)
    val isBlack = NoteHelper.isBlackKey(midiNote)

    val (bg, textColor) = when (isCorrect) {
        true -> PianoSuccess.copy(alpha = 0.2f) to PianoSuccess
        false -> PianoError.copy(alpha = 0.2f) to PianoError
        null -> if (isHighlighted) PianoPrimary to Color.White else PianoSurfaceVariant to PianoTextPrimary
    }

    Surface(
        modifier = modifier.testTag("note_badge_$midiNote"),
        shape = PianoShapes.small,
        color = bg,
        border = if (isHighlighted) androidx.compose.foundation.BorderStroke(2.dp, PianoPrimary) else null
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = noteName,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = textColor
            )
            if (fingerNumber != null && fingerNumber in 1..5) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(if (handMode == HandMode.LEFT) Color(0xFF8E44AD) else PianoPrimary, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "$fingerNumber",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White
                    )
                }
            }
        }
    }
}
