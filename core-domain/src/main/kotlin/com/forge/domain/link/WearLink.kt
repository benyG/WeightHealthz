package com.forge.domain.link

import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import java.time.LocalDate

/**
 * Contrat d'échange entre le téléphone et la montre.
 *
 * Il vit dans `core-domain` parce que c'est le seul module que les deux applications partagent,
 * et qu'un contrat recopié des deux côtés finit toujours par diverger d'un côté. Il ne contient
 * que des chaînes et des fonctions pures — aucune dépendance à la Data Layer, dont l'usage
 * appartient aux modules Android.
 *
 * Le format est délibérément trivial : un identifiant de version, un jour et un poids. La montre
 * n'envoie qu'une pesée, elle n'a pas de modèle à sérialiser.
 */
object WearLink {

    /** Message montre → téléphone : une pesée saisie au poignet. */
    const val WEIGHT_PATH: String = "/forge/pesee"

    /** Donnée téléphone → montre : l'écart au poids cible, affiché par la Tile et la complication. */
    const val GAP_PATH: String = "/forge/ecart"

    const val GAP_KEY_DELTA: String = "ecart_kg"
    const val GAP_KEY_UPDATED_AT: String = "mis_a_jour"

    private const val VERSION = 1
    private const val SEPARATOR = ";"

    fun encodeWeight(entry: WeightEntry): String =
        listOf(VERSION, entry.date.toEpochDay(), entry.kg).joinToString(SEPARATOR)

    /**
     * Renvoie `null` sur un message illisible ou d'une autre version plutôt que de lever : un
     * paquet abîmé venu de l'autre appareil ne doit pas faire tomber le service qui l'écoute.
     */
    fun decodeWeight(raw: String): WeightEntry? {
        val parts = raw.split(SEPARATOR)
        if (parts.size != 3) return null
        if (parts[0].toIntOrNull() != VERSION) return null

        val epochDay = parts[1].toLongOrNull() ?: return null
        val kg = parts[2].toFloatOrNull() ?: return null

        return WeightEntry(
            date = LocalDate.ofEpochDay(epochDay),
            kg = kg,
            // Une pesée venue de la montre reste une saisie manuelle : c'est la même main.
            source = WeightSource.MANUAL,
        )
    }
}
