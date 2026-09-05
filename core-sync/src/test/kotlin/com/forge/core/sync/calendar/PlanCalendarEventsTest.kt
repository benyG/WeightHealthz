package com.forge.core.sync.calendar

import com.forge.domain.model.MealSlot
import com.forge.domain.model.PlannedExercise
import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.RepRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlanCalendarEventsTest {

    // Un dimanche, pour que "prochaine occurrence" ait un sens dans les tests.
    private val from: LocalDate = LocalDate.of(2026, 3, 15)

    private fun workoutDay(
        id: String = "bas-du-corps",
        days: Set<DayOfWeek> = setOf(DayOfWeek.MONDAY, DayOfWeek.THURSDAY),
        time: LocalTime? = LocalTime.of(18, 30),
    ) = PlannedWorkoutDay(
        id = id,
        label = "Bas du corps",
        exercises = listOf(PlannedExercise("Squat gobelet", 4, RepRange(8, 12))),
        daysOfWeek = days,
        timeOfDay = time,
    )

    private fun meal(time: LocalTime? = LocalTime.of(7, 0)) =
        PlannedMeal(MealSlot.PETIT_DEJEUNER, "Petit-déjeuner", "Flocons d'avoine", time)

    @Test
    fun `une seance sans horaire ne produit aucun evenement`() {
        val events = PlanCalendarEvents.build(listOf(workoutDay(time = null)), emptyList(), from)

        // Inventer une heure mettrait un rappel faux dans l'agenda, pire qu'un rappel absent.
        assertTrue(events.isEmpty())
    }

    @Test
    fun `une seance sans jour de semaine ne produit aucun evenement`() {
        val events = PlanCalendarEvents.build(listOf(workoutDay(days = emptySet())), emptyList(), from)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `un repas sans horaire ne produit aucun evenement`() {
        val events = PlanCalendarEvents.build(emptyList(), listOf(meal(time = null)), from)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `la recurrence hebdomadaire liste les jours dans l ordre`() {
        val events = PlanCalendarEvents.build(listOf(workoutDay()), emptyList(), from)

        assertEquals("FREQ=WEEKLY;BYDAY=MO,TH", events.single().recurrenceRule)
    }

    @Test
    fun `la premiere occurrence tombe sur un jour prevu`() {
        // Le 15 mars 2026 est un dimanche ; le premier jour prévu est le lundi 16.
        val events = PlanCalendarEvents.build(listOf(workoutDay()), emptyList(), from)

        val first = events.single().firstDate
        assertEquals(LocalDate.of(2026, 3, 16), first)
        assertEquals(DayOfWeek.MONDAY, first.dayOfWeek)
    }

    @Test
    fun `un repas est quotidien et demarre le jour meme`() {
        val events = PlanCalendarEvents.build(emptyList(), listOf(meal()), from)

        val event = events.single()
        assertEquals("FREQ=DAILY", event.recurrenceRule)
        assertEquals(from, event.firstDate)
        assertEquals(LocalTime.of(7, 0), event.startTime)
    }

    @Test
    fun `les cles sont stables et distinctes`() {
        val events = PlanCalendarEvents.build(
            listOf(workoutDay(id = "haut-du-corps"), workoutDay(id = "bas-du-corps")),
            listOf(meal()),
            from,
        )

        val keys = events.map { it.key }
        assertEquals(keys.size, keys.distinct().size)
        assertTrue(keys.all { it.startsWith(PlanCalendarEvents.KEY_PREFIX) })
        assertTrue(keys.contains("forge:workout:haut-du-corps"))
        assertTrue(keys.contains("forge:meal:PETIT_DEJEUNER"))

        // Rejouer la construction donne les mêmes clés : c'est ce qui rend la sync idempotente.
        val replayed = PlanCalendarEvents.build(listOf(workoutDay(id = "haut-du-corps")), emptyList(), from)
        assertEquals("forge:workout:haut-du-corps", replayed.single().key)
    }

    @Test
    fun `la seance porte son contenu en description`() {
        val events = PlanCalendarEvents.build(listOf(workoutDay()), emptyList(), from)

        assertEquals("Squat gobelet — 4 × 8-12", events.single().description)
    }

    @Test
    fun `la seance se rappelle en avance, le repas a l heure`() {
        val events = PlanCalendarEvents.build(listOf(workoutDay()), listOf(meal()), from)

        val workout = events.first { it.key.contains("workout") }
        val breakfast = events.first { it.key.contains("meal") }

        assertEquals(30, workout.reminderMinutesBefore)
        // Anticiper un repas ferait manger trop tôt.
        assertEquals(0, breakfast.reminderMinutesBefore)
    }
}
