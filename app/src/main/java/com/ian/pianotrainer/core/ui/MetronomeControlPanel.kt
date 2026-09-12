package com.ian.pianotrainer.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ian.pianotrainer.domain.service.MetronomeSound

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeControlPanel(
    running: Boolean,
    bpm: Int,
    currentBeat: Int,
    beatsPerBar: Int,
    accentEnabled: Boolean,
    sound: MetronomeSound,
    volume: Float,
    waitMode: Boolean,
    onToggle: () -> Unit,
    onBpm: (Int) -> Unit,
    onTap: () -> Unit,
    onBeats: (Int) -> Unit,
    onAccent: (Boolean) -> Unit,
    onSound: (MetronomeSound) -> Unit,
    onVolume: (Float) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    var bpmText by remember(bpm) { mutableStateOf(bpm.toString()) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = modifier.fillMaxWidth().testTag("metronome_panel")) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onToggle, enabled = !waitMode, modifier = Modifier.testTag("metronome_toggle")) { Text(if (running) "Tắt" else "Bật máy đếm nhịp") }
            Text(if (running) "Phách " + currentBeat + "/" + beatsPerBar else bpm.toString() + " BPM", style = MaterialTheme.typography.titleMedium)
        }
        if (waitMode) Text("Chế độ Chờ đúng nốt dừng timeline, nên máy đếm nhịp theo bài được tạm dừng.", style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            IconButton(onClick = { onBpm(bpm - 1) }, modifier = Modifier.size(48.dp)) { Text("−") }
            OutlinedTextField(
                value = bpmText,
                onValueChange = { text -> bpmText = text.filter(Char::isDigit).take(3); bpmText.toIntOrNull()?.let { onBpm(it.coerceIn(30, 240)) } },
                label = { Text("BPM 30–240") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.size(width = 132.dp, height = 64.dp)
            )
            IconButton(onClick = { onBpm(bpm + 1) }, modifier = Modifier.size(48.dp)) { Text("+") }
            OutlinedButton(onClick = onTap, modifier = Modifier.testTag("tap_tempo")) { Text("Tap Tempo") }
        }
        Text("Số phách mỗi ô nhịp")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf(2, 3, 4, 6).forEach { value -> FilterChip(value == beatsPerBar, { onBeats(value) }, { Text(value.toString()) }) } }
        Row(verticalAlignment = Alignment.CenterVertically) { Text("Nhấn phách đầu", modifier = Modifier.weight(1f)); Switch(accentEnabled, onAccent) }
        Text("Âm thanh")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { MetronomeSound.builtIns.forEach { value -> FilterChip(value == sound, { onSound(value) }, { Text(value.displayName) }) }; OutlinedButton(onPreview) { Text("Nghe thử") } }
        Text("Âm lượng metronome " + (volume * 100).toInt() + "%")
        Slider(value = volume, onValueChange = onVolume, valueRange = 0f..1f, modifier = Modifier.testTag("metronome_volume"))
    }
}
