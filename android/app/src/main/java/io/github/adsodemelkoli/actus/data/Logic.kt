package io.github.adsodemelkoli.actus.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Règles de l'application sans dépendance à Android, vérifiées par les tests. */

/** Contenus apparus depuis la dernière visite (l'historique d'une nouvelle source n'est jamais « nouveau »). */
fun newSince(feed: Feed, visit: Set<String>?): Set<String> =
    if (visit == null) emptySet() else feed.articles.filter { it.id !in visit && !it.seed }.map { it.id }.toSet()

/** Un rappel d'événement suivi : `daysBefore` vaut 1 (la veille) ou 7 (une semaine avant). */
data class Reminder(val article: Article, val daysBefore: Int) {
    val key: String get() = "${article.id}:$daysBefore"
}

/**
 * Rappels à envoyer maintenant : la veille, et une semaine avant (jusqu'à J-2 si le téléphone était éteint ce jour-là).
 * Jamais pour un événement suivi le jour même du rappel ou après, jamais deux fois, jamais la nuit.
 */
fun dueReminders(
    feed: Feed, follows: Map<String, LocalDate>, sent: Set<String>, today: LocalDate, time: LocalTime,
    dayBefore: Boolean, weekBefore: Boolean,
): List<Reminder> {
    if (time.isBefore(LocalTime.of(8, 0)) || time.isAfter(LocalTime.of(21, 0))) return emptyList()
    val byId = feed.articles.associateBy { it.id }
    return follows.mapNotNull { (id, followedOn) ->
        val a = byId[id]?.takeIf { it.isEvent } ?: return@mapNotNull null
        val day = a.day ?: return@mapNotNull null
        val left = ChronoUnit.DAYS.between(today, day)
        val r = when {
            dayBefore && left == 1L && followedOn.isBefore(day.minusDays(1)) -> Reminder(a, 1)
            weekBefore && left in 2..7 && followedOn.isBefore(day.minusDays(7)) -> Reminder(a, 7)
            else -> null
        }
        r?.takeIf { it.key !in sent }
    }.sortedBy { it.article.day }
}

/** Suivis à oublier : événements terminés depuis plus d'une semaine ou disparus du relevé. */
fun expiredFollows(feed: Feed, follows: Map<String, LocalDate>, today: LocalDate): Set<String> {
    val byId = feed.articles.associateBy { it.id }
    return follows.keys.filter { id ->
        val a = byId[id]
        a == null && feed.articles.isNotEmpty() || (a?.endDay?.isBefore(today.minusDays(7)) ?: false)
    }.toSet()
}

/** Cases d'un mois, semaines du lundi au dimanche ; null pour les cases hors du mois. */
fun monthCells(month: YearMonth): List<LocalDate?> {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - DayOfWeek.MONDAY.value
    val days = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val cells = List<LocalDate?>(lead) { null } + days
    return cells + List((7 - cells.size % 7) % 7) { null }
}

/** Événements qui ont lieu ce jour-là (y compris ceux sur plusieurs jours). */
fun eventsOn(events: List<Article>, d: LocalDate): List<Article> = events.filter { a ->
    val start = a.day ?: return@filter false
    a.isEvent && !d.isBefore(start) && !d.isAfter(a.endDay ?: start)
}

/** Dernières actus pour le widget. */
fun latestNews(feed: Feed, n: Int = 10): List<Article> = feed.sorted().filter { !it.isEvent }.take(n)
