package com.forge.domain.model

/**
 * Résultat de l'analyse hebdomadaire (SPEC.md §5.6).
 *
 * `audioUrl` reste `null` sur tout le MVP : le texte est produit par Gemini en phase 3, la
 * vocalisation Deepgram est explicitement en phase 2 produit (SPEC.md §9). `audioScript` porte
 * en attendant le texte destiné à l'oral, que Gemini rédige déjà séparément du résumé écrit
 * (SPEC.md §6.1) — il attend sa synthèse vocale sans faire vivre de code mort.
 *
 * `recommendedAdjustmentKcal` est celui des règles du plan, pas celui inventé par le modèle
 * (SPEC.md §6.3).
 */
data class WeeklyAnalysis(
    val weekIndex: Int,
    val summaryText: String,
    val focusExercise: String,
    val audioScript: String,
    val audioUrl: String?,
    val recommendedAdjustmentKcal: Int,
)
