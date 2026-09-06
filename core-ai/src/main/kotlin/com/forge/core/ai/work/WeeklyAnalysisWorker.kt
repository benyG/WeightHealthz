package com.forge.core.ai.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkerParameters
import com.forge.core.ai.analysis.WeeklyAnalysisService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Job hebdomadaire de SPEC.md §5.6. Il n'est pas planifié ici : `app` s'en charge en phase 5,
 * en même temps que les autres rappels. Ce module fournit le travail et ses contraintes.
 */
@HiltWorker
class WeeklyAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val service: WeeklyAnalysisService,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        when (val outcome = service.analyseWeek()) {
            is WeeklyAnalysisService.Result.Produced -> {
                Log.i(TAG, "Analyse de la semaine ${outcome.analysis.weekIndex} enregistrée.")
                Result.success()
            }

            WeeklyAnalysisService.Result.NoPlan -> {
                // Pas de plan importé : rien à analyser, et rien à réessayer non plus.
                Log.i(TAG, "Aucun plan importé, analyse ignorée.")
                Result.success()
            }
        }
    } catch (io: IOException) {
        // Réseau absent ou coupé : c'est exactement le cas que l'offline-first prévoit de
        // rejouer (SPEC.md §8), pas un échec définitif.
        Log.w(TAG, "Analyse hebdomadaire différée : ${io.message}")
        Result.retry()
    } catch (error: Exception) {
        // Réponse illisible ou contrat non respecté : réessayer à l'identique n'y changerait
        // rien, on échoue franchement plutôt que de boucler.
        Log.e(TAG, "Analyse hebdomadaire abandonnée", error)
        Result.failure()
    }

    companion object {
        const val UNIQUE_NAME: String = "forge-weekly-analysis"

        private const val TAG = "WeeklyAnalysisWorker"

        /**
         * Contraintes du job : réseau requis, et rejeu automatique à la reconnexion — le job du
         * dimanche ne doit pas être perdu parce que le téléphone était en mode avion.
         */
        fun request() = PeriodicWorkRequestBuilder<WeeklyAnalysisWorker>(7, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
    }
}
