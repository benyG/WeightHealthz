package com.forge.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ForgeRule
import com.forge.ui.theme.ScreenPadding
import com.forge.ui.theme.TouchTarget
import com.forge.ui.theme.VerticalSpace
import java.util.Locale

/**
 * Séance active sur téléphone (SPEC.md §5.4).
 *
 * `DESIGN.md` §7.3 ne dessine que la version montre ; l'écran téléphone reprend sa hiérarchie —
 * l'exercice, puis la série en cours comme élément dominant — dans la mise en page réglée du
 * reste de l'app.
 *
 * La case « technique propre » est décochée à l'ouverture de chaque série et n'est jamais
 * pré-remplie. C'est la seule position défendable : un oubli coûte alors un palier non accordé,
 * là où un défaut à « propre » accorderait une montée de charge que personne n'a jugée méritée,
 * et la double progression est une règle non négociable de `CLAUDE.md`.
 */
@Composable
fun SessionScreen(
    state: SessionUiState,
    onSelectExercise: (String) -> Unit,
    onLogSet: (reps: Int, weightKg: Float, cleanTechnique: Boolean) -> Unit,
    onToggleTechnique: (exerciseName: String, position: Int) -> Unit,
    onUndoLastSet: (exerciseName: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        VerticalSpace(24.dp)

        val current = state.current
        if (current == null) {
            Text(
                text = when {
                    !state.loaded -> "Chargement de la séance."
                    !state.hasPlan -> "Aucune séance dans le plan. Importe le programme pour t'entraîner."
                    else -> "Pas de séance prévue aujourd'hui. Les repas comptent quand même."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = ForgeColors.SableEteint,
            )
            return@Column
        }

        Text(
            text = state.label.orEmpty(),
            style = MaterialTheme.typography.labelMedium,
            color = ForgeColors.SableEteint,
        )

        VerticalSpace(24.dp)
        CurrentExercise(current)

        VerticalSpace(24.dp)
        SetEntry(current, onLogSet)

        if (current.sets.isNotEmpty()) {
            VerticalSpace(24.dp)
            LoggedSets(current, onToggleTechnique, onUndoLastSet)
        }

        if (current.earnedNextLoadKg != null) {
            VerticalSpace(24.dp)
            Text(
                // Laiton : DESIGN.md §2 le réserve à la progression positive, et un palier gagné
                // en est une — mesurée, pas encouragée.
                text = "Palier gagné. Charge " +
                    "${formatKg(current.earnedNextLoadKg)} à la prochaine séance.",
                style = MaterialTheme.typography.bodyLarge,
                color = ForgeColors.LaitonClair,
            )
        }

        VerticalSpace(32.dp)
        DayExercises(state, onSelectExercise)

        VerticalSpace(32.dp)
    }
}

/**
 * L'élément dominant de l'écran (DESIGN.md §5) : la série en cours. C'est la seule information
 * qu'on cherche entre deux séries, l'haltère encore en main.
 */
@Composable
private fun CurrentExercise(exercise: ExerciseRow) {
    Text(
        text = exercise.name,
        style = MaterialTheme.typography.bodyLarge,
        color = ForgeColors.Os,
    )

    VerticalSpace(4.dp)
    Text(
        text = if (exercise.done) {
            "Terminé"
        } else {
            "Série ${exercise.sets.size + 1} sur ${exercise.prescribedSets}"
        },
        style = MaterialTheme.typography.displayMedium,
        color = if (exercise.done) ForgeColors.Mousse else ForgeColors.Os,
    )

    VerticalSpace(4.dp)
    Text(
        text = "${exercise.repRange.min} à ${exercise.repRange.max} reps",
        style = MaterialTheme.typography.bodyMedium,
        color = ForgeColors.SableEteint,
    )

    if (exercise.suggestedLoadKg != null) {
        Text(
            text = "charge proposée ${formatKg(exercise.suggestedLoadKg)}",
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeColors.SableEteint,
        )
    }
}

/**
 * Saisie d'une série : reps, charge, jugement de technique.
 *
 * La charge est pré-remplie — celle de la série précédente, sinon celle que propose la double
 * progression — parce qu'elle ne change pas d'une série à l'autre. Les reps ne le sont jamais :
 * c'est le nombre dont dépend toute la règle, et le pré-remplir au haut de la fourchette
 * reviendrait à suggérer la réponse qui fait monter la charge.
 */
@Composable
private fun SetEntry(
    exercise: ExerciseRow,
    onLogSet: (reps: Int, weightKg: Float, cleanTechnique: Boolean) -> Unit,
) {
    val defaultLoad = exercise.sets.lastOrNull()?.weightKg ?: exercise.suggestedLoadKg

    var reps by remember(exercise.name, exercise.sets.size) { mutableStateOf("") }
    var load by remember(exercise.name, exercise.sets.size) {
        mutableStateOf(defaultLoad?.let { formatNumber(it) } ?: "")
    }
    var cleanTechnique by remember(exercise.name, exercise.sets.size) { mutableStateOf(false) }

    val parsedReps = reps.toIntOrNull()
    val parsedLoad = load.replace(',', '.').toFloatOrNull()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NumberField(
            value = reps,
            onValueChange = { reps = it.filter(Char::isDigit) },
            suffix = "reps",
            keyboardType = KeyboardType.Number,
            modifier = Modifier.weight(1f),
        )
        NumberField(
            value = load,
            onValueChange = { load = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
            suffix = "kg",
            keyboardType = KeyboardType.Decimal,
            modifier = Modifier.weight(1f),
        )
    }

    VerticalSpace(8.dp)
    TechniqueToggle(checked = cleanTechnique, onCheckedChange = { cleanTechnique = it })

    VerticalSpace(8.dp)
    Button(
        onClick = {
            if (parsedReps != null && parsedLoad != null) {
                onLogSet(parsedReps, parsedLoad, cleanTechnique)
            }
        },
        enabled = parsedReps != null && parsedReps > 0 && parsedLoad != null && parsedLoad >= 0f,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = ForgeColors.Laiton,
            contentColor = ForgeColors.Graphite,
            disabledContainerColor = ForgeColors.Charbon,
            disabledContentColor = ForgeColors.SableEteint,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget),
    ) {
        Text("Valider la série", style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    suffix: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        textStyle = MaterialTheme.typography.titleMedium,
        singleLine = true,
        suffix = { Text(suffix, style = MaterialTheme.typography.bodyMedium) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ForgeColors.Os,
            unfocusedTextColor = ForgeColors.Os,
            focusedBorderColor = ForgeColors.Laiton,
            unfocusedBorderColor = ForgeColors.SableEteint.copy(alpha = 0.4f),
            cursorColor = ForgeColors.Laiton,
        ),
        modifier = modifier,
    )
}

@Composable
private fun TechniqueToggle(checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Même glyphe que la checklist repas : DESIGN.md §4 interdit de mélanger des packs
        // d'icônes, et une coche dit l'état sans en importer un.
        Text(
            text = if (checked) "✓" else "○",
            style = MaterialTheme.typography.bodyLarge,
            color = if (checked) ForgeColors.Mousse else ForgeColors.SableEteint,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = "Technique propre",
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.Os,
        )
    }
}

@Composable
private fun LoggedSets(
    exercise: ExerciseRow,
    onToggleTechnique: (String, Int) -> Unit,
    onUndoLastSet: (String) -> Unit,
) {
    ForgeRule()
    exercise.sets.forEach { set ->
        LoggedSetLine(exercise.name, set, onToggleTechnique)
        ForgeRule()
    }

    // Une ligne tapable, pas un bouton Material : la forme par défaut d'un TextButton est une
    // pastille arrondie, là où DESIGN.md §5 tient l'arrondi à 4–6 dp sur ce qui est tapable.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clickable { onUndoLastSet(exercise.name) }
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Défaire la dernière série",
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.SableEteint,
        )
    }
}

/** Une série loguée. Taper la ligne rejuge la technique : c'est la même case, après coup. */
@Composable
private fun LoggedSetLine(
    exerciseName: String,
    set: SetRow,
    onToggleTechnique: (String, Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .toggleable(
                value = set.cleanTechnique,
                role = Role.Checkbox,
                onValueChange = { onToggleTechnique(exerciseName, set.position) },
            )
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (set.cleanTechnique) "✓" else "○",
            style = MaterialTheme.typography.bodyLarge,
            color = if (set.cleanTechnique) ForgeColors.Mousse else ForgeColors.SableEteint,
            modifier = Modifier.width(28.dp),
        )
        Text(
            text = "série ${set.position + 1}",
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.SableEteint,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${set.reps} reps",
            style = MaterialTheme.typography.titleMedium,
            color = ForgeColors.Os,
        )
        Text(
            text = formatKg(set.weightKg),
            style = MaterialTheme.typography.titleMedium,
            color = ForgeColors.Os,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

/**
 * Les exercices du jour, dans l'ordre du plan. Pas de numérotation : le rang se lit déjà dans
 * l'ordre des lignes, et DESIGN.md §11 interdit la numérotation décorative.
 */
@Composable
private fun DayExercises(state: SessionUiState, onSelectExercise: (String) -> Unit) {
    Text(
        text = "Exercices du jour",
        style = MaterialTheme.typography.labelMedium,
        color = ForgeColors.SableEteint,
    )

    VerticalSpace(8.dp)
    ForgeRule()
    state.exercises.forEach { exercise ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = TouchTarget)
                .clickable { onSelectExercise(exercise.name) }
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (exercise.name == state.selected) ForgeColors.Os else ForgeColors.SableEteint,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${exercise.sets.size} sur ${exercise.prescribedSets}",
                style = MaterialTheme.typography.titleMedium,
                color = if (exercise.done) ForgeColors.Mousse else ForgeColors.Os,
            )
        }
        ForgeRule()
    }
}

/** Charges affichées sans décimale inutile : « 16 kg », pas « 16,0 kg ». */
private fun formatKg(value: Float): String = "${formatNumber(value)} kg"

private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) {
        value.toInt().toString()
    } else {
        String.format(Locale.FRANCE, "%.1f", value)
    }
