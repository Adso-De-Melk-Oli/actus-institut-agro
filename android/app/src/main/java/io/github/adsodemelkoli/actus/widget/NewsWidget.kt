package io.github.adsodemelkoli.actus.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import io.github.adsodemelkoli.actus.MainActivity
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.PARIS
import io.github.adsodemelkoli.actus.data.Prefs
import io.github.adsodemelkoli.actus.data.Repository
import io.github.adsodemelkoli.actus.data.latestNews
import io.github.adsodemelkoli.actus.data.newSince
import io.github.adsodemelkoli.actus.ui.relativeLabel
import java.time.LocalDate

/** Widget d'écran d'accueil : les dernières actus, avec la mention « Nouveau » comme dans l'application. */
class NewsWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val feed = Repository(context).cached()
        val today = LocalDate.now(PARIS)
        val news = feed?.let { latestNews(it) }.orEmpty()
        val fresh = feed?.let { newSince(it, Prefs(context).visitIds) }.orEmpty()
        provideContent { GlanceTheme { Content(news, fresh, today) } }
    }

    @Composable
    private fun Content(news: List<Article>, fresh: Set<String>, today: LocalDate) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Actus", style = TextStyle(color = ColorProvider(Color(0xFFF8AC00)), fontWeight = FontWeight.Bold, fontSize = 16.sp))
                Text("  Dernières actus", style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 16.sp))
            }
            Spacer(GlanceModifier.height(8.dp))
            if (news.isEmpty()) {
                Text("Ouvrez l'application pour charger les actus.", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 13.sp))
            } else {
                LazyColumn {
                    items(news, itemId = { it.id.hashCode().toLong() }) { a -> NewsLine(a, a.id in fresh, today) }
                }
            }
        }
    }

    @Composable
    private fun NewsLine(a: Article, isNew: Boolean, today: LocalDate) {
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 5.dp)
                .clickable(actionStartActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.url)))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(GlanceModifier.width(4.dp).height(34.dp).background(ColorProvider(a.school.color)).cornerRadius(2.dp)) {}
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isNew) {
                        Text(
                            "NOUVEAU  ",
                            style = TextStyle(color = ColorProvider(Color(0xFFC77700)), fontSize = 11.sp, fontWeight = FontWeight.Bold),
                            maxLines = 1,
                        )
                    }
                    Text(
                        "${a.relativeLabel(today)} · ${a.school.shortLabel}",
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                    )
                }
                Text(a.title, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp), maxLines = 2)
            }
        }
    }
}

class NewsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewsWidget()
}

/** Met à jour les deux widgets s'ils sont posés sur l'écran d'accueil. */
object Widgets {
    suspend fun refreshAll(context: Context) {
        runCatching {
            val m = GlanceAppWidgetManager(context)
            if (m.getGlanceIds(AgendaWidget::class.java).isNotEmpty()) AgendaWidget().updateAll(context)
            if (m.getGlanceIds(NewsWidget::class.java).isNotEmpty()) NewsWidget().updateAll(context)
        }
    }
}
