package com.forge.core.ai.gemini

import com.forge.domain.model.ExerciseDelta
import com.forge.domain.model.PlanTarget
import com.forge.domain.model.SetSummary
import com.forge.domain.model.WeeklyAveragePoint
import com.forge.domain.model.WeeklyReport
import java.time.LocalDate
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeeklyAnalysisPromptTest {

    private val weekEnd: LocalDate = LocalDate.of(2026, 3, 15)

    private fun report(
        averageKg: Double? = 82.4,
        adjustment: Int = 0,
        deltas: List<ExerciseDelta> = emptyList(),
    ) = WeeklyReport(
        weekIndex = 4,
        weekEnd = weekEnd,
        averageKg = averageKg,
        weeklyGainKg = if (averageKg == null) null else 0.4,
        target = PlanTarget(4, 1.2f, 2.0f),
        eightWeekAverages = if (averageKg == null) {
            emptyList()
        } else {
            listOf(WeeklyAveragePoint(weekEnd.minusWeeks(1), 82.0), WeeklyAveragePoint(weekEnd, averageKg))
        },
        sessionsDone = 3,
        sessionsPlanned = 4,
        ruleBasedAdjustmentKcal = adjustment,
        exerciseDeltas = deltas,
    )

    @Test
    fun `le prompt impose le chiffre calcule par l app`() {
        val prompt = WeeklyAnalysisPrompt.build(report(adjustment = 250))

        assertTrue(prompt.contains("obtient 250 kcal"))
        assertTrue(prompt.contains("Reprends exactement ce chiffre"))
    }

    @Test
    fun `le prompt rappelle les valeurs non negociables du plan`() {
        val prompt = WeeklyAnalysisPrompt.build(report())

        assertTrue(prompt.contains("+250 kcal"))
        assertTrue(prompt.contains("-200 kcal"))
        assertTrue(prompt.contains("0.2 kg/semaine"))
        assertTrue(prompt.contains("0.7 kg/semaine"))
    }

    @Test
    fun `une donnee absente se dit, elle ne devient pas zero`() {
        val prompt = WeeklyAnalysisPrompt.build(report(averageKg = null))

        assertTrue(prompt.contains("non disponible"))
        assertFalse(prompt.contains("0.0 kg"))
    }

    @Test
    fun `le prompt signale la stagnation d un exercice`() {
        val prompt = WeeklyAnalysisPrompt.build(
            report(
                deltas = listOf(
                    ExerciseDelta(
                        name = "Squat gobelet",
                        thisWeek = SetSummary(16f, 12),
                        lastWeek = SetSummary(16f, 12),
                        stagnating = true,
                    ),
                ),
            ),
        )

        assertTrue(prompt.contains("Squat gobelet"))
        assertTrue(prompt.contains("stagnation"))
    }

    @Test
    fun `le prompt ne transporte que des agregats`() {
        val prompt = WeeklyAnalysisPrompt.build(report())

        // Confidentialité (CLAUDE.md) : des moyennes et des libellés, jamais des pesées datées.
        assertFalse(prompt.contains("2026-03"))
        assertTrue(prompt.contains("Historique 8 semaines"))
    }

    @Test
    fun `le prompt annonce le contrat de sortie`() {
        val prompt = WeeklyAnalysisPrompt.build(report())

        listOf("summary", "kcal_adjustment", "focus_exercise", "audio_script").forEach {
            assertTrue("Le champ $it doit être demandé", prompt.contains(it))
        }
    }
}
