package com.forge.wear.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.forge.wear.data.WeightSubmitter
import dagger.hilt.android.AndroidEntryPoint
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Pesée depuis la montre — la Tile et la complication mènent ici (SPEC.md §5.2).
 *
 * La saisie se fait par paliers de 100 g autour d'une valeur de départ, parce qu'un clavier sur
 * un écran de montre n'est pas une saisie « en 1 tap ». Le tap unique promis par SPEC.md §5.2 est
 * celui qui ouvre cet écran depuis le cadran ; la validation en demande un second.
 */
@AndroidEntryPoint
class WeighInActivity : ComponentActivity() {

    @Inject lateinit var submitter: WeightSubmitter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colors = wearColors()) {
                WeighInScreen(
                    startKg = DEFAULT_START_KG,
                    onValidate = { kg ->
                        // L'écran ne se ferme qu'une fois la pesée transmise ou mise en attente :
                        // finir avant annulerait la portée qui porte l'envoi.
                        lifecycleScope.launch {
                            submitter.submit(kg)
                            finish()
                        }
                    },
                )
            }
        }
    }

    private companion object {
        /**
         * Valeur de départ du sélecteur. Elle n'est jamais enregistrée telle quelle : l'ajustement
         * précède toujours la validation.
         */
        const val DEFAULT_START_KG = 80f
    }
}

@Composable
private fun WeighInScreen(startKg: Float, onValidate: (Float) -> Unit) {
    var kg by remember { mutableFloatStateOf(startKg) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(WearPalette.Graphite)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = String.format(Locale.FRANCE, "%.1f kg", kg),
            style = MaterialTheme.typography.display2,
            color = WearPalette.Os,
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StepButton("−") { kg = (kg - STEP_KG).coerceAtLeast(MIN_KG) }
            StepButton("+") { kg = (kg + STEP_KG).coerceAtMost(MAX_KG) }
        }

        Button(
            onClick = { onValidate(kg) },
            colors = ButtonDefaults.primaryButtonColors(backgroundColor = WearPalette.Laiton),
            modifier = Modifier
                .padding(top = 8.dp)
                // DESIGN.md §10 : la cible tactile ne descend pas sous 48 dp, même à l'étroit.
                .size(TOUCH_TARGET),
        ) {
            Text("OK", color = WearPalette.Graphite)
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.secondaryButtonColors(),
        modifier = Modifier.size(TOUCH_TARGET),
    ) {
        Text(label, color = WearPalette.Os, style = MaterialTheme.typography.title2)
    }
}

private val TOUCH_TARGET = 48.dp
private const val STEP_KG = 0.1f
private const val MIN_KG = 30f
private const val MAX_KG = 300f
