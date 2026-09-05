package com.forge.domain.rule

import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog

/**
 * Double progression (CLAUDE.md) : on ne monte la charge que quand **toutes** les séries
 * prescrites atteignent le haut de la fourchette de reps, technique propre.
 *
 * Le "toutes" est la règle : trois séries réussies sur quatre ne donnent pas trois quarts d'une
 * augmentation, elles ne donnent rien.
 */
object DoubleProgression {

    /**
     * @param sets séries effectivement loguées pour l'exercice, sur une séance.
     * @param repRange fourchette prescrite ; le haut est [RepRange.max].
     * @param prescribedSets nombre de séries prévues au plan. Une séance écourtée ne déclenche
     *   pas d'augmentation, même si les séries faites sont parfaites.
     */
    fun shouldIncreaseLoad(
        sets: List<SetLog>,
        repRange: RepRange,
        prescribedSets: Int,
    ): Boolean {
        require(prescribedSets >= 1) {
            "Un exercice prescrit au moins une série (reçu : $prescribedSets)"
        }
        if (sets.size < prescribedSets) return false
        return sets.all { it.reps >= repRange.max && it.cleanTechnique }
    }

    /**
     * Prochain palier réellement disponible au-dessus de [currentKg] — SPEC.md §5.4 parle du
     * "prochain palier disponible sur tes haltères", pas d'un incrément théorique : le matériel
     * décide. `null` quand il n'y a plus de palier au-dessus.
     */
    fun nextLoadKg(currentKg: Float, availableLoadsKg: List<Float>): Float? =
        availableLoadsKg.filter { it > currentKg }.minOrNull()
}
