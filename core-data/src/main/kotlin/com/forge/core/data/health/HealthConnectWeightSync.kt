package com.forge.core.data.health

import com.forge.domain.repository.WeightRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recopie les pesées de Health Connect dans Room. Appelé par le rappel de pesée (phase 5), pas
 * en continu.
 *
 * Rejouer une synchronisation sur la même période ne crée pas de doublon : la clé
 * (jour, source) de `weight_entry` fait de l'insertion un remplacement.
 */
@Singleton
class HealthConnectWeightSync @Inject constructor(
    private val source: HealthConnectWeightSource,
    private val repository: WeightRepository,
) {

    sealed interface Result {
        /** Health Connect absent de l'appareil ou permission non accordée. */
        data object Unavailable : Result

        data class Synced(val entryCount: Int) : Result
    }

    suspend fun syncRecentDays(
        days: Long = DEFAULT_WINDOW_DAYS,
        today: LocalDate = LocalDate.now(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Result {
        if (!source.hasPermission()) return Result.Unavailable

        val from: Instant = today.minusDays(days).atStartOfDay(zone).toInstant()
        val to: Instant = today.plusDays(1).atStartOfDay(zone).toInstant()

        val entries = source.readWeights(from, to, zone)
        entries.forEach { repository.record(it) }
        return Result.Synced(entries.size)
    }

    companion object {
        /**
         * Fenêtre par défaut : de quoi rattraper une balance restée hors ligne quelques jours,
         * et de quoi couvrir la fenêtre de la moyenne mobile 7 jours.
         */
        const val DEFAULT_WINDOW_DAYS: Long = 14
    }
}
