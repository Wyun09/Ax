package com.wyun09.ax

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ServiceStatus(
    val id: String,
    val name: String,
    val status: String,
    val pid: Int,
    val uptimeSeconds: Long,
    val cpuPercent: Double,
    val memoryBytes: Long,
    val restartPolicy: String,
    val restartCount: Int,
    val restartInSeconds: Long,
    val lastError: String?
)

class AxApi(private val baseUrl: String = "http://127.0.0.1:18766") {
    suspend fun services(): List<ServiceStatus> = withContext(Dispatchers.IO) {
        val body = request("GET", "/v1/services")
        val array = JSONArray(body)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(
                    ServiceStatus(
                        id = item.getString("id"),
                        name = item.optString("name", item.getString("id")),
                        status = item.optString("status", "stopped"),
                        pid = item.optInt("pid", 0),
                        uptimeSeconds = item.optLong("uptime_seconds", 0),
                        cpuPercent = item.optDouble("cpu_percent", 0.0),
                        memoryBytes = item.optLong("memory_bytes", 0),
                        restartPolicy = item.optString("restart_policy", "never"),
                        restartCount = item.optInt("restart_count", 0),
                        restartInSeconds = item.optLong("restart_in_seconds", 0),
                        lastError = item.optString("last_error").takeIf { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun start(id: String) = withContext(Dispatchers.IO) {
        request("POST", "/v1/services/${encodeSegment(id)}/start")
    }

    suspend fun stop(id: String) = withContext(Dispatchers.IO) {
        request("POST", "/v1/services/${encodeSegment(id)}/stop")
    }

    suspend fun logs(id: String, tail: Int = 80): List<String> = withContext(Dispatchers.IO) {
        val body = request("GET", "/v1/services/${encodeSegment(id)}/logs?tail=$tail")
        val lines = JSONObject(body).getJSONArray("lines")
        buildList {
            for (i in 0 until lines.length()) add(lines.getString(i))
        }
    }

    private fun request(method: String, path: String): String {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = 2_500
            connection.readTimeout = 5_000
            connection.setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write("{}".toByteArray()) }
            }

            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("Ax agent returned HTTP $code: $text")
            }
            return text
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeSegment(value: String): String =
        java.net.URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")
}
