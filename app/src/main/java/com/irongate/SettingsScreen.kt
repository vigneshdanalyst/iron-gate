package com.irongate

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    initialSettings: SettingsEntity,
    onSaveSettings: (SettingsEntity) -> Unit,
    onExportLogs: () -> Unit
) {
    var spartanEnabled by remember(initialSettings) { mutableStateOf(initialSettings.spartanModeEnabled) }
    var cooldown by remember(initialSettings) { mutableIntStateOf(initialSettings.cooldownSeconds) }
    var email by remember(initialSettings) { mutableStateOf(initialSettings.trustedFriendEmail) }

    Column(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Strictest Mode (Spartan)")
            Switch(checked = spartanEnabled, onCheckedChange = { spartanEnabled = it })
        }

        Text("Cooldown Seconds: $cooldown")
        Slider(
            value = cooldown.toFloat(),
            onValueChange = { cooldown = it.toInt().coerceIn(30, 120) },
            valueRange = 30f..120f
        )

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Trusted Friend Email") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                onSaveSettings(
                    SettingsEntity(
                        id = 0,
                        spartanModeEnabled = spartanEnabled,
                        cooldownSeconds = cooldown,
                        trustedFriendEmail = email.trim()
                    )
                )
            }) {
                Text("Save")
            }
            Button(onClick = onExportLogs) {
                Text("Export Logs")
            }
        }
    }
}
