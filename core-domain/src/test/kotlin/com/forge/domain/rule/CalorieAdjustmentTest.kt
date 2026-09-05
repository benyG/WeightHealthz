package com.forge.domain.rule

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class CalorieAdjustmentTest {

    @Test
    fun `deux semaines sous le seuil bas ajoutent 250 kcal`() {
        assertEquals(250, CalorieAdjustment.forRecentWeeklyGains(listOf(0.1, 0.15)))
    }

    @Test
    fun `deux semaines au-dessus du seuil haut retirent 200 kcal`() {
        assertEquals(-200, CalorieAdjustment.forRecentWeeklyGains(listOf(0.8, 0.9)))
    }

    @Test
    fun `une seule semaine hors cible ne declenche rien`() {
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.4, 0.1)))
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.4, 0.9)))
    }

    @Test
    fun `deux semaines contradictoires ne declenchent rien`() {
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.1, 0.9)))
    }

    @Test
    fun `un gain dans la cible ne declenche rien`() {
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.35, 0.45)))
    }

    @Test
    fun `les seuils sont stricts`() {
        // Exactement 0,2 n'est pas "sous 0,2" ; exactement 0,7 n'est pas "au-dessus de 0,7".
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.2, 0.2)))
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.7, 0.7)))
    }

    @Test
    fun `un historique trop court ne declenche rien`() {
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(emptyList()))
        assertEquals(0, CalorieAdjustment.forRecentWeeklyGains(listOf(0.05)))
    }

    @Test
    fun `seules les deux dernieres semaines comptent`() {
        // Deux semaines rapides, puis deux semaines lentes : c'est le présent qui décide.
        assertEquals(250, CalorieAdjustment.forRecentWeeklyGains(listOf(0.9, 0.9, 0.1, 0.1)))
    }

    @Test
    fun `une proposition externe est bornee a plus ou moins 300 kcal`() {
        assertEquals(300, CalorieAdjustment.validated(900))
        assertEquals(-300, CalorieAdjustment.validated(-900))
    }

    @Test
    fun `une proposition externe dans les bornes passe telle quelle`() {
        assertEquals(250, CalorieAdjustment.validated(250))
        assertEquals(-200, CalorieAdjustment.validated(-200))
        assertEquals(0, CalorieAdjustment.validated(0))
    }
}
