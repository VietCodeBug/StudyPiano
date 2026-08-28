package com.ian.pianotrainer.feature.progress

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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Speed
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoGold
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.EmptyState
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.core.ui.StatCard
import com.ian.pianotrainer.domain.model.PracticeSession
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProgressScreen(
    viewModel: ProgressViewModel,
    onSessionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = uiState.summary

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.title_progress))
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("progress_screen")
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Stat Grid (2x2)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = stringResource(R.string.progress_total_practice_time),
                                value = "${summary.totalPracticeTimeMinutes} phút",
                                icon = Icons.Default.Timer,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = stringResource(R.string.progress_streak_days),
                                value = "${summary.currentStreakDays} ngày",
                                icon = Icons.Default.LocalFireDepartment,
                                iconTint = PianoGold,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            StatCard(
                                title = stringResource(R.string.progress_average_accuracy),
                                value = "%.1f%%".format(summary.averageAccuracy),
                                icon = Icons.Default.CheckCircle,
                                iconTint = PianoSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = stringResource(R.string.progress_completed_lessons),
                                value = "${summary.completedLessonsCount} bài",
                                icon = Icons.Default.MenuBook,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 2. Recent Practice Sessions Header
                item {
                    SectionHeader(title = stringResource(R.string.progress_recent_history))
                }

                // 3. History List or Empty
                if (uiState.recentSessions.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Chưa có lượt luyện tập nào được ghi lại.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PianoTextSecondary
                                )
                            }
                        }
                    }
                } else {
                    items(uiState.recentSessions, key = { it.id }) { session ->
                        PracticeSessionItemCard(
                            session = session,
                            onClick = { onSessionClick(session.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeSessionItemCard(
    session: PracticeSession,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(session.startedAt))
    val durationSeconds = (session.durationMs / 1000).coerceAtLeast(1)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onClick() }
            .testTag("session_card_${session.id}"),
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (session.sourceType) {
                        "LESSON" -> "Bài học giáo trình"
                        "SONG" -> "Bài nhạc yêu thích"
                        "QUICK_DRILL" -> "Luyện tập nhanh"
                        else -> "Tự do luyện tập"
                    },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$formattedDate • ${durationSeconds}s • ${session.bpm} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = PianoTextSecondary
                )
            }

            Surface(
                shape = CircleShape,
                color = if (session.accuracy >= 80f) PianoSuccess.copy(alpha = 0.15f) else PianoSurfaceVariant
            ) {
                Text(
                    text = "%.0f%%".format(session.accuracy),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (session.accuracy >= 80f) PianoSuccess else PianoPrimary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}
