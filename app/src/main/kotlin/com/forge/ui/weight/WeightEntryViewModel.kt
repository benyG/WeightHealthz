package com.forge.ui.weight

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.domain.model.WeightEntry
import com.forge.domain.model.WeightSource
import com.forge.domain.repository.WeightRepository
import com.forge.wearlink.WeightGapPublisher
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WeightEntryUiState(
    val todayWeightKg: Float? = null,
    val lastMeasuredKg: Float? = null,
)

@HiltViewModel
class WeightEntryViewModel @Inject constructor(
    private val weights: WeightRepository,
    private val gapPublisher: WeightGapPublisher,
) : ViewModel() {

    val state: StateFlow<WeightEntryUiState> = weights.observeAll()
        .map { entries ->
            val today = LocalDate.now()
            WeightEntryUiState(
                todayWeightKg = entries.filter { it.date == today }
                    .takeIf { it.isNotEmpty() }
                    ?.map { it.kg }
                    ?.average()
                    ?.toFloat(),
                lastMeasuredKg = entries.filter { it.date < today }.maxByOrNull { it.date }?.kg,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightEntryUiState())

    /**
     * Une saisie manuelle remplace celle du jour au lieu de s'y ajouter : la clé (jour, source)
     * de la base s'en charge, donc corriger un chiffre mal tapé reste une correction.
     */
    fun save(kg: Float) {
        viewModelScope.launch {
            weights.record(WeightEntry(LocalDate.now(), kg, WeightSource.MANUAL))
            // La Tile et la complication montrent l'écart : sans cette publication, elles
            // resteraient sur la valeur d'avant la pesée.
            gapPublisher.publish()
        }
    }
}
