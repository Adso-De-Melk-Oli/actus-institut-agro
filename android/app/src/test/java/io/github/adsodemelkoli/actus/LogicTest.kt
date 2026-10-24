package io.github.adsodemelkoli.actus

import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Feed
import io.github.adsodemelkoli.actus.data.FeedJson
import io.github.adsodemelkoli.actus.notify.CheckWorker
import io.github.adsodemelkoli.actus.ui.Actions
import io.github.adsodemelkoli.actus.ui.longPress
import io.github.adsodemelkoli.actus.ui.relativeLabel
import io.github.adsodemelkoli.actus.ui.whenLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import io.github.adsodemelkoli.actus.data.HttpStatusException
import io.github.adsodemelkoli.actus.data.Problems
import io.github.adsodemelkoli.actus.data.SourceStatus
import io.github.adsodemelkoli.actus.data.downSources
import io.github.adsodemelkoli.actus.data.dueReminders
import io.github.adsodemelkoli.actus.data.eventsOn
import io.github.adsodemelkoli.actus.data.expiredFollows
import io.github.adsodemelkoli.actus.data.latestNews
import io.github.adsodemelkoli.actus.data.monthCells
import io.github.adsodemelkoli.actus.data.newSince
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

class LogicTest {
    private val feed: Feed = FeedJson.decodeFromString(Feed.serializer(), javaClass.getResource("/articles.json")!!.readText())
    private fun a(id: String) = feed.articles.first { it.id == id }

    @Test fun lectureDesDonnees() {
        assertTrue(feed.articles.size > 40)
        assertTrue(feed.articles.any { it.source == "dijon" })
    }

    @Test fun evenementsDuJourSurLesCampus() {
        val ids = CheckWorker.todayOnCampus(feed, LocalDate.of(2026, 11, 5)).map { it.id }.toSet()
        assertTrue("colloque Montpellier", "mtp-colloque-couvrir-pour-produire" in ids)
        assertTrue("assemblée ELLS du 4 au 7", "ia-ag-ag-ells-universities" in ids)
        assertFalse("Controverse est à distance", "mtp-journee-scientifique-controverse" in ids)
    }

    @Test fun nouveautes() {
        val all = feed.articles.map { it.id }.toSet()
        assertTrue(CheckWorker.newItems(feed, all, setOf("montpellier", "dijon")).isEmpty())
        val fresh = CheckWorker.newItems(feed, all - "mtp-concours-vignerons-terroirs-avenir-2027", setOf("montpellier"))
        assertEquals(listOf("mtp-concours-vignerons-terroirs-avenir-2027"), fresh.map { it.id })
        // école non suivie : pas de notification
        assertTrue(CheckWorker.newItems(feed, all - "mtp-concours-vignerons-terroirs-avenir-2027", setOf("rennes")).isEmpty())
        // historique d'une nouvelle source : jamais signalé
        val dijon = feed.articles.first { it.source == "dijon" && it.seed }
        assertTrue(CheckWorker.newItems(feed, all - dijon.id, setOf("dijon")).isEmpty())
    }

    @Test fun appuiLong() {
        val done = mutableListOf<String>()
        val actions = Actions(onShare = { done += "partage:" + it.id }, onAddToCalendar = { done += "agenda:" + it.id })
        val press = actions.longPress(LocalDate.of(2026, 11, 5))
        press(a("ia-ag-ag-ells-universities"))                  // événement en cours : agenda du téléphone
        press(a("mtp-ag-congres-acarologie-2026"))              // événement passé (juillet) : partage
        press(a("mtp-concours-vignerons-terroirs-avenir-2027")) // actu : partage
        assertEquals(
            listOf("agenda:ia-ag-ag-ells-universities", "partage:mtp-ag-congres-acarologie-2026", "partage:mtp-concours-vignerons-terroirs-avenir-2027"),
            done,
        )
    }

    @Test fun libellesDeDates() {
        assertEquals("du 4 au 7 novembre", a("ia-ag-ag-ells-universities").whenLabel())
        val t = Article(id = "x", source = "montpellier", title = "t", url = "u", date = "2026-11-04T10:00:00Z")
        assertEquals("Hier", t.relativeLabel(LocalDate.of(2026, 11, 5)))
        assertEquals("Aujourd'hui", t.relativeLabel(LocalDate.of(2026, 11, 4)))
        assertTrue(a("ia-ag-ag-ells-universities").isOn(LocalDate.of(2026, 11, 6)))
        assertFalse(a("ia-ag-ag-ells-universities").isOn(LocalDate.of(2026, 11, 8)))
    }

    // --- Événements suivis et rappels (journée de la chaire Futurs de l'élevage : jeudi 19 novembre) ---
    private val chaire = "fon-ag-journee-chaire-futurs-elevage"
    private fun due(today: String, followedOn: String = "2026-11-01", sent: Set<String> = emptySet(), at: String = "10:00", veille: Boolean = true, semaine: Boolean = true) =
        dueReminders(feed, mapOf(chaire to LocalDate.parse(followedOn)), sent, LocalDate.parse(today), LocalTime.parse(at), veille, semaine).map { it.key }

    @Test fun rappels() {
        assertEquals("une semaine avant", listOf("$chaire:7"), due("2026-11-12"))
        assertEquals("déjà envoyé", emptyList<String>(), due("2026-11-12", sent = setOf("$chaire:7")))
        assertEquals("rien à J-8", emptyList<String>(), due("2026-11-11"))
        assertEquals("rattrapage si le téléphone était éteint à J-7", listOf("$chaire:7"), due("2026-11-14"))
        assertEquals("la veille", listOf("$chaire:1"), due("2026-11-18", sent = setOf("$chaire:7")))
        assertEquals("pas la nuit", emptyList<String>(), due("2026-11-18", at = "06:30"))
        assertEquals("pas le jour même", emptyList<String>(), due("2026-11-19"))
        assertEquals("suivi la veille : pas de rappel", emptyList<String>(), due("2026-11-18", followedOn = "2026-11-18"))
        assertEquals("suivi à J-4 : seulement la veille", emptyList<String>(), due("2026-11-15", followedOn = "2026-11-15"))
        assertEquals("suivi à J-4, la veille", listOf("$chaire:1"), due("2026-11-18", followedOn = "2026-11-15"))
        assertEquals("rappel de la veille désactivé", emptyList<String>(), due("2026-11-18", sent = setOf("$chaire:7"), veille = false))
        assertEquals("rappel d'une semaine désactivé", emptyList<String>(), due("2026-11-12", semaine = false))
        // une actu ne se « suit » pas
        assertTrue(dueReminders(feed, mapOf("mtp-concours-vignerons-terroirs-avenir-2027" to LocalDate.of(2026, 1, 1)), emptySet(), LocalDate.of(2026, 11, 12), LocalTime.NOON, true, true).isEmpty())
    }

    @Test fun suivisPerimes() {
        val f = mapOf("ren-ag-exposition-mycologique" to LocalDate.of(2026, 10, 1), "inconnu" to LocalDate.of(2026, 10, 1), "mtp-colloque-couvrir-pour-produire" to LocalDate.of(2026, 10, 1))
        assertEquals(setOf("ren-ag-exposition-mycologique", "inconnu"), expiredFollows(feed, f, LocalDate.of(2026, 11, 5)))
        // relevé vide (téléphone hors ligne au premier lancement) : on n'oublie rien
        assertTrue(expiredFollows(Feed(), mapOf("inconnu" to LocalDate.of(2026, 10, 1)), LocalDate.of(2026, 11, 5)).isEmpty())
    }

    @Test fun nouveauDepuisLaDerniereVisite() {
        val all = feed.articles.map { it.id }.toSet()
        assertTrue("jamais quitté l'application : rien de nouveau", newSince(feed, null).isEmpty())
        assertEquals(setOf("mtp-colloque-couvrir-pour-produire"), newSince(feed, all - "mtp-colloque-couvrir-pour-produire"))
        val seed = feed.articles.first { it.seed }.id
        assertTrue("historique d'une nouvelle source", newSince(feed, all - seed).isEmpty())
    }

    @Test fun calendrierDuMois() {
        val nov = monthCells(YearMonth.of(2026, 11))
        assertEquals(42, nov.size)
        assertEquals("le 1er novembre 2026 est un dimanche", LocalDate.of(2026, 11, 1), nov[6])
        assertEquals(LocalDate.of(2026, 11, 30), nov[35])
        assertTrue(nov.subList(36, 42).all { it == null })
        val fev = monthCells(YearMonth.of(2026, 2))
        assertEquals(35, fev.size)
        val juin = monthCells(YearMonth.of(2026, 6)) // commence un lundi
        assertEquals(LocalDate.of(2026, 6, 1), juin[0])
        val ids = eventsOn(feed.articles, LocalDate.of(2026, 11, 6)).map { it.id }.toSet()
        assertEquals(setOf("ia-ag-ag-ells-universities", "ia-ag-sustainable-life-sciences-ells"), ids)
        assertEquals(3 + 1, eventsOn(feed.articles, LocalDate.of(2026, 11, 5)).size)
    }

    @Test fun widgetDernieresActus() {
        val n = latestNews(feed)
        assertEquals(10, n.size)
        assertTrue(n.none { it.isEvent })
        assertTrue(n.zipWithNext().all { (a, b) -> (a.sortInstant ?: java.time.Instant.MIN) >= (b.sortInstant ?: java.time.Instant.MIN) })
    }

    @Test fun messagesClairs() {
        assertEquals("Pas de connexion Internet.", Problems.plain(java.net.UnknownHostException("adso-de-melk-oli.github.io")))
        assertEquals("Le serveur met trop de temps à répondre.", Problems.plain(java.net.SocketTimeoutException("Read timed out")))
        assertEquals("Le serveur est momentanément indisponible.", Problems.plain(HttpStatusException(503)))
        assertEquals("Les données sont introuvables pour le moment.", Problems.plain(HttpStatusException(404)))
        assertEquals("Les données reçues sont illisibles.", Problems.plain(kotlinx.serialization.SerializationException("Unexpected JSON token")))
        assertEquals("Chargement impossible pour le moment.", Problems.plain(null))
        // aucun message ne laisse passer un code, une adresse ou un nom technique
        listOf(java.net.UnknownHostException("x.io"), HttpStatusException(500), java.io.IOException("ECONNRESET"), RuntimeException("NullPointer"))
            .map { Problems.plain(it) }.forEach { m -> assertFalse(m, Regex("""\d{3}|http|\.io|Exception|ECONN""").containsMatchIn(m)) }
    }

    @Test fun sourcesEnPanne() {
        val panne = feed.copy(sources = feed.sources + mapOf(
            "dijon" to SourceStatus(ok = false, count = 28, problem = "le site n'a pas répondu à temps"),
            "rennes" to SourceStatus(ok = false, count = 8, errors = listOf("actus : 404 sur https://x")),
        ))
        assertEquals(listOf("rennes", "dijon"), panne.downSources().map { it.first.key })
        assertEquals("le site n'a pas répondu à temps", panne.downSources().last().second.plainProblem)
        assertEquals("ancien relevé sans explication", "le site n'a pas pu être lu", panne.downSources().first().second.plainProblem)
        assertTrue(feed.downSources().isEmpty())
        // champ « problem » lu depuis le JSON du robot
        val json = """{"sources":{"dijon":{"ok":false,"count":3,"errors":["actus : 404 sur u"],"problem":"la page lue jusqu'ici n'existe plus","failDays":2}},"articles":[]}"""
        val f = FeedJson.decodeFromString(Feed.serializer(), json)
        assertEquals("la page lue jusqu'ici n'existe plus", f.sources["dijon"]!!.plainProblem)
        assertEquals(2, f.sources["dijon"]!!.failDays)
    }
}
