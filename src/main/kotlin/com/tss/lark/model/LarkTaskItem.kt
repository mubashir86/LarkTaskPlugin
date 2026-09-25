package com.tss.lark.model

data class LarkTaskItem(
    val id: String,
    val key: String,
    val type: String,
    val summary: String,
    val status: String,
    val assignee: String,
    val priority: String,
    val description: String = "",
    val createdTime: String = ""
)
