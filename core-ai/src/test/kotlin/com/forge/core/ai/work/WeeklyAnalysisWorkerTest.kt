package com.forge.core.ai.work

import androidx.work.NetworkType
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Garde les contraintes du job hebdomadaire, qui portent deux exigences non fonctionnelles de
 * SPEC.md §8 : le rejeu différé et la sobriété.
 */
class WeeklyAnalysisWorkerTest {

    @Test
    fun `le job attend le reseau au lieu d echouer sans lui`() {
        val constraints = WeeklyAnalysisWorker.request().workSpec.constraints

        // Offline-first : le dimanche en mode avion, l'analyse est différée et rejouée à la
        // reconnexion, pas perdue.
        assertEquals(NetworkType.CONNECTED, constraints.requiredNetworkType)
    }

    @Test
    fun `le job tourne une fois par semaine, pas en continu`() {
        val spec = WeeklyAnalysisWorker.request().workSpec

        assertEquals(TimeUnit.DAYS.toMillis(7), spec.intervalDuration)
    }

    @Test
    fun `le job n exige ni charge ni inactivite`() {
        val constraints = WeeklyAnalysisWorker.request().workSpec.constraints

        // Un appel réseau par semaine ne justifie pas d'attendre le chargeur : ce serait
        // repousser l'analyse de plusieurs jours chez quelqu'un qui charge la nuit seulement.
        assertFalse(constraints.requiresCharging())
        assertFalse(constraints.requiresDeviceIdle())
    }
}
