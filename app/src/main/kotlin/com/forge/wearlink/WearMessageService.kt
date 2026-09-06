package com.forge.wearlink

import android.util.Log
import com.forge.domain.link.WearLink
import com.forge.domain.repository.WeightRepository
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Côté téléphone du lien avec la montre : reçoit les pesées saisies au poignet et les écrit dans
 * la base, qui reste la source de vérité (DEPLOYMENT.md §12).
 *
 * Une pesée réémise depuis la file d'attente de la montre n'ajoute pas de doublon : la clé
 * (jour, source) de `weight_entry` en fait une correction, pas une seconde ligne.
 */
@AndroidEntryPoint
class WearMessageService : WearableListenerService() {

    @Inject lateinit var weights: WeightRepository

    @Inject lateinit var gapPublisher: WeightGapPublisher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (messageEvent.path != WearLink.WEIGHT_PATH) return

        val entry = WearLink.decodeWeight(String(messageEvent.data))
        if (entry == null) {
            // Message abîmé ou d'une autre version : on le signale sans tomber.
            Log.w(TAG, "Pesée illisible reçue de la montre, ignorée.")
            return
        }

        scope.launch {
            weights.record(entry)
            // La montre affiche l'écart, pas la pesée : le republier tout de suite évite que la
            // Tile montre encore l'ancienne valeur juste après une saisie.
            gapPublisher.publish()
        }
    }

    private companion object {
        const val TAG = "WearMessageService"
    }
}
