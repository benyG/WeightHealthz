package com.forge.domain.rule

import com.forge.domain.model.WeightEntry
import java.time.LocalDate

/**
 * Écart cumulé depuis le début du programme — le chiffre qui domine l'écran d'accueil
 * (DESIGN.md §7.1 : "+1.8 kg", comparé à "objectif : +1.2 à +2.0 kg").
 *
 * La référence est la **première pesée enregistrée**, celle saisie à l'onboarding (SPEC.md §5.1).
 * Le poids courant, lui, est la moyenne mobile 7 jours et non la pesée du matin : comparer un
 * point à un point ferait sauter l'écart d'un demi-kilo d'un jour à l'autre.
 */
object ProgramProgress {

    fun cumulativeDeltaKg(entries: List<WeightEntry>, on: LocalDate): Double? {
        val start = entries.minByOrNull { it.date } ?: return null
        val current = WeightTrend.movingAverageKg(entries, on) ?: return null
        return current - start.kg.toDouble()
    }
}
