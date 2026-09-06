package com.forge.wearlink

import android.util.Log
import com.forge.domain.link.WearSetEntry
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WorkoutRepository
import com.forge.domain.rule.SessionLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Écrit dans la base une série saisie au poignet.
 *
 * Le téléphone reste la source de vérité (DEPLOYMENT.md §12) : la montre envoie ce qui a été fait,
 * c'est ici que ça devient une ligne. La position portée par la série est son identité, ce qui
 * rend un renvoi depuis la file d'attente de la montre sans conséquence (`SessionLog.withSetAt`).
 *
 * Le libellé de la journée est relu dans le plan du téléphone plutôt que reçu de la montre : elle
 * l'a obtenu de lui, le lui redemander n'ajouterait qu'une occasion de divergence.
 */
@Singleton
class WatchSetRecorder @Inject constructor(
    private val plans: PlanRepository,
    private val workouts: WorkoutRepository,
) {

    suspend fun record(entry: WearSetEntry): Boolean {
        val label = plans.workoutDays()
            .firstOrNull { entry.date.dayOfWeek in it.daysOfWeek }
            ?.label

        if (label == null) {
            // Aucune séance prévue ce jour-là : on n'invente pas de journée type pour l'accueillir.
            Log.w(TAG, "Série reçue pour un jour sans séance prévue, ignorée.")
            return false
        }

        val current = workouts.observeSession(entry.date).first()
        workouts.save(
            SessionLog.withSetAt(
                session = current,
                date = entry.date,
                dayLabel = label,
                exerciseName = entry.exerciseName,
                position = entry.position,
                set = entry.set,
            ),
        )
        return true
    }

    private companion object {
        const val TAG = "WatchSetRecorder"
    }
}
