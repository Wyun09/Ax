package com.wyun09.ax

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val AxBlue = Color(0xFF4B6EF5)
private val AxBlueSoft = Color(0xFFAFC1FF)
private val AxBackground = Color(0xFF060811)
private val AxSurface = Color(0xFF101522)
private val AxSurfaceRaised = Color(0xFF181F30)
private val AxSuccess = Color(0xFF66D9A2)
private val AxWarning = Color(0xFFFFC56B)
private val AxDanger = Color(0xFFFF7B7B)
private val AxMuted = Color(0xFF9AA6BB)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 不在应用启动时自动拉起前台服务。
        // 部分 Android/厂商系统会因此直接拒绝启动，先保证主界面稳定可用。
        setContent { AxTheme { AxApp() } }
    }
}

@Composable
private fun AxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = AxBlue,
            secondary = AxBlueSoft,
            background = AxBackground,
            surface = AxSurface,
            surfaceVariant = AxSurfaceRaised,
            error = AxDanger
        ),
        content = content
    )
}

private enum class MainTab { HOME, GUIDE, SETTINGS }

@Composable
private fun AxApp() {
    val context = LocalContext.current
    val endpointStore = remember(context) { EndpointStore(context) }
    var endpoint by remember { mutableStateOf(endpointStore.load()) }
    val api = remember(endpoint) { AxApi(endpoint) }
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(MainTab.HOME) }
    var services by remember { mutableStateOf<List<ServiceStatus>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var logsFor by remember { mutableStateOf<String?>(null) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var endpointDialog by remember { mutableStateOf(false) }
    var endpointDraft by remember { mutableStateOf(endpoint) }
    var welcome by remember { mutableStateOf(!endpointStore.guideSeen()) }

    suspend fun refresh() {
        try {
            services = api.services()
            error = null
        } catch (t: Throwable) {
            error = friendlyError(t)
        } finally {
            loading = false
        }
    }

    LaunchedEffect(endpoint) {
        loading = true
        refresh()
        while (true) {
            delay(2_500)
            refresh()
        }
    }

    Scaffold(
        containerColor = AxBackground,
        bottomBar = {
            NavigationBar(containerColor = AxSurface) {
                NavigationBarItem(
                    selected = tab == MainTab.HOME,
                    onClick = { tab = MainTab.HOME },
                    icon = { Text("●") },
                    label = { Text("首页") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.GUIDE,
                    onClick = { tab = MainTab.GUIDE },
                    icon = { Text("?") },
                    label = { Text("使用") }
                )
                NavigationBarItem(
                    selected = tab == MainTab.SETTINGS,
                    onClick = { tab = MainTab.SETTINGS },
                    icon = { Text("⚙") },
                    label = { Text("设置") }
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AxBackground)
        ) {
            when (tab) {
                MainTab.HOME -> HomeScreen(
                    endpoint = endpoint,
                    services = services,
                    loading = loading,
                    error = error,
                    onRefresh = { scope.launch { refresh() } },
                    onGuide = { tab = MainTab.GUIDE },
                    onToggle = { service ->
                        scope.launch {
                            runCatching {
                                if (service.status == "running" || service.status == "restarting") {
                                    api.stop(service.id)
                                } else {
                                    api.start(service.id)
                                }
                            }.onFailure { error = friendlyError(it) }
                            refresh()
                        }
                    },
                    onLogs = { service ->
                        scope.launch {
                            runCatching { api.logs(service.id) }
                                .onSuccess {
                                    logsFor = service.name
                                    logLines = it
                                }
                                .onFailure { error = friendlyError(it) }
                        }
                    }
                )

                MainTab.GUIDE -> GuideScreen(context)
                MainTab.SETTINGS -> SettingsScreen(
                    endpoint = endpoint,
                    onEditEndpoint = {
                        endpointDraft = endpoint
                        endpointDialog = true
                    },
                    onResetEndpoint = {
                        endpointStore.save(EndpointStore.DEFAULT_ENDPOINT)
                        endpoint = EndpointStore.DEFAULT_ENDPOINT
                        services = emptyList()
                        loading = true
                        error = null
                    },
                    onGuide = { tab = MainTab.GUIDE }
                )
            }
        }
    }

    if (welcome) {
        AlertDialog(
            onDismissRequest = {
                endpointStore.markGuideSeen()
                welcome = false
            },
            confirmButton = {
                Button(
                    onClick = {
                        endpointStore.markGuideSeen()
                        welcome = false
                        tab = MainTab.GUIDE
                    }
                ) { Text("开始设置") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        endpointStore.markGuideSeen()
                        welcome = false
                    }
                ) { Text("先看看首页") }
            },
            title = { Text("欢迎使用 Ax") },
            text = {
                Text("Ax 用来控制运行在 Termux 里的 Agent 和代理服务。第一次使用只需要按“使用”页的步骤配置一次。")
            }
        )
    }

    if (endpointDialog) {
        AlertDialog(
            onDismissRequest = { endpointDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = normalizeEndpoint(endpointDraft)
                        if (normalized == null) {
                            error = "连接地址无效，请填写完整的 http:// 或 https:// 地址。"
                        } else {
                            endpointStore.save(normalized)
                            endpoint = normalized
                            services = emptyList()
                            loading = true
                            error = null
                            endpointDialog = false
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { endpointDialog = false }) { Text("取消") }
            },
            title = { Text("Agent 连接地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it },
                        singleLine = true,
                        label = { Text("地址") },
                        placeholder = { Text(EndpointStore.DEFAULT_ENDPOINT) }
                    )
                    Text(
                        "如果 Ax Agent 和 App 都在同一台手机上，保持默认地址即可。",
                        color = AxMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        )
    }

    if (logsFor != null) {
        AlertDialog(
            onDismissRequest = { logsFor = null },
            confirmButton = {
                TextButton(onClick = { logsFor = null }) { Text("关闭") }
            },
            title = { Text("${logsFor.orEmpty()} · 日志") },
            text = {
                Surface(
                    color = Color(0xFF03050A),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 420.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (logLines.isEmpty()) {
                            item {
                                Text(
                                    "暂时没有日志。",
                                    color = AxMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            items(logLines) { line ->
                                Text(
                                    line,
                                    color = Color(0xFFD8E0F0),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }
            }
        )
    }
}

@Composable
private fun HomeScreen(
    endpoint: String,
    services: List<ServiceStatus>,
    loading: Boolean,
    error: String?,
    onRefresh: () -> Unit,
    onGuide: () -> Unit,
    onToggle: (ServiceStatus) -> Unit,
    onLogs: (ServiceStatus) -> Unit
) {
    val running = services.count { it.status == "running" }
    val online = error == null && !loading

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AxSurface),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_launcher_foreground),
                        contentDescription = "Ax",
                        modifier = Modifier.size(78.dp).clip(RoundedCornerShape(22.dp))
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ax 控制台", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (online) "● Agent 已连接" else "○ Agent 未连接",
                            color = if (online) AxSuccess else AxWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(endpoint, color = AxMuted, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("运行中", running.toString(), AxSuccess, Modifier.weight(1f))
                MetricCard("全部服务", services.size.toString(), AxBlueSoft, Modifier.weight(1f))
            }
        }

        if (error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1D18)),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("还没连接到 Agent", fontWeight = FontWeight.Bold, color = AxWarning)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "第一次使用请先打开“使用”页，按步骤在 Termux 里安装并启动 Ax Agent。",
                            color = Color(0xFFE6D6CB)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(error, color = AxMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onGuide) { Text("查看教程") }
                            OutlinedButton(onClick = onRefresh) { Text("重新连接") }
                        }
                    }
                }
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("服务", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh) { Text("刷新") }
            }
        }

        if (loading && services.isEmpty()) {
            item {
                Box(
                    Modifier.fillMaxWidth().padding(34.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
        } else if (services.isEmpty() && error == null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AxSurface),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("还没有服务", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        Text("服务由 Termux 中的 Ax Agent 配置管理。", color = AxMuted)
                    }
                }
            }
        } else {
            items(services, key = { it.id }) { service ->
                ServiceCard(service, { onToggle(service) }, { onLogs(service) })
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, accent: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AxSurfaceRaised),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = accent)
            Text(label, color = AxMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ServiceCard(service: ServiceStatus, onToggle: () -> Unit, onLogs: () -> Unit) {
    val running = service.status == "running"
    val active = running || service.status == "restarting"
    val statusColor = when (service.status) {
        "running" -> AxSuccess
        "restarting" -> AxWarning
        else -> AxMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AxSurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(service.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Text(serviceStatusText(service), color = statusColor)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        if (active) "已启用" else "已停止",
                        color = statusColor,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            if (running) {
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniInfo("PID", service.pid.toString(), Modifier.weight(1f))
                    MiniInfo("运行时间", formatUptime(service.uptimeSeconds), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniInfo("CPU", "%.1f%%".format(service.cpuPercent), Modifier.weight(1f))
                    MiniInfo("内存", formatBytes(service.memoryBytes), Modifier.weight(1f))
                }
            }

            if (!service.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(service.lastError, color = AxDanger, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    colors = if (active) {
                        ButtonDefaults.buttonColors(containerColor = Color(0xFF293246), contentColor = Color.White)
                    } else {
                        ButtonDefaults.buttonColors(containerColor = AxBlue)
                    }
                ) { Text(if (active) "停止" else "启动") }
                OutlinedButton(onClick = onLogs, modifier = Modifier.weight(1f)) { Text("查看日志") }
            }
        }
    }
}

@Composable
private fun MiniInfo(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = AxSurfaceRaised,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, color = AxMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun GuideScreen(context: Context) {
    val install = "pkg install -y git && git clone --depth=1 https://github.com/Wyun09/Ax.git && bash Ax/scripts/install-termux.sh"
    val start = "~/.local/bin/ax-start"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("第一次使用", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("照着做一次，以后打开 Ax 就能直接控制。", color = AxMuted)
        }
        item { GuideStep("1", "安装 Termux", "先安装并打开 Termux，Ax Agent 会运行在 Termux 中。") }
        item {
            CommandStep("2", "安装 Ax Agent", install, "复制安装命令") {
                copyText(context, "Ax 安装命令", install)
            }
        }
        item {
            CommandStep("3", "启动 Agent", start, "复制启动命令") {
                copyText(context, "Ax 启动命令", start)
            }
        }
        item { GuideStep("4", "回到首页", "看到“Agent 已连接”后，就可以启动 Claude Code Proxy 等服务。") }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AxSurface),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("默认地址", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(EndpointStore.DEFAULT_ENDPOINT, fontFamily = FontFamily.Monospace, color = AxBlueSoft)
                    Text("同一台手机使用时无需修改。", color = AxMuted)
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = AxSurface), shape = RoundedCornerShape(22.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(color = AxBlue, shape = RoundedCornerShape(12.dp)) {
                Text(number, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            Spacer(Modifier.size(13.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(5.dp))
                Text(description, color = AxMuted)
            }
        }
    }
}

@Composable
private fun CommandStep(number: String, title: String, command: String, buttonText: String, onCopy: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = AxSurface), shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = AxBlue, shape = RoundedCornerShape(12.dp)) {
                    Text(number, fontWeight = FontWeight.Black, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                }
                Spacer(Modifier.size(13.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFF05070B), shape = RoundedCornerShape(14.dp)) {
                Text(
                    command,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD7E0F3),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) { Text(buttonText) }
        }
    }
}

@Composable
private fun SettingsScreen(
    endpoint: String,
    onEditEndpoint: () -> Unit,
    onResetEndpoint: () -> Unit,
    onGuide: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("一般情况下不需要修改连接地址。", color = AxMuted)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AxSurface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Agent 连接地址", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(endpoint, color = AxBlueSoft, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEditEndpoint) { Text("修改") }
                        OutlinedButton(onClick = onResetEndpoint) { Text("恢复默认") }
                    }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = AxSurface), shape = RoundedCornerShape(22.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("后台通知", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "为解决部分手机启动闪退，0.2.1 暂时关闭自动前台服务。服务控制和状态刷新不受影响。",
                        color = AxMuted
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onGuide, modifier = Modifier.fillMaxWidth()) { Text("查看使用教程") }
        }
        item {
            Text("Ax 0.2.1", color = AxMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun copyText(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
}

private fun serviceStatusText(service: ServiceStatus): String = when (service.status) {
    "running" -> "● 运行中"
    "restarting" -> "↻ ${service.restartInSeconds} 秒后重启"
    else -> "○ 已停止"
}

private fun friendlyError(t: Throwable): String {
    val message = t.message.orEmpty()
    return when {
        message.contains("Connection refused", ignoreCase = true) -> "Agent 没有启动或端口 18766 无法连接。"
        message.contains("Failed to connect", ignoreCase = true) -> "无法连接 Ax Agent，请先在 Termux 中运行 ax-start。"
        message.contains("timeout", ignoreCase = true) -> "连接 Agent 超时。"
        else -> message.ifBlank { "无法连接 Ax Agent。" }
    }
}

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) "%dh %02dm".format(hours, minutes) else "%dm %02ds".format(minutes, secs)
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}
