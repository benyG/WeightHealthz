package com.forge

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.forge.ui.analysis.AnalysisScreen
import com.forge.ui.analysis.AnalysisViewModel
import com.forge.ui.home.HomeScreen
import com.forge.ui.home.HomeViewModel
import com.forge.ui.meals.MealsScreen
import com.forge.ui.meals.MealsViewModel
import com.forge.ui.onboarding.OnboardingScreen
import com.forge.ui.onboarding.OnboardingViewModel
import com.forge.ui.session.SessionScreen
import com.forge.ui.session.SessionViewModel
import com.forge.ui.theme.ForgeColors
import com.forge.ui.theme.ForgeTheme
import com.forge.ui.weight.WeightEntryScreen
import com.forge.ui.weight.WeightEntryViewModel
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForgeTheme {
                ForgeApp(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ForgeColors.Graphite)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                )
            }
        }
    }
}

private object Routes {
    const val HOME = "accueil"
    const val WEIGHT = "pesee"
    const val MEALS = "repas"
    const val SESSION = "seance"
    const val ANALYSIS = "analyse"
    const val ONBOARDING = "onboarding"
}

@Composable
private fun ForgeApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.HOME, modifier = modifier) {
        composable(Routes.HOME) {
            val viewModel: HomeViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            // L'app rouverte le lendemain doit parler du bon jour.
            LaunchedEffect(Unit) { viewModel.refresh() }

            HomeScreen(
                state = state,
                onWeighIn = { navController.navigate(Routes.WEIGHT) },
                onOpenSession = { navController.navigate(Routes.SESSION) },
                onOpenMeals = { navController.navigate(Routes.MEALS) },
                onOpenAnalysis = { navController.navigate(Routes.ANALYSIS) },
                onOpenEcosystem = { navController.navigate(Routes.ONBOARDING) },
            )
        }

        composable(Routes.WEIGHT) {
            val viewModel: WeightEntryViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            WeightEntryScreen(
                state = state,
                onSave = {
                    viewModel.save(it)
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.SESSION) {
            val viewModel: SessionViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            SessionScreen(
                state = state,
                onSelectExercise = viewModel::select,
                onLogSet = viewModel::logSet,
                onToggleTechnique = viewModel::toggleTechnique,
                onUndoLastSet = viewModel::undoLastSet,
            )
        }

        composable(Routes.MEALS) {
            val viewModel: MealsViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            MealsScreen(state = state, onToggle = viewModel::toggle)
        }

        composable(Routes.ANALYSIS) {
            val viewModel: AnalysisViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            AnalysisScreen(state = state)
        }

        composable(Routes.ONBOARDING) {
            val viewModel: OnboardingViewModel = hiltViewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()

            val healthConnectLauncher = rememberPermissionLauncher { viewModel.refresh() }
            val calendarLauncher = rememberPermissionLauncher {
                viewModel.refresh()
                viewModel.syncCalendar()
            }

            OnboardingScreen(
                state = state,
                onConnectHealthConnect = {
                    healthConnectLauncher(viewModel.healthConnectPermissions.toTypedArray())
                },
                onConnectCalendar = {
                    calendarLauncher(
                        arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR),
                    )
                },
                onRevealRelayCode = viewModel::revealRelayCode,
                onConnectRelay = viewModel::connectRelay,
            )
        }
    }
}

/**
 * Demande de permissions multiples, sans distinguer accord et refus : l'écran relit l'état réel
 * après coup plutôt que de croire le résultat sur parole.
 */
@Composable
private fun rememberPermissionLauncher(onResult: () -> Unit): (Array<String>) -> Unit {
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { onResult() }
    return { permissions -> launcher.launch(permissions) }
}
