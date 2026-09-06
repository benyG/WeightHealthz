package com.forge.wearlink

import android.content.Context
import android.util.Log
import com.forge.domain.link.WearLink
import com.forge.domain.repository.WeightRepository
import com.forge.domain.rule.ProgramProgress
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Publie l'écart au poids cible à destination de la montre.
 *
 * Le calcul reste sur le téléphone : la montre n'a ni l'historique ni les règles, elle affiche un
 * chiffre déjà établi. C'est aussi ce qui garantit que la Tile, la complication et l'écran
 * d'accueil montrent la même valeur.
 */
@Singleton
class WeightGapPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val weights: WeightRepository,
) {

    suspend fun publish(on: LocalDate = LocalDate.now()) {
        val gap = ProgramProgress.cumulativeDeltaKg(weights.observeAll().first(), on) ?: return

        try {
            val request = PutDataMapRequest.create(WearLink.GAP_PATH).apply {
                dataMap.putDouble(WearLink.GAP_KEY_DELTA, gap)
                // Sans horodatage, une valeur identique à la précédente ne serait pas propagée.
                dataMap.putLong(WearLink.GAP_KEY_UPDATED_AT, System.currentTimeMillis())
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
        } catch (error: Exception) {
            // Montre absente ou non appairée : ce n'est pas une panne du téléphone.
            Log.i(TAG, "Écart non publié vers la montre : ${error.message}")
        }
    }

    private companion object {
        const val TAG = "WeightGapPublisher"
    }
}
