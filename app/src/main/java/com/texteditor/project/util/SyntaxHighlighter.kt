package com.texteditor.project.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import com.texteditor.project.data.HighlightConfig

fun highlightWithConfig(code: String, cfg: HighlightConfig): AnnotatedString {
    val baseColor = Color(android.graphics.Color.parseColor(cfg.colors["base"] ?: "#FFFFFF"))
    val keywordColor = Color(android.graphics.Color.parseColor(cfg.colors["keyword"] ?: "#569CD6"))
    val commentColor = Color(android.graphics.Color.parseColor(cfg.colors["comment"] ?: "#6A9955"))
    val stringColor = Color(android.graphics.Color.parseColor(cfg.colors["string"] ?: "#D69D85"))
    val numberColor = Color(android.graphics.Color.parseColor(cfg.colors["number"] ?: "#B5CEA8"))

    val builder = AnnotatedString.Builder(code)
    builder.addStyle(SpanStyle(color = baseColor), 0, code.length)

    // Keywords
    if (cfg.keywords.isNotEmpty()) {
        val kwRegex = Regex("\\b(${cfg.keywords.joinToString("|")})\\b")
        kwRegex.findAll(code).forEach {
            builder.addStyle(SpanStyle(color = keywordColor), it.range.first, it.range.last + 1)
        }
    }

    // Single-line comments
    if (cfg.comment.isNotEmpty()) {
        Regex("${Regex.escape(cfg.comment)}.*", RegexOption.MULTILINE).findAll(code).forEach {
            builder.addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1)
        }
    }

    // Strings for each delimiter
    cfg.stringDelimiters.forEach { delim ->
        Regex("$delim([^$delim\\\\]|\\\\.)*$delim").findAll(code).forEach {
            builder.addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1)
        }
    }

    // Numbers
    Regex("\\b\\d+(?:\\.\\d+)?\\b").findAll(code).forEach {
        builder.addStyle(SpanStyle(color = numberColor), it.range.first, it.range.last + 1)
    }

    return builder.toAnnotatedString()
}
