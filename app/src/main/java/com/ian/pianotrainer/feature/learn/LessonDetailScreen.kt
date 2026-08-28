package com.ian.pianotrainer.feature.learn

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
import com.ian.pianotrainer.core.designsystem.PianoPrimaryDark
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.EmptyState
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.core.ui.NoteBadge
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.domain.model.HandMode

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonDetailScreen(
    viewModel: LessonDetailViewModel,
    onBackClick: () -> Unit,
    onStartPractice: (lessonTitle: String, sourceId: String, handMode: String, bpm: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val lesson = uiState.lesson

    Scaffold(
        topBar = {
            AppTopBar(
                title = lesson?.title ?: "Bài học",
                onBackClick = onBackClick
            )
        },
        bottomBar = {
            if (lesson != null) {
                Surface(
                    color = PianoSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        PrimaryButton(
                            text = "Bắt đầu luyện tập bài học",
                            onClick = {
                                onStartPractice(
                                    lesson.title,
                                    lesson.id,
                                    lesson.handMode.name,
                                    lesson.exercise?.defaultBpm ?: 60
                                )
                            },
                            tag = "start_lesson_practice_button"
                        )
                    }
                }
            }
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("lesson_detail_screen")
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.padding(innerPadding))
            }
            lesson == null -> {
                EmptyState(
                    title = "Không tìm thấy bài học",
                    description = "Dữ liệu bài học không tồn tại hoặc đã bị xóa.",
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.large,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Timer,
                                            contentDescription = null,
                                            tint = PianoTextSecondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${lesson.estimatedDurationMinutes} phút",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = PianoTextSecondary
                                        )
                                    }
                                    if (lesson.isCompleted) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = PianoSuccess,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Đã hoàn thành",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                                color = PianoSuccess
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = lesson.title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = PianoTextPrimary
                                )

                                Text(
                                    text = lesson.objective,
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = PianoPrimary
                                )
                            }
                        }
                    }

                    // Content & Instructions
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
                                    text = "Hướng dẫn chi tiết",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextPrimary
                                )
                                Text(
                                    text = lesson.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PianoTextPrimary
                                )
                            }
                        }
                    }

                    // Target Notes
                    if (lesson.targetMidiNotes.isNotEmpty()) {
                        item {
                            SectionHeader(title = "Nốt trọng tâm trong bài")
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                lesson.targetMidiNotes.forEach { note ->
                                    NoteBadge(
                                        midiNote = note,
                                        noteNamingMode = uiState.userSettings.noteNamingMode,
                                        isHighlighted = true
                                    )
                                }
                            }
                        }
                    }

                    // Hand Requirement
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = PianoSurfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PanTool,
                                    contentDescription = null,
                                    tint = PianoPrimaryDark,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Tay thực hiện",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                    Text(
                                        text = when (lesson.handMode) {
                                            HandMode.RIGHT -> "Tay phải (Khoá Sol)"
                                            HandMode.LEFT -> "Tay trái (Khoá Fa)"
                                            HandMode.BOTH -> "Phối hợp cả hai tay"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }
                        }
                    }

                    // Extra bottom space for floating bar
                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }
}
