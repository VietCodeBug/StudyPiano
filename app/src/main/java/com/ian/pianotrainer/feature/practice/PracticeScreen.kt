package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.DisplayModeSelector
import com.ian.pianotrainer.core.ui.HandModeSelector
import com.ian.pianotrainer.core.ui.PracticeModeSelector
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.core.ui.TempoControl
import com.ian.pianotrainer.domain.model.HandMode

@Composable
fun PracticeScreen(
    viewModel: PracticeViewModel,
    onStartPractice: (title: String, sourceType: String, sourceId: String, handMode: String, displayMode: String, bpm: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.title_practice))
        },
        bottomBar = {
            Surface(
                color = PianoSurface,
                tonalElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    PrimaryButton(
                        text = stringResource(R.string.practice_start_button),
                        onClick = {
                            onStartPractice(
                                "Bài luyện tự chọn",
                                "CUSTOM_PRACTICE",
                                "custom_drill",
                                uiState.selectedHand.name,
                                uiState.selectedDisplayMode.name,
                                uiState.bpm
                            )
                        },
                        tag = "start_custom_practice_button"
                    )
                }
            }
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("practice_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Practice Mode Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.practice_mode_label),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary
                        )
                        PracticeModeSelector(
                            selectedMode = uiState.selectedMode,
                            onModeSelected = viewModel::setPracticeMode
                        )
                    }
                }
            }

            // 2. Hand Selection Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.practice_hand_label),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary
                        )
                        HandModeSelector(
                            selectedHand = uiState.selectedHand,
                            onHandSelected = viewModel::setHandMode
                        )
                    }
                }
            }

            // 3. Display Mode Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.practice_display_mode_label),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary
                        )
                        DisplayModeSelector(
                            selectedDisplayMode = uiState.selectedDisplayMode,
                            onDisplayModeSelected = viewModel::setDisplayMode
                        )
                    }
                }
            }

            // 4. Tempo / Metronome Control Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = PianoShapes.medium,
                    colors = CardDefaults.cardColors(containerColor = PianoSurface),
                    border = BorderStroke(1.dp, PianoOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TempoControl(
                            bpm = uiState.bpm,
                            onBpmChanged = viewModel::setBpm
                        )
                    }
                }
            }

            // 5. Quick Practice Drills
            item {
                SectionHeader(title = stringResource(R.string.practice_quick_drills))
            }

            items(uiState.quickDrills, key = { it.id }) { drill ->
                QuickDrillCard(
                    drill = drill,
                    onClick = {
                        onStartPractice(
                            drill.title,
                            "QUICK_DRILL",
                            drill.id,
                            drill.handMode.name,
                            uiState.selectedDisplayMode.name,
                            drill.defaultBpm
                        )
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun QuickDrillCard(
    drill: PracticeQuickDrill,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onClick() }
            .testTag("quick_drill_${drill.id}"),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = CircleShape,
                    color = PianoSurfaceVariant,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = PianoPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = drill.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                    Text(
                        text = "${drill.description} • ${drill.defaultBpm} BPM",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = PianoTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
