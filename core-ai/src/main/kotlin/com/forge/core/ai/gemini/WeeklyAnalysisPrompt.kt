package com.forge.core.ai.gemini

import com.forge.domain.ForgeRules
import com.forge.domain.model.WeeklyReport
import java.util.Locale

/**
 * Construit le prompt d'analyse hebdomadaire de SPEC.md §6.1.
 *
 * Deux principes tiennent ce texte :
 * - il ne transporte que des agrégats et des libellés d'exercices, jamais l'historique brut des
 *   pesées (`CLAUDE.md`, confidentialité) ;
 * - il annonce le chiffre d'ajustement **déjà calculé par les règles du plan** et demande au
 *   modèle de rédiger autour. Sans cela, le résumé pourrait annoncer un ajustement différent de
 *   celui que l'app applique, et l'utilisateur lirait une contradiction.
 */
internal object WeeklyAnalysisPrompt {

    fun build(report: WeeklyReport): String = buildString {
        appendLine("Tu es un coach en prise de masse. Voici les données de la semaine ${report.weekIndex} :")
        appendLine("- Poids moyen 7j : ${report.averageKg.formatKg()} (cible cumulée : ${formatTarget(report)})")
        appendLine("- Gain sur la semaine : ${report.weeklyGainKg.formatKg()}")
        appendLine("- Historique 8 semaines : ${formatSeries(report)}")
        appendLine("- Séances complétées : ${report.sessionsDone}/${report.sessionsPlanned}")
        appendLine("- Charges par exercice (série la plus lourde, cette semaine vs précédente) :")
        appendLine(formatExercises(report))
        appendLine()
        appendLine("Règles d'ajustement du plan, à respecter strictement :")
        appendLine(
            "- +${ForgeRules.KCAL_ADJUSTMENT_UP} kcal si le gain reste sous " +
                "${ForgeRules.WEEKLY_GAIN_LOW_THRESHOLD_KG} kg/semaine " +
                "${ForgeRules.CONSECUTIVE_WEEKS_BEFORE_ADJUSTMENT} semaines de suite.",
        )
        appendLine(
            "- ${ForgeRules.KCAL_ADJUSTMENT_DOWN} kcal si le gain dépasse " +
                "${ForgeRules.WEEKLY_GAIN_HIGH_THRESHOLD_KG} kg/semaine " +
                "${ForgeRules.CONSECUTIVE_WEEKS_BEFORE_ADJUSTMENT} semaines de suite.",
        )
        appendLine("- Aucun ajustement dans tous les autres cas. Cible : de ${ForgeRules.WEEKLY_GAIN_TARGET_MIN_KG} à ${ForgeRules.WEEKLY_GAIN_TARGET_MAX_KG} kg/semaine.")
        appendLine()
        appendLine(
            "L'application a déjà appliqué ces règles et obtient ${report.ruleBasedAdjustmentKcal} kcal. " +
                "Reprends exactement ce chiffre dans `kcal_adjustment` et rédige en cohérence avec lui : " +
                "tu n'as pas à recalculer ni à proposer autre chose.",
        )
        appendLine()
        appendLine("Réponds en JSON avec ces quatre champs :")
        appendLine("- summary : 3 phrases maximum, ton direct, aucune flatterie.")
        appendLine("- kcal_adjustment : entier, celui indiqué ci-dessus.")
        appendLine("- focus_exercise : l'exercice à surveiller cette semaine (celui qui stagne, sinon celui qui progresse le mieux).")
        append("- audio_script : le même constat dit à l'oral, tutoiement, phrases courtes, 60 secondes maximum, aucune énumération.")
    }

    private fun formatTarget(report: WeeklyReport): String {
        val target = report.target ?: return "non définie"
        return "de ${target.targetDeltaKgMin} à ${target.targetDeltaKgMax} kg"
    }

    private fun formatSeries(report: WeeklyReport): String =
        if (report.eightWeekAverages.isEmpty()) {
            "aucune pesée enregistrée"
        } else {
            report.eightWeekAverages.joinToString(", ") { it.averageKg.formatKg() }
        }

    private fun formatExercises(report: WeeklyReport): String =
        if (report.exerciseDeltas.isEmpty()) {
            "  (aucune séance loguée sur les deux dernières semaines)"
        } else {
            report.exerciseDeltas.joinToString("\n") { delta ->
                val current = delta.thisWeek?.let { "${it.weightKg} kg × ${it.reps}" } ?: "non travaillé"
                val previous = delta.lastWeek?.let { "${it.weightKg} kg × ${it.reps}" } ?: "non travaillé"
                val stagnation = if (delta.stagnating) " [stagnation depuis ${ForgeRules.STAGNATION_WEEKS} semaines]" else ""
                "  ${delta.name} : $current vs $previous$stagnation"
            }
        }

    /** Une donnée absente se dit, elle ne se remplace pas par un zéro trompeur. */
    private fun Double?.formatKg(): String =
        this?.let { String.format(Locale.US, "%.1f kg", it) } ?: "non disponible"
}
