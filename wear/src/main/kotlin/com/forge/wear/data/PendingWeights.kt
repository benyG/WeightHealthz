package com.forge.wear.data

import android.content.Context
import com.forge.domain.link.WearLink
import com.forge.domain.model.WeightEntry
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * File d'attente des pesées non transmises.
 *
 * Sans elle, se peser au poignet pendant que le téléphone est resté à la maison perdrait la
 * mesure — exactement le cas d'usage d'une Tile de pesée. Les entrées sont réémises au prochain
 * envoi réussi.
 *
 * Un simple fichier de préférences suffit : on parle de quelques lignes, jamais d'un historique.
 */
@Singleton
class PendingWeights @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun add(entry: WeightEntry) {
        val encoded = WearLink.encodeWeight(entry)
        preferences.edit().putStringSet(KEY, current() + encoded).apply()
    }

    fun drain(): List<WeightEntry> {
        val entries = current().mapNotNull(WearLink::decodeWeight)
        preferences.edit().remove(KEY).apply()
        return entries
    }

    fun isEmpty(): Boolean = current().isEmpty()

    private fun current(): Set<String> = preferences.getStringSet(KEY, emptySet()).orEmpty()

    private companion object {
        const val NAME = "forge-pesees-en-attente"
        const val KEY = "pesees"
    }
}
