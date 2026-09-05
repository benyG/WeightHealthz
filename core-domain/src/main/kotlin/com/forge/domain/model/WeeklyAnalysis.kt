package com.forge.domain.model

/**
 * Résultat de l'analyse hebdomadaire (SPEC.md §5.6).
 *
 * `audioUrl` reste `null` sur tout le MVP : le texte est produit par Gemini en phase 3, la
 * vocalisation Deepgram est explicitement en phase 2 produit (SPEC.md §9).
 *
 * `recommendedAdjustmentKcal` a déjà traversé `CalorieAdjustment.validated` : aucune valeur
 * hors des bornes du plan ne doit atteindre ce type (SPEC.md §6.3).
 */
data class WeeklyAnalysis(
    val weekIndex: Int,
    val summaryText: String,
    val audioUrl: String?,
    val recommendedAdjustmentKcal: Int,
)
