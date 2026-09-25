package com.tss.lark.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent

class TokenHelpDialog(project: Project?) : DialogWrapper(project) {

    init {
        title = "Where to Get Your Personal Access Token"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val openConsoleBtn = JButton("Open Lark Developer Console").apply {
            addActionListener {
                BrowserUtil.browse("https://open.larksuite.com/app")
            }
        }

        return panel {
            group("How to Get Your Access Token") {
                row {
                    label("<html><ol>" +
                            "<li>Open the <b>Lark Developer Console</b> (open.larksuite.com/app).</li>" +
                            "<li>Select your App, go to <b>Permissions & Scopes</b>, and add <code>bitable:app</code> scope.</li>" +
                            "<li>Go to <b>Credentials & Test Notes</b> or <b>API Explorer</b>.</li>" +
                            "<li><b>Tenant Access Token</b> (<code>t-...</code>): Copy this if your app is 'Pending release' or if Personal Token returns HTTP 400.</li>" +
                            "<li><b>User Access Token</b> (<code>u-...</code>): Copy this if your app is published with user permissions.</li>" +
                            "<li>Paste the token into the access token field in Android Studio.</li>" +
                            "</ol></html>")
                }
            }

            row {
                cell(openConsoleBtn)
            }
        }
    }
}
