package com.ian.pianotrainer.feature.practice

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.text.style.TextAlign
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
import com.ian.pianotrainer.core.ui.EmptyState
import com.ian.pianotrainer.core.ui.LoadingState
import com.ian.pianotrainer.core.ui.PianoOutlinedButton
import com.ian.pianotrainer.core.ui.PrimaryButton
import com.ian.pianotrainer.core.ui.StatCard

@Composable
fun PracticeResultScreen(
    viewModel: PracticeResultViewModel,
    onNavigateToHome: () -> Unit,
    onRetryPractice: (title: String, sourceType: String, sourceId: String, handMode: String, bpm: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val session = uiState.session

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.title_practice_result),
                onBackClick = onNavigateToHome
            )
        },
        containerColor = PianoBackground,
        modifier = modifier.testTag("practice_result_screen")
    ) { innerPadding ->
        when {
            uiState.isLoading -> {
                LoadingState(modifier = Modifier.padding(innerPadding))
            }
            session == null -> {
                EmptyState(
                    title = "Không có kết quả",
                    description = "Không tìm thấy dữ liệu phiên luyện tập.",
                    modifier = Modifier.padding(innerPadding)
                )
            }
            else -> {
                val accuracy = session.accuracy
                val motivationalText = when {
                    accuracy >= 90f -> "Xuất sắc! Bạn đã thực hiện bài tập với độ chính xác rất cao!"
                    accuracy >= 75f -> "Rất tốt! Cảm giác phím và nhịp điệu của bạn đang tiến bộ rõ rệt."
                    accuracy >= 50f -> "Khá tốt! Hãy tập chậm lại theo máy đập nhịp để tăng độ chính xác nhé."
                    else -> "Đừng nản lòng! Hãy kiên trì luyện tập từng nốt một cùng cây đàn piano của bạn."
                }

                val slowBpm = (session.bpm * 0.75f).toInt().coerceAtLeast(30)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Trophy / Score Hero Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("result_score_card"),
                            shape = PianoShapes.large,
                            colors = CardDefaults.cardColors(containerColor = PianoPrimaryContainer.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, PianoPrimary.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (accuracy >= 75f) PianoGold.copy(alpha = 0.2f) else PianoSurfaceVariant,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.EmojiEvents,
                                            contentDescription = null,
                                            tint = if (accuracy >= 75f) PianoGold else PianoPrimary,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }

                                Text(
                                    text = "%.0f%%".format(accuracy),
                                    style = MaterialTheme.typography.displayMedium,
                                    color = PianoPrimaryDark
                                )

                                Text(
                                    text = stringResource(R.string.result_accuracy),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = PianoTextPrimary
                                )

                                Text(
                                    text = motivationalText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PianoTextSecondary,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                )
                            }
                        }
                    }

                    // Stat Breakdown Grid (2x2)
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = stringResource(R.string.result_correct_notes),
                                    value = "${session.correctNotes}",
                                    icon = Icons.Default.CheckCircle,
                                    iconTint = PianoSuccess,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = stringResource(R.string.result_wrong_notes),
                                    value = "${session.wrongNotes}",
                                    icon = Icons.Default.Close,
                                    iconTint = PianoError,
                                    modifier = Modifier.weight(1f)
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                StatCard(
                                    title = "Tốc độ",
                                    value = "${session.bpm} BPM",
                                    icon = Icons.Default.Speed,
                                    iconTint = PianoPrimaryDark,
                                    modifier = Modifier.weight(1f)
                                )
                                StatCard(
                                    title = stringResource(R.string.result_duration),
                                    value = "${(session.durationMs / 1000).coerceAtLeast(1)}s",
                                    icon = Icons.Default.Timer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Harmono-inspired Practice Recommendation Card
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PianoShapes.large,
                            colors = CardDefaults.cardColors(containerColor = PianoSurface),
                            border = BorderStroke(1.dp, PianoOutline)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lightbulb,
                                        contentDescription = null,
                                        tint = PianoGold,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = "Gợi ý luyện tập cá nhân hóa",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = PianoTextPrimary
                                    )
                                }

                                val recommendation = when {
                                    accuracy < 70f -> "Bạn nên luyện tập chậm lại ở tốc độ $slowBpm BPM (75%) với chế độ Chờ nốt đúng (Wait Mode) để ngón tay ghi nhớ vị trí phím chính xác hơn."
                                    session.wrongNotes > 3 -> "Hãy thử tập riêng từng tay (Tay Phải / Tay Trái) trước khi ghép cả 2 tay lại với nhau."
                                    accuracy >= 90f -> "Bạn đã chơi rất xuất sắc! Hãy thử thách bản thân bằng cách tăng tốc độ lên ${session.bpm + 10} BPM hoặc chuyển sang chế độ Chạy theo nhịp (In Tempo)."
                                    else -> "Duy trì nhịp điệu đều đặn và chú ý lắng nghe âm thanh từ đàn piano để khớp nhịp tốt hơn."
                                }

                                Text(
                                    text = recommendation,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = PianoTextSecondary
                                )
                            }
                        }
                    }

                    // Action Buttons
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            PrimaryButton(
                                text = stringResource(R.string.result_retry_button),
                                onClick = {
                                    onRetryPractice(
                                        "Luyện tập lại",
                                        session.sourceType,
                                        session.sourceId ?: "",
                                        session.handMode.name,
                                        session.bpm
                                    )
                                },
                                tag = "retry_practice_button"
                            )

                            if (accuracy < 80f) {
                                PianoOutlinedButton(
                                    text = "Tập chậm lại ($slowBpm BPM)",
                                    onClick = {
                                        onRetryPractice(
                                            "Tập chậm lại",
                                            session.sourceType,
                                            session.sourceId ?: "",
                                            session.handMode.name,
                                            slowBpm
                                        )
                                    },
                                    tag = "retry_slow_button"
                                )
                            }

                            PianoOutlinedButton(
                                text = stringResource(R.string.result_home_button),
                                onClick = onNavigateToHome,
                                tag = "back_home_button"
                            )
                        }
                    }
                }
            }
        }
    }
}
