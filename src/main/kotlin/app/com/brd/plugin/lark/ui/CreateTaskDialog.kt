package app.com.brd.plugin.lark.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComboBox
import javax.swing.JComponent

class CreateTaskDialog(project: Project?) : DialogWrapper(project) {

    val summaryField = JBTextField()
    val typeCombo = JComboBox(arrayOf("Bug", "Task", "Story", "Feature"))
    val statusCombo = JComboBox(arrayOf("To Do", "In Progress", "Problem", "Done"))
    val priorityCombo = JComboBox(arrayOf("Critical", "High", "Medium", "Low"))
    val assigneeField = JBTextField("Mubashir")
    val descriptionField = JBTextField()

    init {
        title = "Add New Lark Task Record"
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row("Summary:") {
                cell(summaryField)
                    .align(AlignX.FILL)
                    .comment("Enter task summary or issue title")
            }
            row("Type:") {
                cell(typeCombo)
            }
            row("Status:") {
                cell(statusCombo)
            }
            row("Priority:") {
                cell(priorityCombo)
            }
            row("Assignee:") {
                cell(assigneeField)
                    .align(AlignX.FILL)
            }
            row("Description:") {
                cell(descriptionField)
                    .align(AlignX.FILL)
            }
        }
    }
}
