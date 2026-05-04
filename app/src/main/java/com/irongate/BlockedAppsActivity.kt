package com.irongate

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class InstalledApp(val packageName: String, val appName: String)

class BlockedAppsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = IronGateDatabase.getInstance(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    BlockedAppsScreen(db = db) {
                        finish()
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedAppsScreen(
    db: IronGateDatabase,
    onDone: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var allApps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    val selected = remember { mutableStateMapOf<String, Boolean>() }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = packageManager.queryIntentActivities(launcherIntent, PackageManager.MATCH_ALL)
                .mapNotNull { resolveInfo ->
                    val appInfo = resolveInfo.activityInfo?.applicationInfo ?: return@mapNotNull null
                    if ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) return@mapNotNull null
                    InstalledApp(
                        packageName = appInfo.packageName,
                        appName = resolveInfo.loadLabel(packageManager).toString()
                    )
                }
                .distinctBy { it.packageName }
                .sortedBy { it.appName.lowercase() }

            val blocked = db.dao().getBlockedPackageNames().toSet()
            withContext(Dispatchers.Main) {
                allApps = apps
                apps.forEach { selected[it.packageName] = blocked.contains(it.packageName) }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Select Blocked Apps", style = MaterialTheme.typography.headlineSmall)
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(allApps) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(app.appName, modifier = Modifier.weight(1f))
                    Checkbox(
                        checked = selected[app.packageName] == true,
                        onCheckedChange = { checked -> selected[app.packageName] = checked }
                    )
                }
            }
        }
        Button(onClick = {
            (context as ComponentActivity).lifecycleScope.launch(Dispatchers.IO) {
                val selectedApps = allApps.filter { selected[it.packageName] == true }
                    .map { BlockedAppEntity(packageName = it.packageName, appName = it.appName) }
                db.dao().clearBlockedApps()
                db.dao().upsertBlockedApps(selectedApps)
                withContext(Dispatchers.Main) { onDone() }
            }
        }) {
            Text("Save")
        }
    }
}
