package com.ergou.app.data.tool

import com.ergou.app.data.remote.dto.ToolDefinition
import timber.log.Timber

/**
 * 工具注册表 — 管理所有可用工具
 */
class ToolRegistry {

    private val tools = mutableMapOf<String, Tool>()

    fun register(tool: Tool) {
        tools[tool.name] = tool
        Timber.d("注册工具: ${tool.name}")
    }

    fun getTool(name: String): Tool? = tools[name]

    fun getAllDefinitions(): List<ToolDefinition> = tools.values.map { it.toDefinition() }

    fun hasTools(): Boolean = tools.isNotEmpty()

    suspend fun executeTool(name: String, arguments: Map<String, String>): String {
        val tool = tools[name] ?: return "错误：未找到工具 $name"
        return try {
            Timber.d("执行工具: $name, 参数: $arguments")
            val result = tool.execute(arguments)
            Timber.d("工具结果: $name → ${result.take(100)}")
            result
        } catch (e: Exception) {
            Timber.e(e, "工具执行失败: $name")
            "工具执行出错: ${e.message}"
        }
    }
}
