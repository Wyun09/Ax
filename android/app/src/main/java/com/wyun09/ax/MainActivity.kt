package com.wyun09.ax

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
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
import androidx.compose.foundation.layout.weight
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
private val AxBlueSoft = Color(0xFF9EB7FF)
private val AxBackground = Color(0xFF070910)
private val AxSurface = Color(0xFF101522)
private val AxSurfaceRaised = Color(0xFF171D2B)
private val AxSuccess = Color(0xFF65D79D)
private val AxWarning = Color(0xFFFFC56B)
private val AxTextMuted = Color(0xFF9AA5B8)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startAgentMonitor()
        setContent { AxTheme { AxApp() } }
    }

    private fun startAgentMonitor() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_PERMISSION_REQUEST)
        }
        startForegroundService(Intent(this, AgentMonitorService::class.java))
    }

    companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST = 18766
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
            surfaceVariant = AxSurfaceRaised
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
    var logsTitle by remember { mutableStateOf<String?>(null) }
    var logLines by remember { mutableStateOf<List<String>>(emptyList()) }
    var showEndpointDialog by remember { mutableStateOf(false) }
    var endpointDraft by remember { mutableStateOf(endpoint) }
    var showWelcome by remember { mutableStateOf(!endpointStore.guideSeen()) }

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
                    onEditEndpoint = {
                        endpointDraft = endpoint
                        showEndpointDialog = true
                    },
                    onToggle = { service ->
                        scope.launch {
                            runCatching {
                                if (service.status == "running" || service.status == "restarting") {
                                    api.stop(service.id)
                                } else {
                                    api.start(service.id)
                                }
                            }.onFailure { error = it.message }
                            refresh()
                        }
                    },
                    onLogs = { service ->
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

                MainTab.GUIDE -> GuideScreen(context)

                MainTab.SETTINGS -> SettingsScreen(
                    endpoint = endpoint,
                    onEditEndpoint = {
                        endpointDraft = endpoint
                        showEndpointDialog = true
                    },
                    onResetEndpoint = {
                        endpointStore.save(EndpointStore.DEFAULT_ENDPOINT)
                        endpoint = EndpointStore.DEFAULT_ENDPOINT
                        services = emptyList()
                        loading = true
                        error = null
                    },
                    onShowGuide = { showWelcome = true }
                )
            }
        }
    }

    if (showWelcome) {
        WelcomeDialog(
            onDismiss = {
                endpointStore.markGuideSeen()
                showWelcome = false
            },
            onOpenGuide = {
                endpointStore.markGuideSeen()
                showWelcome = false
                tab = MainTab.GUIDE
            }
        )
    }

    if (showEndpointDialog) {
        AlertDialog(
            onDismissRequest = { showEndpointDialog = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val normalized = normalizeEndpoint(endpointDraft)
                        if (normalized == null) {
                            error = "连接地址无效，请填写以 http:// 或 https:// 开头的完整地址。"
                        } else {
                            endpointStore.save(normalized)
                            endpoint = normalized
                            services = emptyList()
                            loading = true
                            error = null
                            showEndpointDialog = false
                        }
                    }
                ) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showEndpointDialog = false }) { Text("取消") }
            },
            title = { Text("连接地址") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = endpointDraft,
                        onValueChange = { endpointDraft = it },
                        singleLine = true,
                        label = { Text("Ax Agent 地址") },
                        placeholder = { Text(EndpointStore.DEFAULT_ENDPOINT) }
                    )
                    Text(
                        "同一台手机使用 Termux 时，保持默认地址即可。",
                        style = MaterialTheme.typography.bodySmall,
                        color = AxTextMuted
                    )
                }
            }
        )
    }

    if (logsTitle != null) {
        AlertDialog(
            onDismissRequest = { logsTitle = null },
            confirmButton = { TextButton(onClick = { logsTitle = null }) { Text("关闭") } },
            title = { Text("${logsTitle.orEmpty()} · 日志") },
            text = {
                Surface(
                    color = Color(0xFF05070B),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (logLines.isEmpty()) {
                            item {
                                Text(
                                    "暂时没有日志。",
                                    color = AxTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        } else {
                            items(logLines) { line ->
                                Text(
                                    line,
                                    color = Color(0xFFD6DEEE),
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
    onEditEndpoint: () -> Unit,
    onToggle: (ServiceStatus) -> Unit,
    onLogs: (ServiceStatus) -> Unit
) {
    val running = services.count { it.status == "running" }
    val restarting = services.count { it.status == "restarting" }
    val online = error == null && !loading

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AxSurface),
                shape = RoundedCornerShape(28.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.ax_logo),
                        contentDescription = "Ax",
                        modifier = Modifier
                            .size(76.dp)
                            .clip(RoundedCornerShape(22.dp))
                    )
                    Spacer(Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Ax 控制台",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            if (online) "● Agent 已连接" else "○ Agent 未连接",
                            color = if (online) AxSuccess else AxWarning,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            endpoint,
                            color = AxTextMuted,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard("运行中", running.toString(), AxSuccess, Modifier.weight(1f))
                MetricCard("重启中", restarting.toString(), AxWarning, Modifier.weight(1f))
                MetricCard("服务", services.size.toString(), AxBlueSoft, Modifier.weight(1f))
            }
        }

        if (error != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1D18)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("暂时连接不到 Ax Agent", fontWeight = FontWeight.Bold, color = AxWarning)
                        Spacer(Modifier.height(5.dp))
                        Text(
                            "请打开 Termux，确认已安装 Ax，并执行 ax-start。",
                            color = Color(0xFFE4D3C7),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(error, color = AxTextMuted, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onRefresh) { Text("重新连接") }
                            OutlinedButton(onClick = onEditEndpoint) { Text("连接设置") }
                        }
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(36.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        } else if (services.isEmpty() && error == null) {
            item {
                EmptyServicesCard()
            }
        } else {
            items(services, key = { it.id }) { service ->
                ServiceCard(
                    service = service,
                    onToggle = { onToggle(service) },
                    onLogs = { onLogs(service) }
                )
            }
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = AxSurfaceRaised),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = accent)
            Text(label, style = MaterialTheme.typography.bodySmall, color = AxTextMuted)
        }
    }
}

@Composable
private fun EmptyServicesCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = AxSurface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Text("还没有服务", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(5.dp))
            Text(
                "服务由 Termux 中的 ~/.config/ax/ax.json 管理。默认模板已经包含 Claude Code Proxy。",
                color = AxTextMuted
            )
        }
    }
}

@Composable
private fun ServiceCard(
    service: ServiceStatus,
    onToggle: () -> Unit,
    onLogs: () -> Unit
) {
    val running = service.status == "running"
    val active = running || service.status == "restarting"
    val statusColor = when (service.status) {
        "running" -> AxSuccess
        "restarting" -> AxWarning
        else -> AxTextMuted
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AxSurface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(service.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(serviceStatusText(service), color = statusColor, fontWeight = FontWeight.Medium)
                }
                Surface(
                    color = statusColor.copy(alpha = 0.14f),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = if (active) "已启用" else "已停止",
                        color = statusColor,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp)
                    )
                }
            }

            if (running) {
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniInfo("PID", service.pid.toString(), Modifier.weight(1f))
                    MiniInfo("运行时间", formatUptime(service.uptimeSeconds), Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MiniInfo("CPU", "${"%.1f".format(service.cpuPercent)}%", Modifier.weight(1f))
                    MiniInfo("内存", formatBytes(service.memoryBytes), Modifier.weight(1f))
                }
            }

            if (service.restartPolicy != "never") {
                Spacer(Modifier.height(10.dp))
                Text(
                    "自动重启：${restartPolicyText(service.restartPolicy)} · 已重启 ${service.restartCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = AxTextMuted
                )
            }

            if (!service.lastError.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "最近错误：${service.lastError}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.weight(1f),
                    colors = if (active) {
                        ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF293246),
                            contentColor = Color.White
                        )
                    } else {
                        ButtonDefaults.buttonColors(containerColor = AxBlue)
                    }
                ) {
                    Text(if (active) "停止" else "启动")
                }
                OutlinedButton(onClick = onLogs, modifier = Modifier.weight(1f)) {
                    Text("查看日志")
                }
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
            Text(label, color = AxTextMuted, style = MaterialTheme.typography.labelSmall)
            Text(value, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun GuideScreen(context: Context) {
    val installCommand =
        "pkg install -y git && git clone --depth=1 https://github.com/Wyun09/Ax.git && bash Ax/scripts/install-termux.sh"
    val startCommand = "~/.local/bin/ax-start"

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("第一次使用", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("照着下面做一次，以后打开 Ax 就能直接控制。", color = AxTextMuted)
        }
        item {
            GuideStep(
                number = "1",
                title = "安装 Termux",
                description = "先在手机上准备好 Termux。Ax 的后台 Agent 会运行在 Termux 里。"
            )
        }
        item {
            CommandStep(
                number = "2",
                title = "安装 Ax Agent",
                command = installCommand,
                buttonText = "复制安装命令",
                onCopy = { copyText(context, "Ax 安装命令", installCommand) }
            )
        }
        item {
            CommandStep(
                number = "3",
                title = "启动 Agent",
                command = startCommand,
                buttonText = "复制启动命令",
                onCopy = { copyText(context, "Ax 启动命令", startCommand) }
            )
        }
        item {
            GuideStep(
                number = "4",
                title = "回到首页",
                description = "默认连接地址是 127.0.0.1:18766。看到“Agent 已连接”以后，就可以启动 Claude Code Proxy 等服务。"
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111D35)),
                shape = RoundedCornerShape(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Claude Code Proxy", fontWeight = FontWeight.Bold, color = AxBlueSoft)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "默认配置已经包含 claude-code-proxy serve --port 18765。只要 Termux 里已经安装 claude-code-proxy，首页就能直接启动。",
                        color = Color(0xFFC9D7F6)
                    )
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, title: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AxSurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            Surface(color = AxBlue, shape = RoundedCornerShape(12.dp)) {
                Text(
                    number,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
            Spacer(Modifier.size(13.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(description, color = AxTextMuted)
            }
        }
    }
}

@Composable
private fun CommandStep(
    number: String,
    title: String,
    command: String,
    buttonText: String,
    onCopy: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AxSurface),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = AxBlue, shape = RoundedCornerShape(12.dp)) {
                    Text(
                        number,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
                Spacer(Modifier.size(13.dp))
                Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(12.dp))
            Surface(color = Color(0xFF05070B), shape = RoundedCornerShape(14.dp)) {
                Text(
                    command,
                    modifier = Modifier.padding(12.dp),
                    color = Color(0xFFD6DEEE),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
                Text(buttonText)
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    endpoint: String,
    onEditEndpoint: () -> Unit,
    onResetEndpoint: () -> Unit,
    onShowGuide: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 22.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Text("一般情况下不需要修改这里。", color = AxTextMuted)
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AxSurface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Agent 连接地址", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(endpoint, color = AxBlueSoft, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onEditEndpoint) { Text("修改") }
                        OutlinedButton(onClick = onResetEndpoint) { Text("恢复本机地址") }
                    }
                }
            }
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AxSurface),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("后台状态通知", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Ax 会通过低优先级前台通知显示 Agent 是否在线和当前运行服务数量。",
                        color = AxTextMuted
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onShowGuide, modifier = Modifier.fillMaxWidth()) {
                Text("重新查看使用教程")
            }
        }
        item {
            Text(
                "Ax 0.2.0 · Agent eXecution",
                modifier = Modifier.fillMaxWidth(),
                color = AxTextMuted,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun WelcomeDialog(
    onDismiss: () -> Unit,
    onOpenGuide: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onOpenGuide) { Text("开始设置") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("稍后再说") }
        },
        title = { Text("欢迎使用 Ax") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ax 用来控制运行在 Termux 里的 AI 服务。")
                Text("第一次使用需要先安装并启动 Ax Agent，整个过程只需要几条命令。", color = AxTextMuted)
            }
        }
    )
}

private fun serviceStatusText(service: ServiceStatus): String = when (service.status) {
    "running" -> "● 运行中"
    "restarting" -> "↻ ${service.restartInSeconds} 秒后自动重启"
    else -> "○ 已停止"
}

private fun restartPolicyText(policy: String): String = when (policy) {
    "always" -> "始终"
    "on-failure" -> "异常退出时"
    else -> "关闭"
}

private fun formatUptime(seconds: Long): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hours > 0 -> "${hours}小时 ${minutes}分"
        minutes > 0 -> "${minutes}分 ${secs}秒"
        else -> "${secs}秒"
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) "%.2f GB".format(mb / 1024.0) else "%.1f MB".format(mb)
}

private fun copyText(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
