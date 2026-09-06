package com.forge.work

import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class ForgeSchedulerTest {

    @Test
    fun `un rappel a venir aujourd hui se declenche aujourd hui`() {
        val now = LocalDateTime.of(2026, 3, 15, 6, 0)

        val delay = ForgeScheduler.delayUntil(LocalTime.of(7, 0), now)

        assertEquals(60, delay.toMinutes())
    }

    @Test
    fun `un rappel deja passe attend le lendemain`() {
        val now = LocalDateTime.of(2026, 3, 15, 9, 0)

        val delay = ForgeScheduler.delayUntil(LocalTime.of(7, 0), now)

        assertEquals(22 * 60L, delay.toMinutes())
    }

    @Test
    fun `l heure exacte compte pour le lendemain`() {
        // Sinon un rappel programmé à la seconde près se déclencherait immédiatement au démarrage.
        val now = LocalDateTime.of(2026, 3, 15, 7, 0)

        val delay = ForgeScheduler.delayUntil(LocalTime.of(7, 0), now)

        assertEquals(24 * 60L, delay.toMinutes())
    }
}
