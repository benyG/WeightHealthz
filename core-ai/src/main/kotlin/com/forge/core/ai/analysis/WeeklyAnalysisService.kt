package com.forge.core.ai.analysis

import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.model.WeeklyReport
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WeeklyAnalysisRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import com.forge.domain.rule.ProgramWeek
import com.forge.domain.rule.WeeklyReportBuilder
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Producteur de l'analyse hebdomadaire, indépendant du modèle qui la rédige : l'interface
 * permet de tester toute la chaîne (compilation, persistance, absence de plan) sans réseau ni
 * clé d'API.
 */
interface WeeklyAnalyst {
    suspend fun analyse(report: WeeklyReport): WeeklyAnalysis
}

/**
 * Enchaîne compilation de la semaine → rédaction → persistance (SPEC.md §5.6).
 *
 * Rien d'appelé ici ne touche l'UI : le résultat se consulte ensuite en base, conformément à
 * SPEC.md §6.3 qui interdit un appel Gemini bloquant.
 */
@Singleton
class WeeklyAnalysisService @Inject constructor(
    private val weights: WeightRepository,
    private val workouts: WorkoutRepository,
    private val plans: PlanRepository,
    private val analyses: WeeklyAnalysisRepository,
    private val analyst: WeeklyAnalyst,
) {

    sealed interface Result {
        data class Produced(val analysis: WeeklyAnalysis) : Result

        /** Aucun plan importé : il n'y a pas de programme à analyser, ce n'est pas une panne. */
        data object NoPlan : Result
    }

    suspend fun analyseWeek(on: LocalDate = LocalDate.now()): Result {
        val start = plans.programStartDate() ?: return Result.NoPlan
        val weekIndex = ProgramWeek.indexFor(start, on)

        val report = compile(weekIndex, on)
        val analysis = analyst.analyse(report)
        analyses.save(analysis)
        return Result.Produced(analysis)
    }

    private suspend fun compile(weekIndex: Int, on: LocalDate): WeeklyReport {
        val historyStart = on.minusWeeks(WeeklyReportBuilder.DEFAULT_HISTORY_WEEKS.toLong())

        return WeeklyReportBuilder.build(
            weekIndex = weekIndex,
            weekEnd = on,
            // On ne lit que la fenêtre nécessaire : inutile de charger tout l'historique en
            // mémoire pour produire huit moyennes.
            weightEntries = weights.entriesBetween(historyStart.minusDays(7), on),
            sessions = workouts.sessionsBetween(historyStart, on),
            target = plans.targetForWeek(weekIndex),
            sessionsPlanned = plans.plannedSessionsPerWeek(),
        )
    }
}
