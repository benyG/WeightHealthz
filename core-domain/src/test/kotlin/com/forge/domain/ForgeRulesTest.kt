package com.forge.domain

import kotlin.math.absoluteValue
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

/**
 * Verrouille les valeurs non-négociables de CLAUDE.md. Ce test n'existe pas pour vérifier que
 * Kotlin sait lire une constante, mais pour qu'un "arrondi" involontaire (250 → 200, 0,2 → 0,25)
 * casse le build au lieu de passer inaperçu dans un refactor.
 */
class ForgeRulesTest {

    @Test
    fun `les valeurs du plan sont celles de CLAUDE md`() {
        assertEquals(7, ForgeRules.MOVING_AVERAGE_DAYS)
        assertEquals(0.3, ForgeRules.WEEKLY_GAIN_TARGET_MIN_KG)
        assertEquals(0.5, ForgeRules.WEEKLY_GAIN_TARGET_MAX_KG)
        assertEquals(0.2, ForgeRules.WEEKLY_GAIN_LOW_THRESHOLD_KG)
        assertEquals(0.7, ForgeRules.WEEKLY_GAIN_HIGH_THRESHOLD_KG)
        assertEquals(2, ForgeRules.CONSECUTIVE_WEEKS_BEFORE_ADJUSTMENT)
        assertEquals(250, ForgeRules.KCAL_ADJUSTMENT_UP)
        assertEquals(-200, ForgeRules.KCAL_ADJUSTMENT_DOWN)
        assertEquals(300, ForgeRules.KCAL_ADJUSTMENT_BOUND)
    }

    @Test
    fun `la fourchette cible est strictement encadree par les seuils d ajustement`() {
        assertTrue(
            ForgeRules.WEEKLY_GAIN_LOW_THRESHOLD_KG < ForgeRules.WEEKLY_GAIN_TARGET_MIN_KG,
            "Le seuil bas doit rester sous la cible, sinon être dans la cible déclencherait un ajustement",
        )
        assertTrue(
            ForgeRules.WEEKLY_GAIN_TARGET_MIN_KG < ForgeRules.WEEKLY_GAIN_TARGET_MAX_KG,
            "La fourchette cible doit être un intervalle non vide",
        )
        assertTrue(
            ForgeRules.WEEKLY_GAIN_TARGET_MAX_KG < ForgeRules.WEEKLY_GAIN_HIGH_THRESHOLD_KG,
            "Le seuil haut doit rester au-dessus de la cible, même raison",
        )
    }

    @Test
    fun `les ajustements caloriques tiennent dans la borne de validation`() {
        assertTrue(ForgeRules.KCAL_ADJUSTMENT_UP.absoluteValue <= ForgeRules.KCAL_ADJUSTMENT_BOUND)
        assertTrue(ForgeRules.KCAL_ADJUSTMENT_DOWN.absoluteValue <= ForgeRules.KCAL_ADJUSTMENT_BOUND)
    }

    @Test
    fun `l ajustement corrige le sens de l ecart`() {
        assertTrue(ForgeRules.KCAL_ADJUSTMENT_UP > 0, "Un gain trop faible doit ajouter des calories")
        assertTrue(ForgeRules.KCAL_ADJUSTMENT_DOWN < 0, "Un gain trop rapide doit en retirer")
    }

    @Test
    fun `les paliers d escalade sont des jours consecutifs croissants`() {
        assertEquals(1, ForgeRules.ESCALATION_DAYS_LEVEL_1)
        assertEquals(ForgeRules.ESCALATION_DAYS_LEVEL_1 + 1, ForgeRules.ESCALATION_DAYS_LEVEL_2)
        assertEquals(ForgeRules.ESCALATION_DAYS_LEVEL_2 + 1, ForgeRules.ESCALATION_DAYS_CRITICAL)
    }
}
