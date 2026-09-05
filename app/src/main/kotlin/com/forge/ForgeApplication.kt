package com.forge

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée de l'app téléphone. Aucune activité pour l'instant : les écrans arrivent en
 * phase 5, construits sur les wireframes de DESIGN.md §7 (IMPLEMENTATION_PLAN.md §7).
 */
@HiltAndroidApp
class ForgeApplication : Application()
