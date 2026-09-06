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
    val voiceRelay: ChannelStatus = ChannelStatus.UNAVAILABLE,
    val calendarEventsCreated: Int? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val healthConnectSource: HealthConnectWeightSource,
    private val planCalendarSync: PlanCalendarSync,
    private val voiceRelay: VoiceRelay,
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
                        ChannelStatus.UNAVAILABLE
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

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED
}
