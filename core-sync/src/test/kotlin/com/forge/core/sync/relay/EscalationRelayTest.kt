package com.forge.core.sync.relay

import com.forge.domain.model.AdherenceState
import com.forge.domain.model.EscalationLevel
import com.forge.domain.rule.EscalationMessage
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EscalationRelayTest {

    private class RecordingRelay : VoiceRelay {
        val announced = mutableListOf<String>()
        override suspend fun announce(message: String): VoiceRelay.RelayResult {
            announced += message
            return VoiceRelay.RelayResult.Delivered
        }
    }

    private fun state(level: EscalationLevel) = AdherenceState(streakDays = 0, escalationLevel = level)

    @Test
    fun `etre a jour ne fait rien parler`() = runTest {
        val relay = RecordingRelay()

        val result = EscalationRelay(relay).relayIfNeeded(state(EscalationLevel.A_JOUR), "Pesée")

        assertNull(result)
        assertTrue(relay.announced.isEmpty())
    }

    @Test
    fun `un premier retard reste sur le telephone`() = runTest {
        val relay = RecordingRelay()

        // CLAUDE.md : seuls RETARD_2 et CRITIQUE partent vers l'enceinte.
        val result = EscalationRelay(relay).relayIfNeeded(state(EscalationLevel.RETARD_1), "Pesée")

        assertNull(result)
        assertTrue(relay.announced.isEmpty())
    }

    @Test
    fun `retard 2 et critique partent vers l enceinte`() = runTest {
        listOf(EscalationLevel.RETARD_2, EscalationLevel.CRITIQUE).forEach { level ->
            val relay = RecordingRelay()

            val result = EscalationRelay(relay).relayIfNeeded(state(level), "Séance")

            assertEquals(VoiceRelay.RelayResult.Delivered, result)
            assertEquals(1, relay.announced.size)
        }
    }

    @Test
    fun `le message vocal est exactement celui du domaine`() = runTest {
        val relay = RecordingRelay()

        EscalationRelay(relay).relayIfNeeded(state(EscalationLevel.CRITIQUE), "Pesée")

        // DESIGN.md §9 : le même vocabulaire sur tous les canaux. Le vérifier ici empêche la
        // formulation de diverger entre la notification Android et l'enceinte.
        assertEquals(
            EscalationMessage.forLevel(EscalationLevel.CRITIQUE, "Pesée"),
            relay.announced.single(),
        )
    }

    @Test
    fun `un rappel programme part toujours`() = runTest {
        val relay = RecordingRelay()

        val result = EscalationRelay(relay).relayScheduledReminder("Séance haut du corps dans 30 minutes.")

        assertEquals(VoiceRelay.RelayResult.Delivered, result)
        assertEquals(1, relay.announced.size)
    }

    @Test
    fun `un relais non configure ne fait pas echouer l escalade`() = runTest {
        val silent = object : VoiceRelay {
            override suspend fun announce(message: String) = VoiceRelay.RelayResult.NotConfigured
        }

        val result = EscalationRelay(silent).relayIfNeeded(state(EscalationLevel.CRITIQUE), "Pesée")

        // L'app doit rester utilisable sans relais : ce n'est pas une panne, c'est un canal en moins.
        assertEquals(VoiceRelay.RelayResult.NotConfigured, result)
    }
}
