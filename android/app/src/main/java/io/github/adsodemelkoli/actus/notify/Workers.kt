package io.github.adsodemelkoli.actus.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Feed
import io.github.adsodemelkoli.actus.data.PARIS
import io.github.adsodemelkoli.actus.data.Prefs
import io.github.adsodemelkoli.actus.data.Repository
import io.github.adsodemelkoli.actus.widget.AgendaWidget
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/**
 * Vérifie les données du site à intervalles réguliers : signale les contenus jamais vus, et chaque matin
 * (à partir de 7 h 15) les événements du jour sur les campus. Met aussi à jour le widget.
 */
class CheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = Repository(applicationContext).refresh()
        if (result.fromCache && result.feed.articles.isEmpty()) return Result.retry()
        check(applicationContext, result.feed)
        AgendaWidget.refreshAll(applicationContext)
        return Result.success()
    }

    companion object {
        /** Logique commune au travail périodique et au rafraîchissement manuel. */
        fun check(context: Context, feed: Feed, now: ZonedDateTime = ZonedDateTime.now(PARIS)) {
            val prefs = Prefs(context)
            val ids = feed.articles.map { it.id }.toSet()
            // premier lancement : tout l'existant est considéré comme déjà vu
            if (!prefs.initialized) { prefs.seenIds = ids; return }
            val fresh = newItems(feed, prefs.seenIds, prefs.notifySchools)
            prefs.seenIds = ids
            if (prefs.notifyNew) Notifier.postNew(context, fresh)

            val today = now.toLocalDate()
            val weekday = today.dayOfWeek != java.time.DayOfWeek.SATURDAY && today.dayOfWeek != java.time.DayOfWeek.SUNDAY
            if (prefs.notifyToday && weekday && now.toLocalTime() >= LocalTime.of(7, 15) && prefs.lastMorning != today.toString()) {
                prefs.lastMorning = today.toString()
                Notifier.postToday(context, todayOnCampus(feed, today))
            }
        }

        fun newItems(feed: Feed, seen: Set<String>, schools: Set<String>): List<Article> =
            feed.articles.filter { it.id !in seen && !it.seed && it.source in schools }

        fun todayOnCampus(feed: Feed, today: LocalDate): List<Article> =
            feed.articles.filter { it.isOn(today) && it.onCampus }.sortedBy { it.title }

        private const val PERIODIC = "verification"
        private const val MORNING = "matin"

        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CheckWorker>(2, TimeUnit.HOURS, 30, TimeUnit.MINUTES).setConstraints(online).build(),
            )
            scheduleMorning(context, ExistingWorkPolicy.KEEP)
        }

        /** Un passage dédié juste après 7 h 15, pour le résumé du matin. */
        fun scheduleMorning(context: Context, policy: ExistingWorkPolicy) {
            val wm = WorkManager.getInstance(context)
            val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            val now = ZonedDateTime.now(PARIS)
            var next = now.toLocalDate().atTime(7, 16).atZone(PARIS)
            if (!next.isAfter(now)) next = next.plusDays(1)
            wm.enqueueUniqueWork(
                MORNING, policy,
                OneTimeWorkRequestBuilder<MorningWorker>()
                    .setInitialDelay(Duration.between(now, next).toMillis(), TimeUnit.MILLISECONDS)
                    .setConstraints(online).build(),
            )
        }
    }
}

/** Passage du matin ; il se reprogramme pour le lendemain. */
class MorningWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val result = Repository(applicationContext).refresh()
        CheckWorker.check(applicationContext, result.feed)
        AgendaWidget.refreshAll(applicationContext)
        // se reprogramme pour le lendemain, après la fin de ce passage
        CheckWorker.scheduleMorning(applicationContext, ExistingWorkPolicy.APPEND_OR_REPLACE)
        return Result.success()
    }
}
