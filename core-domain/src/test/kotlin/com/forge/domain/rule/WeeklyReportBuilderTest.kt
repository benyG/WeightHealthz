package com.forge.domain.rule

import com.forge.domain.model.DayTemplate
import com.forge.domain.model.ExerciseLog
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.SetLog
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import com.forge.domain.model.WorkoutSession
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class WeeklyReportBuilderTest {

    private val weekEnd: LocalDate = LocalDate.of(2026, 3, 15)

    private fun weight(daysAgo: Long, kg: Float) =
        WeightEntry(weekEnd.minusDays(daysAgo), kg, WeightSource.MANUAL)

    private fun session(daysAgo: Long, exercise: String, kg: Float, reps: Int = 10) =
        WorkoutSession(
            date = weekEnd.minusDays(daysAgo),
            dayTemplate = DayTemplate("Haut du corps"),
            exercises = listOf(ExerciseLog(exercise, listOf(SetLog(reps, kg, cleanTechnique = true)))),
        )

    @Test
    fun `le rapport compte les seances de la semaine ecoulee seulement`() {
        val sessions = listOf(
            session(1, "Squat gobelet", 16f),
            session(4, "Squat gobelet", 16f),
            session(9, "Squat gobelet", 16f), // semaine précédente
        )

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = emptyList(),
            sessions = sessions,
            target = null,
            sessionsPlanned = 3,
        )

        assertEquals(2, report.sessionsDone)
        assertEquals(3, report.sessionsPlanned)
    }

    @Test
    fun `la serie de huit semaines ignore les semaines sans pesee`() {
        // Pesées sur les deux dernières semaines seulement.
        val entries = (0L..13L).map { weight(it, 82f) }

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = entries,
            sessions = emptyList(),
            target = null,
            sessionsPlanned = 3,
        )

        assertEquals(2, report.eightWeekAverages.size)
        assertEquals(weekEnd, report.eightWeekAverages.last().weekEnd)
    }

    @Test
    fun `l ajustement du rapport est celui des regles du plan`() {
        // Trois semaines à +0,1 kg : deux semaines consécutives sous le seuil bas.
        val entries = (0L..20L).map { daysAgo ->
            val weeksAgo = daysAgo / 7
            weight(daysAgo, 80f + (2 - weeksAgo) * 0.1f)
        }

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = entries,
            sessions = emptyList(),
            target = null,
            sessionsPlanned = 3,
        )

        assertEquals(250, report.ruleBasedAdjustmentKcal)
    }

    @Test
    fun `le delta compare la serie la plus lourde d une semaine sur l autre`() {
        val sessions = listOf(
            session(2, "Développé couché", 22f),
            session(5, "Développé couché", 20f), // même semaine, plus léger
            session(9, "Développé couché", 20f), // semaine précédente
        )

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = emptyList(),
            sessions = sessions,
            target = null,
            sessionsPlanned = 3,
        )

        val delta = report.exerciseDeltas.single { it.name == "Développé couché" }
        assertEquals(22f, delta.thisWeek!!.weightKg)
        assertEquals(20f, delta.lastWeek!!.weightKg)
        assertEquals(2f, delta.loadDeltaKg)
        assertFalse(delta.stagnating)
    }

    @Test
    fun `un exercice absent d une semaine ne se lit pas comme une regression`() {
        val sessions = listOf(session(2, "Rowing haltère", 18f))

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = emptyList(),
            sessions = sessions,
            target = null,
            sessionsPlanned = 3,
        )

        val delta = report.exerciseDeltas.single()
        assertNull(delta.lastWeek)
        assertNull(delta.loadDeltaKg)
    }

    @Test
    fun `une charge inchangee depuis deux semaines remonte comme stagnation`() {
        val sessions = listOf(
            session(1, "Squat gobelet", 16f),
            session(8, "Squat gobelet", 16f),
            session(20, "Squat gobelet", 16f),
        )

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = emptyList(),
            sessions = sessions,
            target = null,
            sessionsPlanned = 3,
        )

        assertTrue(report.exerciseDeltas.single().stagnating)
    }

    @Test
    fun `la cible de la semaine est reportee telle quelle`() {
        val target = PlanTarget(weekIndex = 4, targetDeltaKgMin = 1.2f, targetDeltaKgMax = 2.0f)

        val report = WeeklyReportBuilder.build(
            weekIndex = 4,
            weekEnd = weekEnd,
            weightEntries = emptyList(),
            sessions = emptyList(),
            target = target,
            sessionsPlanned = 3,
        )

        assertEquals(target, report.target)
        // Sans pesée, l'absence se propage plutôt que de devenir un zéro.
        assertNull(report.averageKg)
        assertNull(report.weeklyGainKg)
    }
}
