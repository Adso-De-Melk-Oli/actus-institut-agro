package io.github.adsodemelkoli.actus.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lit les données publiées par le site (mises à jour chaque matin par le robot GitHub) et en garde
 * une copie locale pour la consultation hors ligne.
 */
class Repository(private val context: Context) {

    data class Result(val feed: Feed, val fromCache: Boolean, val error: String? = null)

    private val cache: File get() = File(context.filesDir, "articles.json")

    /** Dernière copie locale, ou null si l'application n'a encore jamais rien téléchargé. */
    suspend fun cached(): Feed? = withContext(Dispatchers.IO) {
        runCatching { FeedJson.decodeFromString(Feed.serializer(), cache.readText()) }.getOrNull()
    }

    suspend fun refresh(): Result = withContext(Dispatchers.IO) {
        var lastError: String? = null
        for (url in SOURCES) {
            try {
                val text = download(url)
                val feed = FeedJson.decodeFromString(Feed.serializer(), text)
                cache.writeText(text)
                return@withContext Result(feed, fromCache = false)
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
            }
        }
        val local = cached()
        if (local != null) Result(local, fromCache = true, error = lastError)
        else Result(Feed(), fromCache = true, error = lastError ?: "Aucune donnée")
    }

    private fun download(url: String): String {
        val conn = URL("$url?t=${System.currentTimeMillis() / 60_000}").openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 20_000
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("Cache-Control", "no-cache")
        try {
            if (conn.responseCode !in 200..299) throw IllegalStateException("Erreur ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Site GitHub Pages, puis copie brute du dépôt en secours. */
        val SOURCES = listOf(
            "https://adso-de-melk-oli.github.io/actus-institut-agro/data/articles.json",
            "https://raw.githubusercontent.com/Adso-De-Melk-Oli/actus-institut-agro/main/data/articles.json",
        )
        const val SITE = "https://adso-de-melk-oli.github.io/actus-institut-agro/"
    }
}
