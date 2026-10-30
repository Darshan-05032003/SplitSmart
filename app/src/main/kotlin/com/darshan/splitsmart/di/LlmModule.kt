package com.darshan.splitsmart.di

import com.darshan.splitsmart.BuildConfig
import com.darshan.splitsmart.core.BackoffPolicy
import com.darshan.splitsmart.core.DispatcherProvider
import com.darshan.splitsmart.data.llm.GeminiExtractor
import com.darshan.splitsmart.data.llm.GeminiPins
import com.darshan.splitsmart.data.llm.LlmExtractor
import com.darshan.splitsmart.data.llm.LlmHttpClient
import com.darshan.splitsmart.data.llm.ModelCache
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object LlmModule {
    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = LlmHttpClient.create(GeminiPins.pinner())

    @Provides
    @Singleton
    fun llmExtractor(client: OkHttpClient, modelCache: ModelCache, dispatchers: DispatcherProvider): LlmExtractor = GeminiExtractor(
        client = client,
        apiKey = BuildConfig.LLM_API_KEY,
        baseUrl = GeminiExtractor.PRODUCTION_BASE_URL,
        models = GeminiExtractor.MODELS,
        modelCache = modelCache,
        backoff = BackoffPolicy.LLM,
        dispatchers = dispatchers,
    )
}
