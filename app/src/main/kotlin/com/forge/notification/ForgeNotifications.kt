package com.forge.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.forge.domain.model.EscalationLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Notifications de rappel.
 *
 * Un canal par niveau d'escalade, comme l'exige SPEC.md §5.5 : c'est ce qui permet au système de
 * traiter un `RETARD_1` discrètement et un `CRITIQUE` avec insistance, et à l'utilisateur de
 * régler chaque niveau séparément sans tout couper.
 *
 * **Pas de `fullScreenIntent` pour `CRITIQUE`**, contrairement à ce qu'envisageait la trame : la
 * permission `USE_FULL_SCREEN_INTENT` n'est plus accordée d'office depuis Android 14 aux
 * applications qui ne sont ni des appels ni des alarmes. La « lecture vocale forcée » de
 * SPEC.md §5.5 relève de toute façon de la phase vocale ; ici `CRITIQUE` se distingue par son
 * importance système, sa vibration et sa persistance.
 */
@Singleton
class ForgeNotifications @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_REMINDER,
                "Rappels du programme",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Pesée du matin, repas et séances." },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LATE,
                "Retard",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Deuxième jour sans action sur le programme."
                enableVibration(true)
            },
        )

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_CRITICAL,
                "Critique",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Troisième jour sans action : le programme décroche."
                enableVibration(true)
            },
        )
    }

    /** Ne poste rien si la permission n'est pas accordée — sans lever : ce n'est pas une panne. */
    fun notifyEscalation(level: EscalationLevel, message: String) {
        if (level == EscalationLevel.A_JOUR) return
        if (!canPost()) return

        val notification = NotificationCompat.Builder(context, channelFor(level))
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(titleFor(level))
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setPriority(priorityFor(level))
            .setOngoing(level == EscalationLevel.CRITIQUE)
            .setAutoCancel(level != EscalationLevel.CRITIQUE)
            .build()

        NotificationManagerCompat.from(context).notify(notificationIdFor(level), notification)
    }

    fun notifyReminder(title: String, message: String) {
        if (!canPost()) return

        val notification = NotificationCompat.Builder(context, CHANNEL_REMINDER)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(ID_REMINDER, notification)
    }

    private fun canPost(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun channelFor(level: EscalationLevel) = when (level) {
        EscalationLevel.CRITIQUE -> CHANNEL_CRITICAL
        EscalationLevel.RETARD_2 -> CHANNEL_LATE
        else -> CHANNEL_REMINDER
    }

    private fun priorityFor(level: EscalationLevel) = when (level) {
        EscalationLevel.RETARD_1 -> NotificationCompat.PRIORITY_DEFAULT
        else -> NotificationCompat.PRIORITY_HIGH
    }

    private fun titleFor(level: EscalationLevel) = when (level) {
        EscalationLevel.RETARD_1 -> "Retard sur le programme"
        EscalationLevel.RETARD_2 -> "Deuxième jour de retard"
        EscalationLevel.CRITIQUE -> "Le programme décroche"
        EscalationLevel.A_JOUR -> ""
    }

    /** Un identifiant par niveau : un `CRITIQUE` remplace le `RETARD_2` au lieu de s'empiler. */
    private fun notificationIdFor(level: EscalationLevel) = ID_ESCALATION

    companion object {
        const val CHANNEL_REMINDER = "forge-rappels"
        const val CHANNEL_LATE = "forge-retard"
        const val CHANNEL_CRITICAL = "forge-critique"

        private const val ID_ESCALATION = 1001
        private const val ID_REMINDER = 1002
    }
}
