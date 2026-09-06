package com.forge.ui.weight

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ScreenPadding
import com.forge.ui.theme.TouchTarget
import com.forge.ui.theme.VerticalSpace
import java.util.Locale

/**
 * Saisie de la pesée du jour.
 *
 * Les seuls éléments arrondis de l'écran sont le champ et le bouton : DESIGN.md §5 fait de
 * l'arrondi une information — 0 sur une donnée, 4 à 6 dp sur ce qu'on peut toucher.
 */
@Composable
fun WeightEntryScreen(
    state: WeightEntryUiState,
    onSave: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var input by remember(state.todayWeightKg) {
        mutableStateOf(state.todayWeightKg?.let { String.format(Locale.FRANCE, "%.1f", it) } ?: "")
    }
    val parsed = input.replace(',', '.').toFloatOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = ScreenPadding),
    ) {
        VerticalSpace(24.dp)
        Text(
            text = "Pesée du matin",
            style = MaterialTheme.typography.bodyLarge,
            color = ForgeColors.SableEteint,
        )

        VerticalSpace(24.dp)
        OutlinedTextField(
            value = input,
            onValueChange = { input = it.filter { char -> char.isDigit() || char == ',' || char == '.' } },
            textStyle = MaterialTheme.typography.displayMedium,
            singleLine = true,
            suffix = { Text("kg", style = MaterialTheme.typography.bodyLarge) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

        if (state.lastMeasuredKg != null) {
            VerticalSpace(12.dp)
            Text(
                text = "Dernière pesée enregistrée : " +
                    String.format(Locale.FRANCE, "%.1f kg", state.lastMeasuredKg),
                style = MaterialTheme.typography.bodyMedium,
                color = ForgeColors.SableEteint,
            )
        }

        VerticalSpace(24.dp)
        Button(
            onClick = { parsed?.let(onSave) },
            enabled = parsed != null && parsed in PLAUSIBLE_RANGE,
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
            Text("Enregistrer la pesée", style = MaterialTheme.typography.labelLarge)
        }

        if (parsed != null && parsed !in PLAUSIBLE_RANGE) {
            VerticalSpace(12.dp)
            Text(
                // DESIGN.md §8 : dire le fait et l'action, sans ton d'excuse.
                text = "Poids hors des valeurs plausibles. Vérifie la saisie.",
                style = MaterialTheme.typography.bodyMedium,
                color = ForgeColors.Brique,
            )
        }
    }
}

/**
 * Garde-fou de saisie : une virgule mal placée ("824" au lieu de "82,4") ne doit pas entrer en
 * base et fausser la moyenne mobile pour la semaine.
 */
private val PLAUSIBLE_RANGE = 30f..300f
