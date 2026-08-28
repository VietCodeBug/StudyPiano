package com.ian.pianotrainer.feature.freeplay

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.core.designsystem.PianoAccent
import com.ian.pianotrainer.core.designsystem.PianoPrimary
import com.ian.pianotrainer.core.music.PianoGeometryCalculator
import com.ian.pianotrainer.domain.model.KeyboardRangeMode

data class RisingTrail(
    val id: Long,
    val midiNote: Int,
    val startMs: Long,
    val endMs: Long? = null,
    val color: Color
)

@Composable
fun RisingNotesCanvas(
    trails: List<RisingTrail>,
    currentClockMs: Long,
    activeNotes: Set<Int>,
    startOctave: Int = 3,
    rangeMode: KeyboardRangeMode = KeyboardRangeMode.TWO_OCTAVES,
    trailDurationMs: Long = 4000L,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF070B14))
            .testTag("rising_notes_canvas")
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val hitLineY = canvasHeight - 8.dp.toPx()
            val pixelsPerMs = (hitLineY - 10.dp.toPx()) / trailDurationMs.toFloat()

            val rangeResult = PianoGeometryCalculator.computeRangeForMode(
                mode = rangeMode,
                baseOctave = startOctave,
                totalWidth = canvasWidth,
                upcomingMidiNotes = activeNotes.toList()
            )
            val geometries = rangeResult.geometries

            // 1. Draw Background Lanes
            rangeResult.whiteNotes.forEach { midiNote ->
                val geom = geometries[midiNote] ?: return@forEach
                drawLine(
                    color = Color(0xFF141C2B).copy(alpha = 0.5f),
                    start = Offset(geom.left, 0f),
                    end = Offset(geom.left, canvasHeight),
                    strokeWidth = 1f
                )
            }

            geometries.values.filter { it.isBlack }.forEach { geom ->
                drawRect(
                    color = Color(0xFF04060B).copy(alpha = 0.6f),
                    topLeft = Offset(geom.left, 0f),
                    size = Size(geom.width, canvasHeight)
                )
            }

            // 2. Draw Hit Line (Bottom launchpad)
            drawLine(
                brush = Brush.horizontalGradient(
                    listOf(
                        PianoPrimary.copy(alpha = 0.4f),
                        PianoAccent.copy(alpha = 0.9f),
                        PianoPrimary.copy(alpha = 0.4f)
                    )
                ),
                start = Offset(0f, hitLineY),
                end = Offset(canvasWidth, hitLineY),
                strokeWidth = 2.5.dp.toPx()
            )

            // 3. Draw Active Key Glow / Sparks at bottom hit line
            activeNotes.forEach { midiNote ->
                val geom = geometries[midiNote] ?: return@forEach
                val margin = (geom.width * 0.12f).coerceIn(1f, 3.dp.toPx())
                val glowLeft = geom.left + margin
                val glowWidth = (geom.width - margin * 2).coerceAtLeast(3.dp.toPx())

                // Rising glow emitter
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            PianoAccent.copy(alpha = 0.7f)
                        ),
                        startY = hitLineY - 24.dp.toPx(),
                        endY = hitLineY
                    ),
                    topLeft = Offset(glowLeft, hitLineY - 24.dp.toPx()),
                    size = Size(glowWidth, 24.dp.toPx())
                )
            }

            // 4. Draw Rising Trails
            for (trail in trails) {
                val geom = geometries[trail.midiNote] ?: continue

                // Bottom of the trail is at the hitLine when key is still pressed, or rising after release
                val headAgeMs = currentClockMs - trail.startMs
                val tailAgeMs = if (trail.endMs != null) currentClockMs - trail.endMs else 0L

                // If entire trail has passed off screen top, skip
                if (tailAgeMs > trailDurationMs + 1000L) continue

                val headY = hitLineY - (headAgeMs * pixelsPerMs)
                val tailY = hitLineY - (tailAgeMs * pixelsPerMs)

                val trailTopY = headY.coerceAtLeast(-10.dp.toPx())
                val trailBottomY = tailY.coerceAtMost(hitLineY)
                val trailHeight = (trailBottomY - trailTopY).coerceAtLeast(4.dp.toPx())

                if (trailBottomY < 0f || trailTopY > canvasHeight) continue

                val margin = (geom.width * 0.15f).coerceIn(1f, 3.dp.toPx())
                val blockX = geom.left + margin
                val blockW = (geom.width - margin * 2).coerceAtLeast(3.dp.toPx())

                // Gradient fade for the trail head
                val trailAlpha = (1f - (tailAgeMs.toFloat() / trailDurationMs.toFloat())).coerceIn(0.15f, 1f)
                val brush = Brush.verticalGradient(
                    listOf(
                        trail.color.copy(alpha = trailAlpha * 0.4f),
                        trail.color.copy(alpha = trailAlpha * 0.95f)
                    ),
                    startY = trailTopY,
                    endY = trailBottomY
                )

                drawRoundRect(
                    brush = brush,
                    topLeft = Offset(blockX, trailTopY),
                    size = Size(blockW, trailHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )

                // Soft white head rim if currently rising / active
                if (trail.endMs == null) {
                    drawRoundRect(
                        color = Color.White.copy(alpha = 0.8f),
                        topLeft = Offset(blockX, trailBottomY - 3.dp.toPx()),
                        size = Size(blockW, 3.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }
            }
        }
    }
}
