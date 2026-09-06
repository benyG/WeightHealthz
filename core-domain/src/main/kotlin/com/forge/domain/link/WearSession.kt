package com.forge.domain.link

import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog
import java.time.LocalDate

/**
 * Séance du jour telle que le téléphone la transmet à la montre.
 *
 * Ce n'est pas seulement le plan : les séries déjà loguées en font partie, sinon une séance
 * commencée sur le téléphone repartirait de zéro au poignet et écraserait ce qui existe. Le
 * râtelier voyage aussi, parce que la montre propose de changer de charge et qu'un palier ne
 * s'invente pas.
 */
data class WearSessionPlan(
    val date: LocalDate,
    val label: String,
    val availableLoadsKg: List<Float>,
    val exercises: List<WearExercisePlan>,
)

data class WearExercisePlan(
    val name: String,
    val prescribedSets: Int,
    val repRange: RepRange,
    /** Charge déduite de la dernière séance ; `null` sans historique. */
    val suggestedLoadKg: Float?,
    /** Séries déjà loguées aujourd'hui, dans l'ordre, d'où qu'elles viennent. */
    val loggedSets: List<SetLog>,
)

/**
 * Une série saisie au poignet, en route vers le téléphone.
 *
 * [position] est l'identité de la série dans son exercice, pas un simple rang d'affichage :
 * c'est ce qui rend un renvoi inoffensif. Une série rejouée depuis la file d'attente de la
 * montre corrige la même ligne au lieu d'en ajouter une seconde.
 *
 * Le libellé de la journée ne voyage pas : le téléphone le relit dans son propre plan. La montre
 * n'a pas à être crue sur un nom qu'elle a reçu de lui.
 */
data class WearSetEntry(
    val date: LocalDate,
    val exerciseName: String,
    val position: Int,
    val set: SetLog,
)
