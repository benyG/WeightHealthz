package com.forge.core.sync.di

import com.forge.core.sync.calendar.CalendarContractSync
import com.forge.core.sync.calendar.CalendarSync
import com.forge.core.sync.relay.UnconfiguredVoiceRelay
import com.forge.core.sync.relay.VoiceRelay
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {

    @Binds
    abstract fun calendarSync(impl: CalendarContractSync): CalendarSync

    /**
     * **Seul point à changer** quand le fournisseur de relais vocal sera tranché
     * (DEPLOYMENT.md §11) : remplacer `UnconfiguredVoiceRelay` par le client webhook. Tout le
     * reste de l'app parle déjà à l'interface.
     */
    @Binds
    abstract fun voiceRelay(impl: UnconfiguredVoiceRelay): VoiceRelay
}
