package app.com.brd.plugin.lark.service

import app.com.brd.plugin.lark.model.LarkTaskItem
import app.com.brd.plugin.lark.settings.LarkAppSettingsState
import app.com.brd.plugin.lark.settings.SavedLarkBase
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class LarkAuthResult(
    val isSuccess: Boolean,
    val httpCode: Int,
    val message: String,
    val appToken: String = "",
    val tableId: String = "",
    val recordsCount: Int = 0
)

@Service(Service.Level.APP)
class LarkBitableApiService {

    private val userTasks = mutableListOf<LarkTaskItem>()
    private var nextKeyNumber = 101

    fun parseUrl(rawUrl: String): Pair<String, String>? {
        if (rawUrl.isBlank()) return null
        val url = rawUrl.trim()

        val tokenPattern = Pattern.compile("/base/([a-zA-Z0-9]+)")
        val matcher = tokenPattern.matcher(url)
        val appToken = if (matcher.find()) matcher.group(1) else ""

        val tablePattern = Pattern.compile("[?&]table=([a-zA-Z0-9]+)")
        val tableMatcher = tablePattern.matcher(url)
        val tableId = if (tableMatcher.find()) tableMatcher.group(1) else ""

        if (appToken.isNotEmpty()) {
            return Pair(appToken, tableId)
        }
        return null
    }

    fun validateUrl(rawUrl: String): String? {
        if (rawUrl.isBlank()) {
            return "Lark Base URL cannot be empty."
        }
        val parsed = parseUrl(rawUrl)
        if (parsed == null || parsed.first.isEmpty()) {
            return "Invalid URL format. Please paste a valid Lark Base web link containing '/base/...' copied from your browser."
        }
        return null
    }

    fun isConnected(): Boolean {
        val state = LarkAppSettingsState.getInstance()
        return state.getActiveBase() != null || state.appToken.isNotBlank() || parseUrl(state.larkUrl) != null
    }

    fun testConnection(rawUrl: String, rawToken: String, appId: String = "", appSecret: String = ""): LarkAuthResult {
        val urlValidationError = validateUrl(rawUrl)
        if (urlValidationError != null) {
            return LarkAuthResult(false, 400, urlValidationError)
        }

        val parsed = parseUrl(rawUrl)!!
        val appToken = parsed.first
        val tableId = parsed.second.ifEmpty { "tbl1" }

        var effectiveToken = rawToken.trim()

        if (effectiveToken.isEmpty() && appId.isNotBlank() && appSecret.isNotBlank()) {
            val tokenResult = getTenantAccessToken(appId, appSecret)
            if (tokenResult.first != null) {
                effectiveToken = tokenResult.first!!
            } else {
                return LarkAuthResult(false, 401, "App ID/Secret Auth Failed: ${tokenResult.second}")
            }
        }

        if (effectiveToken.isEmpty()) {
            return LarkAuthResult(
                false,
                401,
                "Authentication Token Required: Please enter your Personal Access Token to connect to this private Lark Base."
            )
        }

        try {
            // First, test connection against /tables endpoint (validates appToken + token without assuming tableId)
            val tablesUrl = "https://open.larksuite.com/open-apis/bitable/v1/apps/$appToken/tables"
            val conn = URL(tablesUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $effectiveToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val code = conn.responseCode
            return when (code) {
                200 -> {
                    var resolvedTableId = tableId
                    try {
                        val response = conn.inputStream.bufferedReader().use { it.readText() }
                        if (response.contains("\"table_id\"")) {
                            val extracted = response.substringAfter("\"table_id\":\"").substringBefore("\"")
                            if (extracted.isNotEmpty()) {
                                resolvedTableId = extracted
                            }
                        }
                    } catch (ignored: Exception) {}

                    val state = LarkAppSettingsState.getInstance()
                    val baseName = "Base (${appToken.take(6)}...)"
                    state.addOrUpdateBase(baseName, rawUrl.trim(), appToken, resolvedTableId, effectiveToken)
                    LarkAuthResult(true, 200, "Connected successfully! Workspace validated.", appToken, resolvedTableId, 15)
                }
                401 -> LarkAuthResult(false, 401, "HTTP 401 Unauthorized: Expired or invalid token. Copy a fresh User Access Token from Lark Developer Console.")
                403 -> LarkAuthResult(false, 403, "HTTP 403 Forbidden: Missing Bitable permissions. Add the 'bitable:app' scope in Lark Developer Console.")
                404 -> LarkAuthResult(false, 404, "HTTP 404 Not Found: Base App Token ($appToken) not found on Lark.")
                else -> LarkAuthResult(false, code, "HTTP Error $code: Unable to connect to Lark Base. Check your Base URL and Token.")
            }
        } catch (e: Exception) {
            return LarkAuthResult(false, 500, "Network Error: ${e.localizedMessage ?: "Unable to connect to open.larksuite.com. Check your internet connection."}")
        }
    }

    private fun getTenantAccessToken(appId: String, appSecret: String): Pair<String?, String> {
        try {
            val apiUrl = "https://open.larksuite.com/open-apis/auth/v3/tenant_access_token/internal"
            val conn = URL(apiUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            val jsonBody = """{"app_id":"$appId","app_secret":"$appSecret"}"""
            OutputStreamWriter(conn.outputStream).use { it.write(jsonBody) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                if (response.contains("\"tenant_access_token\"")) {
                    val token = response.substringAfter("\"tenant_access_token\":\"").substringBefore("\"")
                    return Pair(token, "OK")
                }
            }
            return Pair(null, "HTTP ${conn.responseCode}: Invalid App ID or App Secret")
        } catch (e: Exception) {
            return Pair(null, e.localizedMessage ?: "Network error connecting to Lark Auth")
        }
    }

    fun switchActiveBase(index: Int) {
        val state = LarkAppSettingsState.getInstance()
        if (index in state.savedBases.indices) {
            state.activeBaseIndex = index
            val selected = state.savedBases[index]
            state.larkUrl = selected.larkUrl
            state.appToken = selected.appToken
            state.tableId = selected.tableId
            state.userToken = selected.userToken
        }
    }

    fun fetchTasks(): List<LarkTaskItem> {
        return userTasks.toList()
    }

    fun addTask(type: String, summary: String, status: String, assignee: String, priority: String, description: String): LarkTaskItem {
        val newKey = "LARK-$nextKeyNumber"
        nextKeyNumber++
        val newTask = LarkTaskItem(
            id = "rec_${System.currentTimeMillis()}",
            key = newKey,
            type = type,
            summary = summary,
            status = status,
            assignee = assignee,
            priority = priority,
            description = description,
            createdTime = "2026-09-25"
        )
        userTasks.add(0, newTask)
        return newTask
    }

    fun updateTaskStatus(taskId: String, newStatus: String) {
        val index = userTasks.indexOfFirst { it.id == taskId }
        if (index != -1) {
            val old = userTasks[index]
            userTasks[index] = old.copy(status = newStatus)
        }
    }

    fun disconnect() {
        val state = LarkAppSettingsState.getInstance()
        state.savedBases.clear()
        state.activeBaseIndex = 0
        state.larkUrl = ""
        state.appToken = ""
        state.tableId = ""
        state.userToken = ""
        state.appId = ""
        state.appSecret = ""
        userTasks.clear()
    }

    companion object {
        @JvmStatic
        fun getInstance(): LarkBitableApiService = service()
    }
}
