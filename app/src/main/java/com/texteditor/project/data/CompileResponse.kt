package com.texteditor.project.data

data class CompileResponse(
    val ok: Boolean,
    val phase: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int
)
