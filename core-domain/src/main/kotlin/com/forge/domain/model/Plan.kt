package com.forge.domain.model

import java.time.DayOfWeek
import java.time.LocalTime

/**
 * Contenu du programme importé (SPEC.md §5.1). Distinct de `WorkoutSession`, qui est ce qui a
 * réellement été fait : ici c'est ce qui est prévu.
 *
 * `daysOfWeek` et `timeOfDay` peuvent être absents. Dans ce cas aucun événement d'agenda n'est
 * créé pour cette ligne — un créneau inventé vaudrait moins qu'un créneau manquant.
 */
data class PlannedWorkoutDay(
    val id: String,
    val label: String,
    val exercises: List<PlannedExercise>,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val timeOfDay: LocalTime? = null,
)

data class PlannedExercise(
    val name: String,
    val prescribedSets: Int,
    val repRange: RepRange,
)

data class PlannedMeal(
    val slot: MealSlot,
    val label: String,
    val description: String,
    val timeOfDay: LocalTime? = null,
)
