package com.forge.core.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pont Health Connect → domaine, **en lecture seule** : Forge lit le poids remonté par la
 * balance, elle n'écrit jamais dans Health Connect.
 *
 * Aucune écoute continue ici, uniquement des appels ponctuels : SPEC.md §8 interdit le polling
 * et le service foreground permanent, la lecture est déclenchée par le rappel programmé
 * (WorkManager, phase 5).
 */
@Singleton
class HealthConnectWeightSource @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val permissions: Set<String> = setOf(HealthPermission.getReadPermission(WeightRecord::class))

    /** Health Connect peut être absent ou obsolète sur l'appareil — l'app doit rester utilisable. */
    fun isAvailable(): Boolean =
        HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE

    suspend fun hasPermission(): Boolean {
        if (!isAvailable()) return false
        val granted = client().permissionController.getGrantedPermissions()
        return granted.containsAll(permissions)
    }

    /**
     * Pesées entre [from] et [to]. La date retenue est celle du fuseau de l'appareil : une pesée
     * du matin doit tomber dans la journée où elle a eu lieu, pas dans la veille en UTC.
     *
     * Renvoie une liste vide si Health Connect est indisponible ou non autorisé — l'absence de
     * source n'est pas une erreur, la saisie manuelle reste le chemin de repli (DESIGN.md §7.6).
     */
    suspend fun readWeights(
        from: Instant,
        to: Instant,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<WeightEntry> {
        if (!hasPermission()) return emptyList()

        val response = client().readRecords(
            ReadRecordsRequest(
                recordType = WeightRecord::class,
                timeRangeFilter = TimeRangeFilter.between(from, to),
            ),
        )

        return response.records.map { record ->
            WeightEntry(
                date = record.time.atZone(record.zoneOffset ?: zone).toLocalDate(),
                kg = record.weight.inKilograms.toFloat(),
                source = WeightSource.HEALTH_CONNECT,
            )
        }
    }

    private fun client(): HealthConnectClient = HealthConnectClient.getOrCreate(context)
}
