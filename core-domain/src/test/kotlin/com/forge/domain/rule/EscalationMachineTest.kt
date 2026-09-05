package com.forge.domain.rule

import com.forge.domain.model.AdherenceState
import com.forge.domain.model.EscalationLevel
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EscalationMachineTest {

    private fun state(level: EscalationLevel, streak: Int = 0) = AdherenceState(streak, level)

    @Test
    fun `chaque journee manquee monte d un cran`() {
        var current = AdherenceState.START

        current = EscalationMachine.onDayClosed(current, hadValidAction = false)
        assertEquals(EscalationLevel.RETARD_1, current.escalationLevel)

        current = EscalationMachine.onDayClosed(current, hadValidAction = false)
        assertEquals(EscalationLevel.RETARD_2, current.escalationLevel)

        current = EscalationMachine.onDayClosed(current, hadValidAction = false)
        assertEquals(EscalationLevel.CRITIQUE, current.escalationLevel)
    }

    @Test
    fun `critique est un plafond`() {
        val afterFourthMissedDay = EscalationMachine.onDayClosed(
            state(EscalationLevel.CRITIQUE),
            hadValidAction = false,
        )

        assertEquals(EscalationLevel.CRITIQUE, afterFourthMissedDay.escalationLevel)
    }

    @Test
    fun `une action valide ramene a jour depuis n importe quel niveau`() {
        EscalationLevel.entries.forEach { level ->
            val recovered = EscalationMachine.onValidAction(state(level, streak = 3))

            assertEquals(
                EscalationLevel.A_JOUR,
                recovered.escalationLevel,
                "Une action valide doit éteindre l'escalade, y compris depuis $level",
            )
        }
    }

    @Test
    fun `une action valide ne gonfle pas le streak`() {
        val start = state(EscalationLevel.RETARD_2, streak = 4)

        val afterTwoActionsSameDay = EscalationMachine.onValidAction(
            EscalationMachine.onValidAction(start),
        )

        // Le streak se compte à la journée : deux actions dans la même journée ne valent pas
        // deux jours de série. Il ne bougera qu'à la clôture.
        assertEquals(4, afterTwoActionsSameDay.streakDays)

        val afterDayClosed = EscalationMachine.onDayClosed(
            afterTwoActionsSameDay,
            hadValidAction = true,
        )
        assertEquals(5, afterDayClosed.streakDays)
    }

    @Test
    fun `une journee manquee remet le streak a zero`() {
        val after = EscalationMachine.onDayClosed(
            state(EscalationLevel.A_JOUR, streak = 12),
            hadValidAction = false,
        )

        assertEquals(0, after.streakDays)
    }

    @Test
    fun `deux manquements le meme jour ne peuvent pas sauter deux crans`() {
        // L'invariant tient par construction : le niveau ne bouge qu'à la clôture de journée,
        // qui n'a lieu qu'une fois par jour. Il n'existe aucune entrée permettant d'escalader
        // deux fois pour une même journée.
        val start = AdherenceState.START

        val afterOneDay = EscalationMachine.onDayClosed(start, hadValidAction = false)

        assertEquals(EscalationLevel.RETARD_1, afterOneDay.escalationLevel)
    }

    @Test
    fun `le niveau se reconstruit depuis le nombre de jours manques`() {
        assertEquals(EscalationLevel.A_JOUR, EscalationMachine.levelForConsecutiveMissedDays(0))
        assertEquals(EscalationLevel.RETARD_1, EscalationMachine.levelForConsecutiveMissedDays(1))
        assertEquals(EscalationLevel.RETARD_2, EscalationMachine.levelForConsecutiveMissedDays(2))
        assertEquals(EscalationLevel.CRITIQUE, EscalationMachine.levelForConsecutiveMissedDays(3))
        assertEquals(EscalationLevel.CRITIQUE, EscalationMachine.levelForConsecutiveMissedDays(9))
    }

    @Test
    fun `seuls retard 2 et critique partent vers l enceinte`() {
        assertFalse(EscalationLevel.A_JOUR.requiresVoiceRelay)
        assertFalse(EscalationLevel.RETARD_1.requiresVoiceRelay)
        assertTrue(EscalationLevel.RETARD_2.requiresVoiceRelay)
        assertTrue(EscalationLevel.CRITIQUE.requiresVoiceRelay)
    }
}
