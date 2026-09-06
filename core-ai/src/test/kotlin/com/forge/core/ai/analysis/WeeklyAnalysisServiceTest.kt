package com.forge.core.ai.analysis

import com.forge.domain.model.PlanTarget
import com.forge.domain.model.PlannedMeal
import com.forge.domain.model.PlannedWorkoutDay
import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.model.WeeklyReport
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WorkoutSession
import com.forge.domain.repository.PlanRepository
import com.forge.domain.repository.WeeklyAnalysisRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.repository.WorkoutRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyAnalysisServiceTest {

    private val start: LocalDate = LocalDate.of(2026, 3, 2)
    private val today: LocalDate = start.plusDays(24) // semaine 4

    private class FakeWeights(private val entries: List<WeightEntry> = emptyList()) : WeightRepository {
        override fun observeAll(): Flow<List<WeightEntry>> = flowOf(entries)
        override suspend fun entriesBetween(from: LocalDate, to: LocalDate) =
            entries.filter { it.date >= from && it.date <= to }
        override suspend fun record(entry: WeightEntry) = Unit
    }

    private class FakeWorkouts(private val sessions: List<WorkoutSession> = emptyList()) : WorkoutRepository {
        override fun observeSession(date: LocalDate): Flow<WorkoutSession?> = flowOf(null)
        override suspend fun save(session: WorkoutSession) = Unit
        override suspend fun historyFor(exerciseName: String, since: LocalDate) = sessions
        override suspend fun sessionsBetween(from: LocalDate, to: LocalDate) =
            sessions.filter { it.date >= from && it.date <= to }
    }

    private class FakePlan(
        private val start: LocalDate?,
        private val target: PlanTarget? = null,
        private val sessionsPerWeek: Int = 4,
    ) : PlanRepository {
        override suspend fun targetForWeek(weekIndex: Int) = target
        override suspend fun plannedSessionsPerWeek() = sessionsPerWeek
        override suspend fun programStartDate() = start
        override suspend fun workoutDays(): List<PlannedWorkoutDay> = emptyList()
        override suspend fun meals(): List<PlannedMeal> = emptyList()
        override suspend fun programWeekCount() = 0
    }

    private class RecordingAnalyses : WeeklyAnalysisRepository {
        val saved = mutableListOf<WeeklyAnalysis>()
        override fun observeLatest(): Flow<WeeklyAnalysis?> = flowOf(saved.lastOrNull())
        override suspend fun save(analysis: WeeklyAnalysis) {
            saved += analysis
        }
    }

    private class CapturingAnalyst : WeeklyAnalyst {
        var lastReport: WeeklyReport? = null
        override suspend fun analyse(report: WeeklyReport): WeeklyAnalysis {
            lastReport = report
            return WeeklyAnalysis(
                weekIndex = report.weekIndex,
                summaryText = "résumé",
                focusExercise = "Squat gobelet",
                audioScript = "script",
                audioUrl = null,
                recommendedAdjustmentKcal = report.ruleBasedAdjustmentKcal,
            )
        }
    }

    @Test
    fun `sans plan importe il n y a rien a analyser`() = runTest {
        val analyses = RecordingAnalyses()
        val service = WeeklyAnalysisService(
            weights = FakeWeights(),
            workouts = FakeWorkouts(),
            plans = FakePlan(start = null),
            analyses = analyses,
            analyst = CapturingAnalyst(),
        )

        val result = service.analyseWeek(today)

        assertEquals(WeeklyAnalysisService.Result.NoPlan, result)
        assertTrue("Aucune analyse ne doit être écrite", analyses.saved.isEmpty())
    }

    @Test
    fun `l index de semaine se deduit du debut du programme`() = runTest {
        val analyst = CapturingAnalyst()
        val service = WeeklyAnalysisService(
            weights = FakeWeights(),
            workouts = FakeWorkouts(),
            plans = FakePlan(start = start, target = PlanTarget(4, 1.2f, 2.0f)),
            analyses = RecordingAnalyses(),
            analyst = analyst,
        )

        service.analyseWeek(today)

        assertEquals(4, analyst.lastReport!!.weekIndex)
        assertEquals(4, analyst.lastReport!!.sessionsPlanned)
    }

    @Test
    fun `l analyse produite est persistee`() = runTest {
        val analyses = RecordingAnalyses()
        val service = WeeklyAnalysisService(
            weights = FakeWeights(),
            workouts = FakeWorkouts(),
            plans = FakePlan(start = start),
            analyses = analyses,
            analyst = CapturingAnalyst(),
        )

        val result = service.analyseWeek(today)

        assertTrue(result is WeeklyAnalysisService.Result.Produced)
        assertEquals(1, analyses.saved.size)
        assertEquals(4, analyses.saved.single().weekIndex)
        // Le MVP produit le texte, pas encore l'audio (SPEC.md §9).
        assertNull(analyses.saved.single().audioUrl)
    }
}
