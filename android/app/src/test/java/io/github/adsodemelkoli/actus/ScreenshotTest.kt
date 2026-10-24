package io.github.adsodemelkoli.actus

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import io.github.adsodemelkoli.actus.data.Feed
import io.github.adsodemelkoli.actus.data.FeedJson
import io.github.adsodemelkoli.actus.data.SourceStatus
import io.github.adsodemelkoli.actus.ui.Actions
import io.github.adsodemelkoli.actus.ui.AgendaView
import io.github.adsodemelkoli.actus.ui.ActusApp
import io.github.adsodemelkoli.actus.ui.ActusTheme
import io.github.adsodemelkoli.actus.ui.Brand
import io.github.adsodemelkoli.actus.ui.SettingsState
import io.github.adsodemelkoli.actus.ui.Tab
import io.github.adsodemelkoli.actus.ui.UiState
import io.github.adsodemelkoli.actus.ui.UpdateState
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** Captures d'écran générées sans téléphone (Paparazzi), relues pour vérifier le rendu. */
class ScreenshotTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_6, theme = "android:Theme.Material.Light.NoActionBar")

    private val feed: Feed = FeedJson.decodeFromString(Feed.serializer(), javaClass.getResource("/articles.json")!!.readText())
    private val today = LocalDate.of(2026, 11, 5)
    private val settings = SettingsState(version = "1.0.1")

    private fun shot(tab: Tab, dark: Boolean = false, state: UiState = UiState(feed = feed), s: SettingsState = settings) =
        paparazzi.snapshot { ActusTheme(dark = dark) { ActusApp(state, s, tab, today, Actions()) } }

    @Test fun fil() = shot(Tab.FEED)
    @Test fun filSombre() = shot(Tab.FEED, dark = true)
    @Test fun filDijon() = shot(Tab.FEED, state = UiState(feed = feed, filter = "dijon"))
    @Test fun filRecherche() = shot(Tab.FEED, state = UiState(feed = feed, searchOpen = true, query = "colloque"))
    @Test fun agenda() = shot(Tab.AGENDA)
    @Test fun agendaSombre() = shot(Tab.AGENDA, dark = true)
    @Test fun reglages() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5200))
        shot(Tab.SETTINGS, s = settings.copy(canNotify = false, builtOn = "24 octobre 2026", update = UpdateState(latest = "1.0.4")))
    }
    @Test fun reglagesMiseAJour() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5200))
        shot(Tab.SETTINGS, dark = true, s = settings.copy(builtOn = "24 octobre 2026", update = UpdateState(latest = "1.0.9", downloadUrl = "https://example.org/a.apk")))
    }
    @Test fun horsLigne() = shot(Tab.FEED, state = UiState(feed = feed, offline = true, error = "Pas de connexion Internet."))
    @Test fun vide() = shot(Tab.FEED, state = UiState())
    @Test fun echecChargement() = shot(Tab.FEED, state = UiState(offline = true, error = "Pas de connexion Internet."))
    @Test fun echecChargementAgenda() = shot(Tab.AGENDA, state = UiState(offline = true, error = "Le serveur est momentanément indisponible."))

    private val panne = feed.copy(sources = feed.sources + mapOf("dijon" to SourceStatus(ok = false, count = 28, problem = "le site n'a pas répondu à temps")))
    @Test fun panneUneEcole() = shot(Tab.FEED, state = UiState(feed = panne))
    @Test fun panneDeuxEcoles() = shot(Tab.FEED, dark = true, state = UiState(feed = panne.copy(sources = panne.sources + ("rennes" to SourceStatus(ok = false, count = 8, problem = "son agenda n'a pas pu être lu : le site est en panne ou en maintenance")))))
    @Test fun reglagesPanne() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5600))
        shot(Tab.SETTINGS, state = UiState(feed = panne, followed = setOf("a", "b")), s = settings.copy(update = UpdateState(latest = "1.0.9", latestCode = 9, downloadUrl = "u", downloading = true, progress = 0.42f)))
    }

    // badge « Nouveau » : la une, un événement à venir, une actu de la liste
    private val nouveaux = setOf("dij-envie-de-decouvrir-nos-formations-rencontrons-nous", "fon-ag-journee-chaire-futurs-elevage", "mtp-concours-vignerons-terroirs-avenir-2027")
    private val suivis = setOf("fon-ag-journee-chaire-futurs-elevage", "ia-ag-sustainable-life-sciences-ells")
    @Test fun filNouveautes() = shot(Tab.FEED, state = UiState(feed = feed, newIds = nouveaux + feed.articles.first().id, followed = suivis))
    @Test fun filNouveautesSombre() = shot(Tab.FEED, dark = true, state = UiState(feed = feed, newIds = nouveaux + feed.articles.first().id, followed = suivis))
    @Test fun agendaSuivis() = shot(Tab.AGENDA, state = UiState(feed = feed, followed = suivis, newIds = nouveaux))
    @Test fun agendaMois() = shot(Tab.AGENDA, state = UiState(feed = feed, followed = suivis, agendaView = AgendaView.MONTH, selectedDay = today))
    @Test fun agendaMoisSombre() = shot(Tab.AGENDA, dark = true, state = UiState(feed = feed, followed = suivis, agendaView = AgendaView.MONTH, selectedDay = LocalDate.of(2026, 11, 19)))
    @Test fun agendaMoisJourVide() = shot(Tab.AGENDA, state = UiState(feed = feed, agendaView = AgendaView.MONTH, selectedDay = LocalDate.of(2026, 11, 10)))
    @Test fun majAutorisation() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5600))
        shot(Tab.SETTINGS, dark = true, s = settings.copy(update = UpdateState(latest = "1.0.9", latestCode = 9, downloadUrl = "u", needsPermission = true)))
    }
    @Test fun majEchec() {
        paparazzi.unsafeUpdateConfig(deviceConfig = DeviceConfig.PIXEL_6.copy(screenHeight = 5600))
        shot(Tab.SETTINGS, s = settings.copy(update = UpdateState(latest = "1.0.9", latestCode = 9, downloadUrl = "u", problem = "La connexion a été interrompue.")))
    }

    /** Cartes en gros plan : étoile, mention « Nouveau », pilule Bêta. */
    @Test fun cartes() {
        val a = { id: String -> feed.articles.first { it.id == id } }
        paparazzi.snapshot {
            ActusTheme {
                androidx.compose.foundation.layout.Column(
                    Modifier.background(androidx.compose.material3.MaterialTheme.colorScheme.background).padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    io.github.adsodemelkoli.actus.ui.BetaPill()
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        io.github.adsodemelkoli.actus.ui.UpcomingCard(a("fon-ag-journee-chaire-futurs-elevage"), {}, isNew = true, followed = true)
                    }
                    io.github.adsodemelkoli.actus.ui.UpcomingCard(a("ia-ag-sustainable-life-sciences-ells"), {}, followed = false)
                    io.github.adsodemelkoli.actus.ui.ArticleRow(a("fon-ag-colloque-risque-climatique"), today, {}, {}, isNew = true, followed = false)
                    io.github.adsodemelkoli.actus.ui.ArticleRow(a("mtp-concours-vignerons-terroirs-avenir-2027"), today, {}, {}, isNew = true)
                }
            }
        }
    }

    @Test fun icone() = paparazzi.snapshot {
        Row(Modifier.background(Color.White).padding(24.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            listOf(CircleShape, RoundedCornerShape(28.dp), RoundedCornerShape(6.dp)).forEach { shape ->
                Box(Modifier.size(108.dp).clip(shape).background(Brand)) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(108.dp))
                }
            }
            Image(painterResource(R.drawable.logo_institut_agro), null, Modifier.size(108.dp))
        }
    }
}
