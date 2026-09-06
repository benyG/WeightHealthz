package com.forge.core.sync.di

import com.forge.core.sync.calendar.CalendarContractSync
import com.forge.core.sync.calendar.CalendarSync
import com.forge.core.sync.relay.NotifyMyAlexaRelay
import com.forge.core.sync.relay.PreferencesRelayCredentials
import com.forge.core.sync.relay.RelayCredentials
import com.forge.core.sync.relay.RelayEndpoint
import com.forge.core.sync.relay.RelayHttpClient
import com.forge.core.sync.relay.VoiceRelay
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun calendarSync(impl: CalendarContractSync): CalendarSync

    @Binds
    abstract fun voiceRelay(impl: NotifyMyAlexaRelay): VoiceRelay

    @Binds
    abstract fun relayCredentials(impl: PreferencesRelayCredentials): RelayCredentials
}

@Module
@InstallIn(SingletonComponent::class)
internal object RelayModule {

    @Provides
    @Singleton
    @RelayEndpoint
    fun endpoint(): String = NotifyMyAlexaRelay.DEFAULT_ENDPOINT

    /**
     * Client propre au relais. Celui de `core-ai` ajoute la clé Gemini à chaque requête ; le
     * réutiliser ici l'enverrait à un service tiers qui n'a rien à en faire.
     *
     * Délais courts : une annonce vocale a une valeur qui s'évapore. Mieux vaut échouer vite et
     * le dire que retenir un thread pendant une minute pour une phrase devenue fausse.
     */
    @Provides
    @Singleton
    @RelayHttpClient
    fun client(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    private const val TIMEOUT_SECONDS = 10L
}
