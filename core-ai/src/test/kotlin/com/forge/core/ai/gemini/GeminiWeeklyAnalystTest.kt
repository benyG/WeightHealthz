package com.forge.core.ai.gemini

import com.forge.domain.model.WeeklyReport
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiWeeklyAnalystTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun report(adjustment: Int) = WeeklyReport(
        weekIndex = 4,
        weekEnd = LocalDate.of(2026, 3, 15),
        averageKg = 82.4,
        weeklyGainKg = 0.1,
        target = null,
        eightWeekAverages = emptyList(),
        sessionsDone = 3,
        sessionsPlanned = 4,
        ruleBasedAdjustmentKcal = adjustment,
        exerciseDeltas = emptyList(),
    )

    private fun apiReturning(responseJson: String) = object : GeminiApi {
        override suspend fun generateContent(model: String, request: GeminiRequest): GeminiResponse =
            GeminiResponse(listOf(GeminiCandidate(GeminiContent(listOf(GeminiPart(responseJson))))))
    }

    private fun modelAnswer(kcal: Int) = """
        {
          "summary": "Gain trop lent, deux semaines de suite.",
          "kcal_adjustment": $kcal,
          "focus_exercise": "Squat gobelet",
          "audio_script": "Ton gain ralentit. On ajoute des calories."
        }
    """.trimIndent()

    @Test
    fun `l analyse reprend le texte du modele`() = runTest {
        val analyst = GeminiWeeklyAnalyst(apiReturning(modelAnswer(250)), json)

        val analysis = analyst.analyse(report(adjustment = 250))

        assertEquals(4, analysis.weekIndex)
        assertEquals("Gain trop lent, deux semaines de suite.", analysis.summaryText)
        assertEquals("Squat gobelet", analysis.focusExercise)
        assertTrue(analysis.audioScript.startsWith("Ton gain ralentit"))
    }

    @Test
    fun `un chiffre invente par le modele ne s applique pas`() = runTest {
        // Le modèle propose 150 kcal, valeur qui n'existe dans aucune règle du plan.
        val analyst = GeminiWeeklyAnalyst(apiReturning(modelAnswer(150)), json)

        val analysis = analyst.analyse(report(adjustment = 250))

        assertEquals(250, analysis.recommendedAdjustmentKcal)
    }

    @Test
    fun `un chiffre hors bornes ne s applique pas davantage`() = runTest {
        val analyst = GeminiWeeklyAnalyst(apiReturning(modelAnswer(5000)), json)

        val analysis = analyst.analyse(report(adjustment = 0))

        assertEquals(0, analysis.recommendedAdjustmentKcal)
    }

    @Test
    fun `le desaccord avec le plan se constate`() {
        val report = report(adjustment = 250)

        assertFalse(GeminiWeeklyAnalyst.disagreesWithPlan(report, 250))
        assertTrue(GeminiWeeklyAnalyst.disagreesWithPlan(report, 150))
        // 900 est d'abord borné à 300, ce qui ne le rend pas conforme pour autant.
        assertTrue(GeminiWeeklyAnalyst.disagreesWithPlan(report, 900))
    }

    @Test
    fun `le script audio existe sans fichier audio en MVP`() = runTest {
        val analyst = GeminiWeeklyAnalyst(apiReturning(modelAnswer(0)), json)

        val analysis = analyst.analyse(report(adjustment = 0))

        assertTrue(analysis.audioScript.isNotBlank())
        assertNull(analysis.audioUrl)
    }

    @Test
    fun `une reponse vide echoue explicitement`() = runTest {
        val emptyApi = object : GeminiApi {
            override suspend fun generateContent(model: String, request: GeminiRequest) = GeminiResponse()
        }
        val analyst = GeminiWeeklyAnalyst(emptyApi, json)

        val error = runCatching { analyst.analyse(report(adjustment = 0)) }.exceptionOrNull()

        assertTrue(error is IllegalArgumentException)
        assertTrue(error!!.message!!.contains("sans contenu"))
    }
}
