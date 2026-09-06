package com.forge.wear.data

import android.content.Context
import android.util.Log
import com.forge.domain.link.WearLink
import com.forge.domain.model.WeightEntry
import com.google.android.gms.wearable.DataClient
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
 * La montre n'a pas de base : le téléphone reste la source de vérité (DEPLOYMENT.md §12). Une
 * pesée saisie au poignet part par message, et l'écart au poids cible revient par une donnée
 * publiée par le téléphone, que le système conserve et redonne même hors de portée.
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

        /** Téléphone hors de portée : la pesée est conservée pour un prochain essai. */
        data object PhoneUnreachable : SendResult
    }

    suspend fun sendWeight(entry: WeightEntry): SendResult {
        val payload = WearLink.encodeWeight(entry).toByteArray()

        return try {
            val nodes = nodeClient.connectedNodes.await()
            if (nodes.isEmpty()) return SendResult.PhoneUnreachable

            nodes.forEach { node ->
                messageClient.sendMessage(node.id, WearLink.WEIGHT_PATH, payload).await()
            }
            SendResult.Sent
        } catch (error: Exception) {
            Log.w(TAG, "Envoi de la pesée impossible : ${error.message}")
            SendResult.PhoneUnreachable
        }
    }

    /**
     * Dernier écart publié par le téléphone. `null` tant que rien n'a été publié — la Tile et la
     * complication disent alors qu'il n'y a pas encore de mesure, plutôt que d'afficher zéro.
     */
    suspend fun lastKnownGapKg(): Double? = try {
        dataClient.dataItems.await().use { buffer ->
            buffer.firstOrNull { it.uri.path == WearLink.GAP_PATH }
                ?.let { item ->
                    com.google.android.gms.wearable.DataMapItem.fromDataItem(item)
                        .dataMap
                        .takeIf { it.containsKey(WearLink.GAP_KEY_DELTA) }
                        ?.getDouble(WearLink.GAP_KEY_DELTA)
                }
        }
    } catch (error: Exception) {
        Log.w(TAG, "Lecture de l'écart impossible : ${error.message}")
        null
    }

    private companion object {
        const val TAG = "PhoneLink"
    }
}
