package app.com.brd.plugin.lark.ui

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent

class BaseUrlHelpDialog(project: Project?) : DialogWrapper(project) {

    init {
        title = "Where to Find Your Lark Base URL"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val openLarkBtn = JButton("Open Lark in Browser").apply {
            addActionListener {
                BrowserUtil.browse("https://www.larksuite.com")
            }
        }

        return panel {
            group("Step-by-Step Instructions") {
                row {
                    label("<html><ol>" +
                            "<li>Open <b>Lark</b> or <b>Feishu</b> in your web browser.</li>" +
                            "<li>Navigate to the <b>Base</b> workspace you want to connect to.</li>" +
                            "<li>Click on your browser's address bar at the top of the window.</li>" +
                            "<li>Copy the full web address (URL).<br/>Example: <code>https://domain.larksuite.com/base/basusIoa13FBsS1ZQBsSudtIwEe?table=tblZn9M...</code></li>" +
                            "<li>Paste it into the <b>Lark Base URL</b> field in Android Studio.</li>" +
                            "</ol></html>")
                }
            }

            row {
                cell(openLarkBtn)
            }
        }
    }
}
