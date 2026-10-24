package io.github.adsodemelkoli.actus

import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Feed
import io.github.adsodemelkoli.actus.data.FeedJson
import io.github.adsodemelkoli.actus.notify.CheckWorker
import io.github.adsodemelkoli.actus.ui.relativeLabel
import io.github.adsodemelkoli.actus.ui.whenLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

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

    @Test fun libellesDeDates() {
        assertEquals("du 4 au 7 novembre", a("ia-ag-ag-ells-universities").whenLabel())
        val t = Article(id = "x", source = "montpellier", title = "t", url = "u", date = "2026-11-04T10:00:00Z")
        assertEquals("Hier", t.relativeLabel(LocalDate.of(2026, 11, 5)))
        assertEquals("Aujourd'hui", t.relativeLabel(LocalDate.of(2026, 11, 4)))
        assertTrue(a("ia-ag-ag-ells-universities").isOn(LocalDate.of(2026, 11, 6)))
        assertFalse(a("ia-ag-ag-ells-universities").isOn(LocalDate.of(2026, 11, 8)))
    }
}
