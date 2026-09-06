package com.forge.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.forge.domain.model.WeeklyAnalysis
import com.forge.domain.repository.WeeklyAnalysisRepository
import com.forge.domain.repository.WeightRepository
import com.forge.domain.rule.WeightTrend
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AnalysisUiState(
    val analysis: WeeklyAnalysis? = null,
    val averageKg: Double? = null,
    /** Vrai tant que le job hebdomadaire n'a pas encore produit de résultat consultable. */
    val analysisPending: Boolean = false,
)

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    analyses: WeeklyAnalysisRepository,
    weights: WeightRepository,
) : ViewModel() {

    val state: StateFlow<AnalysisUiState> =
        combine(analyses.observeLatest(), weights.observeAll()) { analysis, entries ->
            AnalysisUiState(
                analysis = analysis,
                averageKg = WeightTrend.movingAverageKg(entries, LocalDate.now()),
                analysisPending = analysis == null && entries.isNotEmpty(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())
}
