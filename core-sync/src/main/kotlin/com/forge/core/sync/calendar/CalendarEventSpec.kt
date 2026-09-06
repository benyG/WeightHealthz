package com.forge.core.sync.calendar

import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * Événement d'agenda à créer, décrit indépendamment de l'API qui l'écrira. Cette séparation rend
 * la partie intéressante — quels événements, quelle récurrence, quel rappel — testable sans
 * appareil.
 */
data class CalendarEventSpec(
    /** Clé stable, dérivée du plan : c'est elle qui rend la création idempotente. */
    val key: String,
    val title: String,
    val description: String,
    /** Première occurrence : pour une séance, le prochain jour prévu ; pour un repas, aujourd'hui. */
    val firstDate: LocalDate,
    val startTime: LocalTime,
    val duration: Duration,
    /** Règle de récurrence iCalendar (RFC 5545), telle que l'attend l'agenda. */
    val recurrenceRule: String,
    /** Minutes avant l'événement pour le rappel natif de l'agenda (SPEC.md §5.8). */
    val reminderMinutesBefore: Int,
)

/**
 * Traduit le programme importé en événements d'agenda (SPEC.md §5.8) : « que le programme
 * existe aussi dans l'agenda que tu regardes déjà tous les jours ».
 *
 * Règle structurante : **une ligne du plan sans horaire ne produit aucun événement**. Inventer
 * 12h30 pour un déjeuner que le programme ne situe pas mettrait un rappel faux dans l'agenda, ce
 * qui est pire qu'un rappel absent.
 */
object PlanCalendarEvents {

    fun build(
        workoutDays: List<PlannedWorkoutDay>,
        meals: List<PlannedMeal>,
        from: LocalDate,
    ): List<CalendarEventSpec> = buildWorkouts(workoutDays, from) + buildMeals(meals, from)

    private fun buildWorkouts(days: List<PlannedWorkoutDay>, from: LocalDate): List<CalendarEventSpec> =
        days.mapNotNull { day ->
            val time = day.timeOfDay ?: return@mapNotNull null
            if (day.daysOfWeek.isEmpty()) return@mapNotNull null

            CalendarEventSpec(
                key = "$KEY_PREFIX:workout:${day.id}",
                firstDate = nextOccurrence(from, day.daysOfWeek),
                title = day.label,
                description = day.exercises.joinToString("\n") {
                    "${it.name} — ${it.prescribedSets} × ${it.repRange.min}-${it.repRange.max}"
                },
                startTime = time,
                duration = WORKOUT_DURATION,
                recurrenceRule = weeklyRule(day),
                reminderMinutesBefore = WORKOUT_REMINDER_MINUTES,
            )
        }

    private fun buildMeals(meals: List<PlannedMeal>, from: LocalDate): List<CalendarEventSpec> =
        meals.mapNotNull { meal ->
            val time = meal.timeOfDay ?: return@mapNotNull null

            CalendarEventSpec(
                key = "$KEY_PREFIX:meal:${meal.slot.name}",
                firstDate = from,
                title = meal.label,
                description = meal.description,
                startTime = time,
                duration = MEAL_DURATION,
                recurrenceRule = DAILY_RULE,
                reminderMinutesBefore = MEAL_REMINDER_MINUTES,
            )
        }

    /**
     * Première date à partir de [from] tombant sur un des jours prévus. La première occurrence
     * doit coïncider avec la récurrence, sinon l'agenda place un événement en trop le jour du
     * démarrage.
     */
    private fun nextOccurrence(from: LocalDate, days: Set<DayOfWeek>): LocalDate =
        (0L..6L).map { from.plusDays(it) }.first { it.dayOfWeek in days }

    /** `BYDAY` attend les deux premières lettres du jour en anglais : MO, TU, WE… */
    private fun weeklyRule(day: PlannedWorkoutDay): String {
        val days = day.daysOfWeek
            .sortedBy { it.value }
            .joinToString(",") { it.name.take(2) }
        return "FREQ=WEEKLY;BYDAY=$days"
    }

    const val KEY_PREFIX: String = "forge"

    private const val DAILY_RULE = "FREQ=DAILY"
    private val WORKOUT_DURATION: Duration = Duration.ofMinutes(75)
    private val MEAL_DURATION: Duration = Duration.ofMinutes(30)

    /** Une séance se prépare : le rappel arrive assez tôt pour partir à la salle. */
    private const val WORKOUT_REMINDER_MINUTES = 30

    /** Un repas se rappelle à l'heure : l'anticiper ferait manger trop tôt. */
    private const val MEAL_REMINDER_MINUTES = 0
}
