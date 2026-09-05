package com.forge.domain.rule

import com.forge.domain.ForgeRules

/**
 * Ajustement calorique du plan (CLAUDE.md, valeurs non négociables).
 *
 * Un seul déclencheur : deux semaines consécutives du même côté de la cible. Une semaine isolée
 * hors cible ne change rien — c'est ce qui distingue un ajustement d'une réaction au bruit.
 */
object CalorieAdjustment {

    /**
     * Ajustement à appliquer au vu des gains hebdomadaires, du plus ancien au plus récent
     * (tels que produits par `WeightTrend.weeklyGainKg`).
     *
     * Seules les [ForgeRules.CONSECUTIVE_WEEKS_BEFORE_ADJUSTMENT] dernières semaines comptent.
     * Historique plus court, semaines contradictoires ou gain dans la cible : aucun ajustement.
     *
     * Les seuils sont stricts : un gain exactement égal à un seuil n'est pas "hors cible".
     */
    fun forRecentWeeklyGains(weeklyGainsKg: List<Double>): Int {
        val required = ForgeRules.CONSECUTIVE_WEEKS_BEFORE_ADJUSTMENT
        if (weeklyGainsKg.size < required) return 0

        val recent = weeklyGainsKg.takeLast(required)
        return when {
            recent.all { it < ForgeRules.WEEKLY_GAIN_LOW_THRESHOLD_KG } -> ForgeRules.KCAL_ADJUSTMENT_UP
            recent.all { it > ForgeRules.WEEKLY_GAIN_HIGH_THRESHOLD_KG } -> ForgeRules.KCAL_ADJUSTMENT_DOWN
            else -> 0
        }
    }

    /**
     * Borne un ajustement proposé de l'extérieur à ±[ForgeRules.KCAL_ADJUSTMENT_BOUND].
     *
     * C'est le garde-fou de SPEC.md §6.3 : Gemini propose un chiffre, l'app le valide avant de
     * l'appliquer. Le modèle ne peut pas inventer une règle d'ajustement hors de celles codées
     * ici, quelle que soit sa sortie.
     */
    fun validated(proposedKcal: Int): Int = proposedKcal.coerceIn(
        -ForgeRules.KCAL_ADJUSTMENT_BOUND,
        ForgeRules.KCAL_ADJUSTMENT_BOUND,
    )
}
