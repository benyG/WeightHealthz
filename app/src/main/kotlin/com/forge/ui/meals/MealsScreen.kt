package com.forge.ui.meals

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ForgeRule
import com.forge.ui.theme.ScreenPadding
import com.forge.ui.theme.TouchTarget
import com.forge.ui.theme.VerticalSpace
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Checklist des prises quotidiennes — wireframe DESIGN.md §7.2.
 *
 * Aucune numérotation : les repas n'ont pas d'ordre imposé entre eux, seulement des horaires
 * indicatifs (DESIGN.md §5). L'heure est affichée à ce titre, pas comme un rang.
 */
@Composable
fun MealsScreen(
    state: MealsUiState,
    onToggle: (MealRow, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        VerticalSpace(24.dp)

        if (state.rows.isEmpty()) {
            Text(
                // DESIGN.md §8 : le fait, puis l'action.
                text = "Aucun repas dans le plan. Importe le programme pour suivre les prises.",
                style = MaterialTheme.typography.bodyLarge,
                color = ForgeColors.SableEteint,
            )
            return@Column
        }

        Text(
            text = "${state.remaining} repas restants aujourd'hui",
            style = MaterialTheme.typography.displayMedium,
            color = if (state.remaining == 0) ForgeColors.Mousse else ForgeColors.Os,
        )

        VerticalSpace(24.dp)
        ForgeRule()
        state.rows.forEach { row ->
            MealLine(row, onToggle)
            ForgeRule()
        }

        if (state.variationOfTheDay != null) {
            VerticalSpace(24.dp)
            Text(
                text = state.variationOfTheDay,
                style = MaterialTheme.typography.bodyMedium,
                color = ForgeColors.SableEteint,
            )
        }

        VerticalSpace(32.dp)
    }
}

@Composable
private fun MealLine(row: MealRow, onToggle: (MealRow, Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .toggleable(
                value = row.done,
                role = Role.Checkbox,
                onValueChange = { onToggle(row, it) },
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Marque de coche en texte plutôt qu'une icône : DESIGN.md §4 interdit de mélanger les
        // packs d'icônes, et une seule glyphe suffit à dire l'état.
        Text(
            text = if (row.done) "✓" else "○",
            style = MaterialTheme.typography.bodyLarge,
            color = if (row.done) ForgeColors.Mousse else ForgeColors.SableEteint,
            modifier = Modifier.width(28.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.bodyLarge,
                color = if (row.done) ForgeColors.SableEteint else ForgeColors.Os,
            )
            if (row.description.isNotBlank()) {
                Text(
                    text = row.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = ForgeColors.SableEteint,
                )
            }
        }
        if (row.timeOfDay != null) {
            Text(
                text = row.timeOfDay.format(TIME_FORMAT),
                style = MaterialTheme.typography.labelMedium,
                color = ForgeColors.SableEteint,
            )
        }
    }
}

private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.FRANCE)
