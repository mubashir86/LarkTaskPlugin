package app.com.brd.plugin.lark.service

import app.com.brd.plugin.lark.model.LarkFieldInfo
import app.com.brd.plugin.lark.model.LarkFieldOption
import app.com.brd.plugin.lark.model.LarkRecord
import app.com.brd.plugin.lark.model.LarkTableInfo
import app.com.brd.plugin.lark.model.LarkTaskItem
import app.com.brd.plugin.lark.settings.LarkAppSettingsState
import app.com.brd.plugin.lark.settings.SavedLarkBase
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
    val recordsCount: Int = 0,
    val rawResponseBody: String = "",
    val detailLog: String = ""
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
            val responseText = try {
                if (code in 200..299) {
                    conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
                } else {
                    conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                }
            } catch (e: Exception) {
                ""
            }

            val tokenPrefix = if (effectiveToken.length > 8) effectiveToken.take(8) + "..." else "short"
            val detailLog = "GET $tablesUrl\nHTTP Code: $code\nToken Prefix: $tokenPrefix\nResponse Body:\n$responseText"
            LOG.info("Lark Connection Test Result:\n$detailLog")

            return when (code) {
                200 -> {
                    var resolvedTableId = tableId
                    if (responseText.contains("\"table_id\"")) {
                        val extracted = responseText.substringAfter("\"table_id\":\"").substringBefore("\"")
                        if (extracted.isNotEmpty()) {
                            resolvedTableId = extracted
                        }
                    }

                    val state = LarkAppSettingsState.getInstance()
                    val baseName = "Base (${appToken.take(6)}...)"
                    state.addOrUpdateBase(baseName, rawUrl.trim(), appToken, resolvedTableId, effectiveToken)
                    LarkAuthResult(
                        isSuccess = true,
                        httpCode = 200,
                        message = "Connected successfully! Workspace validated.",
                        appToken = appToken,
                        tableId = resolvedTableId,
                        recordsCount = 15,
                        rawResponseBody = responseText,
                        detailLog = detailLog
                    )
                }
                400 -> {
                    val errorDetail = extractLarkErrorMsg(responseText)
                    val msg = buildString {
                        append("HTTP 400 Bad Request")
                        if (errorDetail != null) append(": ").append(errorDetail)
                        else append(": Unable to connect to Lark Base.")
                        append("\n💡 Tip: If using a Personal/User Access Token, try switching to a Tenant Access Token (t-...) from Lark Developer Console -> Credentials / API Explorer.")
                    }
                    LarkAuthResult(false, 400, msg, rawResponseBody = responseText, detailLog = detailLog)
                }
                401 -> {
                    val errorDetail = extractLarkErrorMsg(responseText)
                    val msg = buildString {
                        append("HTTP 401 Unauthorized")
                        if (errorDetail != null) append(": ").append(errorDetail)
                        else append(": Expired or invalid token.")
                        append("\n💡 Tip: Copy a fresh Tenant Access Token (t-...) or User Access Token (u-...) from Lark Developer Console.")
                    }
                    LarkAuthResult(false, 401, msg, rawResponseBody = responseText, detailLog = detailLog)
                }
                403 -> {
                    val errorDetail = extractLarkErrorMsg(responseText)
                    val msg = buildString {
                        append("HTTP 403 Forbidden")
                        if (errorDetail != null) append(": ").append(errorDetail)
                        else append(": Missing Bitable permissions or app is in 'Pending release' mode.")
                        append("\n💡 Tip: Add 'bitable:app' scope in Lark Developer Console and test with a Tenant Access Token (t-...).")
                    }
                    LarkAuthResult(false, 403, msg, rawResponseBody = responseText, detailLog = detailLog)
                }
                404 -> {
                    val errorDetail = extractLarkErrorMsg(responseText)
                    val msg = "HTTP 404 Not Found: Base App Token ($appToken) not found on Lark." +
                            if (errorDetail != null) " ($errorDetail)" else ""
                    LarkAuthResult(false, 404, msg, rawResponseBody = responseText, detailLog = detailLog)
                }
                else -> {
                    val errorDetail = extractLarkErrorMsg(responseText)
                    val msg = "HTTP Error $code: " + (errorDetail ?: "Unable to connect to Lark Base. Check your Base URL and Token.")
                    LarkAuthResult(false, code, msg, rawResponseBody = responseText, detailLog = detailLog)
                }
            }
        } catch (e: Exception) {
            val logMsg = "Network Error: ${e.localizedMessage ?: "Unable to connect to open.larksuite.com"}"
            LOG.warn("Lark Connection Test Error", e)
            return LarkAuthResult(false, 500, logMsg, detailLog = logMsg)
        }
    }

    private fun extractLarkErrorMsg(json: String): String? {
        if (json.isBlank()) return null
        return try {
            val codeStr = if (json.contains("\"code\":")) json.substringAfter("\"code\":").substringBefore(",").substringBefore("}").trim() else ""
            val msgStr = if (json.contains("\"msg\":")) json.substringAfter("\"msg\":\"").substringBefore("\"") else ""
            if (codeStr.isNotEmpty() || msgStr.isNotEmpty()) {
                "Lark Code $codeStr: $msgStr"
            } else null
        } catch (e: Exception) {
            null
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

    fun fetchTables(appToken: String, token: String): Result<List<LarkTableInfo>> {
        val url = "https://open.larksuite.com/open-apis/bitable/v1/apps/$appToken/tables"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val code = conn.responseCode
            val text = if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code == 200) {
                val tables = mutableListOf<LarkTableInfo>()
                val json = JsonParser.parseString(text).asJsonObject
                val data = json.getAsJsonObject("data")
                if (data != null && data.has("items")) {
                    val items = data.getAsJsonArray("items")
                    for (item in items) {
                        val obj = item.asJsonObject
                        val tableId = obj.get("table_id")?.asString ?: ""
                        val name = obj.get("name")?.asString ?: "Table"
                        if (tableId.isNotEmpty()) {
                            tables.add(LarkTableInfo(tableId, name))
                        }
                    }
                }
                Result.success(tables)
            } else {
                Result.failure(Exception("HTTP $code: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchFields(appToken: String, tableId: String, token: String): Result<List<LarkFieldInfo>> {
        val url = "https://open.larksuite.com/open-apis/bitable/v1/apps/$appToken/tables/$tableId/fields"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 6000
            conn.readTimeout = 6000

            val code = conn.responseCode
            val text = if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code == 200) {
                val fields = mutableListOf<LarkFieldInfo>()
                val json = JsonParser.parseString(text).asJsonObject
                val data = json.getAsJsonObject("data")
                if (data != null && data.has("items")) {
                    val items = data.getAsJsonArray("items")
                    for (item in items) {
                        val obj = item.asJsonObject
                        val fieldId = obj.get("field_id")?.asString ?: ""
                        val fieldName = obj.get("field_name")?.asString ?: ""
                        val type = obj.get("type")?.asInt ?: 1
                        val isPrimary = obj.get("is_primary")?.asBoolean ?: false

                        val optionsList = mutableListOf<LarkFieldOption>()
                        val propObj = obj.getAsJsonObject("property")
                        if (propObj != null && propObj.has("options")) {
                            val optsArray = propObj.getAsJsonArray("options")
                            for (opt in optsArray) {
                                val optObj = opt.asJsonObject
                                val optId = optObj.get("id")?.asString ?: ""
                                val optName = optObj.get("name")?.asString ?: ""
                                if (optName.isNotEmpty()) {
                                    optionsList.add(LarkFieldOption(optId, optName))
                                }
                            }
                        }

                        val isReadOnly = type in setOf(20, 1001, 1002, 1003, 1004, 1005)

                        if (fieldName.isNotEmpty()) {
                            fields.add(LarkFieldInfo(fieldId, fieldName, type, isPrimary, optionsList, isReadOnly))
                        }
                    }
                }
                Result.success(fields)
            } else {
                Result.failure(Exception("HTTP $code: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun fetchRecords(appToken: String, tableId: String, token: String): Result<Pair<List<LarkFieldInfo>, List<LarkRecord>>> {
        val fieldsResult = fetchFields(appToken, tableId, token)
        val fields = fieldsResult.getOrDefault(emptyList())

        val url = "https://open.larksuite.com/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records?page_size=100"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val code = conn.responseCode
            val text = if (code == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code == 200) {
                val records = mutableListOf<LarkRecord>()
                val json = JsonParser.parseString(text).asJsonObject
                val data = json.getAsJsonObject("data")
                if (data != null && data.has("items")) {
                    val items = data.getAsJsonArray("items")
                    for (item in items) {
                        val obj = item.asJsonObject
                        val recordId = obj.get("record_id")?.asString ?: ""
                        val fieldsObj = obj.getAsJsonObject("fields")
                        val fieldsMap = mutableMapOf<String, Any?>()

                        if (fieldsObj != null) {
                            for ((key, value) in fieldsObj.entrySet()) {
                                fieldsMap[key] = parseJsonValue(value)
                            }
                        }
                        records.add(LarkRecord(recordId, fieldsMap))
                    }
                }
                val mergedFields = mergeFieldsAndRecords(fields, records)
                Result.success(Pair(mergedFields, records))
            } else {
                Result.failure(Exception("HTTP $code: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun mergeFieldsAndRecords(fetchedFields: List<LarkFieldInfo>, records: List<LarkRecord>): List<LarkFieldInfo> {
        val fieldMap = fetchedFields.associateBy { it.fieldName }.toMutableMap()
        val recordKeys = records.flatMap { it.fields.keys }.distinct()

        for (key in recordKeys) {
            val existing = fieldMap[key]
            val sampleVal = records.mapNotNull { it.fields[key] }.firstOrNull()

            val inferredType = when (sampleVal) {
                is Boolean -> 7
                is Number -> if (sampleVal.toLong() > 1_000_000_000_000L) 5 else 2
                is List<*> -> 4
                else -> 1
            }

            val distinctValues = mutableSetOf<String>()
            for (rec in records) {
                val rawVal = rec.fields[key] ?: continue
                when (rawVal) {
                    is String -> if (rawVal.isNotBlank()) distinctValues.add(rawVal.trim())
                    is List<*> -> {
                        rawVal.forEach { item ->
                            when (item) {
                                is Map<*, *> -> {
                                    val name = item["name"]?.toString() ?: item["text"]?.toString()
                                    if (!name.isNullOrEmpty()) distinctValues.add(name.trim())
                                }
                                is String -> if (item.isNotBlank()) distinctValues.add(item.trim())
                            }
                        }
                    }
                    is Map<*, *> -> {
                        val name = rawVal["name"]?.toString() ?: rawVal["text"]?.toString()
                        if (!name.isNullOrEmpty()) distinctValues.add(name.trim())
                    }
                }
            }

            val optionObjs = distinctValues.map { LarkFieldOption(it, it) }

            if (existing != null) {
                val mergedOpts = if (existing.options.isEmpty() && optionObjs.isNotEmpty()) optionObjs else existing.options
                fieldMap[key] = existing.copy(options = mergedOpts)
            } else {
                fieldMap[key] = LarkFieldInfo(
                    fieldId = key,
                    fieldName = key,
                    type = inferredType,
                    isPrimary = (key == recordKeys.firstOrNull()),
                    options = optionObjs,
                    isReadOnly = false
                )
            }
        }

        val orderedFields = mutableListOf<LarkFieldInfo>()
        for (f in fetchedFields) {
            fieldMap[f.fieldName]?.let { orderedFields.add(it) }
        }
        for (key in recordKeys) {
            if (orderedFields.none { it.fieldName == key }) {
                fieldMap[key]?.let { orderedFields.add(it) }
            }
        }

        return if (orderedFields.isNotEmpty()) orderedFields else fetchedFields
    }

    private fun parseJsonValue(element: com.google.gson.JsonElement?): Any? {
        if (element == null || element.isJsonNull) return null
        return when {
            element.isJsonPrimitive -> {
                val prim = element.asJsonPrimitive
                when {
                    prim.isBoolean -> prim.asBoolean
                    prim.isNumber -> prim.asNumber
                    else -> prim.asString
                }
            }
            element.isJsonArray -> {
                val arr = element.asJsonArray
                arr.map { parseJsonValue(it) }
            }
            element.isJsonObject -> {
                val obj = element.asJsonObject
                val map = mutableMapOf<String, Any?>()
                for ((k, v) in obj.entrySet()) {
                    map[k] = parseJsonValue(v)
                }
                map
            }
            else -> element.toString()
        }
    }

    fun createRecord(appToken: String, tableId: String, token: String, fieldsMap: Map<String, Any?>): Result<Boolean> {
        val url = "https://open.larksuite.com/open-apis/bitable/v1/apps/$appToken/tables/$tableId/records"
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val gson = Gson()
            val payload = mapOf("fields" to fieldsMap)
            val jsonBody = gson.toJson(payload)

            OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(jsonBody) }

            val code = conn.responseCode
            val text = if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }

            if (code in 200..299) {
                Result.success(true)
            } else {
                Result.failure(Exception("HTTP $code: $text"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private val LOG = Logger.getInstance(LarkBitableApiService::class.java)

        @JvmStatic
        fun getInstance(): LarkBitableApiService = service()
    }
}
