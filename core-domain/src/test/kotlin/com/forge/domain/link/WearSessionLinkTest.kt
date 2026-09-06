package com.forge.domain.link

import com.forge.domain.model.RepRange
import com.forge.domain.model.SetLog
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.jupiter.api.Test

/**
 * Le contrat de la séance entre les deux appareils. Ce qui compte ici n'est pas la beauté du
 * format mais deux garanties : un aller-retour fidèle, et l'absence totale de plantage sur un
 * paquet abîmé — le service qui écoute tourne en tâche de fond, personne n'est là pour le relever.
 */
class WearSessionLinkTest {

    private val plan = WearSessionPlan(
        date = LocalDate.of(2026, 3, 16),
        label = "Bas du corps",
        availableLoadsKg = listOf(12f, 16f, 20f),
        exercises = listOf(
            WearExercisePlan(
                name = "Squat gobelet",
                prescribedSets = 4,
                repRange = RepRange(8, 12),
                suggestedLoadKg = 16f,
                loggedSets = listOf(
                    SetLog(reps = 12, weightKg = 16f, cleanTechnique = true),
                    SetLog(reps = 10, weightKg = 16f, cleanTechnique = false),
                ),
            ),
            WearExercisePlan(
                name = "Fente avant",
                prescribedSets = 3,
                repRange = RepRange(10, 12),
                suggestedLoadKg = null,
                loggedSets = emptyList(),
            ),
        ),
    )

    @Test
    fun `la seance fait l aller-retour sans rien perdre`() {
        assertEquals(plan, WearLink.decodeSessionPlan(WearLink.encodeSessionPlan(plan)))
    }

    @Test
    fun `une seance sans exercice reste lisible`() {
        val empty = plan.copy(exercises = emptyList(), availableLoadsKg = emptyList())

        assertEquals(empty, WearLink.decodeSessionPlan(WearLink.encodeSessionPlan(empty)))
    }

    @Test
    fun `un nom d exercice ponctue survit au transport`() {
        // C'est la raison des séparateurs de contrôle : un libellé venu d'un plan réel porte des
        // virgules, des points-virgules et des deux-points sans que le paquet s'en trouve coupé.
        val ponctue = plan.copy(
            exercises = listOf(
                plan.exercises.first().copy(name = "Squat gobelet ; charge : 16,0 kg"),
            ),
        )

        assertEquals(ponctue, WearLink.decodeSessionPlan(WearLink.encodeSessionPlan(ponctue)))
    }

    @Test
    fun `une seance illisible ne fait pas tomber la montre`() {
        assertNull(WearLink.decodeSessionPlan(""))
        assertNull(WearLink.decodeSessionPlan("n'importe quoi"))
        // Version inconnue : une montre restée en arrière ne doit pas afficher une séance
        // qu'elle comprend de travers.
        assertNull(WearLink.decodeSessionPlan("2\u001F20000\u001FBas du corps\u001F16"))
        assertNull(WearLink.decodeSessionPlan("1\u001Fpas-un-jour\u001FBas du corps\u001F16"))
        // Exercice amputé d'un champ.
        assertNull(
            WearLink.decodeSessionPlan(
                "1\u001F20000\u001FBas du corps\u001F16\u001ESquat\u001F4\u001F8\u001F12",
            ),
        )
        // Zéro série prescrite : le plan ne le permet pas, le lien non plus.
        assertNull(
            WearLink.decodeSessionPlan(
                "1\u001F20000\u001FBas du corps\u001F16\u001ESquat\u001F0\u001F8\u001F12\u001F\u001F",
            ),
        )
    }

    @Test
    fun `une serie fait l aller-retour avec sa position`() {
        val entry = WearSetEntry(
            date = LocalDate.of(2026, 3, 16),
            exerciseName = "Squat gobelet",
            position = 2,
            set = SetLog(reps = 12, weightKg = 18f, cleanTechnique = true),
        )

        assertEquals(entry, WearLink.decodeSetEntry(WearLink.encodeSetEntry(entry)))
    }

    @Test
    fun `le jugement de technique traverse le lien tel quel`() {
        val degradee = WearSetEntry(
            date = LocalDate.of(2026, 3, 16),
            exerciseName = "Squat gobelet",
            position = 0,
            set = SetLog(reps = 12, weightKg = 18f, cleanTechnique = false),
        )

        // Une technique jugée dégradée au poignet doit arriver dégradée : c'est elle qui bloque
        // la montée de charge.
        assertEquals(false, WearLink.decodeSetEntry(WearLink.encodeSetEntry(degradee))?.set?.cleanTechnique)
    }

    @Test
    fun `une serie illisible est ignoree plutot qu ecrite de travers`() {
        assertNull(WearLink.decodeSetEntry(""))
        // Position négative : ce serait une identité de série impossible.
        assertNull(WearLink.decodeSetEntry("1\u001F20000\u001FSquat\u001F-1\u001F12:18.0:1"))
        // Jugement de technique non booléen : mieux vaut rien écrire que deviner.
        assertNull(WearLink.decodeSetEntry("1\u001F20000\u001FSquat\u001F0\u001F12:18.0:oui"))
        assertNull(WearLink.decodeSetEntry("1\u001F20000\u001FSquat\u001F0\u001F12:18.0"))
    }
}
