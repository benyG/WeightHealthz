package com.forge.domain.rule

import java.time.LocalDate
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class ProgramWeekTest {

    private val start: LocalDate = LocalDate.of(2026, 3, 2)

    @Test
    fun `le jour du demarrage est la semaine 1`() {
        assertEquals(1, ProgramWeek.indexFor(start, start))
        assertEquals(1, ProgramWeek.indexFor(start, start.plusDays(6)))
    }

    @Test
    fun `le huitieme jour ouvre la semaine 2`() {
        assertEquals(2, ProgramWeek.indexFor(start, start.plusDays(7)))
        assertEquals(4, ProgramWeek.indexFor(start, start.plusDays(21)))
    }

    @Test
    fun `une date anterieure au demarrage reste en semaine 1`() {
        // Peut arriver si l'horloge de l'appareil recule ; mieux vaut la semaine 1 qu'un index nul.
        assertEquals(1, ProgramWeek.indexFor(start, start.minusDays(3)))
    }

    @Test
    fun `la fin de semaine clot bien sept jours`() {
        assertEquals(start.plusDays(6), ProgramWeek.endOfWeek(start, 1))
        assertEquals(start.plusDays(27), ProgramWeek.endOfWeek(start, 4))
    }
}
