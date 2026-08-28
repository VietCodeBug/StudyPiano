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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.domain.model.Course

@Composable
fun LearnScreen(
    viewModel: LearnViewModel,
    onCourseClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            AppTopBar(title = stringResource(R.string.title_learn))
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("learn_screen")
    ) { innerPadding ->
        if (uiState.isLoading) {
            LoadingState(modifier = Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Progress Card
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("learn_overview_card"),
                        shape = PianoShapes.large,
                        colors = CardDefaults.cardColors(containerColor = PianoPrimaryContainer.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, PianoPrimary.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Lộ trình vỡ lòng piano 88 phím",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = PianoTextPrimary
                            )
                            Text(
                                text = "Đã hoàn thành ${uiState.completedLessonsCount}/${uiState.totalLessonsCount} bài học",
                                style = MaterialTheme.typography.bodyMedium,
                                color = PianoTextSecondary
                            )
                            LinearProgressIndicator(
                                progress = { (uiState.overallProgressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape),
                                color = PianoPrimary,
                                trackColor = Color.White.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                // 8 Curriculum Courses
                items(uiState.courses, key = { it.id }) { course ->
                    CourseItemCard(
                        course = course,
                        onClick = { onCourseClick(course.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseItemCard(
    course: Course,
    onClick: () -> Unit
) {
    val isCompleted = course.completionPercent == 100

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PianoShapes.medium)
            .clickable { onClick() }
            .testTag("course_card_${course.id}"),
        shape = PianoShapes.medium,
        colors = CardDefaults.cardColors(containerColor = PianoSurface),
        border = BorderStroke(1.dp, if (isCompleted) PianoSuccess.copy(alpha = 0.5f) else PianoOutline)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = PianoSurfaceVariant
                ) {
                    Text(
                        text = course.difficulty,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                        color = PianoPrimary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                if (isCompleted) {
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
                } else {
                    Text(
                        text = "${course.lessons.size} bài học",
                        style = MaterialTheme.typography.bodySmall,
                        color = PianoTextSecondary
                    )
                }
            }

            Text(
                text = course.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PianoTextPrimary
            )

            Text(
                text = course.description,
                style = MaterialTheme.typography.bodySmall,
                color = PianoTextSecondary,
                maxLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LinearProgressIndicator(
                    progress = { (course.completionPercent / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (isCompleted) PianoSuccess else PianoPrimary,
                    trackColor = PianoSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = PianoTextSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
