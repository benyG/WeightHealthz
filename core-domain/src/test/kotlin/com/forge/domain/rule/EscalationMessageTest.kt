package com.forge.domain.rule

import com.forge.domain.model.EscalationLevel
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class EscalationMessageTest {

    @Test
    fun `etre a jour ne produit aucun message`() {
        assertNull(EscalationMessage.forLevel(EscalationLevel.A_JOUR, "Pesée"))
    }

    @Test
    fun `chaque niveau dit le fait et l action dans la meme phrase`() {
        val levels = listOf(EscalationLevel.RETARD_1, EscalationLevel.RETARD_2, EscalationLevel.CRITIQUE)

        levels.forEach { level ->
            val message = EscalationMessage.forLevel(level, "Pesée")!!

            assertTrue(message.contains("Pesée"), "Le message doit nommer ce qui manque : $message")
            // DESIGN.md §9 : le fait, puis l'action, dans la même phrase.
            assertTrue(
                message.contains("Rattrape") || message.contains("Fais-le") || message.contains("reprends"),
                "Le message doit dire quoi faire : $message",
            )
        }
    }

    @Test
    fun `le ton reste factuel`() {
        val levels = EscalationLevel.entries.mapNotNull { EscalationMessage.forLevel(it, "Séance") }

        levels.forEach { message ->
            // DESIGN.md §9 : ni exclamation, ni excuse, ni ton enjoué.
            assertTrue(!message.contains("!"), "Aucune exclamation : $message")
            assertTrue(!message.contains("Oups"), "Aucune excuse : $message")
        }
    }

    @Test
    fun `l urgence monte avec le niveau`() {
        val retard1 = EscalationMessage.forLevel(EscalationLevel.RETARD_1, "Séance")!!
        val critique = EscalationMessage.forLevel(EscalationLevel.CRITIQUE, "Séance")!!

        assertTrue(retard1.contains("1 jour"))
        assertTrue(critique.contains("3 jours"))
        assertTrue(critique.contains("décroche"))
    }
}
