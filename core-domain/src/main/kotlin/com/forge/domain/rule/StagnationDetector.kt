package com.forge.domain.rule

import com.forge.domain.ForgeRules
import java.time.LocalDate

/**
 * Détection de stagnation sur un exercice (SPEC.md §5.4) : rien n'a progressé depuis deux
 * semaines. Le constat remonte dans l'analyse hebdomadaire, il ne déclenche pas de rappel.
 */
object StagnationDetector {

    /** Charge la plus lourde travaillée sur un exercice à une date donnée. */
    data class LoadPoint(val date: LocalDate, val topSetKg: Float)

    /**
     * Vrai quand la meilleure charge des [weeks] dernières semaines ne dépasse pas la meilleure
     * charge atteinte avant cette fenêtre.
     *
     * Renvoie `false` faute d'historique des deux côtés : sans point de comparaison antérieur,
     * un débutant sur un exercice serait déclaré stagnant dès sa deuxième séance. Une absence
     * de preuve n'est pas une stagnation.
     */
    fun isStagnating(
        history: List<LoadPoint>,
        on: LocalDate,
        weeks: Int = ForgeRules.STAGNATION_WEEKS,
    ): Boolean {
        require(weeks >= 1) { "La fenêtre de stagnation couvre au moins une semaine (reçu : $weeks)" }
        val windowStart = on.minusDays(weeks * 7L - 1)

        val recent = history.filter { it.date >= windowStart && it.date <= on }
        val earlier = history.filter { it.date < windowStart }
        if (recent.isEmpty() || earlier.isEmpty()) return false

        return recent.maxOf { it.topSetKg } <= earlier.maxOf { it.topSetKg }
    }
}
