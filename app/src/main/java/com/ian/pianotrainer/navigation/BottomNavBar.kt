package com.ian.pianotrainer.navigation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.core.designsystem.PianoBackground
import com.ian.pianotrainer.core.designsystem.PianoOutline
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.designsystem.PianoPrimaryContainer
import com.ian.pianotrainer.core.designsystem.PianoSurface
import com.ian.pianotrainer.core.designsystem.PianoTextPrimary
import com.ian.pianotrainer.core.designsystem.PianoTextSecondary

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigateToRoute: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.Column(modifier = modifier) {
        HorizontalDivider(thickness = 1.dp, color = PianoOutline)
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("bottom_navigation_bar"),
            containerColor = PianoSurface,
            tonalElevation = 0.dp
        ) {
            bottomNavItems.forEach { item ->
                val isSelected = currentRoute == item.screen.route
                NavigationBarItem(
                    selected = isSelected,
                    onClick = { onNavigateToRoute(item.screen.route) },
                    icon = {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = stringResource(item.titleRes)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(item.titleRes),
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        )
                    },
                    modifier = Modifier.testTag(item.testTag),
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = PianoPrimary,
                        selectedTextColor = PianoPrimary,
                        unselectedIconColor = PianoTextSecondary.copy(alpha = 0.6f),
                        unselectedTextColor = PianoTextSecondary.copy(alpha = 0.6f),
                        indicatorColor = PianoPrimaryContainer.copy(alpha = 0.6f)
                    )
                )
            }
        }
    }
}
