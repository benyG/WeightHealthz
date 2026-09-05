package com.forge.domain.rule

import com.forge.domain.ForgeRules
import com.forge.domain.model.WeightEntry
import java.time.LocalDate

/**
 * Lissage du poids. Aucune décision du programme ne se prend sur une pesée isolée
 * (CLAUDE.md) : tout passe par la moyenne mobile.
 */
object WeightTrend {

    /**
     * Moyenne mobile sur la fenêtre de [ForgeRules.MOVING_AVERAGE_DAYS] jours qui se termine à
     * [on], bornes incluses.
     *
     * Les pesées d'une même journée sont d'abord moyennées entre elles : trois lectures de
     * balance le même matin pèsent autant qu'un jour où l'on s'est pesé une fois, sans quoi une
     * journée bavarde tirerait la moyenne à elle.
     *
     * Renvoie `null` quand la fenêtre ne contient aucune pesée — l'absence de donnée se
     * propage, elle ne se remplace pas par un zéro.
     */
    fun movingAverageKg(
        entries: List<WeightEntry>,
        on: LocalDate,
        windowDays: Int = ForgeRules.MOVING_AVERAGE_DAYS,
    ): Double? {
        require(windowDays >= 1) { "La fenêtre doit couvrir au moins un jour (reçu : $windowDays)" }
        val from = on.minusDays(windowDays - 1L)

        val dailyAverages = entries
            .filter { it.date >= from && it.date <= on }
            .groupBy { it.date }
            .map { (_, sameDay) -> sameDay.map { it.kg.toDouble() }.average() }

        return if (dailyAverages.isEmpty()) null else dailyAverages.average()
    }

    /**
     * Prise de poids sur la semaine qui se termine à [on] : écart entre la moyenne mobile du
     * jour et celle d'il y a sept jours. C'est cette valeur, jamais un écart de poids brut, que
     * consomme la règle d'ajustement calorique.
     *
     * Renvoie `null` si l'une des deux fenêtres est vide — on ne compare pas à du vide.
     */
    fun weeklyGainKg(entries: List<WeightEntry>, on: LocalDate): Double? {
        val current = movingAverageKg(entries, on) ?: return null
        val previous = movingAverageKg(entries, on.minusDays(7)) ?: return null
        return current - previous
    }
}
