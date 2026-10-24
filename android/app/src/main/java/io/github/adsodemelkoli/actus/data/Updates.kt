package io.github.adsodemelkoli.actus.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URL

/** Lit la dernière version publiée dans la release « android » du dépôt GitHub. */
object Updates {
    data class Latest(val versionName: String, val versionCode: Int, val url: String)

    @Serializable private data class Release(val body: String? = null, val assets: List<Asset> = emptyList())
    @Serializable private data class Asset(val name: String, val browser_download_url: String)

    private const val API = "https://api.github.com/repos/Adso-De-Melk-Oli/actus-institut-agro/releases/tags/android"

    suspend fun latest(): Latest? = withContext(Dispatchers.IO) {
        val conn = URL(API).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        try {
            if (conn.responseCode != 200) return@withContext null
            val r = FeedJson.decodeFromString(Release.serializer(), conn.inputStream.bufferedReader().use { it.readText() })
            // les notes de version commencent par « Version 1.0.N »
            val m = Regex("""Version (1\.0\.(\d+))""").find(r.body ?: "") ?: return@withContext null
            val apk = r.assets.firstOrNull { it.name.endsWith(".apk") } ?: return@withContext null
            Latest(m.groupValues[1], m.groupValues[2].toInt(), apk.browser_download_url)
        } finally {
            conn.disconnect()
        }
    }
}
