package com.forge.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.forge.R

/**
 * Deux familles, pas une de plus (DESIGN.md §3) : une slab pour les chiffres qui portent la
 * mesure, une sans-serif pour tout le reste. Aucune graisse Light — elle manque de présence sur
 * fond sombre.
 */
val ZillaSlab = FontFamily(
    Font(R.font.zilla_slab_medium, FontWeight.Medium),
    Font(R.font.zilla_slab_bold, FontWeight.Bold),
)

/**
 * IBM Plex Sans est livré en fonte variable : une seule ressource, deux graisses obtenues par
 * l'axe `wght`.
 */
val IbmPlexSans = FontFamily(
    Font(
        R.font.ibm_plex_sans,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.ibm_plex_sans,
        FontWeight.Medium,
        variationSettings = FontVariation.Settings(FontVariation.weight(500)),
    ),
)

/**
 * Style des chiffres qui portent une mesure — poids, charges, écarts.
 *
 * `TextAlign.Unspecified` et surtout les chiffres tabulaires : DESIGN.md §3 impose `tnum` pour
 * que la largeur ne saute pas quand la valeur change. Sans cela, un poids qui passe de 82.4 à
 * 82.9 fait bouger toute la ligne.
 */
fun measureStyle(sizeSp: Int, weight: FontWeight = FontWeight.Medium) = TextStyle(
    fontFamily = ZillaSlab,
    fontWeight = weight,
    fontSize = sizeSp.sp,
    fontFeatureSettings = TABULAR_FIGURES,
    textAlign = TextAlign.Unspecified,
)

const val TABULAR_FIGURES: String = "tnum"

val ForgeTypography = Typography(
    // Le chiffre dominant d'un écran (l'écart au poids cible).
    displayLarge = measureStyle(56, FontWeight.Bold),
    displayMedium = measureStyle(36),
    // Chiffres secondaires en ligne réglée.
    titleMedium = measureStyle(20),

    bodyLarge = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    // Métadonnées et libellés secondaires.
    labelMedium = TextStyle(
        fontFamily = IbmPlexSans,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
)
