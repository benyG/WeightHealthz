package com.forge.domain.link

import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog
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

    /** Donnée téléphone → montre : la séance du jour et les séries déjà loguées. */
    const val SESSION_PATH: String = "/forge/seance"

    const val SESSION_KEY_PLAN: String = "seance"
    const val SESSION_KEY_UPDATED_AT: String = "mis_a_jour"

    /** Message montre → téléphone : une série saisie au poignet. */
    const val SET_PATH: String = "/forge/serie"

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

    // --- Séance du jour ---
    //
    // La pesée se contente d'un point-virgule parce que ses trois champs sont des nombres. La
    // séance transporte des noms d'exercices écrits par un humain : elle emploie donc les
    // séparateurs de contrôle ASCII, qu'aucun libellé ne contient, plutôt qu'un caractère qu'un
    // jour un nom finira par porter.

    private const val FIELD = "\u001F"
    private const val RECORD = "\u001E"
    private const val SET_ITEM = ","
    private const val SET_FIELD = ":"
    private const val LOAD_ITEM = ","

    fun encodeSessionPlan(plan: WearSessionPlan): String {
        val header = listOf(
            VERSION.toString(),
            plan.date.toEpochDay().toString(),
            plan.label,
            plan.availableLoadsKg.joinToString(LOAD_ITEM),
        ).joinToString(FIELD)

        val exercises = plan.exercises.map { exercise ->
            listOf(
                exercise.name,
                exercise.prescribedSets.toString(),
                exercise.repRange.min.toString(),
                exercise.repRange.max.toString(),
                exercise.suggestedLoadKg?.toString().orEmpty(),
                exercise.loggedSets.joinToString(SET_ITEM) { encodeSet(it) },
            ).joinToString(FIELD)
        }

        return (listOf(header) + exercises).joinToString(RECORD)
    }

    /** `null` sur un paquet illisible : la montre dira qu'elle n'a pas la séance, elle ne tombera pas. */
    fun decodeSessionPlan(raw: String): WearSessionPlan? {
        val records = raw.split(RECORD)
        val header = records.firstOrNull()?.split(FIELD) ?: return null
        if (header.size != 4) return null
        if (header[0].toIntOrNull() != VERSION) return null

        val epochDay = header[1].toLongOrNull() ?: return null
        val loads = decodeFloats(header[3]) ?: return null

        val exercises = records.drop(1).map { decodeExercise(it) ?: return null }

        return WearSessionPlan(
            date = LocalDate.ofEpochDay(epochDay),
            label = header[2],
            availableLoadsKg = loads,
            exercises = exercises,
        )
    }

    fun encodeSetEntry(entry: WearSetEntry): String = listOf(
        VERSION.toString(),
        entry.date.toEpochDay().toString(),
        entry.exerciseName,
        entry.position.toString(),
        encodeSet(entry.set),
    ).joinToString(FIELD)

    fun decodeSetEntry(raw: String): WearSetEntry? {
        val fields = raw.split(FIELD)
        if (fields.size != 5) return null
        if (fields[0].toIntOrNull() != VERSION) return null

        val epochDay = fields[1].toLongOrNull() ?: return null
        val position = fields[3].toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val set = decodeSet(fields[4]) ?: return null

        return WearSetEntry(
            date = LocalDate.ofEpochDay(epochDay),
            exerciseName = fields[2],
            position = position,
            set = set,
        )
    }

    private fun decodeExercise(raw: String): WearExercisePlan? {
        val fields = raw.split(FIELD)
        if (fields.size != 6) return null

        val prescribedSets = fields[1].toIntOrNull()?.takeIf { it >= 1 } ?: return null
        val repMin = fields[2].toIntOrNull() ?: return null
        val repMax = fields[3].toIntOrNull() ?: return null
        if (repMin < 1 || repMax < repMin) return null

        val suggested = if (fields[4].isEmpty()) null else fields[4].toFloatOrNull() ?: return null
        val sets = if (fields[5].isEmpty()) {
            emptyList()
        } else {
            fields[5].split(SET_ITEM).map { decodeSet(it) ?: return null }
        }

        return WearExercisePlan(
            name = fields[0],
            prescribedSets = prescribedSets,
            repRange = RepRange(repMin, repMax),
            suggestedLoadKg = suggested,
            loggedSets = sets,
        )
    }

    private fun encodeSet(set: SetLog): String =
        listOf(set.reps, set.weightKg, if (set.cleanTechnique) 1 else 0).joinToString(SET_FIELD)

    private fun decodeSet(raw: String): SetLog? {
        val parts = raw.split(SET_FIELD)
        if (parts.size != 3) return null

        val reps = parts[0].toIntOrNull() ?: return null
        val weightKg = parts[1].toFloatOrNull() ?: return null
        val clean = when (parts[2]) {
            "1" -> true
            "0" -> false
            else -> return null
        }

        return SetLog(reps = reps, weightKg = weightKg, cleanTechnique = clean)
    }

    private fun decodeFloats(raw: String): List<Float>? {
        if (raw.isEmpty()) return emptyList()
        return raw.split(LOAD_ITEM).map { it.toFloatOrNull() ?: return null }
    }
}
