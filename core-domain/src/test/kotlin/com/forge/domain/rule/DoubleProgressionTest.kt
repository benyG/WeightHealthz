package com.forge.domain.rule

import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class DoubleProgressionTest {

    private val range = RepRange(min = 8, max = 12)

    private fun set(reps: Int, clean: Boolean = true) =
        SetLog(reps = reps, weightKg = 16f, cleanTechnique = clean)

    @Test
    fun `toutes les series au haut de la fourchette autorisent la montee`() {
        val sets = List(4) { set(reps = 12) }

        assertTrue(DoubleProgression.shouldIncreaseLoad(sets, range, prescribedSets = 4))
    }

    @Test
    fun `depasser le haut de la fourchette autorise aussi la montee`() {
        val sets = List(4) { set(reps = 14) }

        assertTrue(DoubleProgression.shouldIncreaseLoad(sets, range, prescribedSets = 4))
    }

    @Test
    fun `une seule serie sous le haut bloque la montee`() {
        val sets = listOf(set(12), set(12), set(12), set(11))

        assertFalse(DoubleProgression.shouldIncreaseLoad(sets, range, prescribedSets = 4))
    }

    @Test
    fun `une serie a la technique degradee bloque la montee`() {
        val sets = listOf(set(12), set(12), set(12), set(12, clean = false))

        assertFalse(DoubleProgression.shouldIncreaseLoad(sets, range, prescribedSets = 4))
    }

    @Test
    fun `une seance ecourtee ne monte pas la charge`() {
        val sets = List(3) { set(reps = 12) } // trois séries parfaites sur quatre prescrites

        assertFalse(DoubleProgression.shouldIncreaseLoad(sets, range, prescribedSets = 4))
    }

    @Test
    fun `aucune serie loguee ne monte pas la charge`() {
        assertFalse(DoubleProgression.shouldIncreaseLoad(emptyList(), range, prescribedSets = 4))
    }

    @Test
    fun `le prochain palier est le plus petit disponible au-dessus de la charge`() {
        val availableLoads = listOf(20f, 12f, 18f, 16f, 14f) // non triés, comme un râtelier

        assertEquals(18f, DoubleProgression.nextLoadKg(currentKg = 16f, availableLoadsKg = availableLoads))
    }

    @Test
    fun `sans palier superieur il n y a pas de suggestion`() {
        assertNull(DoubleProgression.nextLoadKg(currentKg = 20f, availableLoadsKg = listOf(12f, 16f, 20f)))
        assertNull(DoubleProgression.nextLoadKg(currentKg = 16f, availableLoadsKg = emptyList()))
    }
}
