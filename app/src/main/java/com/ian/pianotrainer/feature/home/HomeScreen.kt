package com.ian.pianotrainer.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoGold
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
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.SectionHeader
import com.ian.pianotrainer.core.ui.StatCard
import com.ian.pianotrainer.domain.model.DeviceConnectionState

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToLesson: (String) -> Unit,
    onNavigateToPractice: () -> Unit,
    onNavigateToFreePlay: () -> Unit,
    onNavigateToDiagnostics: () -> Unit,
    onNavigateToDevice: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.app_name),
                connectionState = uiState.connectionState,
                onConnectionBadgeClick = onNavigateToDevice,
                actions = {
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("home_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.title_settings),
                            tint = PianoTextPrimary
                        )
                    }
                }
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("home_screen")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Personalized Greeting & Top Bar Area
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.home_subtitle),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = PianoTextSecondary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.home_greeting),
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary
                        )
                    }
                }
            }

            // 2. Practice Quick Stats (2-column layout matching Natural Tones)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_streak_stat"),
                        shape = PianoShapes.large,
                        colors = CardDefaults.cardColors(containerColor = PianoSurface),
                        border = BorderStroke(1.dp, PianoOutline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocalFireDepartment,
                                    contentDescription = null,
                                    tint = PianoGold,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "CHUỖI",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextSecondary
                                )
                            }
                            Text(
                                text = "${uiState.progressSummary.currentStreakDays} ngày",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                            Text(
                                text = "Bạn đang làm rất tốt!",
                                style = MaterialTheme.typography.bodySmall,
                                color = PianoTextSecondary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_time_stat"),
                        shape = PianoShapes.large,
                        colors = CardDefaults.cardColors(containerColor = PianoSurface),
                        border = BorderStroke(1.dp, PianoOutline)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Timer,
                                    contentDescription = null,
                                    tint = PianoPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "THỜI GIAN",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextSecondary
                                )
                            }
                            Text(
                                text = "${uiState.progressSummary.totalPracticeTimeMinutes} phút",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                            Text(
                                text = "Tổng luyện tập",
                                style = MaterialTheme.typography.bodySmall,
                                color = PianoTextSecondary
                            )
                        }
                    }
                }
            }

            // 3. Recommended Lesson Hero Card (Natural Tones vibrant Primary Card)
            item {
                val lesson = uiState.recommendedLesson
                val course = uiState.recommendedCourse
                if (lesson != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("recommended_lesson_card"),
                        shape = PianoShapes.extraLarge,
                        colors = CardDefaults.cardColors(containerColor = PianoPrimary),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            // Background decorative music note
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier
                                    .size(130.dp)
                                    .align(Alignment.TopEnd)
                                    .padding(top = 8.dp, end = 8.dp)
                            )

                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = Color.White.copy(alpha = 0.22f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.home_recommended_lesson).uppercase(),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                    )
                                }

                                Text(
                                    text = lesson.title,
                                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )

                                Text(
                                    text = if (course != null) "${course.title} • ${lesson.objective}" else lesson.objective,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 2
                                )

                                Spacer(modifier = Modifier.height(4.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Tiến độ",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.8f)
                                        )
                                        Text(
                                            text = "${if (lesson.isCompleted) 100 else 25}%",
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White
                                        )
                                    }

                                    Surface(
                                        shape = CircleShape,
                                        color = Color.White,
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .clickable { onNavigateToLesson(lesson.id) }
                                            .testTag("resume_lesson_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.home_resume_learning),
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            color = PianoPrimaryDark,
                                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 4. Victor VT02 Piano Device Connection Banner
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PianoShapes.large)
                        .clickable { onNavigateToDevice() }
                        .testTag("device_status_card"),
                    shape = PianoShapes.large,
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
                                shape = PianoShapes.medium,
                                color = PianoBackground,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Piano,
                                        contentDescription = null,
                                        tint = PianoPrimary,
                                        modifier = Modifier.size(26.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "Kết nối đàn piano",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextPrimary
                                )
                                Text(
                                    text = uiState.connectedDevice?.name ?: "Chưa kết nối đàn MIDI (USB / Bluetooth)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PianoTextSecondary
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = if (uiState.connectionState == DeviceConnectionState.CONNECTED) PianoSuccess else PianoTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(12.dp)
                        ) {}
                    }
                }
            }

            // 5. Quick Access Grid / Actions
            item {
                SectionHeader(title = "Lối tắt tính năng")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(PianoShapes.large)
                            .clickable { onNavigateToPractice() }
                            .testTag("home_shortcut_practice"),
                        shape = PianoShapes.large,
                        colors = CardDefaults.cardColors(containerColor = PianoSurface),
                        border = BorderStroke(1.dp, PianoOutline)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                tint = PianoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Luyện tập nốt",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                        }
                    }

                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(96.dp)
                            .clip(PianoShapes.large)
                            .clickable { onNavigateToFreePlay() }
                            .testTag("home_shortcut_freeplay"),
                        shape = PianoShapes.large,
                        colors = CardDefaults.cardColors(containerColor = PianoSurface),
                        border = BorderStroke(1.dp, PianoOutline)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Piano,
                                contentDescription = null,
                                tint = PianoPrimary,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Chơi tự do",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                        }
                    }
                }
            }

            item {
                QuickActionRow(
                    title = "Kiểm tra tín hiệu MIDI",
                    subtitle = "Chẩn đoán phím bấm & độ nhạy lực (Velocity)",
                    icon = Icons.Default.ElectricBolt,
                    onClick = onNavigateToDiagnostics,
                    tag = "home_shortcut_diagnostics"
                )
            }
        }
    }
}

@Composable
private fun QuickActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onClick() }
            .testTag(tag),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, PianoOutline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
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
                            imageVector = icon,
                            contentDescription = null,
                            tint = PianoPrimaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                    Text(
                        text = subtitle,
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
