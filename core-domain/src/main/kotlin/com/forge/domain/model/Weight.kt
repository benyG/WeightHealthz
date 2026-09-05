package com.forge.domain.model

import java.time.LocalDate

/**
 * Une pesée. `kg` est un `Float` conformément au modèle de SPEC.md §4 ; les calculs de
 * tendance convertissent en `Double` pour éviter les comparaisons de seuils en précision
 * simple (voir `WeightTrend`).
 */
data class WeightEntry(
    val date: LocalDate,
    val kg: Float,
    val source: WeightSource,
)

enum class WeightSource {
    /** Saisie à la main, depuis le téléphone ou la Tile de la montre. */
    MANUAL,

    /** Remontée par Health Connect (balance connectée). */
    HEALTH_CONNECT,
}

/**
 * Fourchette de prise de poids visée pour une semaine du programme, exprimée en écart
 * **cumulé** depuis le poids de départ — c'est ce que compare l'écran d'accueil
 * (DESIGN.md §7.1 : "+1.8 kg / objectif : +1.2 à +2.0 kg").
 */
data class PlanTarget(
    val weekIndex: Int,
    val targetDeltaKgMin: Float,
    val targetDeltaKgMax: Float,
) {
    fun contains(deltaKg: Float): Boolean = deltaKg in targetDeltaKgMin..targetDeltaKgMax
}
