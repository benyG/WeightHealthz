package com.forge.wear.ui

import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.forge.domain.link.WearExercisePlan
import com.forge.domain.link.WearSessionPlan
import com.forge.domain.model.SetLog
import com.forge.domain.rule.DoubleProgression
import com.forge.wear.data.PhoneLink
import com.forge.wear.data.SetSubmitter
import dagger.hilt.android.AndroidEntryPoint
import java.time.LocalDate
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Séance active au poignet (SPEC.md §5.4, wireframe DESIGN.md §7.3).
 *
 * La montre n'a ni plan ni historique : le téléphone lui publie la séance du jour, séries déjà
 * loguées comprises, et elle renvoie chaque série validée. Hors de portée, elle continue de
 * fonctionner — la séance reçue est conservée par le système, les séries partent en file
 * d'attente. C'est le cas normal en salle, pas un mode dégradé.
 *
 * Deux écarts assumés par rapport au wireframe §7.3, tous deux postérieurs à son dessin :
 *  - une case « technique propre » par série, puisque c'est elle qui autorise la double
 *    progression, jamais pré-cochée ;
 *  - les reps se règlent au lieu d'être validées d'un « Fait », parce qu'un « Fait » revient à
 *    déclarer le haut de la fourchette, c'est-à-dire à s'accorder un palier sans le dire.
 *
 * On n'y corrige pas une série déjà validée : l'écran de séance du téléphone le fait, avec de la
 * place pour montrer ce qu'on corrige. Ici, tout ce qu'on peut faire est ajouter ce qu'on vient
 * de faire.
 */
@AndroidEntryPoint
class SessionActivity : ComponentActivity() {

    @Inject lateinit var phoneLink: PhoneLink

    @Inject lateinit var submitter: SetSubmitter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = wearColors()) {
                SessionRoot(
                    loadPlan = {
                        // Une séance publiée un autre jour ne vaut pas pour aujourd'hui.
                        phoneLink.lastKnownSessionPlan()?.takeIf { it.date == LocalDate.now() }
                    },
                    onValidate = { exerciseName, position, set ->
                        lifecycleScope.launch {
                            submitter.submit(exerciseName, position, set)
                        }
                    },
                    onRestOver = ::vibrate,
                )
            }
        }
    }

    /** Fin du repos : la montre le dit au poignet, personne ne regarde l'écran entre deux séries. */
    private fun vibrate() {
        val vibrator = getSystemService(Vibrator::class.java) ?: return
        vibrator.vibrate(VibrationEffect.createOneShot(VIBRATION_MS, VibrationEffect.DEFAULT_AMPLITUDE))
    }

    private companion object {
        const val VIBRATION_MS = 400L
    }
}

@Composable
private fun SessionRoot(
    loadPlan: suspend () -> WearSessionPlan?,
    onValidate: (exerciseName: String, position: Int, set: SetLog) -> Unit,
    onRestOver: () -> Unit,
) {
    var plan by remember { mutableStateOf<WearSessionPlan?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var logged by remember { mutableStateOf<Map<String, List<SetLog>>>(emptyMap()) }
    var exerciseIndex by remember { mutableIntStateOf(0) }
    var restRemaining by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val received = loadPlan()
        plan = received
        logged = received?.exercises.orEmpty().associate { it.name to it.loggedSets }
        // On ouvre sur le premier exercice qui reste à faire, comme sur le téléphone.
        exerciseIndex = received?.exercises.orEmpty()
            .indexOfFirst { it.loggedSets.size < it.prescribedSets }
            .coerceAtLeast(0)
        loaded = true
    }

    val session = plan
    val current = session?.exercises?.getOrNull(exerciseIndex)

    when {
        !loaded -> Centered("Chargement.")

        session == null || current == null -> Centered(
            // DESIGN.md §8 : le fait, puis l'action.
            "Séance du jour non reçue. Ouvre Forge sur le téléphone.",
        )

        restRemaining > 0 -> {
            LaunchedEffect(restRemaining) {
                delay(SECOND_MS)
                restRemaining -= 1
                if (restRemaining == 0) onRestOver()
            }
            RestScreen(restRemaining) { restRemaining = 0 }
        }

        else -> ExerciseScreen(
            plan = session,
            exercise = current,
            exerciseIndex = exerciseIndex,
            sets = logged[current.name].orEmpty(),
            onValidate = { set ->
                val done = logged[current.name].orEmpty()
                onValidate(current.name, done.size, set)
                logged = logged + (current.name to (done + set))
                restRemaining = REST_SECONDS
            },
            onNextExercise = {
                exerciseIndex = (exerciseIndex + 1) % session.exercises.size
            },
        )
    }
}

@Composable
private fun ExerciseScreen(
    plan: WearSessionPlan,
    exercise: WearExercisePlan,
    exerciseIndex: Int,
    sets: List<SetLog>,
    onValidate: (SetLog) -> Unit,
    onNextExercise: () -> Unit,
) {
    // Les reps repartent de la série précédente, sinon du bas de la fourchette. Jamais du haut :
    // ce serait proposer d'emblée le chiffre qui accorde le palier suivant.
    var reps by remember(exercise.name, sets.size) {
        mutableIntStateOf(sets.lastOrNull()?.reps ?: exercise.repRange.min)
    }
    var loadKg by remember(exercise.name, sets.size) {
        mutableStateOf(
            sets.lastOrNull()?.weightKg
                ?: exercise.suggestedLoadKg
                ?: plan.availableLoadsKg.minOrNull()
                ?: 0f,
        )
    }
    var cleanTechnique by remember(exercise.name, sets.size) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearPalette.Graphite)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Numérotation légitime : les exercices d'une séance forment une vraie séquence
        // (DESIGN.md §7.3).
        Text(
            text = "${exerciseIndex + 1} sur ${plan.exercises.size}   ${exercise.name}",
            style = MaterialTheme.typography.caption1,
            color = WearPalette.SableEteint,
            textAlign = TextAlign.Center,
        )

        Text(
            text = if (sets.size >= exercise.prescribedSets) {
                "Terminé"
            } else {
                "série ${sets.size + 1} sur ${exercise.prescribedSets}"
            },
            style = MaterialTheme.typography.title2,
            color = if (sets.size >= exercise.prescribedSets) WearPalette.Mousse else WearPalette.Os,
            modifier = Modifier.padding(top = 8.dp),
        )

        Stepper(
            value = "$reps reps",
            onDecrease = { reps = (reps - 1).coerceAtLeast(1) },
            onIncrease = { reps += 1 },
        )

        Stepper(
            value = formatKg(loadKg),
            onDecrease = { loadKg = previousLoad(loadKg, plan.availableLoadsKg) },
            onIncrease = { loadKg = nextLoad(loadKg, plan.availableLoadsKg) },
        )

        TechniqueToggle(cleanTechnique) { cleanTechnique = it }

        Button(
            onClick = {
                onValidate(SetLog(reps = reps, weightKg = loadKg, cleanTechnique = cleanTechnique))
            },
            colors = ButtonDefaults.primaryButtonColors(backgroundColor = WearPalette.Laiton),
            modifier = Modifier
                .padding(top = 8.dp)
                .size(TOUCH_TARGET),
        ) {
            Text("OK", color = WearPalette.Graphite)
        }

        if (plan.exercises.size > 1) {
            TapText("Exercice suivant", onNextExercise)
        }
    }
}

/** Minuteur de repos entre séries (SPEC.md §5.4). */
@Composable
private fun RestScreen(remainingSeconds: Int, onSkip: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearPalette.Graphite)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "$remainingSeconds",
            style = MaterialTheme.typography.display2,
            color = WearPalette.Laiton,
        )
        Text(
            text = "secondes de repos",
            style = MaterialTheme.typography.caption1,
            color = WearPalette.SableEteint,
        )

        TapText("Passer", onSkip)
    }
}

/** Ligne tapable : la hauteur vient de la cible tactile, pas de la taille du texte. */
@Composable
private fun TapText(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 12.dp)
            .fillMaxWidth()
            .heightIn(min = TOUCH_TARGET)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.button,
            color = WearPalette.SableEteint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Stepper(value: String, onDecrease: () -> Unit, onIncrease: () -> Unit) {
    Row(
        modifier = Modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton("−", onDecrease)
        Text(
            text = value,
            style = MaterialTheme.typography.title3,
            color = WearPalette.Os,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        StepButton("+", onIncrease)
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(TOUCH_TARGET),
    ) {
        Text(label, color = WearPalette.Os, style = MaterialTheme.typography.title2)
    }
}

@Composable
private fun TechniqueToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .padding(top = 8.dp)
            .fillMaxWidth()
            .heightIn(min = TOUCH_TARGET)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (checked) "✓ technique propre" else "○ technique propre",
            style = MaterialTheme.typography.caption1,
            color = if (checked) WearPalette.Mousse else WearPalette.SableEteint,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Centered(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearPalette.Graphite)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.body2,
            color = WearPalette.SableEteint,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Le pas suit le râtelier du plan : SPEC.md §5.4 parle du « prochain palier disponible sur tes
 * haltères ». Sans râtelier renseigné, un kilo — un pas arbitraire assumé vaut mieux qu'un
 * sélecteur qui ne bouge pas.
 */
private fun nextLoad(current: Float, availableLoadsKg: List<Float>): Float =
    if (availableLoadsKg.isEmpty()) {
        current + FALLBACK_STEP_KG
    } else {
        DoubleProgression.nextLoadKg(current, availableLoadsKg) ?: current
    }

private fun previousLoad(current: Float, availableLoadsKg: List<Float>): Float =
    if (availableLoadsKg.isEmpty()) {
        (current - FALLBACK_STEP_KG).coerceAtLeast(0f)
    } else {
        availableLoadsKg.filter { it < current }.maxOrNull() ?: current
    }

private fun formatKg(value: Float): String =
    if (value % 1f == 0f) "${value.toInt()} kg" else String.format(Locale.FRANCE, "%.1f kg", value)

private val TOUCH_TARGET = 48.dp
private const val SECOND_MS = 1_000L
private const val FALLBACK_STEP_KG = 1f

/**
 * Durée de repos entre séries. Le plan ne la porte pas encore — le format d'import n'a pas de
 * champ pour elle, et lui en ajouter un ferait une migration de base pour une valeur que
 * personne n'a demandée. Quatre-vingt-dix secondes est la durée usuelle sur du 8–12 reps ; le
 * bouton « Passer » existe pour les fois où elle ne convient pas.
 */
private const val REST_SECONDS = 90
