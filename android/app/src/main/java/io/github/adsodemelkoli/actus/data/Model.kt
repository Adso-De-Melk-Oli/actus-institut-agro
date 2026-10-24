package io.github.adsodemelkoli.actus.data

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/** Le fichier data/articles.json publié par le robot de collecte du site. */
@Serializable
data class Feed(
    val updatedAt: String? = null,
    val sources: Map<String, SourceStatus> = emptyMap(),
    val articles: List<Article> = emptyList(),
)

@Serializable
data class SourceStatus(
    val ok: Boolean = true,
    val count: Int = 0,
    val errors: List<String> = emptyList(),
)

@Serializable
data class Article(
    val id: String,
    val source: String,
    val kind: String = "actu",
    val title: String,
    val summary: String? = null,
    val date: String? = null,
    val endDate: String? = null,
    val place: String? = null,
    val url: String,
    val image: String? = null,
    val remote: Boolean = false,
    val firstSeen: String? = null,
    val seed: Boolean = false,
) {
    val isEvent: Boolean get() = kind == "agenda"

    /** Jour de publication (actu) ou de début (événement), à l'heure de Paris. */
    val day: LocalDate? get() = parseDay(date)
    val endDay: LocalDate? get() = parseDay(endDate) ?: day

    fun isUpcoming(today: LocalDate): Boolean = isEvent && (endDay?.let { !it.isBefore(today) } ?: false)
    fun isOn(today: LocalDate): Boolean {
        val d = day ?: return false
        return isEvent && !d.isAfter(today) && !(endDay ?: d).isBefore(today)
    }
    /** Sur un campus (pas à distance), comme dans le digest du matin. */
    val onCampus: Boolean get() = !remote && (place.isNullOrBlank() || CAMPUS.containsMatchIn(place))

    val school: School get() = School.of(source)

    /** Instant de tri : date de publication pour une actu, début pour un événement. */
    val sortInstant: Instant? get() = parseInstant(date)

    companion object {
        private val CAMPUS = Regex(
            "campus|gaillarde|lavalette|florac|institut agro|agrocampus|amphith|rennes|angers|dijon|montpellier|viala|saint[- ]brieuc",
            RegexOption.IGNORE_CASE,
        )

        fun parseDay(v: String?): LocalDate? = when {
            v.isNullOrBlank() -> null
            v.length == 10 -> runCatching { LocalDate.parse(v) }.getOrNull()
            else -> parseInstant(v)?.atZone(PARIS)?.toLocalDate()
        }

        fun parseInstant(v: String?): Instant? = when {
            v.isNullOrBlank() -> null
            v.length == 10 -> parseDay(v)?.atTime(12, 0)?.atZone(PARIS)?.toInstant()
            else -> runCatching { Instant.parse(v) }.getOrNull()
        }
    }
}

/** Les sources suivies, avec les couleurs du site et du digest. */
enum class School(val key: String, val label: String, val shortLabel: String, val color: Color) {
    INSTITUT("institut", "L'Institut Agro", "Institut Agro", Color(0xFF2F8F46)),
    MONTPELLIER("montpellier", "Institut Agro Montpellier", "Montpellier", Color(0xFFC9531F)),
    RENNES("rennes", "Institut Agro Rennes-Angers", "Rennes-Angers", Color(0xFF1F6FD1)),
    DIJON("dijon", "Institut Agro Dijon", "Dijon", Color(0xFF8E2B4E)),
    FONDATION("fondation", "Fondation de l'Institut Agro", "Fondation", Color(0xFF0E7F81)),
    AUTRE("", "Autre", "Autre", Color(0xFF6E6E73));

    companion object {
        val listed = entries.filter { it != AUTRE }
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: AUTRE
    }
}

val FeedJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

/** Les articles affichables, du plus récent au plus ancien (sans date en dernier). */
fun Feed.sorted(): List<Article> = articles
    .filter { it.title.isNotBlank() && it.url.isNotBlank() }
    .sortedWith(compareByDescending<Article, Instant?>(nullsFirst()) { it.sortInstant })
