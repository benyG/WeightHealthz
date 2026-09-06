package com.forge.ui.analysis

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ForgeRule
import com.forge.ui.theme.ScreenPadding
import com.forge.ui.theme.VerticalSpace
import java.util.Locale

/**
 * Analyse hebdomadaire — wireframe DESIGN.md §7.5.
 *
 * La ligne « ▸ Écouter (0:52) » du wireframe n'est pas rendue : la vocalisation est en phase 2
 * produit (SPEC.md §9) et aucun fichier audio n'existe encore. Afficher une commande de lecture
 * qui ne lit rien serait pire que de ne rien afficher.
 */
@Composable
fun AnalysisScreen(state: AnalysisUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        VerticalSpace(24.dp)

        if (state.analysis == null) {
            Text(
                text = if (state.analysisPending) {
                    "Analyse de la semaine en cours."
                } else {
                    "Pas encore d'analyse. La première arrive au terme d'une semaine complète."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = ForgeColors.SableEteint,
            )
            return@Column
        }

        Text(
            text = "Semaine ${state.analysis.weekIndex}",
            style = MaterialTheme.typography.labelMedium,
            color = ForgeColors.SableEteint,
        )

        // Un seul élément domine (DESIGN.md §5 et §11) : la mesure de la semaine. Le texte de
        // Gemini la commente, il ne la remplace pas.
        VerticalSpace(16.dp)
        Text(
            text = state.averageKg?.let { String.format(Locale.FRANCE, "%.1f kg", it) } ?: "—",
            style = MaterialTheme.typography.displayLarge,
            color = ForgeColors.Os,
        )
        Text(
            text = "moyenne sur 7 jours",
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeColors.SableEteint,
        )

        VerticalSpace(32.dp)
        Text(
            text = state.analysis.summaryText,
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.Os,
        )

        VerticalSpace(32.dp)
        ForgeRule()
        MeasureLine("Exercice à surveiller", state.analysis.focusExercise)
        ForgeRule()
        MeasureLine("Ajustement calorique", formatAdjustment(state.analysis.recommendedAdjustmentKcal))
        ForgeRule()

        VerticalSpace(32.dp)
    }
}

@Composable
private fun MeasureLine(label: String, value: String?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.SableEteint,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value ?: "non disponible",
            style = MaterialTheme.typography.titleMedium,
            color = ForgeColors.Os,
        )
    }
}

/** Zéro n'est pas un chiffre à afficher ici : « aucun » dit mieux qu'il n'y a rien à changer. */
private fun formatAdjustment(kcal: Int): String = when {
    kcal == 0 -> "aucun"
    else -> String.format(Locale.FRANCE, "%+d kcal", kcal)
}
