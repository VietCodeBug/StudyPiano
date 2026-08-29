package com.ian.pianotrainer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.domain.model.DisplayMode
import com.ian.pianotrainer.domain.model.HandMode
import com.ian.pianotrainer.domain.model.PracticeMode

@Composable
fun HandModeSelector(
    selectedHand: HandMode,
    onHandSelected: (HandMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PianoShapes.medium,
        color = PianoSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            HandMode.entries.forEach { mode ->
                val isSelected = (mode == selectedHand)
                val label = when (mode) {
                    HandMode.RIGHT -> stringResource(R.string.hand_right)
                    HandMode.LEFT -> stringResource(R.string.hand_left)
                    HandMode.BOTH -> stringResource(R.string.hand_both)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(PianoShapes.small)
                        .background(if (isSelected) PianoPrimary else Color.Transparent)
                        .clickable { onHandSelected(mode) }
                        .testTag("hand_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) Color.White else PianoTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun PracticeModeSelector(
    selectedMode: PracticeMode,
    onModeSelected: (PracticeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PianoShapes.medium,
        color = PianoSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            PracticeMode.entries.forEach { mode ->
                val isSelected = (mode == selectedMode)
                val label = when (mode) {
                    PracticeMode.WAIT_FOR_NOTE -> stringResource(R.string.mode_wait_for_note)
                    PracticeMode.RHYTHM -> stringResource(R.string.mode_in_tempo)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(PianoShapes.small)
                        .background(if (isSelected) PianoPrimary else Color.Transparent)
                        .clickable { onModeSelected(mode) }
                        .testTag("practice_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) Color.White else PianoTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun DisplayModeSelector(
    selectedDisplayMode: DisplayMode,
    onDisplayModeSelected: (DisplayMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = PianoShapes.medium,
        color = PianoSurfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            DisplayMode.entries.filter { it != DisplayMode.SHEET_MUSIC }.forEach { mode ->
                val isSelected = (mode == selectedDisplayMode)
                val label = when (mode) {
                    DisplayMode.FALLING_NOTES -> stringResource(R.string.display_falling_notes)
                    DisplayMode.SHEET_MUSIC -> stringResource(R.string.display_sheet_music)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(PianoShapes.small)
                        .background(if (isSelected) PianoPrimary else Color.Transparent)
                        .clickable { onDisplayModeSelected(mode) }
                        .testTag("display_mode_${mode.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = if (isSelected) Color.White else PianoTextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun TempoControl(
    bpm: Int,
    onBpmChanged: (Int) -> Unit,
    modifier: Modifier = Modifier,
    minBpm: Int = 40,
    maxBpm: Int = 140
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_tempo),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = PianoTextPrimary
            )
            Text(
                text = "$bpm BPM",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PianoPrimary
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { if (bpm > minBpm) onBpmChanged(bpm - 5) },
                modifier = Modifier
                    .size(40.dp)
                    .background(PianoSurfaceVariant, shape = CircleShape)
                    .testTag("tempo_decrease_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = stringResource(R.string.action_decrease),
                    tint = PianoTextPrimary
                )
            }

            Slider(
                value = bpm.toFloat(),
                onValueChange = { onBpmChanged(it.toInt()) },
                valueRange = minBpm.toFloat()..maxBpm.toFloat(),
                steps = (maxBpm - minBpm) / 5 - 1,
                modifier = Modifier
                    .weight(1f)
                    .testTag("tempo_slider"),
                colors = SliderDefaults.colors(
                    thumbColor = PianoPrimary,
                    activeTrackColor = PianoPrimary,
                    inactiveTrackColor = PianoOutline
                )
            )

            IconButton(
                onClick = { if (bpm < maxBpm) onBpmChanged(bpm + 5) },
                modifier = Modifier
                    .size(40.dp)
                    .background(PianoSurfaceVariant, shape = CircleShape)
                    .testTag("tempo_increase_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = stringResource(R.string.action_increase),
                    tint = PianoTextPrimary
                )
            }
        }
    }
}
