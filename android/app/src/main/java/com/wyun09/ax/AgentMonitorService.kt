package com.wyun09.ax

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AgentMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIFICATION_ID, buildNotification("正在连接 Ax Agent…"))
        scope.launch { monitorLoop() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun monitorLoop() {
        val notificationManager = getSystemService(NotificationManager::class.java)
        while (currentCoroutineContext().isActive) {
            val endpoint = EndpointStore(this).load()
            val result = runCatching { AxApi(endpoint).services() }
            val text = result.fold(
                onSuccess = { services ->
                    val running = services.count { it.status == "running" }
                    val restarting = services.count { it.status == "restarting" }
                    buildString {
                        append("Agent 已连接 · ")
                        append(running)
                        append(" 个服务运行中")
                        if (restarting > 0) {
                            append(" · ")
                            append(restarting)
                            append(" 个正在重启")
                        }
                    }
                },
                onFailure = { "Agent 未连接 · $endpoint" }
            )
            notificationManager.notify(NOTIFICATION_ID, buildNotification(text))
            delay(10_000)
        }
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ax Agent 状态",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示 Ax Agent 是否在线以及当前运行服务数量。"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_ax_status)
            .setContentTitle("Ax")
            .setContentText(text)
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "ax-agent-status"
        private const val NOTIFICATION_ID = 18766
    }
}
