package com.forge.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.forge.wear.data.PhoneLink
import com.forge.wear.ui.WearPalette
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

/**
 * Tile de pesée rapide (SPEC.md §5.2, DESIGN.md §7.4).
 *
 * Elle affiche l'écart au poids cible publié par le téléphone et ouvre l'écran de pesée d'un
 * tap. Aucun calcul ici : la montre montre ce que le téléphone a calculé, elle ne recalcule pas
 * une moyenne mobile de son côté.
 */
@AndroidEntryPoint
class WeighInTileService : TileService() {

    @Inject lateinit var phoneLink: PhoneLink

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> = scope.future {
        val gap = phoneLink.lastKnownGapKg()

        TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder()
                                    .setRoot(layout(gap))
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .build()
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> = scope.future {
        // Aucune image : la Tile n'affiche que des chiffres et du texte (DESIGN.md §4).
        ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
    }

    private fun layout(gapKg: Double?): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHeight(expand())
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(WearPalette.Graphite.toArgbInt()))
                            .build(),
                    )
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(CLICK_ID)
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(WEIGH_IN_ACTIVITY)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(gapKg?.let { String.format(Locale.FRANCE, "%+.1f kg", it) } ?: "—")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(28f))
                            .setColor(argb(WearPalette.Laiton.toArgbInt()))
                            .build(),
                    )
                    .build(),
            )
            .addContent(
                LayoutElementBuilders.Text.Builder()
                    .setText(if (gapKg == null) "Pas encore de pesée" else "Se peser")
                    .setFontStyle(
                        LayoutElementBuilders.FontStyle.Builder()
                            .setSize(androidx.wear.protolayout.DimensionBuilders.sp(14f))
                            .setColor(argb(WearPalette.SableEteint.toArgbInt()))
                            .build(),
                    )
                    .build(),
            )
            .build()

    private companion object {
        const val RESOURCES_VERSION = "1"
        const val CLICK_ID = "forge-pesee"
        const val WEIGH_IN_ACTIVITY = "com.forge.wear.ui.WeighInActivity"

        /** Une heure : l'écart ne bouge qu'après une pesée, inutile de rafraîchir plus souvent. */
        const val FRESHNESS_MILLIS = 60L * 60L * 1000L
    }
}

private fun androidx.compose.ui.graphics.Color.toArgbInt(): Int =
    android.graphics.Color.argb(
        (alpha * 255).toInt(),
        (red * 255).toInt(),
        (green * 255).toInt(),
        (blue * 255).toInt(),
    )
