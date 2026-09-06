package com.forge.work

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.forge.core.sync.relay.EscalationRelay
import com.forge.domain.repository.AdherenceRepository
import com.forge.domain.repository.MealRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import com.forge.domain.rule.EscalationMessage
import com.forge.domain.rule.EscalationMachine
import com.forge.notification.ForgeNotifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate
import kotlinx.coroutines.flow.first

/**
 * Clôture de journée : c'est ici que la machine d'escalade avance d'un cran ou repart à zéro.
 *
 * Ce worker est le point où les canaux convergent, exigence de SPEC.md §10 : un passage en
 * `CRITIQUE` poste la notification Android **et** part vers l'enceinte dans le même événement,
 * pas dans deux traitements séparés. Le message est identique sur les deux canaux parce qu'il
 * vient du domaine.
 */
@HiltWorker
class DailyEscalationWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val weights: WeightRepository,
    private val meals: MealRepository,
    private val workouts: WorkoutRepository,
    private val adherence: AdherenceRepository,
    private val notifications: ForgeNotifications,
    private val relay: EscalationRelay,
) : CoroutineWorker(context, parameters) {

    override suspend fun doWork(): Result = try {
        val today = LocalDate.now()
        val hadValidAction = hadValidAction(today)

        val next = EscalationMachine.onDayClosed(adherence.observeState().first(), hadValidAction)
        adherence.update(next)

        val message = EscalationMessage.forLevel(next.escalationLevel, missedItem(today))
        if (message != null) {
            notifications.notifyEscalation(next.escalationLevel, message)
            // `relayIfNeeded` ne parle que pour RETARD_2 et CRITIQUE : la décision appartient au
            // domaine, ce worker ne la rejoue pas.
            relay.relayIfNeeded(next, missedItem(today))
        }

        Result.success()
    } catch (error: Exception) {
        Log.e(TAG, "Clôture de journée impossible", error)
        Result.retry()
    }

    /** Pesée, repas coché ou séance loguée : n'importe laquelle remet le compteur à zéro. */
    private suspend fun hadValidAction(today: LocalDate): Boolean {
        val weighed = weights.entriesBetween(today, today).isNotEmpty()
        val ateSomething = meals.observeDay(today).first().any { it.done }
        val trained = workouts.observeSession(today).first() != null
        return weighed || ateSomething || trained
    }

    /**
     * Ce qui manque en premier, dans l'ordre où le programme le réclame — le message nomme une
     * seule chose, parce qu'une liste de reproches ne dit pas quoi faire maintenant.
     */
    private suspend fun missedItem(today: LocalDate): String = when {
        weights.entriesBetween(today, today).isEmpty() -> "Pesée"
        meals.observeDay(today).first().none { it.done } -> "Repas"
        else -> "Séance"
    }

    companion object {
        const val UNIQUE_NAME = "forge-cloture-journee"
        private const val TAG = "DailyEscalationWorker"
    }
}
