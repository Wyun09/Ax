package com.wyun09.ax

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AxTheme { Dashboard() } }
    }
}

@Composable
private fun AxTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = darkColorScheme(), content = content)
}

@Composable
private fun Dashboard() {
    val context = LocalContext.current
    val endpointStore = remember(context) { EndpointStore(context) }
    var endpoint by remember { mutableStateOf(endpointStore.load()) }
    val api = remember(endpoint) { AxApi(endpoint) }
    val scope = rememberCoroutineScope()

    var services by remember { mutableStateOf<List<ServiceStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var logsTitle by remember { mutableStateOf<String?>(null) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var endpointDraft by remember { mutableStateOf(endpoint) }

    suspend fun refresh() {
        try {
            services = api.services()
            error = null
        } catch (t: Throwable) {
            error = t.message ?: t::class.java.simpleName
        } finally {
            loading = false
        }
    }

    LaunchedEffect(endpoint) {
        loading = true
        refresh()
        while (true) {
            delay(2_000)
            refresh()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp, vertical = 22.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("AX", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black)
                    Text("Agent eXecution", style = MaterialTheme.typography.bodyMedium)
                    Text(endpoint, style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = {
                        endpointDraft = endpoint
                        showEndpointDialog = true
                    }
                ) {
                    Text("Endpoint")
                }
            }
            Spacer(Modifier.height(20.dp))

            if (error != null) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Agent offline", fontWeight = FontWeight.Bold)
                        Text(error.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
                Spacer(Modifier.height(14.dp))
            }

            if (loading && services.isEmpty()) {
                CircularProgressIndicator()
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(services, key = { it.id }) { service ->
                        ServiceCard(
                            service = service,
                            onToggle = {
                                scope.launch {
                                    runCatching {
                                        if (service.status == "running") api.stop(service.id) else api.start(service.id)
                                    }.onFailure { error = it.message }
                                    refresh()
                                }
                            },
                            onLogs = {
                                scope.launch {
                                    runCatching { api.logs(service.id) }
                                        .onSuccess {
                                            logsTitle = service.name
                                            logLines = it
                                        }
                                        .onFailure { error = it.message }
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = normalizeEndpoint(endpointDraft)
                        if (normalized == null) {
                            error = "Endpoint must be a valid http:// or https:// URL"
                        } else {
                            endpointStore.save(normalized)
                            endpoint = normalized
                            services = emptyList()
                            loading = true
                            error = null
                            showEndpointDialog = false
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) { Text("Cancel") }
            },
            title = { Text("Agent endpoint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it },
                        singleLine = true,
                        label = { Text("URL") },
                        placeholder = { Text(EndpointStore.DEFAULT_ENDPOINT) }
                    )
                    Text(
                        "Keep the default loopback endpoint unless the Ax agent is explicitly configured for authenticated remote access.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    if (logsTitle != null) {
        AlertDialog(
            onDismissRequest = { logsTitle = null },
            confirmButton = { TextButton(onClick = { logsTitle = null }) { Text("Close") } },
            title = { Text(logsTitle.orEmpty()) },
            text = {
                Text(
                    text = if (logLines.isEmpty()) "No logs yet." else logLines.joinToString("\n"),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        )
    }
}

@Composable
private fun ServiceCard(
    service: ServiceStatus,
    onToggle: () -> Unit,
    onLogs: () -> Unit
) {
    val running = service.status == "running"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(service.name, fontWeight = FontWeight.Bold)
                    Text(if (running) "● Running" else "○ Stopped")
                }
                Button(onClick = onToggle) { Text(if (running) "Stop" else "Start") }
            }
            if (running) {
                Spacer(Modifier.height(8.dp))
                Text("PID ${service.pid}  ·  ${formatUptime(service.uptimeSeconds)}")
            }
            if (!service.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(service.lastError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onLogs) { Text("Logs") }
        }
    }
}

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm %02ds".format(minutes, secs)
}
