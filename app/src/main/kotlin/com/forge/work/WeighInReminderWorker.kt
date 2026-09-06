package com.forge.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forge.core.data.health.HealthConnectWeightSync
import com.forge.domain.repository.WeightRepository
import com.forge.notification.ForgeNotifications
import com.forge.wearlink.SessionPlanPublisher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.time.LocalDate

/**
 * Rappel de pesée du matin (SPEC.md §5.2).
 *
 * Lit d'abord Health Connect — c'est le seul moment où on le consulte, jamais en continu
 * (SPEC.md §8) — puis ne rappelle que si la balance n'a rien remonté : un rappel pour une pesée
 * déjà faite apprend à ignorer les rappels.
 *
 * Le même réveil matinal publie la séance du jour vers la montre : elle tombe avant
 * l'entraînement, et une donnée à poser ne mérite pas un travail périodique de plus.
 */
@HiltWorker
class WeighInReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val healthConnectSync: HealthConnectWeightSync,
    private val weights: WeightRepository,
    private val notifications: ForgeNotifications,
    private val sessionPublisher: SessionPlanPublisher,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        healthConnectSync.syncRecentDays()

        val today = LocalDate.now()
        if (weights.entriesBetween(today, today).isEmpty()) {
            notifications.notifyReminder(
                title = "Pesée du matin",
                message = "Pèse-toi maintenant pour garder la moyenne à jour.",
            )
        }

        // Publier la séance du jour ici plutôt que dans un quatrième travail périodique : c'est
        // le seul réveil qui tombe avant l'entraînement, et un `putDataItem` ne justifie pas de
        // réveiller l'appareil une fois de plus (SPEC.md §8).
        sessionPublisher.publish(today)

        Result.success()
    } catch (io: IOException) {
        Log.w(TAG, "Lecture Health Connect différée : ${io.message}")
        Result.retry()
    } catch (error: Exception) {
        Log.e(TAG, "Rappel de pesée impossible", error)
        Result.failure()
    }

    companion object {
        const val UNIQUE_NAME = "forge-rappel-pesee"
        private const val TAG = "WeighInReminderWorker"
    }
}
