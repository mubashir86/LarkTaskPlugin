package com.tss.lark.model

data class LarkTableInfo(
    val tableId: String,
    val name: String
)

data class LarkFieldOption(
    val id: String,
    val name: String
)

data class LarkFieldInfo(
    val fieldId: String,
    val fieldName: String,
    val type: Int,
    val isPrimary: Boolean = false,
    val options: List<LarkFieldOption> = emptyList(),
    val isReadOnly: Boolean = false
)

data class LarkRecord(
    val recordId: String,
    val fields: Map<String, Any?>
)
