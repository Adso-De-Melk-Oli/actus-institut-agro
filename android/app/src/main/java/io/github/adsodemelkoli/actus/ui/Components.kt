@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.adsodemelkoli.actus.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.School
import java.time.LocalDate

/** Pastille de l'école, aux couleurs du site. */
@Composable
fun SchoolPill(school: School, modifier: Modifier = Modifier, onImage: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = if (onImage) Color.White.copy(alpha = 0.92f) else school.color.copy(alpha = 0.14f),
        modifier = modifier,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(school.color))
            Spacer(Modifier.width(6.dp))
            Text(
                school.shortLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (onImage) Color(0xFF1D1D1F) else school.color,
            )
        }
    }
}

/** Photo du contenu ; à défaut (ou pendant le chargement) une vignette aux couleurs de l'école. */
@Composable
fun ArticleImage(article: Article, modifier: Modifier = Modifier, showLabel: Boolean = true) {
    Box(modifier.background(article.school.color)) {
        if (showLabel) {
            Text(
                article.school.shortLabel,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            )
        }
        if (!article.image.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(article.image).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** L'actu à la une : grande photo, titre, résumé. */
@Composable
fun FeaturedCard(article: Article, today: LocalDate, onOpen: (Article) -> Unit, onShare: (Article) -> Unit) {
    ElevatedCard(
        onClick = { onOpen(article) },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            ArticleImage(article, Modifier.fillMaxSize(), showLabel = false)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.35f to Color.Transparent),
                ),
            )
            SchoolPill(article.school, Modifier.padding(12.dp).align(Alignment.TopStart), onImage = true)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Text("À la une", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(article.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            if (!article.summary.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    article.summary, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis,
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (article.isEvent) article.whenLabel() else article.relativeLabel(today),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalIconButton(onClick = { onShare(article) }) {
                    Icon(Icons.Filled.Share, contentDescription = "Partager")
                }
            }
        }
    }
}

/** Une actu dans la liste : vignette à gauche, texte à droite. Appui long pour partager. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArticleRow(article: Article, today: LocalDate, onOpen: (Article) -> Unit, onShare: (Article) -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = { onOpen(article) }, onLongClick = { onShare(article) }),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArticleImage(article, Modifier.size(width = 104.dp, height = 78.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                SchoolPill(article.school)
                Spacer(Modifier.height(6.dp))
                Text(
                    article.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 3, overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (article.isEvent) eventMeta(article) else article.relativeLabel(today),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (article.isEvent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

fun eventMeta(a: Article): String = buildString {
    append(a.whenLabel().replaceFirstChar { it.uppercase() })
    if (a.remote) append(" · À distance") else if (!a.place.isNullOrBlank()) append(" · ").append(a.place)
}

/** Pavé calendrier : mois en couleur de l'école, jour en grand, jour de la semaine. */
@Composable
fun CalendarBlock(article: Article, modifier: Modifier = Modifier) {
    val d = article.day ?: return
    Column(
        modifier.width(56.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            monthShort(d), color = Color.White, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().background(article.school.color).padding(vertical = 3.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text("${d.dayOfMonth}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 2.dp))
        Text(weekdayShort(d), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
    }
}

/** Événement dans le bandeau « À venir ». */
@Composable
fun UpcomingCard(article: Article, onOpen: (Article) -> Unit) {
    Card(
        onClick = { onOpen(article) },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(280.dp),
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarBlock(article)
            Column(Modifier.weight(1f)) {
                SchoolPill(article.school)
                Spacer(Modifier.height(6.dp))
                Text(article.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                val where = if (article.remote) "À distance" else article.place
                if (!where.isNullOrBlank()) {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(where, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun SectionTitle(text: String, trailing: String? = null) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Puce de lieu « sur le campus » / « à distance » pour l'écran Agenda. */
@Composable
fun PlaceChip(article: Article) {
    val where = if (article.remote) "À distance" else article.place ?: return
    AssistChip(
        onClick = {},
        label = { Text(where, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(Icons.Filled.LocationOn, null, Modifier.size(AssistChipDefaults.IconSize)) },
    )
}
