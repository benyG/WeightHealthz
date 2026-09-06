package com.forge.wear.complication

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.forge.wear.data.PhoneLink
import com.forge.wear.ui.WeighInActivity
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject

/**
 * Complication de cadran affichant l'écart au poids cible — DESIGN.md §7.4 : `[ +1.8 ]`, un tap
 * ouvre la pesée du jour.
 *
 * C'est le chiffre le plus consulté du produit ramené à sa plus simple expression : sur un
 * cadran, il n'y a de place que pour le fait, pas pour son commentaire.
 */
@AndroidEntryPoint
class WeightGapComplicationService : SuspendingComplicationDataSourceService() {

    @Inject lateinit var phoneLink: PhoneLink

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type != ComplicationType.SHORT_TEXT) null else shortText("+1,8", "Écart au poids cible")

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        if (request.complicationType != ComplicationType.SHORT_TEXT) return null

        val gap = phoneLink.lastKnownGapKg()
        return shortText(
            text = gap?.let { String.format(Locale.FRANCE, "%+.1f", it) } ?: "—",
            description = "Écart au poids cible",
        )
    }

    private fun shortText(text: String, description: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(text).build(),
            contentDescription = PlainComplicationText.Builder(description).build(),
        )
            .setTapAction(weighInIntent())
            .build()

    private fun weighInIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WeighInActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
