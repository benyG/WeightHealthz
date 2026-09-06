package com.forge.domain.rule

import com.forge.domain.model.DayTemplate
import com.forge.domain.model.ExerciseLog
import com.forge.domain.model.SetLog
import com.forge.domain.model.WorkoutSession
import java.time.LocalDate

/**
 * Écriture d'une série à une position donnée dans la séance du jour.
 *
 * C'est la règle qui rend inoffensif le renvoi d'une série depuis la file d'attente de la montre :
 * la position est l'identité de la série, pas son rang d'arrivée. Recevoir deux fois la même série
 * corrige la même ligne au lieu d'en créer une seconde.
 *
 * Quand les deux écrans ont servi le même jour, la règle de fusion est celle-là et rien d'autre :
 * la dernière écriture sur une position donnée gagne. Un utilisateur n'a qu'un corps — deux
 * saisies simultanées de la même série ne sont pas un scénario réel, une correction après coup
 * l'est.
 */
object SessionLog {

    fun withSetAt(
        session: WorkoutSession?,
        date: LocalDate,
        dayLabel: String,
        exerciseName: String,
        position: Int,
        set: SetLog,
    ): WorkoutSession {
        require(position >= 0) { "Une position de série ne peut pas être négative (reçu : $position)" }

        val current = session ?: WorkoutSession(date, DayTemplate(dayLabel), emptyList())
        val existing = current.exercises.firstOrNull { it.name == exerciseName }

        val updatedSets = when {
            existing == null -> listOf(set)
            position in existing.sets.indices ->
                existing.sets.mapIndexed { index, previous -> if (index == position) set else previous }
            // Une position au-delà de ce qui existe veut dire qu'une série intermédiaire n'est
            // jamais arrivée. On ajoute à la suite plutôt que de combler le trou : inventer des
            // séries fausserait la double progression, qui compte les séries faites.
            else -> existing.sets + set
        }

        val updatedExercise = ExerciseLog(exerciseName, updatedSets)
        val exercises = if (existing == null) {
            current.exercises + updatedExercise
        } else {
            current.exercises.map { if (it.name == exerciseName) updatedExercise else it }
        }

        return current.copy(exercises = exercises)
    }

    /**
     * Séries de la dernière séance, strictement avant [date], où [exerciseName] a été travaillé.
     *
     * C'est la matière de la charge proposée. Le téléphone la calcule deux fois — pour son écran
     * de séance, et pour la séance qu'il publie vers la montre — d'où une fonction pure partagée
     * plutôt que deux boucles jumelles qui finiraient par diverger.
     */
    fun lastSetsBefore(
        sessions: List<WorkoutSession>,
        exerciseName: String,
        date: LocalDate,
    ): List<SetLog> = sessions
        .filter { it.date < date }
        .maxByOrNull { it.date }
        ?.exercises
        ?.firstOrNull { it.name == exerciseName }
        ?.sets
        .orEmpty()
}
