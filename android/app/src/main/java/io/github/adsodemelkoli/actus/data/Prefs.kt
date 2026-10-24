package io.github.adsodemelkoli.actus.data

import android.content.Context

/** Réglages et mémoire des notifications (contenus déjà signalés, dernier résumé du matin). */
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
}
