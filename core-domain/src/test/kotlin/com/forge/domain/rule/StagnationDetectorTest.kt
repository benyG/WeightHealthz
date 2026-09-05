package com.forge.domain.rule

import com.forge.domain.rule.StagnationDetector.LoadPoint
import java.time.LocalDate
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class StagnationDetectorTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun point(daysAgo: Long, kg: Float) = LoadPoint(today.minusDays(daysAgo), kg)

    @Test
    fun `une charge inchangee depuis deux semaines est une stagnation`() {
        val history = listOf(
            point(21, 16f),
            point(17, 16f),
            point(10, 16f),
            point(3, 16f),
        )

        assertTrue(StagnationDetector.isStagnating(history, today))
    }

    @Test
    fun `une charge montee dans les deux dernieres semaines n est pas une stagnation`() {
        val history = listOf(
            point(21, 16f),
            point(17, 16f),
            point(10, 18f),
            point(3, 18f),
        )

        assertFalse(StagnationDetector.isStagnating(history, today))
    }

    @Test
    fun `une regression compte comme une stagnation`() {
        // Redescendre n'est pas progresser : l'analyse hebdo doit le signaler aussi.
        val history = listOf(
            point(21, 20f),
            point(10, 18f),
            point(3, 18f),
        )

        assertTrue(StagnationDetector.isStagnating(history, today))
    }

    @Test
    fun `sans historique anterieur on ne conclut pas a la stagnation`() {
        // Un exercice commencé il y a huit jours n'a pas encore eu l'occasion de progresser.
        val history = listOf(point(8, 16f), point(1, 16f))

        assertFalse(StagnationDetector.isStagnating(history, today))
    }

    @Test
    fun `sans seance recente on ne conclut pas a la stagnation`() {
        // Exercice non pratiqué depuis un mois : c'est un abandon à traiter ailleurs, pas une
        // stagnation de charge.
        val history = listOf(point(40, 16f), point(30, 16f))

        assertFalse(StagnationDetector.isStagnating(history, today))
    }
}
