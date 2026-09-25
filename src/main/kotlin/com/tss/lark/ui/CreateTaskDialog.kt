package com.tss.lark.ui

import com.tss.lark.model.LarkFieldInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.swing.JComboBox
import javax.swing.JComponent

class DynamicCreateRecordDialog(
    project: Project?,
    val tableName: String,
    val fields: List<LarkFieldInfo>,
    val existingPersons: List<String> = emptyList()
) : DialogWrapper(project) {

    private val textInputs = mutableMapOf<LarkFieldInfo, JBTextField>()
    private val comboInputs = mutableMapOf<LarkFieldInfo, JComboBox<String>>()
    private val checkInputs = mutableMapOf<LarkFieldInfo, JBCheckBox>()
    private val dateInputs = mutableMapOf<LarkFieldInfo, JBTextField>()
    private val numberInputs = mutableMapOf<LarkFieldInfo, JBTextField>()

    init {
        title = "Add Record to $tableName"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val displayFields = fields.filter { !it.isReadOnly }
            .ifEmpty { fields }

        return panel {
            for (field in displayFields) {
                val availableOpts = field.options.map { it.name }.filter { it.isNotBlank() }

                when {
                    field.type == 7 -> { // Checkbox / Boolean
                        val cb = JBCheckBox()
                        checkInputs[field] = cb
                        row("${field.fieldName}:") {
                            cell(cb)
                        }
                    }
                    field.type == 11 -> { // Person / User
                        val persons = mutableListOf("-- Select Person --")
                        val allPersons = (availableOpts + existingPersons).distinct()
                        persons.addAll(allPersons)
                        val cb = JComboBox(persons.toTypedArray()).apply {
                            isEditable = true
                        }
                        comboInputs[field] = cb
                        row("${field.fieldName}:") {
                            cell(cb).align(AlignX.FILL)
                                .comment("Select or type person name")
                        }
                    }
                    field.type in setOf(3, 4) || availableOpts.isNotEmpty() -> { // Single / Multi Select
                        val options = mutableListOf("-- Select Option --")
                        options.addAll(availableOpts.distinct())
                        val cb = JComboBox(options.toTypedArray()).apply {
                            isEditable = true
                        }
                        comboInputs[field] = cb
                        row("${field.fieldName}:") {
                            cell(cb).align(AlignX.FILL)
                        }
                    }
                    field.type == 5 -> { // DateTime
                        val defaultDate = SimpleDateFormat("yyyy/MM/dd", Locale.US).format(Date())
                        val tf = JBTextField(defaultDate)
                        dateInputs[field] = tf
                        row("${field.fieldName}:") {
                            cell(tf).align(AlignX.FILL)
                                .comment("Format: YYYY/MM/DD (e.g. 2026/09/25)")
                        }
                    }
                    field.type == 2 -> { // Number
                        val tf = JBTextField()
                        numberInputs[field] = tf
                        row("${field.fieldName}:") {
                            cell(tf).align(AlignX.FILL)
                                .comment("Enter numeric value")
                        }
                    }
                    else -> { // Text, Phone, URL, etc.
                        val tf = JBTextField()
                        textInputs[field] = tf
                        row("${field.fieldName}:") {
                            cell(tf).align(AlignX.FILL)
                        }
                    }
                }
            }
        }
    }

    override fun doValidate(): ValidationInfo? {
        for ((field, tf) in dateInputs) {
            val text = tf.text.trim()
            if (text.isNotEmpty() && parseDateToMillis(text) == null) {
                return ValidationInfo("Invalid date format for '${field.fieldName}'. Use YYYY/MM/DD (e.g., 2026/09/25).", tf)
            }
        }
        for ((field, tf) in numberInputs) {
            val text = tf.text.trim()
            if (text.isNotEmpty() && text.toDoubleOrNull() == null) {
                return ValidationInfo("Invalid number for '${field.fieldName}'. Please enter a valid numeric value.", tf)
            }
        }
        return null
    }

    fun getRecordFields(): Map<String, Any?> {
        val resultMap = mutableMapOf<String, Any?>()

        // Text inputs
        for ((field, tf) in textInputs) {
            val valStr = tf.text.trim()
            if (valStr.isNotEmpty()) {
                resultMap[field.fieldName] = valStr
            }
        }

        // Combo inputs (Select & Person)
        for ((field, cb) in comboInputs) {
            val selected = cb.selectedItem?.toString()?.trim() ?: ""
            if (selected.isNotEmpty() && !selected.startsWith("-- Select")) {
                if (field.type == 11) { // Person field: format as json list of objects
                    resultMap[field.fieldName] = listOf(mapOf("name" to selected))
                } else if (field.type == 4) { // Multi select
                    resultMap[field.fieldName] = listOf(selected)
                } else { // Single select
                    resultMap[field.fieldName] = selected
                }
            }
        }

        // Checkbox inputs
        for ((field, cb) in checkInputs) {
            resultMap[field.fieldName] = cb.isSelected
        }

        // Date inputs
        for ((field, tf) in dateInputs) {
            val dateStr = tf.text.trim()
            if (dateStr.isNotEmpty()) {
                val millis = parseDateToMillis(dateStr)
                if (millis != null) {
                    resultMap[field.fieldName] = millis
                }
            }
        }

        // Number inputs
        for ((field, tf) in numberInputs) {
            val numStr = tf.text.trim()
            if (numStr.isNotEmpty()) {
                val numVal = numStr.toDoubleOrNull()
                if (numVal != null) {
                    resultMap[field.fieldName] = numVal
                }
            }
        }

        return resultMap
    }

    private fun parseDateToMillis(input: String): Long? {
        val formats = listOf("yyyy/MM/dd HH:mm", "yyyy-MM-dd HH:mm", "yyyy/MM/dd", "yyyy-MM-dd")
        for (fmt in formats) {
            try {
                val sdf = SimpleDateFormat(fmt, Locale.US)
                sdf.isLenient = false
                val date = sdf.parse(input.trim())
                if (date != null) return date.time
            } catch (ignored: Exception) {}
        }
        return null
    }
}
