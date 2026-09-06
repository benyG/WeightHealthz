package com.forge.wearlink

import android.content.Context
import android.util.Log
import com.forge.domain.link.WearExercisePlan
import com.forge.domain.link.WearLink
import com.forge.domain.link.WearSessionPlan
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WorkoutRepository
import com.forge.domain.rule.DoubleProgression
import com.forge.domain.rule.SessionLog
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

/**
 * Publie la séance du jour à destination de la montre.
 *
 * La montre n'a ni le plan, ni l'historique, ni les règles : le téléphone lui envoie une séance
 * déjà résolue — exercices prescrits, charge proposée par la double progression, et séries déjà
 * loguées. Sans ces dernières, une séance commencée sur le téléphone repartirait de zéro au
 * poignet et écraserait ce qui existe.
 *
 * C'est une donnée, pas un message : la Data Layer la conserve et la redonne à la montre même
 * hors de portée du téléphone, ce qui est exactement le cas d'usage d'une salle de sport.
 */
@Singleton
class SessionPlanPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val plans: PlanRepository,
    private val workouts: WorkoutRepository,
) {

    suspend fun publish(on: LocalDate = LocalDate.now()) {
        // Jour de repos : rien à publier. La montre compare la date de ce qu'elle a reçu à la
        // sienne, donc une séance d'hier ne se fait pas passer pour celle d'aujourd'hui.
        val plan = buildPlan(on) ?: return

        try {
            val request = PutDataMapRequest.create(WearLink.SESSION_PATH).apply {
                dataMap.putString(WearLink.SESSION_KEY_PLAN, WearLink.encodeSessionPlan(plan))
                // Sans horodatage, republier une séance identique ne serait pas propagé.
                dataMap.putLong(WearLink.SESSION_KEY_UPDATED_AT, System.currentTimeMillis())
            }
            Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent()).await()
        } catch (error: Exception) {
            // Montre absente ou non appairée : ce n'est pas une panne du téléphone.
            Log.i(TAG, "Séance non publiée vers la montre : ${error.message}")
        }
    }

    private suspend fun buildPlan(on: LocalDate): WearSessionPlan? {
        val day = plans.workoutDays().firstOrNull { on.dayOfWeek in it.daysOfWeek } ?: return null
        val availableLoads = plans.availableLoadsKg()
        val logged = workouts.observeSession(on).first()

        return WearSessionPlan(
            date = on,
            label = day.label,
            availableLoadsKg = availableLoads,
            exercises = day.exercises.map { planned ->
                WearExercisePlan(
                    name = planned.name,
                    prescribedSets = planned.prescribedSets,
                    repRange = planned.repRange,
                    suggestedLoadKg = DoubleProgression.suggestedLoadKg(
                        previousSets = SessionLog.lastSetsBefore(
                            sessions = workouts.historyFor(planned.name, on.minusDays(LOOKBACK_DAYS)),
                            exerciseName = planned.name,
                            date = on,
                        ),
                        repRange = planned.repRange,
                        prescribedSets = planned.prescribedSets,
                        availableLoadsKg = availableLoads,
                    ),
                    loggedSets = logged?.exercises?.firstOrNull { it.name == planned.name }?.sets.orEmpty(),
                )
            },
        )
    }

    private companion object {
        const val TAG = "SessionPlanPublisher"

        /** Même profondeur d'historique que l'écran de séance du téléphone : la durée du programme. */
        const val LOOKBACK_DAYS = 56L
    }
}
