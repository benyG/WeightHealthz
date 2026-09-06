package com.forge.domain.rule

import com.forge.domain.ForgeRules
import com.forge.domain.model.ExerciseDelta
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.SetSummary
import com.forge.domain.model.WeeklyAveragePoint
import com.forge.domain.model.WeeklyReport
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WorkoutSession
import java.time.LocalDate

/**
 * Agrège une semaine en un [WeeklyReport]. Toute la compilation vit ici, en JVM pur : c'est de
 * la règle métier, pas de la plomberie réseau, et elle doit se tester sans émulateur ni clé API.
 *
 * Cette fonction décide aussi de ce qui part vers Gemini. Elle ne produit que des agrégats et
 * des libellés d'exercices — jamais l'historique brut des pesées (`CLAUDE.md`, confidentialité).
 */
object WeeklyReportBuilder {

    fun build(
        weekIndex: Int,
        weekEnd: LocalDate,
        weightEntries: List<WeightEntry>,
        sessions: List<WorkoutSession>,
        target: PlanTarget?,
        sessionsPlanned: Int,
        historyWeeks: Int = DEFAULT_HISTORY_WEEKS,
    ): WeeklyReport {
        val averages = (historyWeeks - 1 downTo 0).mapNotNull { weeksAgo ->
            val end = weekEnd.minusWeeks(weeksAgo.toLong())
            WeightTrend.movingAverageKg(weightEntries, end)?.let { WeeklyAveragePoint(end, it) }
        }

        val weeklyGains = averages.zipWithNext { previous, current -> current.averageKg - previous.averageKg }

        return WeeklyReport(
            weekIndex = weekIndex,
            weekEnd = weekEnd,
            averageKg = WeightTrend.movingAverageKg(weightEntries, weekEnd),
            weeklyGainKg = WeightTrend.weeklyGainKg(weightEntries, weekEnd),
            target = target,
            eightWeekAverages = averages,
            sessionsDone = sessions.count { it.date > weekEnd.minusDays(7) && it.date <= weekEnd },
            sessionsPlanned = sessionsPlanned,
            ruleBasedAdjustmentKcal = CalorieAdjustment.forRecentWeeklyGains(weeklyGains),
            exerciseDeltas = buildDeltas(sessions, weekEnd),
        )
    }

    /**
     * Compare la série la plus lourde de chaque exercice entre cette semaine et la précédente.
     * Un exercice absent d'une des deux semaines garde un côté `null` plutôt qu'un zéro, pour
     * qu'"il n'a pas été travaillé" ne se lise pas comme "il a régressé à 0 kg".
     */
    private fun buildDeltas(sessions: List<WorkoutSession>, weekEnd: LocalDate): List<ExerciseDelta> {
        val thisWeek = sessions.filter { it.date > weekEnd.minusDays(7) && it.date <= weekEnd }
        val lastWeek = sessions.filter { it.date > weekEnd.minusDays(14) && it.date <= weekEnd.minusDays(7) }

        val names = (thisWeek + lastWeek).flatMap { session -> session.exercises.map { it.name } }.distinct()

        return names.map { name ->
            val current = heaviestSet(thisWeek, name)
            val previous = heaviestSet(lastWeek, name)
            ExerciseDelta(
                name = name,
                thisWeek = current,
                lastWeek = previous,
                stagnating = StagnationDetector.isStagnating(
                    history = sessions.mapNotNull { session ->
                        heaviestSet(listOf(session), name)?.let {
                            StagnationDetector.LoadPoint(session.date, it.weightKg)
                        }
                    },
                    on = weekEnd,
                    weeks = ForgeRules.STAGNATION_WEEKS,
                ),
            )
        }
    }

    private fun heaviestSet(sessions: List<WorkoutSession>, exerciseName: String): SetSummary? =
        sessions
            .flatMap { session -> session.exercises.filter { it.name == exerciseName } }
            .flatMap { it.sets }
            .maxByOrNull { it.weightKg }
            ?.let { SetSummary(weightKg = it.weightKg, reps = it.reps) }

    /** Huit semaines : l'horizon du programme et celui du prompt de SPEC.md §6.1. */
    const val DEFAULT_HISTORY_WEEKS: Int = 8
}
