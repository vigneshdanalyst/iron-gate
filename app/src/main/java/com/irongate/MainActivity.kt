package com.irongate

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val durationsMinutes = listOf(30, 60, 120, 240, 480, 720)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = IronGateDatabase.getInstance(this)
        lifecycleScope.launch(Dispatchers.IO) {
            if (db.dao().getSettings() == null) {
                db.dao().upsertSettings(SettingsEntity())
            }
        }
        setContent {
            val darkScheme = MaterialTheme.colorScheme.copy(
                primary = Color.White,
                onPrimary = Color.Black,
                background = Color(0xFF0E0E0E),
                surface = Color(0xFF121212),
                onSurface = Color(0xFFEDEDED),
                onBackground = Color(0xFFEDEDED)
            )
            MaterialTheme(colorScheme = darkScheme) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainApp(db = db, durationsMinutes = durationsMinutes)
                }
            }
        }
    }
}

private enum class Tab { Home, BlockedApps, Logs, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp(db: IronGateDatabase, durationsMinutes: List<Int>) {
    val context = LocalContext.current
    val blockedApps by db.dao().observeBlockedApps().collectAsState(initial = emptyList())
    val logs by db.dao().observeRecentLogs().collectAsState(initial = emptyList())
    val activeSession by db.dao().observeActiveLockSession().collectAsState(initial = null)
    val settings by db.dao().observeSettings().collectAsState(initial = SettingsEntity())
    var selectedDuration by rememberSaveable { mutableIntStateOf(durationsMinutes.first()) }
    var durationExpanded by remember { mutableStateOf(false) }
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Home) }
    var showUnlockDialog by remember { mutableStateOf(false) }
    var unlockReason by remember { mutableStateOf("") }
    var spartanCodeInput by remember { mutableStateOf("") }
    var cooldownRemaining by remember { mutableIntStateOf(settings?.cooldownSeconds ?: 60) }
    var isCooldownRunning by remember { mutableStateOf(false) }

    LaunchedEffect(activeSession?.id, isCooldownRunning) {
        if (!isCooldownRunning) return@LaunchedEffect
        cooldownRemaining = (settings?.cooldownSeconds ?: 60).coerceIn(30, 120)
        while (cooldownRemaining > 0) {
            delay(1_000L)
            cooldownRemaining -= 1
        }
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("End Lock Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unlockReason,
                        onValueChange = { unlockReason = it },
                        label = { Text("Reason (required)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (LockStateStore.read(context).spartanEnabled) {
                        OutlinedTextField(
                            value = spartanCodeInput,
                            onValueChange = { spartanCodeInput = it },
                            label = { Text("Spartan Unlock Code") },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Text(
                        if (isCooldownRunning) "Cooldown: $cooldownRemaining seconds"
                        else "Press start cooldown to proceed."
                    )
                }
            },
            confirmButton = {
                if (!isCooldownRunning) {
                    TextButton(onClick = {
                        if (unlockReason.isNotBlank()) {
                            isCooldownRunning = true
                        }
                    }) { Text("Start Cooldown") }
                } else {
                    TextButton(
                        onClick = {
                            val reason = unlockReason.trim()
                            context.startForegroundService(
                                Intent(context, LockForegroundService::class.java).apply {
                                    action = LockForegroundService.ACTION_END_LOCK
                                }
                            )
                            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                                db.dao().insertAttemptLog(
                                    AttemptLogEntity(
                                        timestampMillis = System.currentTimeMillis(),
                                        type = "EARLY_UNLOCK",
                                        details = "Early unlock with reason: $reason"
                                    )
                                )
                            }
                            isCooldownRunning = false
                            unlockReason = ""
                            spartanCodeInput = ""
                            showUnlockDialog = false
                        },
                        enabled = cooldownRemaining <= 0 &&
                            unlockReason.isNotBlank() &&
                            (
                                !LockStateStore.read(context).spartanEnabled ||
                                    spartanCodeInput == LockStateStore.read(context).spartanUnlockCode
                                )
                    ) { Text("End Lock") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    if (!LockStateStore.read(context).spartanEnabled) {
                        isCooldownRunning = false
                        showUnlockDialog = false
                    }
                }) { Text("Back") }
            }
        )
    }

    Scaffold(
        bottomBar = {
            BottomAppBar {
                listOf(
                    Tab.Home to Icons.Filled.Home,
                    Tab.BlockedApps to Icons.AutoMirrored.Filled.List,
                    Tab.Logs to Icons.AutoMirrored.Filled.List,
                    Tab.Settings to Icons.Filled.Settings
                ).forEach { (tab, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = { Icon(icon, contentDescription = tab.name) },
                        label = { Text(tab.name) }
                    )
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (selectedTab) {
                Tab.Home -> {
                    val status = activeSession?.let { "Active until ${formatTime(it.endsAtMillis)}" } ?: "Lock Inactive"
                    Text(status, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { durationExpanded = true }, enabled = activeSession == null) {
                            Text("Duration: ${durationLabel(selectedDuration)}")
                        }
                        DropdownMenu(expanded = durationExpanded, onDismissRequest = { durationExpanded = false }) {
                            durationsMinutes.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(durationLabel(option)) },
                                    onClick = {
                                        selectedDuration = option
                                        durationExpanded = false
                                    }
                                )
                            }
                        }
                        Button(onClick = {
                            val now = System.currentTimeMillis()
                            val end = now + selectedDuration * 60_000L
                            val spartan = settings?.spartanModeEnabled == true
                            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                                val sessionId = db.dao().insertLockSession(
                                    LockSessionEntity(
                                        startedAtMillis = now,
                                        endsAtMillis = end,
                                        isActive = true,
                                        isSpartan = spartan
                                    )
                                )
                                val code = LockStateStore.start(context, end, sessionId, spartan)
                                db.dao().insertAttemptLog(
                                    AttemptLogEntity(
                                        timestampMillis = now,
                                        type = "LOCK_STARTED",
                                        details = if (spartan) {
                                            "Spartan lock started. Unlock code: $code"
                                        } else "Lock started."
                                    )
                                )
                            }
                            context.startForegroundService(
                                Intent(context, LockForegroundService::class.java).apply {
                                    action = LockForegroundService.ACTION_START_LOCK
                                }
                            )
                        }, enabled = blockedApps.isNotEmpty() && activeSession == null) {
                            Text("Start Lock")
                        }
                    }

                    Button(onClick = { showUnlockDialog = true }, enabled = activeSession != null) {
                        Text("End Lock")
                    }
                    Text(
                        "Blocked Apps (${blockedApps.size}) - tap to edit",
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, BlockedAppsActivity::class.java))
                        }
                    )
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(blockedApps) { app -> Text("- ${app.appName}") }
                    }
                    Text("Recent unlock attempts", fontWeight = FontWeight.Bold)
                    logs.filter { it.type.contains("UNLOCK") || it.type.contains("ADMIN") }
                        .take(5)
                        .forEach { log -> Text("${formatTime(log.timestampMillis)} - ${log.details}") }

                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                        Text("Open Accessibility Settings")
                    }
                    TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }) {
                        Text("Open Usage Access Settings")
                    }
                }

                Tab.BlockedApps -> {
                    Text("Tap to manage blocked apps.", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = {
                        context.startActivity(Intent(context, BlockedAppsActivity::class.java))
                    }) {
                        Text("Open Blocked Apps Screen")
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(blockedApps) { app -> Text(app.appName) }
                    }
                }

                Tab.Logs -> {
                    Text("Attempt Logs", style = MaterialTheme.typography.titleMedium)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(logs) { log ->
                            Text("${formatTime(log.timestampMillis)} | ${log.type} | ${log.packageName ?: "-"}")
                            Text(log.details, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                Tab.Settings -> {
                    SettingsScreen(
                        initialSettings = settings ?: SettingsEntity(),
                        onSaveSettings = { newSettings ->
                            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                                db.dao().upsertSettings(newSettings)
                            }
                        },
                        onExportLogs = {
                            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                                val lines = db.dao().getAllLogs().joinToString("\n") { log ->
                                    "${log.timestampMillis},${log.type},${log.packageName ?: ""},${log.details}"
                                }
                                context.openFileOutput("iron_gate_logs.csv", android.content.Context.MODE_PRIVATE)
                                    .bufferedWriter()
                                    .use { it.write("timestamp,type,package,details\n$lines") }
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun durationLabel(minutes: Int): String {
    return when (minutes) {
        30 -> "30 min"
        60 -> "1 hr"
        120 -> "2 hr"
        240 -> "4 hr"
        480 -> "8 hr"
        720 -> "12 hr"
        else -> "$minutes min"
    }
}

private fun formatTime(millis: Long): String {
    return SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(millis))
}
