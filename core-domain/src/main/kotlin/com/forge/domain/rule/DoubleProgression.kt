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

    /**
     * Charge à proposer aujourd'hui pour un exercice, d'après la dernière séance où il a été
     * travaillé. `null` quand il n'y a pas d'historique : on ne devine pas une première charge,
     * c'est à la personne de la poser.
     *
     * La charge de référence est celle de la série la **plus légère** de la dernière séance, pas
     * de la plus lourde. Les deux coïncident dans le cas normal — mêmes haltères sur toutes les
     * séries. Quand elles diffèrent, la plus légère est la seule charge à laquelle toutes les
     * séries ont réellement été tenues, et c'est ce que la double progression fait monter.
     * (`WeeklyReportBuilder` prend la plus lourde, lui, mais pour raconter la semaine, pas pour
     * décider d'un palier.)
     *
     * Quand le palier est gagné mais qu'il n'y a plus rien au-dessus sur le râtelier, on reste à
     * la charge de référence : la progression n'invente pas d'haltère.
     */
    fun suggestedLoadKg(
        previousSets: List<SetLog>,
        repRange: RepRange,
        prescribedSets: Int,
        availableLoadsKg: List<Float>,
    ): Float? {
        val reference = previousSets.minOfOrNull { it.weightKg } ?: return null
        if (!shouldIncreaseLoad(previousSets, repRange, prescribedSets)) return reference
        return nextLoadKg(reference, availableLoadsKg) ?: reference
    }
}
