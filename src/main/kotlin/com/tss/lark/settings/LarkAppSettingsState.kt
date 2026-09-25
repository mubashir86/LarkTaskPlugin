package com.tss.lark.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.xmlb.XmlSerializerUtil

data class SavedLarkBase(
    var name: String = "Lark Base",
    var larkUrl: String = "",
    var appToken: String = "",
    var tableId: String = "",
    var userToken: String = ""
)

@Service(Service.Level.APP)
@State(
    name = "com.tss.lark.settings.LarkAppSettingsState",
    storages = [Storage("LarkTaskSettings.xml")]
)
class LarkAppSettingsState : PersistentStateComponent<LarkAppSettingsState> {

    var savedBases: MutableList<SavedLarkBase> = mutableListOf()
    var activeBaseIndex: Int = 0

    var larkUrl: String = ""
    var appToken: String = ""
    var tableId: String = ""
    var userToken: String = ""
    var appId: String = ""
    var appSecret: String = ""

    override fun getState(): LarkAppSettingsState {
        return this
    }

    override fun loadState(state: LarkAppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    fun getActiveBase(): SavedLarkBase? {
        if (savedBases.isNotEmpty() && activeBaseIndex in savedBases.indices) {
            return savedBases[activeBaseIndex]
        }
        if (larkUrl.isNotBlank()) {
            return SavedLarkBase("Current Base", larkUrl, appToken, tableId, userToken)
        }
        return null
    }

    fun addOrUpdateBase(name: String, url: String, appTokenStr: String, tableIdStr: String, tokenStr: String) {
        val newBase = SavedLarkBase(name, url, appTokenStr, tableIdStr, tokenStr)
        val existingIndex = savedBases.indexOfFirst { it.appToken == appTokenStr || it.larkUrl == url }
        if (existingIndex != -1) {
            savedBases[existingIndex] = newBase
            activeBaseIndex = existingIndex
        } else {
            savedBases.add(newBase)
            activeBaseIndex = savedBases.size - 1
        }
        larkUrl = url
        appToken = appTokenStr
        tableId = tableIdStr
        userToken = tokenStr
    }

    companion object {
        const val DEFAULT_URL = "https://www.larksuite.com"

        @JvmStatic
        fun getInstance(): LarkAppSettingsState {
            return service()
        }
    }
}
