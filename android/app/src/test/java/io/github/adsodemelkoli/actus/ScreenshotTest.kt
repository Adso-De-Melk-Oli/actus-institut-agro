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
import io.github.adsodemelkoli.actus.ui.Actions
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
    @Test fun horsLigne() = shot(Tab.FEED, state = UiState(feed = feed, offline = true, error = "timeout"))
    @Test fun vide() = shot(Tab.FEED, state = UiState())

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
