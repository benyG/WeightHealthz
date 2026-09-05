package com.forge.domain.rule

import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class WeightTrendTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun entry(daysAgo: Long, kg: Float) =
        WeightEntry(today.minusDays(daysAgo), kg, WeightSource.MANUAL)

    @Test
    fun `moyenne sur sept jours pleins`() {
        val entries = (0L..6L).map { entry(it, 80f + it) } // 80 à 86

        val average = WeightTrend.movingAverageKg(entries, today)

        assertEquals(83.0, average!!, 1e-9)
    }

    @Test
    fun `une serie lacunaire ne compte que les jours presents`() {
        val entries = listOf(entry(0, 82f), entry(3, 80f), entry(6, 84f))

        val average = WeightTrend.movingAverageKg(entries, today)

        assertEquals(82.0, average!!, 1e-9)
    }

    @Test
    fun `les pesees hors fenetre sont ignorees`() {
        val entries = listOf(
            entry(0, 82f),
            entry(7, 60f), // hors fenêtre de 7 jours : tirerait la moyenne si elle était comptée
        )

        val average = WeightTrend.movingAverageKg(entries, today)

        assertEquals(82.0, average!!, 1e-9)
    }

    @Test
    fun `plusieurs pesees le meme jour ne pesent que pour un jour`() {
        val entries = listOf(
            entry(0, 80f),
            entry(0, 82f), // deux lectures de balance le même matin
            entry(1, 90f),
        )

        val average = WeightTrend.movingAverageKg(entries, today)

        // Moyenne des moyennes journalières : (81 + 90) / 2. Une moyenne brute des trois
        // pesées donnerait 84 et laisserait la journée bavarde tirer la tendance.
        assertEquals(85.5, average!!, 1e-9)
    }

    @Test
    fun `une pesee aberrante ne fait pas basculer la tendance`() {
        // Six jours autour de 82 kg et un matin à 79,5 (mauvaise nuit, balance mal posée).
        val entries = listOf(
            entry(0, 79.5f),
            entry(1, 82f), entry(2, 82f), entry(3, 82f),
            entry(4, 82f), entry(5, 82f), entry(6, 82f),
        )

        val average = WeightTrend.movingAverageKg(entries, today)!!

        // La moyenne encaisse le creux (moins de 0,4 kg d'écart) là où lire la pesée du jour
        // ferait conclure à une perte de 2,5 kg. C'est tout l'intérêt de la fenêtre 7 jours.
        assertTrue(
            average > 81.6,
            "La moyenne mobile ne doit pas suivre une pesée isolée (obtenu : $average)",
        )
    }

    @Test
    fun `sans pesee dans la fenetre la moyenne est absente`() {
        assertNull(WeightTrend.movingAverageKg(entries = emptyList(), on = today))
        assertNull(WeightTrend.movingAverageKg(listOf(entry(30, 80f)), today))
    }

    @Test
    fun `le gain hebdomadaire compare les deux moyennes mobiles`() {
        val previousWeek = (7L..13L).map { entry(it, 80f) }
        // 80,5 est exactement représentable en Float : le test mesure la règle, pas l'arrondi.
        val currentWeek = (0L..6L).map { entry(it, 80.5f) }

        val gain = WeightTrend.weeklyGainKg(previousWeek + currentWeek, today)

        assertEquals(0.5, gain!!, 1e-9)
    }

    @Test
    fun `le gain est absent tant que la semaine precedente est vide`() {
        val currentWeekOnly = (0L..6L).map { entry(it, 80f) }

        assertNull(WeightTrend.weeklyGainKg(currentWeekOnly, today))
    }
}
