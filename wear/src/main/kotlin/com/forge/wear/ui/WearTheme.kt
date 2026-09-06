package com.forge.wear.ui

import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors

/**
 * Palette de `DESIGN.md` §2, redéclarée ici parce que la montre ne dépend pas du module `app`.
 * Les valeurs doivent rester identiques à `ForgeColors` : c'est la même application sur deux
 * écrans, pas deux produits.
 */
object WearPalette {
    val Graphite = Color(0xFF1E1B17)
    val Charbon = Color(0xFF28241D)
    val Os = Color(0xFFEDE7D9)
    val SableEteint = Color(0xFFA79E8C)
    val Laiton = Color(0xFFC39A3C)
    val Mousse = Color(0xFF6E8F5C)
    val Brique = Color(0xFFA8462D)
}

fun wearColors(): Colors = Colors(
    primary = WearPalette.Laiton,
    onPrimary = WearPalette.Graphite,
    secondary = WearPalette.SableEteint,
    onSecondary = WearPalette.Graphite,
    background = WearPalette.Graphite,
    onBackground = WearPalette.Os,
    surface = WearPalette.Charbon,
    onSurface = WearPalette.Os,
    error = WearPalette.Brique,
    onError = WearPalette.Os,
)
