package com.ergou.app.util

/**
 * Prompt注入防护：所有用户生成内容注入prompt前必须sanitize
 */
object PromptSanitizer {

    private const val DEFAULT_MAX_LEN = 500

    fun sanitize(text: String, maxLen: Int = DEFAULT_MAX_LEN): String {
        return text
            .take(maxLen)
            .replace("<", " ")
            .replace(">", " ")
            .replace("\\{\\{".toRegex(), " ")
            .replace("\\}\\}".toRegex(), " ")
            .filter { it == '\n' || it == '\r' || !it.isISOControl() }
            .trim()
    }
}
