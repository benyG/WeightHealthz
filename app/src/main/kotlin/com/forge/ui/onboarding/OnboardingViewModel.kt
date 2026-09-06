package com.forge.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.core.data.health.HealthConnectWeightSource
import com.forge.core.sync.calendar.CalendarSync
import com.forge.core.sync.calendar.PlanCalendarSync
import com.forge.core.sync.relay.RelayCredentials
import com.forge.core.sync.relay.VoiceRelay
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * État d'un canal de rappel. `DESIGN.md` §7.6 le dit à l'utilisateur : chaque connexion ajoute un
 * canal, l'app fonctionne sans, en moins complet.
 */
enum class ChannelStatus {
    CONNECTED,
    NOT_CONNECTED,

    /** Le canal ne peut pas exister sur cet appareil ou n'est pas encore configurable. */
    UNAVAILABLE,
}

data class OnboardingUiState(
    val healthConnect: ChannelStatus = ChannelStatus.NOT_CONNECTED,
    val calendar: ChannelStatus = ChannelStatus.NOT_CONNECTED,
    val voiceRelay: ChannelStatus = ChannelStatus.NOT_CONNECTED,
    val calendarEventsCreated: Int? = null,
    /** La saisie du code d'accès est dépliée, ou non. */
    val relayCodeVisible: Boolean = false,
    /** Résultat de l'annonce de test, en clair — c'est le seul retour qu'on ait sur ce canal. */
    val relayTest: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectSource: HealthConnectWeightSource,
    private val planCalendarSync: PlanCalendarSync,
    private val voiceRelay: VoiceRelay,
    private val relayCredentials: RelayCredentials,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    val healthConnectPermissions: Set<String> = healthConnectSource.permissions

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    healthConnect = when {
                        !healthConnectSource.isAvailable() -> ChannelStatus.UNAVAILABLE
                        healthConnectSource.hasPermission() -> ChannelStatus.CONNECTED
                        else -> ChannelStatus.NOT_CONNECTED
                    },
                    calendar = if (hasCalendarPermission()) ChannelStatus.CONNECTED else ChannelStatus.NOT_CONNECTED,
                    voiceRelay = if (voiceRelay.isConfigured()) {
                        ChannelStatus.CONNECTED
                    } else {
                        ChannelStatus.NOT_CONNECTED
                    },
                )
            }
        }
    }

    /**
     * Pose les événements du programme dans l'agenda. Rejouable : la synchronisation compte ce
     * qui existe déjà au lieu de le recréer.
     */
    fun syncCalendar() {
        viewModelScope.launch {
            val result = planCalendarSync.syncPlan()
            _state.update {
                it.copy(
                    calendar = if (result is CalendarSync.Result.Synced) {
                        ChannelStatus.CONNECTED
                    } else {
                        ChannelStatus.NOT_CONNECTED
                    },
                    calendarEventsCreated = (result as? CalendarSync.Result.Synced)?.created,
                )
            }
        }
    }

    /** Déplie la saisie du code d'accès du relais vocal. */
    fun revealRelayCode() {
        _state.update { it.copy(relayCodeVisible = true, relayTest = null) }
    }

    /**
     * Enregistre le code, puis fait parler l'enceinte tout de suite.
     *
     * L'annonce de test n'est pas un gadget : c'est le seul canal de Forge dont on ne peut pas
     * vérifier l'arrivée depuis l'écran. Sans elle, on croirait le relais connecté jusqu'au
     * premier passage en RETARD_2, c'est-à-dire au pire moment pour découvrir qu'il ne l'est pas.
     */
    fun connectRelay(accessCode: String) {
        viewModelScope.launch {
            relayCredentials.store(accessCode)

            val result = voiceRelay.announce("Forge est connecté à cette enceinte.")
            _state.update {
                it.copy(
                    voiceRelay = if (result is VoiceRelay.RelayResult.Delivered) {
                        ChannelStatus.CONNECTED
                    } else {
                        ChannelStatus.NOT_CONNECTED
                    },
                    relayCodeVisible = result !is VoiceRelay.RelayResult.Delivered,
                    relayTest = when (result) {
                        is VoiceRelay.RelayResult.Delivered ->
                            "Annonce envoyée. L'enceinte doit la dire maintenant."
                        is VoiceRelay.RelayResult.NotConfigured ->
                            "Code vide. Colle le code reçu par courriel après avoir activé la skill."
                        is VoiceRelay.RelayResult.Failed ->
                            "Le service a refusé l'annonce (${result.reason}). Vérifie le code."
                    },
                )
            }
        }
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
