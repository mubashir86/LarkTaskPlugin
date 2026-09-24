package app.com.brd.plugin.lark.ui

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
            group("How to Generate Your Access Token") {
                row {
                    label("<html><ol>" +
                            "<li>Open the <b>Lark Developer Console</b> (open.larksuite.com/app).</li>" +
                            "<li>Select your existing App or create a new internal app.</li>" +
                            "<li>In the left menu, click <b>Permissions & Scopes</b>.</li>" +
                            "<li>Grant the <b>View, edit, and manage Bitable</b> permission (<code>bitable:app</code>).</li>" +
                            "<li>Go to <b>Credentials & Test Notes</b> and copy your <b>User Access Token</b>.</li>" +
                            "<li>Paste the token into the Personal Access Token field in Android Studio.</li>" +
                            "</ol></html>")
                }
            }

            row {
                cell(openConsoleBtn)
            }
        }
    }
}
