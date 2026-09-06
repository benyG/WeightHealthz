package com.forge.core.sync.calendar

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Écrit les événements via le fournisseur d'agenda du système.
 *
 * L'idempotence repose sur `CUSTOM_APP_PACKAGE` + `CUSTOM_APP_URI`, deux colonnes prévues pour
 * marquer l'application d'origine d'un événement : relancer l'onboarding ne recrée pas six repas
 * en double, il constate qu'ils sont déjà là.
 */
@Singleton
class CalendarContractSync @Inject constructor(
    @ApplicationContext private val context: Context,
) : CalendarSync {

    override suspend fun sync(
        specs: List<CalendarEventSpec>,
        zone: ZoneId,
    ): CalendarSync.Result = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext CalendarSync.Result.PermissionMissing
        val calendarId = writableCalendarId() ?: return@withContext CalendarSync.Result.NoWritableCalendar

        val existing = existingKeys()
        var created = 0

        specs.forEach { spec ->
            if (spec.key in existing) return@forEach
            if (insert(spec, calendarId, zone)) created++
        }

        CalendarSync.Result.Synced(created = created, alreadyPresent = specs.size - created)
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED

    /**
     * Premier calendrier où l'on peut écrire, en préférant un compte Google : c'est celui qui
     * remonte dans Google Calendar, ce que demande SPEC.md §5.8.
     */
    @SuppressLint("MissingPermission") // Garde vérifié dans `sync`, que lint ne suit pas jusqu'ici.
    private fun writableCalendarId(): Long? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.IS_PRIMARY,
        )
        val selection = "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ?"
        val args = arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString())

        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            var fallback: Long? = null
            while (cursor.moveToNext()) {
                val id = cursor.getLong(0)
                val accountType = cursor.getString(1)
                val isPrimary = cursor.getInt(2) == 1
                if (accountType == GOOGLE_ACCOUNT_TYPE && isPrimary) return id
                if (fallback == null) fallback = id
            }
            return fallback
        }
        return null
    }

    @SuppressLint("MissingPermission") // Garde vérifié dans `sync`, que lint ne suit pas jusqu'ici.
    private fun existingKeys(): Set<String> {
        val projection = arrayOf(CalendarContract.Events.CUSTOM_APP_URI)
        val selection = "${CalendarContract.Events.CUSTOM_APP_PACKAGE} = ?"
        val args = arrayOf(context.packageName)

        return context.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            args,
            null,
        )?.use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let(::add)
                }
            }
        } ?: emptySet()
    }

    @SuppressLint("MissingPermission") // Garde vérifié dans `sync`, que lint ne suit pas jusqu'ici.
    private fun insert(spec: CalendarEventSpec, calendarId: Long, zone: ZoneId): Boolean {
        val start = spec.firstDate.atTime(spec.startTime).atZone(zone).toInstant().toEpochMilli()

        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, spec.title)
            put(CalendarContract.Events.DESCRIPTION, spec.description)
            put(CalendarContract.Events.DTSTART, start)
            // Un événement récurrent se décrit par une DURATION, jamais par un DTEND : c'est ce
            // qu'exige le fournisseur, et c'est ce qui fait durer chaque occurrence.
            put(CalendarContract.Events.DURATION, "PT${spec.duration.toMinutes()}M")
            put(CalendarContract.Events.RRULE, spec.recurrenceRule)
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            put(CalendarContract.Events.CUSTOM_APP_PACKAGE, context.packageName)
            put(CalendarContract.Events.CUSTOM_APP_URI, spec.key)
        }

        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values)
        if (uri == null) {
            Log.w(TAG, "Création refusée par l'agenda pour ${spec.key}")
            return false
        }

        addReminder(ContentUris.parseId(uri), spec.reminderMinutesBefore)
        return true
    }

    /** Le rappel natif de l'agenda vient en plus des notifications Forge (SPEC.md §5.8). */
    @SuppressLint("MissingPermission") // Garde vérifié dans `sync`, que lint ne suit pas jusqu'ici.
    private fun addReminder(eventId: Long, minutesBefore: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }

    private companion object {
        const val TAG = "CalendarContractSync"
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}
