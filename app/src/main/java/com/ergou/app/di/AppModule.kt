package com.ergou.app.di

import androidx.room.Room
import com.ergou.app.data.local.database.ErgouDatabase
import com.ergou.app.data.remote.api.DeepSeekService
import com.ergou.app.data.remote.api.LLMService
import com.ergou.app.data.repository.ChatRepository
import com.ergou.app.data.repository.ChatRepositoryImpl
import com.ergou.app.data.repository.MemoryRepository
import com.ergou.app.data.repository.MemoryRepositoryImpl
import com.ergou.app.data.tool.ToolExecutor
import com.ergou.app.data.tool.ToolRegistry
import com.ergou.app.data.tool.tools.GetDateTimeTool
import com.ergou.app.data.tool.tools.SaveMemoryTool
import com.ergou.app.data.tool.tools.SearchMemoryTool
import com.ergou.app.data.tool.tools.SetReminderTool
import com.ergou.app.data.tool.tools.SimpleCalculateTool
import com.ergou.app.ui.chat.ChatViewModel
import com.ergou.app.ui.memory.MemoryViewModel
import com.ergou.app.ui.settings.SettingsViewModel
import com.ergou.app.util.ApiKeyProvider
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import timber.log.Timber

val appModule = module {
    // ApiKey Provider
    single { ApiKeyProvider(androidContext()) }

    // Room Database
    single {
        Room.databaseBuilder(
            androidContext(),
            ErgouDatabase::class.java,
            "ergou.db"
        ).fallbackToDestructiveMigration(true).build()
    }
    single { get<ErgouDatabase>().sessionDao() }
    single { get<ErgouDatabase>().messageDao() }
    single { get<ErgouDatabase>().memoryDao() }
    single { get<ErgouDatabase>().personDao() }
    single { get<ErgouDatabase>().reminderDao() }

    // Ktor HttpClient
    single {
        HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    prettyPrint = false
                })
            }
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        Timber.tag("Ktor").d(message)
                    }
                }
                level = LogLevel.BODY
            }
        }
    }

    // LLM Service
    single<LLMService> { DeepSeekService(httpClient = get(), apiKeyProvider = get()) }

    // Tool System
    single {
        ToolRegistry().apply {
            register(GetDateTimeTool())
            register(SimpleCalculateTool())
            register(SaveMemoryTool(get()))
            register(SearchMemoryTool(get()))
            register(SetReminderTool(get(), androidContext()))
        }
    }
    single { ToolExecutor(llmService = get(), toolRegistry = get()) }

    // Repositories
    single<ChatRepository> { ChatRepositoryImpl(sessionDao = get(), messageDao = get(), llmService = get()) }
    single<MemoryRepository> { MemoryRepositoryImpl(memoryDao = get(), personDao = get()) }

    // ViewModels
    viewModel { ChatViewModel(chatRepository = get(), memoryRepository = get(), toolExecutor = get(), apiKeyProvider = get()) }
    viewModel { MemoryViewModel(memoryRepository = get()) }
    viewModel { SettingsViewModel(apiKeyProvider = get(), memoryRepository = get()) }
}
