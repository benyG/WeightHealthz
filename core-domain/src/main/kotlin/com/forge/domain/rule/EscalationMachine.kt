package com.forge.domain.rule

import com.forge.domain.ForgeRules
import com.forge.domain.model.AdherenceState
import com.forge.domain.model.EscalationLevel

/**
 * Machine à états des rappels (SPEC.md §5.5).
 *
 * Deux entrées seulement, et la séparation est volontaire :
 * - [onValidAction] réagit à l'instant où l'utilisateur agit — une pesée à 8h doit éteindre un
 *   rappel tout de suite, pas à minuit.
 * - [onDayClosed] est le seul point où le niveau monte et où le streak bouge. Comme il ne
 *   s'exécute qu'une fois par journée, "deux manquements le même jour" ne peut pas faire sauter
 *   deux crans d'escalade : l'invariant tient par construction, pas par vigilance.
 */
object EscalationMachine {

    /**
     * Pesée loguée, repas coché ou séance validée : retour immédiat à
     * [EscalationLevel.A_JOUR]. Le streak n'est pas touché ici — il se compte à la journée,
     * sinon trois actions dans la même journée vaudraient trois jours de série.
     */
    fun onValidAction(state: AdherenceState): AdherenceState =
        state.copy(escalationLevel = EscalationLevel.A_JOUR)

    /**
     * Clôture d'une journée. Journée tenue : le streak avance et le niveau retombe à jour.
     * Journée manquée : le streak repart de zéro et l'escalade monte d'un cran.
     */
    fun onDayClosed(state: AdherenceState, hadValidAction: Boolean): AdherenceState =
        if (hadValidAction) {
            AdherenceState(state.streakDays + 1, EscalationLevel.A_JOUR)
        } else {
            AdherenceState(0, escalate(state.escalationLevel))
        }

    /**
     * Niveau correspondant à un nombre de jours consécutifs sans action valide — utile pour
     * reconstruire l'état depuis l'historique en base plutôt que de le rejouer événement par
     * événement.
     */
    fun levelForConsecutiveMissedDays(days: Int): EscalationLevel = when {
        days <= 0 -> EscalationLevel.A_JOUR
        days == ForgeRules.ESCALATION_DAYS_LEVEL_1 -> EscalationLevel.RETARD_1
        days == ForgeRules.ESCALATION_DAYS_LEVEL_2 -> EscalationLevel.RETARD_2
        else -> EscalationLevel.CRITIQUE
    }

    /** Au-delà du troisième jour, `CRITIQUE` est un plafond : il n'y a pas de cran suivant. */
    private fun escalate(level: EscalationLevel): EscalationLevel = when (level) {
        EscalationLevel.A_JOUR -> EscalationLevel.RETARD_1
        EscalationLevel.RETARD_1 -> EscalationLevel.RETARD_2
        EscalationLevel.RETARD_2 -> EscalationLevel.CRITIQUE
        EscalationLevel.CRITIQUE -> EscalationLevel.CRITIQUE
    }
}
