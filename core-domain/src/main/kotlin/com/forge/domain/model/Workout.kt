package com.forge.domain.model

import java.time.LocalDate

data class WorkoutSession(
    val date: LocalDate,
    val dayTemplate: DayTemplate,
    val exercises: List<ExerciseLog>,
)

/**
 * Modèle de journée du plan (ex. "Haut du corps"). Volontairement pas une `enum` : le contenu
 * réel du programme est importé en phase 2 depuis le plan bundlé (SPEC.md §5.1). Figer ici une
 * liste de séances reviendrait à deviner le programme.
 */
@JvmInline
value class DayTemplate(val label: String)

data class ExerciseLog(
    val name: String,
    val sets: List<SetLog>,
)

data class SetLog(
    val reps: Int,
    val weightKg: Float,
    /**
     * Technique jugée propre sur cette série — exigée par la règle de double progression
     * (CLAUDE.md). Sans valeur par défaut délibérément : la façon dont l'information est saisie
     * à l'écran n'est pas tranchée (IMPLEMENTATION_PLAN.md §11), et un défaut à `true` la
     * trancherait en douce en faveur de "propre sauf mention contraire".
     */
    val cleanTechnique: Boolean,
)

/** Fourchette de répétitions prescrite pour un exercice, bornes incluses. */
data class RepRange(val min: Int, val max: Int) {
    init {
        require(min >= 1) { "La borne basse doit être d'au moins 1 rep (reçu : $min)" }
        require(max >= min) { "La borne haute ($max) ne peut pas être sous la borne basse ($min)" }
    }
}
