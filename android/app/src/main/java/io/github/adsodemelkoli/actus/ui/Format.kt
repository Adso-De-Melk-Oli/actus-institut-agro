package io.github.adsodemelkoli.actus.ui

import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.PARIS
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val FR = Locale.FRENCH
private val DAY_LONG = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)
private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", FR)
private val DAY_MONTH_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy", FR)
private val MONTH_SHORT = DateTimeFormatter.ofPattern("MMM", FR)
private val WEEKDAY_SHORT = DateTimeFormatter.ofPattern("EEE", FR)
private val TIME = DateTimeFormatter.ofPattern("HH 'h' mm", FR)

/** « jeudi 5 novembre », ou « du 4 au 7 novembre » pour un événement sur plusieurs jours. */
fun Article.whenLabel(): String {
    val start = day ?: return ""
    val end = endDay ?: start
    if (end == start) return start.format(DAY_LONG)
    return if (start.month == end.month) "du ${start.dayOfMonth} au ${end.format(DAY_MONTH)}"
    else "du ${start.format(DAY_MONTH)} au ${end.format(DAY_MONTH)}"
}

/** Date d'une actu, relative quand elle est récente. */
fun Article.relativeLabel(today: LocalDate): String {
    val d = day ?: return "Date non communiquée"
    val days = ChronoUnit.DAYS.between(d, today)
    return when {
        days == 0L -> "Aujourd'hui"
        days == 1L -> "Hier"
        days in 2..6 -> "Il y a $days jours"
        d.year == today.year -> d.format(DAY_MONTH)
        else -> d.format(DAY_MONTH_YEAR)
    }
}

/** Titre de groupe dans l'agenda. */
fun dayHeader(d: LocalDate, today: LocalDate): String = when (d) {
    today -> "Aujourd'hui"
    today.plusDays(1) -> "Demain"
    else -> d.format(DAY_LONG).replaceFirstChar { it.uppercase() }
}

fun monthShort(d: LocalDate): String = d.format(MONTH_SHORT).trimEnd('.').uppercase()
fun weekdayShort(d: LocalDate): String = d.format(WEEKDAY_SHORT)

fun updatedLabel(iso: String?, today: LocalDate): String {
    val t = runCatching { Instant.parse(iso) }.getOrNull() ?: return "Pas encore de relevé"
    val z = t.atZone(PARIS)
    return if (z.toLocalDate() == today) "Relevé aujourd'hui à ${z.format(TIME)}"
    else "Relevé le ${z.toLocalDate().format(DAY_MONTH)} à ${z.format(TIME)}"
}
