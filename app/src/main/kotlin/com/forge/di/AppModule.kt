package com.forge.di

import com.forge.notification.ForgeNotifications
import com.forge.work.EscalationNotifier
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    /**
     * L'escalade ne connaît que l'interface : c'est ce qui permet au test de convergence de
     * vérifier que la notification et le relais vocal partent bien du même événement.
     */
    @Binds
    abstract fun escalationNotifier(impl: ForgeNotifications): EscalationNotifier
}
