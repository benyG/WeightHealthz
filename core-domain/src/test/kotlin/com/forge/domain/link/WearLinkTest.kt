package com.forge.domain.link

import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

class WearLinkTest {

    private val entry = WeightEntry(LocalDate.of(2026, 3, 15), 82.4f, WeightSource.MANUAL)

    @Test
    fun `une pesee fait l aller-retour entre les deux appareils`() {
        assertEquals(entry, WearLink.decodeWeight(WearLink.encodeWeight(entry)))
    }

    @Test
    fun `un message illisible ne fait pas tomber le service qui l ecoute`() {
        assertNull(WearLink.decodeWeight(""))
        assertNull(WearLink.decodeWeight("n'importe quoi"))
        assertNull(WearLink.decodeWeight("1;20000"))
        assertNull(WearLink.decodeWeight("1;pas-un-jour;82.4"))
        assertNull(WearLink.decodeWeight("1;20000;pas-un-poids"))
    }

    @Test
    fun `un message d une autre version est ignore`() {
        // Une montre restée sur une ancienne version ne doit pas écrire des données mal comprises.
        val futureVersion = WearLink.encodeWeight(entry).replaceFirst("1;", "2;")

        assertNull(WearLink.decodeWeight(futureVersion))
    }

    @Test
    fun `une pesee venue de la montre reste une saisie manuelle`() {
        val decoded = WearLink.decodeWeight(WearLink.encodeWeight(entry))!!

        assertEquals(WeightSource.MANUAL, decoded.source)
    }
}
