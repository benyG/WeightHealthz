package com.forge.domain.rule

import com.forge.domain.model.EscalationLevel

/**
 * Texte des rappels d'escalade.
 *
 * Il vit dans le domaine, et pas dans l'UI, parce que le même message part sur trois canaux —
 * notification Android, montre et enceinte — et que `DESIGN.md` §9 exige un vocabulaire
 * identique d'un canal à l'autre. Le partager mécaniquement vaut mieux que de le recopier trois
 * fois en espérant qu'il reste cohérent.
 *
 * Ton imposé par `DESIGN.md` §9 : voix active, pas d'exclamation, pas de flatterie, et le fait
 * **et** l'action dans la même phrase.
 */
object EscalationMessage {

    /**
     * Message correspondant à un niveau. `A_JOUR` n'en produit aucun : rien à signaler n'est pas
     * une nouvelle à annoncer.
     */
    fun forLevel(level: EscalationLevel, missedItem: String): String? = when (level) {
        EscalationLevel.A_JOUR -> null
        EscalationLevel.RETARD_1 -> "$missedItem manquant depuis 1 jour. Rattrape-le aujourd'hui."
        EscalationLevel.RETARD_2 -> "$missedItem manquant depuis 2 jours. Fais-le maintenant."
        EscalationLevel.CRITIQUE -> "$missedItem manquant depuis 3 jours. Le programme décroche, reprends aujourd'hui."
    }
}
