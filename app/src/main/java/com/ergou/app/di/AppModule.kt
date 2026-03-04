package com.ergou.app.di

import androidx.room.Room
import com.ergou.app.data.local.database.ErgouDatabase
import com.ergou.app.data.remote.api.DeepSeekService
import com.ergou.app.data.remote.api.LLMService
import com.ergou.app.data.repository.ChatRepository
import com.ergou.app.data.repository.ChatRepositoryImpl
import com.ergou.app.ui.chat.ChatViewModel
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
        ).build()
    }
    single { get<ErgouDatabase>().sessionDao() }
    single { get<ErgouDatabase>().messageDao() }

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

    // Repository
    single<ChatRepository> { ChatRepositoryImpl(sessionDao = get(), messageDao = get(), llmService = get()) }

    // ViewModels
    viewModel { ChatViewModel(chatRepository = get(), apiKeyProvider = get()) }
}
