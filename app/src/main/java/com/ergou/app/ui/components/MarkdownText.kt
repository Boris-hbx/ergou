package com.ergou.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 轻量级 Markdown 渲染
 * 支持：**加粗**、*斜体*、`行内代码`、```代码块```
 */
@Composable
fun MarkdownText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val annotatedString = remember(text) { parseMarkdown(text, color) }
    Text(
        text = annotatedString,
        modifier = modifier
    )
}

private fun parseMarkdown(text: String, defaultColor: Color): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        val len = text.length

        while (i < len) {
            when {
                // 代码块 ```...```
                i + 2 < len && text[i] == '`' && text[i + 1] == '`' && text[i + 2] == '`' -> {
                    val endIdx = text.indexOf("```", i + 3)
                    if (endIdx != -1) {
                        // 跳过 ``` 后可能的语言标记行
                        val codeStart = text.indexOf('\n', i + 3).let {
                            if (it != -1 && it < endIdx) it + 1 else i + 3
                        }
                        val code = text.substring(codeStart, endIdx).trimEnd()
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = defaultColor.copy(alpha = 0.1f)
                        )) {
                            append(code)
                        }
                        i = endIdx + 3
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // 行内代码 `...`
                text[i] == '`' -> {
                    val endIdx = text.indexOf('`', i + 1)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            background = defaultColor.copy(alpha = 0.1f)
                        )) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // 加粗 **...**
                i + 1 < len && text[i] == '*' && text[i + 1] == '*' -> {
                    val endIdx = text.indexOf("**", i + 2)
                    if (endIdx != -1) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, endIdx))
                        }
                        i = endIdx + 2
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // 斜体 *...*
                text[i] == '*' -> {
                    val endIdx = text.indexOf('*', i + 1)
                    if (endIdx != -1 && endIdx > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, endIdx))
                        }
                        i = endIdx + 1
                    } else {
                        append(text[i])
                        i++
                    }
                }
                // 普通字符
                else -> {
                    append(text[i])
                    i++
                }
            }
        }
    }
}
