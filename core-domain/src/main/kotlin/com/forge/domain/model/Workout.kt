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
     * (CLAUDE.md).
     *
     * Toujours sans valeur par défaut, maintenant pour une autre raison : la saisie est tranchée
     * (une case par série, cochée à la main), et c'est justement parce que le jugement vient de
     * la personne qu'aucun défaut n'a de sens ici. L'écran part case décochée — un oubli coûte
     * alors un palier non accordé, jamais un palier accordé sans l'avoir mérité.
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
