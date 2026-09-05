package com.forge.core.ai.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Corps d'échange avec `generateContent`. Types internes : le reste de l'app ne voit que des
 * modèles de domaine, jamais la forme d'une réponse Gemini.
 */
@Serializable
internal data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GenerationConfig,
)

@Serializable
internal data class GeminiContent(val parts: List<GeminiPart>)

@Serializable
internal data class GeminiPart(val text: String = "")

/**
 * `responseMimeType` + `responseSchema` : c'est le mécanisme de sortie structurée de Gemini,
 * celui qui fait respecter le contrat JSON de SPEC.md §6.1. SPEC.md §3 évoque le "function
 * calling", conçu pour faire appeler des fonctions de l'app — ce que ce cas d'usage ne demande
 * pas : on veut un JSON conforme, pas un outil invoqué. Le schéma ci-dessous est la traduction
 * littérale du contrat de §6.1.
 */
@Serializable
internal data class GenerationConfig(
    val responseMimeType: String = "application/json",
    val responseSchema: ResponseSchema = ResponseSchema.WEEKLY_ANALYSIS,
    val temperature: Double = 0.4,
)

@Serializable
internal data class ResponseSchema(
    val type: String,
    val properties: Map<String, SchemaProperty>,
    val required: List<String>,
) {
    companion object {
        val WEEKLY_ANALYSIS = ResponseSchema(
            type = "OBJECT",
            properties = mapOf(
                "summary" to SchemaProperty("STRING"),
                "kcal_adjustment" to SchemaProperty("INTEGER"),
                "focus_exercise" to SchemaProperty("STRING"),
                "audio_script" to SchemaProperty("STRING"),
            ),
            required = listOf("summary", "kcal_adjustment", "focus_exercise", "audio_script"),
        )
    }
}

@Serializable
internal data class SchemaProperty(val type: String)

@Serializable
internal data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
)

@Serializable
internal data class GeminiCandidate(
    val content: GeminiContent? = null,
    val finishReason: String? = null,
)

/** Contrat de sortie de SPEC.md §6.1, tel que le modèle doit le renvoyer. */
@Serializable
internal data class WeeklyAnalysisJson(
    val summary: String,
    @SerialName("kcal_adjustment") val kcalAdjustment: Int,
    @SerialName("focus_exercise") val focusExercise: String,
    @SerialName("audio_script") val audioScript: String,
)
