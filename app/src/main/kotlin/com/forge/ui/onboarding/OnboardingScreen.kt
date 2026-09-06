package com.forge.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ForgeRule
import com.forge.ui.theme.ScreenPadding
import com.forge.ui.theme.TouchTarget
import com.forge.ui.theme.VerticalSpace

/**
 * Connexion de l'écosystème — wireframe DESIGN.md §7.6.
 *
 * « Google Calendar » y devient « Agenda » : depuis la phase 4, Forge écrit dans le calendrier du
 * système plutôt que via l'API Google en OAuth (DEPLOYMENT.md §9). Le libellé dit ce que fait
 * réellement le bouton.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onConnectHealthConnect: () -> Unit,
    onConnectCalendar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding),
    ) {
        VerticalSpace(24.dp)
        Text(
            text = "Connecter l'écosystème",
            style = MaterialTheme.typography.displayMedium,
            color = ForgeColors.Os,
        )

        VerticalSpace(24.dp)
        ForgeRule()
        ChannelLine("Health Connect", state.healthConnect, onConnectHealthConnect)
        ForgeRule()
        ChannelLine("Agenda", state.calendar, onConnectCalendar)
        ForgeRule()
        ChannelLine("Relais vocal", state.voiceRelay, onClick = null)
        ForgeRule()

        VerticalSpace(24.dp)
        Text(
            text = "Chaque connexion ajoute un canal de rappel. L'app fonctionne sans, en moins complet.",
            style = MaterialTheme.typography.bodyMedium,
            color = ForgeColors.SableEteint,
        )

        if (state.calendarEventsCreated != null) {
            VerticalSpace(16.dp)
            Text(
                text = "${state.calendarEventsCreated} événements ajoutés à l'agenda.",
                style = MaterialTheme.typography.bodyMedium,
                color = ForgeColors.Mousse,
            )
        }
    }
}

@Composable
private fun ChannelLine(label: String, status: ChannelStatus, onClick: (() -> Unit)?) {
    val clickable = onClick != null && status == ChannelStatus.NOT_CONNECTED

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = TouchTarget)
            .let { if (clickable) it.clickable { onClick!!() } else it }
            .padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, color = ForgeColors.Os)
        Text(
            text = when (status) {
                ChannelStatus.CONNECTED -> "connecté"
                ChannelStatus.NOT_CONNECTED -> "connecter"
                // Le relais vocal reste indisponible tant que son fournisseur n'est pas choisi.
                ChannelStatus.UNAVAILABLE -> "indisponible"
            },
            style = MaterialTheme.typography.bodyLarge,
            color = when (status) {
                ChannelStatus.CONNECTED -> ForgeColors.Mousse
                ChannelStatus.NOT_CONNECTED -> ForgeColors.LaitonClair
                ChannelStatus.UNAVAILABLE -> ForgeColors.SableEteint
            },
        )
    }
}
