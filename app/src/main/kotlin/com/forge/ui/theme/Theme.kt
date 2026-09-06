package com.forge.ui.theme

import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Thème de Forge.
 *
 * Un seul schéma de couleurs : l'app est sombre par nature (DESIGN.md §2 ne définit qu'un fond,
 * le graphite chaud). Pas de thème clair, pas de couleurs dynamiques Material You — la palette
 * encode des états, elle ne suit pas le fond d'écran.
 */
private val ForgeColorScheme = darkColorScheme(
    background = ForgeColors.Graphite,
    onBackground = ForgeColors.Os,
    surface = ForgeColors.Graphite,
    onSurface = ForgeColors.Os,
    surfaceVariant = ForgeColors.Charbon,
    onSurfaceVariant = ForgeColors.SableEteint,
    primary = ForgeColors.Laiton,
    onPrimary = ForgeColors.Graphite,
    error = ForgeColors.Brique,
    onError = ForgeColors.Os,
    outline = ForgeColors.SableEteint,
)

@Composable
fun ForgeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ForgeColorScheme,
        typography = ForgeTypography,
        // Formes par défaut à angle vif : DESIGN.md §5 réserve l'arrondi aux éléments tapables,
        // qui le déclarent eux-mêmes. Un bloc de données est un fait, pas un objet à manipuler.
        content = content,
    )
}

/**
 * Séparateur du « carnet réglé » (DESIGN.md §5) : un trait de 1px en sable éteint à 20 %, à la
 * place des cartes à ombre portée.
 */
@Composable
fun ForgeRule(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier.fillMaxWidth(),
        thickness = 1.dp,
        color = ForgeColors.SableEteint.copy(alpha = 0.2f),
    )
}

/**
 * Empile des lignes séparées par une règle, sans règle en tête ni en pied — la liste réglée de
 * DESIGN.md §5.
 */
@Composable
fun RuledColumn(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rows: List<@Composable () -> Unit>,
) {
    Column(modifier = modifier.padding(contentPadding)) {
        rows.forEachIndexed { index, row ->
            if (index > 0) ForgeRule()
            row()
        }
    }
}

@Composable
fun VerticalSpace(height: Dp) {
    Spacer(Modifier.height(height))
}

/**
 * Vrai quand le système demande de réduire les animations.
 *
 * Android n'expose pas de drapeau « reduced motion » : l'échelle de durée des animateurs à zéro
 * en est l'équivalent retenu, et c'est ce que règle l'option d'accessibilité. DESIGN.md §6 exige
 * qu'une animation non essentielle ait un équivalent statique.
 */
@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f,
        ) == 0f
    }
}

/** Marge horizontale unique de l'app : tout s'aligne à gauche sur cette colonne. */
val ScreenPadding: Dp = 20.dp

/** Hauteur minimale d'une cible tactile (DESIGN.md §10). */
val TouchTarget: Dp = 48.dp
