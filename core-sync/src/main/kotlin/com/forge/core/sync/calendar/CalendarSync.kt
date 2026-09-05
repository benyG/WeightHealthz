package com.forge.core.sync.calendar

import java.time.ZoneId

/**
 * Écriture du programme dans l'agenda (SPEC.md §5.8).
 *
 * **Écart assumé par rapport à SPEC.md §3**, qui prévoyait l'API Google Calendar en OAuth : on
 * passe par le fournisseur `CalendarContract` d'Android. Pour une app mono-utilisateur, cela
 * évite un projet Google Cloud, un écran de consentement OAuth, l'enregistrement d'empreintes
 * SHA-1 et le rafraîchissement de jetons, sans rien perdre : les événements écrits dans le
 * calendrier du compte Google du téléphone remontent dans Google Calendar par la synchronisation
 * du système, et l'écriture fonctionne hors ligne. À reprendre en OAuth si l'on veut écrire dans
 * un agenda auquel le téléphone n'est pas connecté.
 */
interface CalendarSync {

    suspend fun sync(specs: List<CalendarEventSpec>, zone: ZoneId = ZoneId.systemDefault()): Result

    sealed interface Result {
        data class Synced(val created: Int, val alreadyPresent: Int) : Result

        /** Permission d'agenda non accordée : l'app reste utilisable, ce canal en moins. */
        data object PermissionMissing : Result

        /** Aucun calendrier inscriptible sur l'appareil (aucun compte configuré). */
        data object NoWritableCalendar : Result
    }
}
