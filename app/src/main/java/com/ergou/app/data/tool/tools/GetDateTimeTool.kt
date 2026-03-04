package com.ergou.app.data.tool.tools

import com.ergou.app.data.tool.Tool
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GetDateTimeTool : Tool {
    override val name = "get_date_time"
    override val description = "获取当前日期和时间"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {})
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val sdf = SimpleDateFormat("yyyy年M月d日 EEEE HH:mm:ss", Locale.CHINESE)
        return sdf.format(Date())
    }
}
