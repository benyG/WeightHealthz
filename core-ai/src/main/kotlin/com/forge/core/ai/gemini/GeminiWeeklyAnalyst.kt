package com.forge.core.ai.gemini

import android.util.Log
import com.forge.core.ai.analysis.WeeklyAnalyst
import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.model.WeeklyReport
import com.forge.domain.rule.CalorieAdjustment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
internal class GeminiWeeklyAnalyst @Inject constructor(
    private val api: GeminiApi,
    @GeminiJson private val json: Json,
) : WeeklyAnalyst {

    override suspend fun analyse(report: WeeklyReport): WeeklyAnalysis {
        val response = api.generateContent(
            model = GeminiApi.DEFAULT_MODEL,
            request = GeminiRequest(
                contents = listOf(GeminiContent(listOf(GeminiPart(WeeklyAnalysisPrompt.build(report))))),
                generationConfig = GenerationConfig(),
            ),
        )

        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
        require(!text.isNullOrBlank()) {
            "Réponse Gemini sans contenu exploitable (finishReason=${response.candidates.firstOrNull()?.finishReason})"
        }

        val parsed = json.decodeFromString(WeeklyAnalysisJson.serializer(), text)

        if (disagreesWithPlan(report, parsed.kcalAdjustment)) {
            Log.w(
                TAG,
                "Gemini propose ${parsed.kcalAdjustment} kcal, la règle du plan donne " +
                    "${report.ruleBasedAdjustmentKcal} kcal — c'est la règle qui s'applique.",
            )
        }

        return WeeklyAnalysis(
            weekIndex = report.weekIndex,
            summaryText = parsed.summary.trim(),
            focusExercise = parsed.focusExercise.trim(),
            audioScript = parsed.audioScript.trim(),
            // Phase 2 produit : le script existe, sa synthèse vocale viendra (SPEC.md §9).
            audioUrl = null,
            recommendedAdjustmentKcal = report.ruleBasedAdjustmentKcal,
        )
    }

    companion object {
        /**
         * L'ajustement enregistré est toujours celui des règles du plan ; celui du modèle est
         * borné puis comparé, uniquement pour détecter un désaccord. Un modèle qui propose autre
         * chose a inventé une règle, ce que SPEC.md §6.3 et `CLAUDE.md` interdisent — la règle
         * s'applique, le désaccord se constate.
         */
        internal fun disagreesWithPlan(report: WeeklyReport, modelProposalKcal: Int): Boolean =
            CalorieAdjustment.validated(modelProposalKcal) != report.ruleBasedAdjustmentKcal

        private const val TAG = "GeminiWeeklyAnalyst"
    }
}
