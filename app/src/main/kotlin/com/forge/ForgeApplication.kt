package com.forge

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.forge.core.data.plan.PlanImporter
import com.forge.notification.ForgeNotifications
import com.forge.wearlink.SessionPlanPublisher
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
 * Quatre choses au démarrage, toutes idempotentes : importer le plan s'il ne l'est pas déjà,
 * déclarer les canaux de notification, programmer les travaux périodiques, et publier la séance
 * du jour vers la montre. Aucune n'a d'effet la deuxième fois, donc un relancement ne réimporte
 * rien, ne redécale aucun rappel et ne duplique aucune séance.
 */
@HiltAndroidApp
class ForgeApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var planImporter: PlanImporter

    @Inject lateinit var notifications: ForgeNotifications

    @Inject lateinit var scheduler: ForgeScheduler

    @Inject lateinit var sessionPublisher: SessionPlanPublisher

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

            // La montre doit connaître la séance du jour dès la première ouverture du téléphone,
            // sans attendre le réveil du lendemain matin.
            sessionPublisher.publish()
        }
    }

    private companion object {
        const val TAG = "ForgeApplication"
    }
}
