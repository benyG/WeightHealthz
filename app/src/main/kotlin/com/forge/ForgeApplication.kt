package com.forge

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.forge.core.data.plan.PlanImporter
import com.forge.notification.ForgeNotifications
import com.forge.work.ForgeScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Point d'entrée de l'app téléphone.
 *
 * Trois choses au démarrage, toutes idempotentes : importer le plan s'il ne l'est pas déjà,
 * déclarer les canaux de notification, et programmer les travaux périodiques. Aucune n'a d'effet
 * la deuxième fois, donc un relancement ne réimporte rien et ne redécale aucun rappel.
 */
@HiltAndroidApp
class ForgeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var planImporter: PlanImporter

    @Inject lateinit var notifications: ForgeNotifications

    @Inject lateinit var scheduler: ForgeScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        notifications.ensureChannels()
        scheduler.scheduleAll()

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { planImporter.importIfNeeded() }
                .onSuccess { Log.i(TAG, "Import du plan : $it") }
                // Un plan illisible ne doit pas empêcher l'app de démarrer : le suivi manuel
                // reste possible, et l'erreur est tracée pour être corrigée.
                .onFailure { Log.e(TAG, "Import du plan impossible", it) }
        }
    }

    private companion object {
        const val TAG = "ForgeApplication"
    }
}
