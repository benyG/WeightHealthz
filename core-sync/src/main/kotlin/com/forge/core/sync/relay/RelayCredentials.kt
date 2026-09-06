package com.forge.core.sync.relay

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Code d'accès du relais vocal, saisi à l'onboarding.
 *
 * Il n'est **pas** dans `BuildConfig` comme les clés API (DEPLOYMENT.md §4) : c'est un secret
 * propre à un compte, pas au build. Le compiler dans l'APK le figerait, obligerait à recompiler
 * pour en changer, et le distribuerait avec l'application.
 */
interface RelayCredentials {

    /** `null` tant qu'aucun code n'est saisi — un code blanc n'en est pas un. */
    fun accessCode(): String?

    fun store(accessCode: String)

    fun clear()
}

/**
 * Stockage en préférences privées de l'app, non chiffrées. C'est le niveau qui correspond au
 * risque : le code permet de faire parler une enceinte, pas de lire des données. Un chiffrement
 * local dont la clé vit sur le même appareil n'ajouterait qu'une dépendance et l'illusion d'une
 * protection.
 */
@Singleton
class PreferencesRelayCredentials @Inject constructor(
    @ApplicationContext context: Context,
) : RelayCredentials {

    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    override fun accessCode(): String? =
        preferences.getString(KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    override fun store(accessCode: String) {
        preferences.edit().putString(KEY, accessCode.trim()).apply()
    }

    override fun clear() {
        preferences.edit().remove(KEY).apply()
    }

    private companion object {
        const val NAME = "forge-relais-vocal"
        const val KEY = "code-acces"
    }
}
