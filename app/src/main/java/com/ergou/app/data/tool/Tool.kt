package com.ergou.app.data.tool

import com.ergou.app.data.remote.dto.FunctionDefinition
import com.ergou.app.data.remote.dto.ToolDefinition
import kotlinx.serialization.json.JsonObject

/**
 * 工具接口 — 二狗能调用的所有工具都实现这个接口
 */
interface Tool {
    val name: String
    val description: String
    val parameters: JsonObject

    suspend fun execute(arguments: Map<String, String>): String

    fun toDefinition(): ToolDefinition = ToolDefinition(
        function = FunctionDefinition(
            name = name,
            description = description,
            parameters = parameters
        )
    )
}
