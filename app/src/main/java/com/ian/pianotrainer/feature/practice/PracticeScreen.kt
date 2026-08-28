package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
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
import com.ian.pianotrainer.domain.model.FingerExercise
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
                    val firstExercise = uiState.exercises.firstOrNull()
                    PrimaryButton(
                        text = if (firstExercise != null) "Bắt đầu: ${firstExercise.title}" else stringResource(R.string.practice_start_button),
                        onClick = {
                            if (firstExercise != null) {
                                onStartPractice(
                                    firstExercise.title,
                                    "EXERCISE",
                                    firstExercise.id,
                                    firstExercise.handMode.name,
                                    uiState.selectedDisplayMode.name,
                                    firstExercise.recommendedBpm
                                )
                            } else {
                                onStartPractice(
                                    "Bài luyện tự chọn",
                                    "CUSTOM_PRACTICE",
                                    "custom_drill",
                                    uiState.selectedHand.name,
                                    uiState.selectedDisplayMode.name,
                                    uiState.bpm
                                )
                            }
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

            // 5. Category Chips for Finger Exercises
            item {
                SectionHeader(title = "Danh mục bài luyện ngón")
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val categories = listOf(
                        "ALL" to "Tất cả",
                        "HANON" to "Hanon độc lập ngón",
                        "ARPEGGIO" to "Hợp âm rải (Arpeggio)",
                        "SCALE" to "Âm giai (Scale)",
                        "OCTAVE" to "Quãng 8 (Octave)",
                        "CHORD" to "Hợp âm & Chuyển ngón"
                    )
                    categories.forEach { (catKey, catLabel) ->
                        FilterChip(
                            selected = uiState.selectedCategory == catKey,
                            onClick = { viewModel.setCategory(catKey) },
                            label = { Text(catLabel) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PianoPrimary,
                                selectedLabelColor = PianoBackground
                            )
                        )
                    }
                }
            }

            // 6. Loaded Real Finger Exercises
            items(uiState.exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onClick = {
                        onStartPractice(
                            exercise.title,
                            "EXERCISE",
                            exercise.id,
                            exercise.handMode.name,
                            uiState.selectedDisplayMode.name,
                            exercise.recommendedBpm
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
private fun ExerciseCard(
    exercise: FingerExercise,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onClick() }
            .testTag("exercise_${exercise.id}"),
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
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = PianoAccent,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = exercise.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                    Text(
                        text = "${exercise.description} • ${exercise.recommendedBpm} BPM • ${exercise.difficulty}",
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
