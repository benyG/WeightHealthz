package com.forge.domain.rule

import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class ProgramProgressTest {

    private val today: LocalDate = LocalDate.of(2026, 3, 15)

    private fun entry(daysAgo: Long, kg: Float) =
        WeightEntry(today.minusDays(daysAgo), kg, WeightSource.MANUAL)

    @Test
    fun `l ecart se compte depuis la premiere pesee`() {
        val entries = listOf(entry(28, 80f)) + (0L..6L).map { entry(it, 82f) }

        assertEquals(2.0, ProgramProgress.cumulativeDeltaKg(entries, today)!!, 1e-9)
    }

    @Test
    fun `le poids courant est la moyenne, pas la pesee du jour`() {
        // Pesée du jour à 84, mais la semaine tourne autour de 82.
        val entries = listOf(entry(28, 80f), entry(0, 84f)) + (1L..6L).map { entry(it, 82f) }

        val delta = ProgramProgress.cumulativeDeltaKg(entries, today)!!

        // Moyenne = (84 + 82×6) / 7 ≈ 82.29, donc un écart proche de 2,3 et non de 4.
        assertEquals(2.2857142857, delta, 1e-6)
    }

    @Test
    fun `sans pesee il n y a pas d ecart a afficher`() {
        assertNull(ProgramProgress.cumulativeDeltaKg(emptyList(), today))
        // Une pesée de départ seule, hors fenêtre : pas de moyenne courante, donc pas d'écart.
        assertNull(ProgramProgress.cumulativeDeltaKg(listOf(entry(30, 80f)), today))
    }
}
