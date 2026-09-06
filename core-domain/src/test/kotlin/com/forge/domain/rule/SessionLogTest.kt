package com.forge.domain.rule

import com.forge.domain.model.DayTemplate
import com.forge.domain.model.ExerciseLog
import com.forge.domain.model.SetLog
import com.forge.domain.model.WorkoutSession
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.jupiter.api.Test

class SessionLogTest {

    private val date: LocalDate = LocalDate.of(2026, 3, 16)
    private val set = SetLog(reps = 10, weightKg = 16f, cleanTechnique = true)

    private fun sessionWith(vararg sets: SetLog) = WorkoutSession(
        date = date,
        dayTemplate = DayTemplate("Bas du corps"),
        exercises = listOf(ExerciseLog("Squat gobelet", sets.toList())),
    )

    @Test
    fun `sans seance du jour la premiere serie en cree une`() {
        val session = SessionLog.withSetAt(
            session = null,
            date = date,
            dayLabel = "Bas du corps",
            exerciseName = "Squat gobelet",
            position = 0,
            set = set,
        )

        assertEquals(date, session.date)
        assertEquals(DayTemplate("Bas du corps"), session.dayTemplate)
        assertEquals(listOf(set), session.exercises.single().sets)
    }

    @Test
    fun `une position deja occupee est corrigee, pas dupliquee`() {
        val existing = sessionWith(set, set)
        val corrected = SetLog(reps = 12, weightKg = 18f, cleanTechnique = false)

        val session = SessionLog.withSetAt(existing, date, "Bas du corps", "Squat gobelet", 1, corrected)

        // C'est ce qui rend un renvoi depuis la file d'attente de la montre inoffensif.
        assertEquals(listOf(set, corrected), session.exercises.single().sets)
    }

    @Test
    fun `une position au-dela des series connues ajoute a la suite`() {
        val existing = sessionWith(set)

        val session = SessionLog.withSetAt(existing, date, "Bas du corps", "Squat gobelet", 7, set)

        // Pas de trou comblé par des séries inventées : la double progression compte les séries
        // faites, pas des places réservées.
        assertEquals(2, session.exercises.single().sets.size)
    }

    @Test
    fun `un exercice absent de la seance s ajoute sans toucher aux autres`() {
        val existing = sessionWith(set)

        val session = SessionLog.withSetAt(existing, date, "Bas du corps", "Fente avant", 0, set)

        assertEquals(listOf("Squat gobelet", "Fente avant"), session.exercises.map { it.name })
        assertEquals(1, session.exercises.first().sets.size)
    }

    @Test
    fun `le libelle d une seance existante n est pas ecrase`() {
        val existing = sessionWith(set)

        val session = SessionLog.withSetAt(existing, date, "Haut du corps", "Squat gobelet", 0, set)

        // Le libellé transmis ne sert qu'à créer une séance absente ; il ne renomme pas la séance
        // en cours.
        assertEquals(DayTemplate("Bas du corps"), session.dayTemplate)
    }

    @Test
    fun `une position negative est refusee`() {
        assertFailsWith<IllegalArgumentException> {
            SessionLog.withSetAt(null, date, "Bas du corps", "Squat gobelet", -1, set)
        }
    }
}
