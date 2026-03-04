package com.ergou.app

import android.app.Application
import com.ergou.app.di.appModule
import com.ergou.app.util.NotificationHelper
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import timber.log.Timber

class ErgouApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // Timber logging (debug only)
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        // 通知渠道
        NotificationHelper.createChannel(this)

        // Koin DI
        startKoin {
            androidLogger()
            androidContext(this@ErgouApplication)
            modules(appModule)
        }

        Timber.d("二狗启动了")
    }
}
