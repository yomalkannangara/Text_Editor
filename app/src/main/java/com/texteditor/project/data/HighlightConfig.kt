package com.texteditor.project.data

data class HighlightConfig(
    val language: String,
    val keywords: List<String>,
    val comment: String,
    val stringDelimiters: List<String>,
    val colors: Map<String, String>
)
