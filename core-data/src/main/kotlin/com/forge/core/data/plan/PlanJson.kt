package com.forge.core.data.plan

import kotlinx.serialization.Serializable

/**
 * Format du plan bundlé (`assets/plan.json`), importé au premier lancement — SPEC.md §5.1 exige
 * que le programme soit pré-chargé, jamais saisi à la main.
 *
 * `version` pilote le remplacement : réimporter la même version ne fait rien, une version
 * supérieure remplace le contenu. C'est ce qui permet de livrer un nouveau programme par simple
 * mise à jour du fichier, sans code ni migration.
 */
@Serializable
internal data class PlanJson(
    val version: Int,
    /**
     * Séances prévues par semaine. Absent, on retombe sur le nombre de journées types du plan —
     * l'hypothèse "chaque journée est faite une fois par semaine" est explicite ici plutôt que
     * cachée dans le code appelant, et un vrai programme peut la contredire en le renseignant.
     */
    val sessionsPerWeek: Int? = null,
    val availableLoadsKg: List<Float> = emptyList(),
    val weeklyTargets: List<WeeklyTargetJson> = emptyList(),
    val workoutDays: List<WorkoutDayJson> = emptyList(),
    val meals: List<MealJson> = emptyList(),
)

@Serializable
internal data class WeeklyTargetJson(
    val weekIndex: Int,
    val targetDeltaKgMin: Float,
    val targetDeltaKgMax: Float,
)

@Serializable
internal data class WorkoutDayJson(
    val id: String,
    val label: String,
    val exercises: List<PlannedExerciseJson> = emptyList(),
    /** Jours ISO ("MONDAY"). Absent, aucun événement d'agenda n'est créé pour cette journée. */
    val daysOfWeek: List<String> = emptyList(),
    /** Heure locale "HH:mm". Absente, aucun événement d'agenda n'est créé. */
    val timeOfDay: String? = null,
)

@Serializable
internal data class PlannedExerciseJson(
    val name: String,
    val prescribedSets: Int,
    val repMin: Int,
    val repMax: Int,
)

@Serializable
internal data class MealJson(
    /** Doit correspondre à une valeur de `MealSlot` ; l'import échoue explicitement sinon. */
    val slot: String,
    val label: String,
    val description: String = "",
    /** Heure locale "HH:mm". Absente, aucun événement d'agenda n'est créé pour ce repas. */
    val timeOfDay: String? = null,
)
