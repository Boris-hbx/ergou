package com.ergou.app.data.tool.tools

import android.content.Context
import com.ergou.app.data.local.dao.ReminderDao
import com.ergou.app.data.local.entity.ReminderEntity
import com.ergou.app.data.tool.Tool
import com.ergou.app.util.ReminderScheduler
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.text.SimpleDateFormat
import java.util.Locale

class SetReminderTool(
    private val reminderDao: ReminderDao,
    private val context: Context
) : Tool {
    override val name = "set_reminder"
    override val description = "设置一个提醒。支持自然语言时间如'明天下午3点'、'2小时后'、'下周一上午9点'"
    override val parameters: JsonObject = buildJsonObject {
        put("type", "object")
        putJsonObject("properties") {
            putJsonObject("content") {
                put("type", "string")
                put("description", "提醒内容")
            }
            putJsonObject("time") {
                put("type", "string")
                put("description", "触发时间，ISO格式如'2026-03-05T15:00:00'，或相对时间如'2h'(2小时后)、'30m'(30分钟后)")
            }
        }
        put("required", buildJsonArray {
            add(JsonPrimitive("content"))
            add(JsonPrimitive("time"))
        })
    }

    override suspend fun execute(arguments: Map<String, String>): String {
        val content = arguments["content"] ?: return "缺少提醒内容"
        val timeStr = arguments["time"] ?: return "缺少时间"

        val triggerAt = parseTime(timeStr) ?: return "无法解析时间: $timeStr"
        val now = System.currentTimeMillis()

        if (triggerAt <= now) return "提醒时间已过"

        val id = reminderDao.insert(
            ReminderEntity(
                content = content,
                triggerAt = triggerAt,
                createdAt = now
            )
        )

        // 调度系统闹钟
        ReminderScheduler.schedule(context, id, triggerAt, content)

        val sdf = SimpleDateFormat("M月d日 HH:mm", Locale.CHINESE)
        return "提醒已设置：${sdf.format(triggerAt)} - $content (ID:$id)"
    }

    private fun parseTime(timeStr: String): Long? {
        // 相对时间: "30m", "2h", "1d"
        val relativeMatch = Regex("""(\d+)([mhd])""").matchEntire(timeStr.trim())
        if (relativeMatch != null) {
            val amount = relativeMatch.groupValues[1].toLong()
            val unit = relativeMatch.groupValues[2]
            val millis = when (unit) {
                "m" -> amount * 60 * 1000
                "h" -> amount * 60 * 60 * 1000
                "d" -> amount * 24 * 60 * 60 * 1000
                else -> return null
            }
            return System.currentTimeMillis() + millis
        }

        // ISO 格式: "2026-03-05T15:00:00"
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            sdf.parse(timeStr)?.time
        } catch (e: Exception) {
            // 尝试简单格式: "2026-03-05 15:00"
            try {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                sdf.parse(timeStr)?.time
            } catch (e2: Exception) {
                null
            }
        }
    }
}
