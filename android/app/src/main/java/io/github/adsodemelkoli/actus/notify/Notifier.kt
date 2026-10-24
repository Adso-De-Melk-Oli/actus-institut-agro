package io.github.adsodemelkoli.actus.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.adsodemelkoli.actus.MainActivity
import io.github.adsodemelkoli.actus.R
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Reminder
import io.github.adsodemelkoli.actus.ui.whenLabel

object Notifier {
    const val CH_NEW = "nouveautes"
    const val CH_TODAY = "aujourdhui"
    const val CH_REMIND = "rappels"
    private const val ID_NEW = 1
    private const val ID_TODAY = 2
    private const val BRAND = 0xFFF8AC00.toInt()

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_NEW, "Nouvelles publications", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Une école ou la Fondation publie une actu ou un événement" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_TODAY, "Aujourd'hui sur les campus", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Le matin, les événements du jour sur les campus" },
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_REMIND, "Rappels des événements suivis", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "La veille et une semaine avant un événement que vous suivez" },
        )
    }

    fun canNotify(context: Context): Boolean =
        (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** Ouvre le contenu s'il est seul, sinon l'application. */
    private fun tapIntent(context: Context, single: Article?): PendingIntent {
        val intent = if (single != null) Intent(Intent.ACTION_VIEW, Uri.parse(single.url))
        else Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(context, single?.id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun postNew(context: Context, items: List<Article>) {
        if (items.isEmpty() || !canNotify(context)) return
        val title = if (items.size == 1) items[0].school.label
        else "${items.size} nouvelles publications"
        val text = if (items.size == 1) items[0].title else items.joinToString(" · ") { it.school.shortLabel }
        val style = if (items.size == 1) NotificationCompat.BigTextStyle().bigText(items[0].title)
        else NotificationCompat.InboxStyle().also { s ->
            items.take(6).forEach { s.addLine("${it.school.shortLabel} — ${it.title}") }
            if (items.size > 6) s.setSummaryText("et ${items.size - 6} autres")
        }
        val n = NotificationCompat.Builder(context, CH_NEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setNumber(items.size)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(context, items.singleOrNull()))
            .build()
        notify(context, ID_NEW, n)
    }

    fun postToday(context: Context, events: List<Article>) {
        if (events.isEmpty() || !canNotify(context)) return
        val title = if (events.size == 1) "Aujourd'hui sur les campus" else "${events.size} événements aujourd'hui sur les campus"
        val style = NotificationCompat.InboxStyle().also { s ->
            events.take(6).forEach { s.addLine("${it.school.shortLabel} — ${it.title}") }
        }
        val first = events.first()
        val n = NotificationCompat.Builder(context, CH_TODAY)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(if (events.size == 1) "${first.title} · ${first.place ?: first.whenLabel()}" else events.joinToString(" · ") { it.title })
            .setStyle(if (events.size == 1) NotificationCompat.BigTextStyle().bigText("${first.title}\n${first.place ?: ""}".trim()) else style)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(context, events.singleOrNull()))
            .build()
        notify(context, ID_TODAY, n)
    }

    /** Un rappel par événement : « Demain » ou « Dans 7 jours », avec la date et le lieu. */
    fun postReminder(context: Context, r: Reminder) {
        if (!canNotify(context)) return
        val a = r.article
        val left = a.day?.let { java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.now(io.github.adsodemelkoli.actus.data.PARIS), it) } ?: r.daysBefore.toLong()
        val title = when (left) { 1L -> "Demain"; 7L -> "Dans une semaine"; else -> "Dans $left jours" } + " : ${a.title}"
        val where = if (a.remote) "À distance" else a.place?.takeIf { it.isNotBlank() } ?: a.school.label
        val text = "${a.whenLabel().replaceFirstChar { it.uppercase() }} · $where"
        val n = NotificationCompat.Builder(context, CH_REMIND)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(BRAND)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nÉvénement suivi · ${a.school.label}"))
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(tapIntent(context, a))
            .build()
        notify(context, 1000 + (r.key.hashCode() and 0x7fffffff) % 100_000, n)
    }

    @Suppress("MissingPermission")
    private fun notify(context: Context, id: Int, n: android.app.Notification) {
        if (canNotify(context)) NotificationManagerCompat.from(context).notify(id, n)
    }
}
