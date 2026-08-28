package com.ian.pianotrainer.feature.learn

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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.Color
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
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary
import com.ian.pianotrainer.core.ui.AppTopBar
import com.ian.pianotrainer.core.ui.EmptyState
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.domain.model.Lesson

@Composable
fun CourseDetailScreen(
    viewModel: CourseDetailViewModel,
    onBackClick: () -> Unit,
    onLessonSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val course = uiState.course

    Scaffold(
        topBar = {
            AppTopBar(
                title = course?.title ?: "Chi tiết phần học",
                onBackClick = onBackClick
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("course_detail_screen")
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.padding(innerPadding))
            }
            course == null -> {
                EmptyState(
                    title = "Không tìm thấy phần học",
                    description = "Dữ liệu phần học này hiện không khả dụng.",
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.large,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = PianoSurfaceVariant
                                ) {
                                    Text(
                                        text = course.difficulty,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoPrimary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                                Text(
                                    text = course.title,
                                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextPrimary
                                )
                                Text(
                                    text = course.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PianoTextSecondary
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Danh sách bài học (${course.lessons.size})",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = PianoTextPrimary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    itemsIndexed(course.lessons, key = { _, lesson -> lesson.id }) { index, lesson ->
                        LessonRowCard(
                            lesson = lesson,
                            index = index + 1,
                            onClick = { onLessonSelected(lesson.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRowCard(
    lesson: Lesson,
    index: Int,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onClick() }
            .testTag("lesson_item_${lesson.id}"),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, if (lesson.isCompleted) PianoSuccess.copy(alpha = 0.4f) else PianoOutline)
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
                    color = if (lesson.isCompleted) PianoSuccess.copy(alpha = 0.15f) else PianoSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (lesson.isCompleted) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = PianoSuccess,
                                modifier = Modifier.size(20.dp)
                            )
                        } else {
                            Text(
                                text = "$index",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = PianoPrimary
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = lesson.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoTextPrimary
                    )
                    Text(
                        text = "${lesson.estimatedDurationMinutes} phút • ${lesson.objective}",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary,
                        maxLines = 1
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
