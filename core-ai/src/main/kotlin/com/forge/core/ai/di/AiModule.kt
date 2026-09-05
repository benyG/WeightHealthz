package com.forge.core.ai.di

import com.forge.core.ai.BuildConfig
import com.forge.core.ai.analysis.WeeklyAnalyst
import com.forge.core.ai.gemini.GeminiApi
import com.forge.core.ai.gemini.GeminiJson
import com.forge.core.ai.gemini.GeminiWeeklyAnalyst
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

@Module
@InstallIn(SingletonComponent::class)
internal object GeminiModule {

    /**
     * `ignoreUnknownKeys` : l'API ajoute régulièrement des champs (métadonnées de sécurité,
     * comptage de jetons) qui ne doivent pas faire échouer une réponse par ailleurs valide.
     */
    @Provides
    @Singleton
    @GeminiJson
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun okHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor { chain ->
            // La clé vit dans BuildConfig, alimenté par local.properties ou l'environnement CI
            // (DEPLOYMENT.md §4) — jamais en dur, jamais dans l'URL.
            val request = chain.request().newBuilder()
                .addHeader(API_KEY_HEADER, BuildConfig.GEMINI_API_KEY)
                .build()
            chain.proceed(request)
        }
        .apply {
            if (BuildConfig.DEBUG) {
                // BASIC : méthode, URL et code de retour. Ni les en-têtes (donc pas la clé), ni
                // les corps (donc pas les données de poids).
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, @GeminiJson json: Json): Retrofit = Retrofit.Builder()
        .baseUrl(GeminiApi.BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun geminiApi(retrofit: Retrofit): GeminiApi = retrofit.create(GeminiApi::class.java)

    private const val API_KEY_HEADER = "x-goog-api-key"
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AnalystModule {

    @Binds
    abstract fun weeklyAnalyst(impl: GeminiWeeklyAnalyst): WeeklyAnalyst
}
