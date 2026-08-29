package com.ian.pianotrainer.feature.progress

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoGold
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.ConfirmationDialog
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
    var sessionToDeleteId by remember { mutableStateOf<String?>(null) }

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
                // 1. Time Filters
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf(
                            7 to "7 ngày qua",
                            30 to "30 ngày qua",
                            null to "Tất cả"
                        ).forEach { (days, label) ->
                            FilterChip(
                                selected = uiState.selectedDaysFilter == days,
                                onClick = { viewModel.setDaysFilter(days) },
                                label = { Text(label) },
                                modifier = Modifier.weight(1f),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PianoPrimaryContainer,
                                    selectedLabelColor = PianoPrimary
                                )
                            )
                        }
                    }
                }

                // 2. Daily Goal Progress Card
                item {
                    val goalMinutes = uiState.userSettings.dailyGoalMinutes
                    val todayMinutes = summary.todayPracticeTimeMinutes
                    val progressFraction = (todayMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = PianoShapes.medium,
                        colors = CardDefaults.cardColors(containerColor = PianoSurface),
                        border = BorderStroke(1.dp, PianoOutline)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Timer,
                                        contentDescription = null,
                                        tint = PianoPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Mục tiêu hôm nay",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                }
                                Text(
                                    text = "$todayMinutes / $goalMinutes phút",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = if (todayMinutes >= goalMinutes) PianoSuccess else PianoPrimary
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (todayMinutes >= goalMinutes) PianoSuccess else PianoPrimary,
                                trackColor = PianoSurfaceVariant
                            )
                        }
                    }
                }

                // 3. Stat Grid (2x2)
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
                                title = "Chuỗi ngày luyện",
                                value = "${summary.currentStreakDays} ngày (Kỉ lục: ${summary.longestStreakDays})",
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
                                title = "Độ chính xác chuẩn",
                                value = "%.1f%%".format(summary.weightedAccuracy * 100f),
                                icon = Icons.Default.CheckCircle,
                                iconTint = PianoSuccess,
                                modifier = Modifier.weight(1f)
                            )
                            StatCard(
                                title = "Tốc độ cao nhất",
                                value = "${summary.bestBpm} BPM",
                                icon = Icons.Default.Speed,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 4. Daily Activity Bar Chart
                if (summary.weeklyHistory.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Hoạt động theo ngày (phút)",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextPrimary
                                )
                                Spacer(modifier = Modifier.height(14.dp))
                                val maxMinutes = (summary.weeklyHistory.maxOfOrNull { it.durationMinutes } ?: 30L).coerceAtLeast(10L)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(100.dp),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Bottom
                                ) {
                                    summary.weeklyHistory.forEach { stat ->
                                        val barHeightFraction = (stat.durationMinutes.toFloat() / maxMinutes.toFloat()).coerceIn(0.05f, 1f)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Bottom,
                                            modifier = Modifier.fillMaxHeight()
                                        ) {
                                            if (stat.durationMinutes > 0) {
                                                Text(
                                                    text = "${stat.durationMinutes}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                    color = PianoTextSecondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Box(
                                                modifier = Modifier
                                                    .width(16.dp)
                                                    .fillMaxHeight(barHeightFraction)
                                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                                    .background(if (stat.durationMinutes > 0) PianoPrimary else PianoSurfaceVariant)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = stat.dayLabel,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                                color = PianoTextSecondary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Weakest Pitches Breakdown (Gate E2)
                if (summary.weakPitches.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WarningAmber,
                                        contentDescription = null,
                                        tint = PianoError,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "5 nốt hay bấm nhầm nhất",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    summary.weakPitches.forEach { weak ->
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = PianoSurfaceVariant,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(vertical = 8.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = weak.noteName,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = PianoPrimary
                                                )
                                                Text(
                                                    text = "${weak.totalMistakes} lỗi",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = PianoError
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 6. Most Practiced Song (if any)
                summary.mostPracticedSongTitle?.let { title ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.medium,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MusicNote,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Bài luyện tập nhiều nhất",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = PianoTextSecondary
                                    )
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                    Text(
                                        text = "Đã luyện ${summary.mostPracticedSongCount} lượt",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = PianoTextSecondary
                                    )
                                }
                            }
                        }
                    }
                }

                // 7. Recent Practice Sessions Header
                item {
                    SectionHeader(title = stringResource(R.string.progress_recent_history))
                }

                // 8. History List or Empty
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
                            onClick = { onSessionClick(session.id) },
                            onDelete = { sessionToDeleteId = session.id }
                        )
                    }
                }
            }
        }
    }

    // Delete session confirmation dialog
    sessionToDeleteId?.let { id ->
        ConfirmationDialog(
            title = "Xóa lượt luyện tập này?",
            message = "Kết quả của buổi luyện tập sẽ bị xóa khỏi lịch sử và thống kê.",
            confirmText = "Xóa",
            dismissText = "Hủy",
            isDestructive = true,
            onConfirm = {
                viewModel.deleteSession(id)
                sessionToDeleteId = null
            },
            onDismiss = { sessionToDeleteId = null }
        )
    }
}

@Composable
private fun PracticeSessionItemCard(
    session: PracticeSession,
    onClick: () -> Unit,
    onDelete: () -> Unit
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
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val title = session.sourceTitleSnapshot ?: when (session.sourceType) {
                    "LESSON" -> "Bài học giáo trình"
                    "SONG" -> "Bài nhạc yêu thích"
                    "FREE_PLAY" -> "Chơi tự do"
                    "QUICK_DRILL" -> "Luyện tập nhanh"
                    else -> "Luyện tập"
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = PianoTextPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$formattedDate • ${durationSeconds}s • ${session.bpm} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = PianoTextSecondary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = CircleShape,
                    color = if (session.accuracy >= 0.8f) PianoSuccess.copy(alpha = 0.15f) else PianoSurfaceVariant
                ) {
                    val accPct = if (session.accuracy <= 1.0f) session.accuracy * 100f else session.accuracy
                    Text(
                        text = "%.0f%%".format(accPct),
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (session.accuracy >= 0.8f) PianoSuccess else PianoPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Xóa lượt",
                        tint = PianoTextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
