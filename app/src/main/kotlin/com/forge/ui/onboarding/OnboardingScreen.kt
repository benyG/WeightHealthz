package com.forge.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
 *
 * Le relais vocal se connecte en collant un code d'accès, seul canal qui demande une saisie. Son
 * bouton envoie une annonce de test dans la foulée : c'est le seul canal dont l'écran ne peut pas
 * constater l'arrivée, et le découvrir muet au premier passage en RETARD_2 serait le découvrir au
 * pire moment.
 */
@Composable
fun OnboardingScreen(
    state: OnboardingUiState,
    onConnectHealthConnect: () -> Unit,
    onConnectCalendar: () -> Unit,
    onRevealRelayCode: () -> Unit,
    onConnectRelay: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
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
        ChannelLine("Relais vocal", state.voiceRelay, onRevealRelayCode)
        ForgeRule()

        if (state.relayCodeVisible) {
            RelayCodeEntry(onConnectRelay)
        }

        if (state.relayTest != null) {
            VerticalSpace(12.dp)
            Text(
                text = state.relayTest,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.voiceRelay == ChannelStatus.CONNECTED) {
                    ForgeColors.Mousse
                } else {
                    // Os et non brique : §2 réserve la brique aux niveaux de retard, et un code
                    // refusé n'est pas un état du programme.
                    ForgeColors.Os
                },
            )
        }

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

/**
 * Saisie du code d'accès Notify My Alexa, obtenu par courriel après activation de la skill
 * (DEPLOYMENT.md §11). Le champ n'est pas masqué : ce code fait parler une enceinte, il ne donne
 * accès à aucune donnée, et le masquer empêcherait surtout de vérifier un collage.
 */
@Composable
private fun RelayCodeEntry(onConnectRelay: (String) -> Unit) {
    var code by remember { mutableStateOf("") }

    VerticalSpace(16.dp)
    Text(
        text = "Colle le code d'accès reçu après avoir activé la skill Notify My Alexa.",
        style = MaterialTheme.typography.bodyMedium,
        color = ForgeColors.SableEteint,
    )

    VerticalSpace(12.dp)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.trim() },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = RoundedCornerShape(6.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = ForgeColors.Os,
            unfocusedTextColor = ForgeColors.Os,
            focusedBorderColor = ForgeColors.Laiton,
            unfocusedBorderColor = ForgeColors.SableEteint.copy(alpha = 0.4f),
            cursorColor = ForgeColors.Laiton,
        ),
        modifier = Modifier.fillMaxWidth(),
    )

    VerticalSpace(12.dp)
    Button(
        onClick = { onConnectRelay(code) },
        enabled = code.isNotBlank(),
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
        Text("Enregistrer et annoncer un test", style = MaterialTheme.typography.labelLarge)
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
                // Reste possible : Health Connect absent de l'appareil, par exemple.
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
