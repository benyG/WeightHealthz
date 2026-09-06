package com.forge.wear.data

import android.content.Context
import com.forge.domain.link.WearLink
import com.forge.domain.link.WearSetEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File d'attente des séries non transmises.
 *
 * C'est le cas normal, pas l'exception : on s'entraîne avec la montre au poignet et le téléphone
 * au vestiaire. Une séance entière peut donc s'accumuler ici et ne partir qu'au retour à portée.
 *
 * Un ensemble et non une liste : chaque série porte sa position dans son exercice, donc un
 * doublon exact n'apporte rien, et l'ordre d'envoi n'a pas d'importance — le téléphone écrit
 * chaque série à sa place, pas à la suite.
 */
@Singleton
class PendingSets @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun add(entry: WearSetEntry) {
        preferences.edit().putStringSet(KEY, current() + WearLink.encodeSetEntry(entry)).apply()
    }

    fun drain(): List<WearSetEntry> {
        val entries = current().mapNotNull(WearLink::decodeSetEntry)
        preferences.edit().remove(KEY).apply()
        return entries
    }

    private fun current(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty()

    private companion object {
        const val NAME = "forge-series-en-attente"
        const val KEY = "series"
    }
}
