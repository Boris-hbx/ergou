package com.ergou.app.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ergou.app.data.local.database.ErgouDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

class ReminderReceiver : BroadcastReceiver(), KoinComponent {

    private val database: ErgouDatabase by inject()

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra("reminder_id", -1)
        val content = intent.getStringExtra("content") ?: "提醒时间到了"

        Timber.d("收到提醒: ID=$reminderId, content=$content")

        // 显示通知
        NotificationHelper.showReminder(context, reminderId, content)

        // 标记完成
        if (reminderId > 0) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    database.reminderDao().markCompleted(reminderId)
                } catch (e: Exception) {
                    Timber.e(e, "标记提醒完成失败")
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
