package com.forge.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Clôture de journée quotidienne. Le worker ne fait que déclencher : toute la logique vit dans
 * [EscalationRunner], qui se teste sans WorkManager ni émulateur.
 */
@HiltWorker
class DailyEscalationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val runner: EscalationRunner,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        val state = runner.closeDay()
        Log.i(TAG, "Journée close, niveau ${state.escalationLevel}.")
        Result.success()
    } catch (error: Exception) {
        Log.e(TAG, "Clôture de journée impossible", error)
        Result.retry()
    }

    companion object {
        const val UNIQUE_NAME = "forge-cloture-journee"
        private const val TAG = "DailyEscalationWorker"
    }
}
