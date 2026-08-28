package com.ian.pianotrainer.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cable
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.R
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoSuccess
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.domain.model.DeviceConnectionState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBackClick: (() -> Unit)? = null,
    connectionState: DeviceConnectionState? = null,
    onConnectionBadgeClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = PianoTextPrimary,
                maxLines = 1
            )
        },
        modifier = modifier.testTag("app_top_bar"),
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.action_back),
                        tint = PianoTextPrimary
                    )
                }
            }
        },
        actions = {
            if (connectionState != null) {
                ConnectionStatusChip(
                    state = connectionState,
                    onClick = onConnectionBadgeClick
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = PianoBackground
        )
    )
}

@Composable
fun ConnectionStatusChip(
    state: DeviceConnectionState,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val (color, textRes) = when (state) {
        DeviceConnectionState.CONNECTED -> PianoSuccess to R.string.device_connected
        DeviceConnectionState.CONNECTING -> PianoPrimary to R.string.device_connecting
        DeviceConnectionState.SCANNING -> PianoPrimary to R.string.device_status_scanning
        DeviceConnectionState.DISCONNECTED -> PianoTextPrimary.copy(alpha = 0.5f) to R.string.device_disconnected
        DeviceConnectionState.ERROR -> PianoError to R.string.device_connection_failed
    }

    Surface(
        shape = CircleShape,
        color = color.copy(alpha = 0.12f),
        modifier = modifier
            .clip(CircleShape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .testTag("connection_status_chip")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(color = color, shape = CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(textRes),
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = color
            )
        }
    }
}
