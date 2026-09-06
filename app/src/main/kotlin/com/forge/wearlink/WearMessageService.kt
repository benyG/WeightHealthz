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
 * Côté téléphone du lien avec la montre : reçoit ce qui a été saisi au poignet — pesées et séries
 * — et l'écrit dans la base, qui reste la source de vérité (DEPLOYMENT.md §12).
 *
 * Rien de ce qui arrive ici ne peut créer de doublon, et c'est voulu des deux côtés : une pesée
 * est identifiée par (jour, source), une série par sa position dans son exercice. Un renvoi
 * depuis la file d'attente de la montre corrige la même ligne au lieu d'en ajouter une seconde.
 */
@AndroidEntryPoint
class WearMessageService : WearableListenerService() {

    @Inject lateinit var weights: WeightRepository

    @Inject lateinit var gapPublisher: WeightGapPublisher

    @Inject lateinit var setRecorder: WatchSetRecorder

    @Inject lateinit var sessionPublisher: SessionPlanPublisher

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        val payload = String(messageEvent.data)

        when (messageEvent.path) {
            WearLink.WEIGHT_PATH -> receiveWeight(payload)
            WearLink.SET_PATH -> receiveSet(payload)
        }
    }

    private fun receiveWeight(payload: String) {
        val entry = WearLink.decodeWeight(payload)
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

    private fun receiveSet(payload: String) {
        val entry = WearLink.decodeSetEntry(payload)
        if (entry == null) {
            Log.w(TAG, "Série illisible reçue de la montre, ignorée.")
            return
        }

        scope.launch {
            if (setRecorder.record(entry)) {
                // Republier la séance renvoie à la montre ce que la base contient réellement,
                // y compris ce qui aurait été logué sur le téléphone entre-temps.
                sessionPublisher.publish(entry.date)
            }
        }
    }

    private companion object {
        const val TAG = "WearMessageService"
    }
}
