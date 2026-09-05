package com.forge.domain.model

import java.time.LocalDate

/**
 * Photographie d'une semaine, prête à être résumée. C'est l'unique matière envoyée à Gemini
 * (SPEC.md §6.1) : des séries agrégées et des libellés d'exercices, jamais un export brut de
 * pesées horodatées — la contrainte de confidentialité de `CLAUDE.md` tient à ce que ce type
 * reste pauvre.
 */
data class WeeklyReport(
    val weekIndex: Int,
    val weekEnd: LocalDate,
    /** Moyenne mobile 7 jours à la fin de la semaine ; `null` si aucune pesée. */
    val averageKg: Double?,
    val weeklyGainKg: Double?,
    val target: PlanTarget?,
    /** Moyennes hebdomadaires des huit dernières semaines, de la plus ancienne à la plus récente. */
    val eightWeekAverages: List<WeeklyAveragePoint>,
    val sessionsDone: Int,
    val sessionsPlanned: Int,
    /**
     * Ajustement calorique calculé par les règles du plan. Il est transmis à Gemini pour qu'il
     * rédige autour du bon chiffre, et c'est lui qui fait foi (voir SPEC.md §6.3).
     */
    val ruleBasedAdjustmentKcal: Int,
    val exerciseDeltas: List<ExerciseDelta>,
)

data class WeeklyAveragePoint(
    val weekEnd: LocalDate,
    val averageKg: Double,
)

/**
 * Progression d'un exercice d'une semaine sur l'autre, sur la base de la série la plus lourde.
 * `null` d'un côté signifie que l'exercice n'a pas été travaillé cette semaine-là.
 */
data class ExerciseDelta(
    val name: String,
    val thisWeek: SetSummary?,
    val lastWeek: SetSummary?,
    val stagnating: Boolean,
) {
    val loadDeltaKg: Float?
        get() = if (thisWeek != null && lastWeek != null) thisWeek.weightKg - lastWeek.weightKg else null
}

data class SetSummary(val weightKg: Float, val reps: Int)
