package com.ian.pianotrainer.core.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.core.designsystem.PianoError
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoShapes
import com.ian.pianotrainer.core.designsystem.PianoSurfaceVariant
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String = "primary_button",
    leadingIcon: (@Composable () -> Unit)? = null
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .testTag(tag),
        enabled = enabled,
        shape = PianoShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = PianoPrimary,
            contentColor = Color.White,
            disabledContainerColor = PianoSurfaceVariant,
            disabledContentColor = PianoTextPrimary.copy(alpha = 0.4f)
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
    ) {
        if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String = "secondary_button"
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(tag),
        enabled = enabled,
        shape = PianoShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = PianoSurfaceVariant,
            contentColor = PianoTextPrimary
        ),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun PianoOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String = "outlined_button"
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(tag),
        enabled = enabled,
        shape = PianoShapes.medium,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = PianoPrimary
        ),
        border = ButtonDefaults.outlinedButtonBorder.copy(
            brush = androidx.compose.ui.graphics.SolidColor(PianoOutline)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

@Composable
fun DangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tag: String = "danger_button"
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .testTag(tag),
        enabled = enabled,
        shape = PianoShapes.medium,
        colors = ButtonDefaults.buttonColors(
            containerColor = PianoError.copy(alpha = 0.12f),
            contentColor = PianoError
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}
