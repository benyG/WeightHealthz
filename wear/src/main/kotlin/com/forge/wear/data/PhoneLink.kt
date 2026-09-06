package com.forge.wear.data

import android.content.Context
import android.util.Log
import com.forge.domain.link.WearLink
import com.forge.domain.link.WearSessionPlan
import com.forge.domain.link.WearSetEntry
import com.forge.domain.model.WeightEntry
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageClient
import com.google.android.gms.wearable.NodeClient
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * Lien avec le téléphone.
 *
 * La montre n'a pas de base : le téléphone reste la source de vérité (DEPLOYMENT.md §12). Ce qui
 * est saisi au poignet — une pesée, une série — part par message ; ce qui vient du téléphone —
 * l'écart au poids cible, la séance du jour — arrive par une donnée que le système conserve et
 * redonne même hors de portée. C'est la seule moitié du lien qui fonctionne en salle, loin du
 * téléphone resté au vestiaire.
 */
@Singleton
class PhoneLink @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val messageClient: MessageClient by lazy { Wearable.getMessageClient(context) }
    private val dataClient: DataClient by lazy { Wearable.getDataClient(context) }
    private val nodeClient: NodeClient by lazy { Wearable.getNodeClient(context) }

    sealed interface SendResult {
        data object Sent : SendResult

        /** Téléphone hors de portée : la saisie est conservée pour un prochain essai. */
        data object PhoneUnreachable : SendResult
    }

    suspend fun sendWeight(entry: WeightEntry): SendResult =
        send(WearLink.WEIGHT_PATH, WearLink.encodeWeight(entry), "la pesée")

    suspend fun sendSet(entry: WearSetEntry): SendResult =
        send(WearLink.SET_PATH, WearLink.encodeSetEntry(entry), "la série")

    private suspend fun send(path: String, payload: String, what: String): SendResult = try {
        val nodes = nodeClient.connectedNodes.await()
        if (nodes.isEmpty()) {
            SendResult.PhoneUnreachable
        } else {
            nodes.forEach { node ->
                messageClient.sendMessage(node.id, path, payload.toByteArray()).await()
            }
            SendResult.Sent
        }
    } catch (error: Exception) {
        Log.w(TAG, "Envoi de $what impossible : ${error.message}")
        SendResult.PhoneUnreachable
    }

    /**
     * Dernier écart publié par le téléphone. `null` tant que rien n'a été publié — la Tile et la
     * complication disent alors qu'il n'y a pas encore de mesure, plutôt que d'afficher zéro.
     */
    suspend fun lastKnownGapKg(): Double? =
        dataMapAt(WearLink.GAP_PATH, "l'écart")
            ?.takeIf { it.containsKey(WearLink.GAP_KEY_DELTA) }
            ?.getDouble(WearLink.GAP_KEY_DELTA)

    /**
     * Dernière séance publiée par le téléphone, quelle que soit sa date. C'est à l'appelant de
     * vérifier qu'elle est bien celle du jour : une séance d'hier conservée par le système ne
     * doit pas se faire passer pour celle d'aujourd'hui.
     */
    suspend fun lastKnownSessionPlan(): WearSessionPlan? =
        dataMapAt(WearLink.SESSION_PATH, "la séance")
            ?.getString(WearLink.SESSION_KEY_PLAN)
            ?.let(WearLink::decodeSessionPlan)

    private suspend fun dataMapAt(path: String, what: String): DataMap? = try {
        dataClient.dataItems.await().use { buffer ->
            buffer.firstOrNull { it.uri.path == path }
                ?.let { DataMapItem.fromDataItem(it).dataMap }
        }
    } catch (error: Exception) {
        Log.w(TAG, "Lecture de $what impossible : ${error.message}")
        null
    }

    private companion object {
        const val TAG = "PhoneLink"
    }
}
