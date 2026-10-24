package io.github.adsodemelkoli.actus.data

import android.content.Context
import java.time.LocalDate

/** Réglages et mémoire de l'application (contenus déjà signalés, événements suivis, dernière visite). */
class Prefs(context: Context) {
    private val p = context.getSharedPreferences("actus", Context.MODE_PRIVATE)

    var notifyNew: Boolean
        get() = p.getBoolean("notifyNew", true)
        set(v) = p.edit().putBoolean("notifyNew", v).apply()

    var notifyToday: Boolean
        get() = p.getBoolean("notifyToday", true)
        set(v) = p.edit().putBoolean("notifyToday", v).apply()

    /** Écoles dont les nouveautés sont signalées (toutes par défaut). */
    var notifySchools: Set<String>
        get() = p.getStringSet("notifySchools", null) ?: School.listed.map { it.key }.toSet()
        set(v) = p.edit().putStringSet("notifySchools", v).apply()

    /** Identifiants des contenus déjà connus : seuls les autres déclenchent une notification. */
    var seenIds: Set<String>
        get() = p.getStringSet("seenIds", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("seenIds", v).apply()

    val initialized: Boolean get() = p.contains("seenIds")

    var lastMorning: String?
        get() = p.getString("lastMorning", null)
        set(v) = p.edit().putString("lastMorning", v).apply()

    var askedPermission: Boolean
        get() = p.getBoolean("askedPermission", false)
        set(v) = p.edit().putBoolean("askedPermission", v).apply()

    // --- Badge « Nouveau » : contenus connus à la fin de la dernière visite dans l'application ---

    /** null tant que l'application n'a jamais été quittée : rien n'est alors marqué « Nouveau ». */
    var visitIds: Set<String>?
        get() = p.getStringSet("visitIds", null)
        set(v) = p.edit().putStringSet("visitIds", v).apply()

    // --- Événements suivis et rappels ---

    /** Événements suivis, avec le jour où ils l'ont été (un rappel n'est jamais antérieur au suivi). */
    var follows: Map<String, LocalDate>
        get() = (p.getStringSet("follows", emptySet()) ?: emptySet()).mapNotNull { line ->
            val i = line.lastIndexOf('|')
            if (i <= 0) null else runCatching { line.substring(0, i) to LocalDate.parse(line.substring(i + 1)) }.getOrNull()
        }.toMap()
        set(v) = p.edit().putStringSet("follows", v.map { (id, d) -> "$id|$d" }.toSet()).apply()

    var remindDayBefore: Boolean
        get() = p.getBoolean("remindDayBefore", true)
        set(v) = p.edit().putBoolean("remindDayBefore", v).apply()

    var remindWeekBefore: Boolean
        get() = p.getBoolean("remindWeekBefore", true)
        set(v) = p.edit().putBoolean("remindWeekBefore", v).apply()

    /** Rappels déjà envoyés, notés « id:1 » (veille) ou « id:7 » (semaine). */
    var remindersSent: Set<String>
        get() = p.getStringSet("remindersSent", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("remindersSent", v).apply()

    // --- Mise à jour en attente d'autorisation d'installation ---
    var pendingApk: String?
        get() = p.getString("pendingApk", null)
        set(v) = p.edit().putString("pendingApk", v).apply()
}
