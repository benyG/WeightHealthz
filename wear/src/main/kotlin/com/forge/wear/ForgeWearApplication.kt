package com.forge.wear

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Point d'entrée de l'app montre. Tile de pesée, complication d'écart et écran de séance
 * arrivent en phase 6 (IMPLEMENTATION_PLAN.md §8, wireframes DESIGN.md §7.3–§7.4).
 */
@HiltAndroidApp
class ForgeWearApplication : Application()
