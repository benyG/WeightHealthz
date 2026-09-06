package com.forge.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.forge.domain.model.EscalationLevel
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ForgeRule
import com.forge.ui.theme.ScreenPadding
import com.forge.ui.theme.TouchTarget
import com.forge.ui.theme.VerticalSpace
import com.forge.ui.theme.rememberReducedMotion
import java.util.Locale

/**
 * Écran d'accueil — wireframe DESIGN.md §7.1.
 *
 * Deux écarts assumés par rapport au dessin de §7.1, tous deux imposés par la checklist §11 qui
 * fait autorité en cas de conflit : pas de point médian comme séparateur de métadonnée
 * ("Semaine 4 sur 8" au lieu de "Forge · Semaine 4/8"), et pas de flèche en fin de libellé sur
 * les lignes tapables.
 */
@Composable
fun HomeScreen(
    state: HomeUiState,
    onWeighIn: () -> Unit,
    onOpenSession: () -> Unit,
    onOpenMeals: () -> Unit,
    onOpenAnalysis: () -> Unit,
    onOpenEcosystem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = ScreenPadding),
    ) {
        VerticalSpace(24.dp)

        if (state.weekIndex != null && state.totalWeeks > 0) {
            Text(
                text = "Semaine ${state.weekIndex} sur ${state.totalWeeks}",
                style = MaterialTheme.typography.labelMedium,
                color = ForgeColors.SableEteint,
            )
        }

        VerticalSpace(32.dp)
        DominantDelta(state)
        VerticalSpace(32.dp)

        ForgeRule()
        WeighInRow(state, onWeighIn)
        ForgeRule()
        SessionRow(state, onOpenSession)
        ForgeRule()
        MealsRow(state, onOpenMeals)
        ForgeRule()
        AnalysisRow(onOpenAnalysis)
        ForgeRule()
        LinkRow("Écosystème", onOpenEcosystem)
        ForgeRule()

        VerticalSpace(32.dp)
    }
}

/**
 * Le seul élément dominant de l'écran (DESIGN.md §5).
 *
 * Sa couleur est un état, pas une décoration : mousse uniquement quand la donnée confirme qu'on
 * est dans la cible, laiton sinon (DESIGN.md §2). L'incrémentation depuis zéro est l'unique
 * séquence orchestrée de l'écran (§6), et elle disparaît si le système demande de réduire les
 * animations.
 */
@Composable
private fun DominantDelta(state: HomeUiState) {
    val delta = state.cumulativeDeltaKg

    if (delta == null) {
        Text(
            text = "Pas encore de pesée. Pèse-toi ce matin pour démarrer le suivi.",
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.SableEteint,
        )
        return
    }

    val reducedMotion = rememberReducedMotion()
    var target by remember { mutableFloatStateOf(if (reducedMotion) delta.toFloat() else 0f) }
    LaunchedEffect(delta, reducedMotion) { target = delta.toFloat() }
    val animated by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (reducedMotion) 0 else 700),
        label = "ecart-au-poids-cible",
    )

    val formatted = formatSignedKg(animated.toDouble())

    Text(
        text = formatted,
        style = MaterialTheme.typography.displayLarge,
        color = if (state.isOnTarget) ForgeColors.Mousse else ForgeColors.Laiton,
        // Le chiffre s'anime : le lecteur d'écran doit entendre la valeur finale, pas les étapes.
        modifier = Modifier.semantics { contentDescription = formatSignedKg(delta) },
    )

    val targetRange = state.target
    if (targetRange != null) {
        VerticalSpace(8.dp)
        Text(
            text = "objectif : ${formatSignedKg(targetRange.targetDeltaKgMin.toDouble())} à " +
                formatSignedKg(targetRange.targetDeltaKgMax.toDouble()),
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeColors.SableEteint,
        )
    }
}

@Composable
private fun WeighInRow(state: HomeUiState, onWeighIn: () -> Unit) {
    val weight = state.todayWeightKg
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clickable(onClick = onWeighIn)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Pesée du jour", style = MaterialTheme.typography.bodyLarge, color = ForgeColors.Os)
        Text(
            text = weight?.let { String.format(Locale.FRANCE, "%.1f kg", it) } ?: "à faire",
            style = MaterialTheme.typography.titleMedium,
            color = if (weight != null) ForgeColors.Os else escalationColor(state.adherence.escalationLevel),
        )
    }
}

@Composable
private fun SessionRow(state: HomeUiState, onOpenSession: () -> Unit) {
    val session = state.todaySession
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            // Un jour de repos n'ouvre rien : une ligne tapable qui mène à un écran vide dit
            // qu'on a raté quelque chose.
            .then(if (session != null) Modifier.clickable(onClick = onOpenSession) else Modifier)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Séance", style = MaterialTheme.typography.bodyLarge, color = ForgeColors.Os)
        Text(
            text = session?.label ?: "repos",
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.SableEteint,
        )
    }
}

@Composable
private fun MealsRow(state: HomeUiState, onOpenMeals: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clickable(onClick = onOpenMeals)
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Repas restants", style = MaterialTheme.typography.bodyLarge, color = ForgeColors.Os)
        Text(
            text = "${state.mealsRemaining} sur ${state.mealsTotal}",
            style = MaterialTheme.typography.titleMedium,
            color = ForgeColors.Os,
        )
    }
}

@Composable
private fun AnalysisRow(onOpenAnalysis: () -> Unit) = LinkRow("Analyse de la semaine", onOpenAnalysis)

@Composable
private fun LinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ForgeColors.Os)
    }
}

/** Brique est réservée aux niveaux de retard (DESIGN.md §2) — nulle part ailleurs. */
private fun escalationColor(level: EscalationLevel): Color = when (level) {
    EscalationLevel.A_JOUR -> ForgeColors.SableEteint
    else -> ForgeColors.Brique
}

private fun formatSignedKg(value: Double): String =
    String.format(Locale.FRANCE, "%+.1f kg", value)
