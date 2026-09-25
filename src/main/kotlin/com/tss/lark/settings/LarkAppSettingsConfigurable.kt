package com.tss.lark.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class LarkAppSettingsConfigurable : Configurable {

    private var myMainPanel: DialogPanel? = null
    private val state = LarkAppSettingsState.getInstance()

    override fun getDisplayName(): String {
        return "Lark Connector"
    }

    override fun createComponent(): JComponent {
        val panel = panel {
            group("Lark Base Workspace Connection") {
                row("Base URL:") {
                    textField()
                        .bindText(state::larkUrl)
                        .align(AlignX.FILL)
                        .comment("Paste the shareable URL of your Lark Base workspace.")
                }
                row("Personal Access Token (Optional):") {
                    passwordField()
                        .bindText(state::userToken)
                        .align(AlignX.FILL)
                        .comment("Enter a Lark personal access token or user token for API synchronization.")
                }
            }

            group("Extracted Parameters") {
                row("Base App Token:") {
                    textField()
                        .bindText(state::appToken)
                        .align(AlignX.FILL)
                }
                row("Table ID:") {
                    textField()
                        .bindText(state::tableId)
                        .align(AlignX.FILL)
                }
            }

            group("Lark Developer App Credentials (Optional)") {
                row("App ID:") {
                    textField()
                        .bindText(state::appId)
                        .align(AlignX.FILL)
                }
                row("App Secret:") {
                    passwordField()
                        .bindText(state::appSecret)
                        .align(AlignX.FILL)
                }
            }
        }
        myMainPanel = panel
        return panel
    }

    override fun isModified(): Boolean {
        return myMainPanel?.isModified() ?: false
    }

    override fun apply() {
        myMainPanel?.apply()
        LarkAppSettingsNotifier.notifySettingsChanged()
    }

    override fun reset() {
        myMainPanel?.reset()
    }

    override fun disposeUIResources() {
        myMainPanel = null
    }
}
