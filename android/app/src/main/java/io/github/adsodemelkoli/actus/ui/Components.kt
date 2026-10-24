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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.sp
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

/** Mention « Nouveau » : contenu apparu depuis la dernière visite dans l'application. */
@Composable
fun NewPill(modifier: Modifier = Modifier) {
    Surface(shape = CircleShape, color = Brand, modifier = modifier) {
        Text(
            "Nouveau", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = Color(0xFF2B1D00),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

/** Pilule « Bêta » au-dessus du titre de l'application. */
@Composable
fun BetaPill(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Surface(shape = CircleShape, color = Brand.copy(alpha = if (dark) 0.22f else 0.16f), modifier = modifier) {
        Text(
            "BÊTA", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            letterSpacing = 1.2.sp,
            color = if (dark) Brand else Color(0xFF8A5A00),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 1.dp),
        )
    }
}

/** Étoile pour suivre un événement (rappel la veille et une semaine avant). */
@Composable
fun FollowButton(followed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            if (followed) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = if (followed) "Ne plus suivre cet événement" else "Suivre cet événement et recevoir un rappel",
            tint = if (followed) Brand else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Pastille de l'école et, s'il y a lieu, la mention « Nouveau ». */
@Composable
fun PillsLine(article: Article, isNew: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        SchoolPill(article.school)
        if (isNew) NewPill()
    }
}

/** Photo du contenu ; à défaut (ou pendant le chargement) une vignette aux couleurs de l'école. */
@Composable
fun ArticleImage(article: Article, modifier: Modifier = Modifier, large: Boolean = false) {
    Box(modifier.background(article.school.color)) {
        // vignette de remplacement : nom de l'école, sans couper les mots (« Montpellier » tient sur une ligne)
        Text(
            article.school.shortLabel.replace("-", "-\u200B"),
            color = Color.White,
            style = if (large) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart).padding(if (large) 20.dp else 8.dp),
        )
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
fun FeaturedCard(article: Article, today: LocalDate, onOpen: (Article) -> Unit, onShare: (Article) -> Unit, isNew: Boolean = false) {
    ElevatedCard(
        onClick = { onOpen(article) },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
            ArticleImage(article, Modifier.fillMaxSize(), large = true)
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.25f), 0.35f to Color.Transparent),
                ),
            )
            SchoolPill(article.school, Modifier.padding(12.dp).align(Alignment.TopStart), onImage = true)
        }
        Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("À la une", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                if (isNew) NewPill()
            }
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
fun ArticleRow(
    article: Article, today: LocalDate, onOpen: (Article) -> Unit, onLongPress: (Article) -> Unit,
    isNew: Boolean = false, followed: Boolean? = null, onFollow: (Article) -> Unit = {},
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .combinedClickable(onClick = { onOpen(article) }, onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onLongPress(article)
            }),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            ArticleImage(article, Modifier.size(width = 104.dp, height = 78.dp).clip(RoundedCornerShape(14.dp)))
            Column(Modifier.weight(1f)) {
                PillsLine(article, isNew)
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
            // étoile seulement pour un événement à venir (null : pas d'étoile)
            if (followed != null) FollowButton(followed, { onFollow(article) }, Modifier.align(Alignment.Top).padding(top = 0.dp))
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
@OptIn(ExperimentalFoundationApi::class)
fun UpcomingCard(
    article: Article, onOpen: (Article) -> Unit, onLongPress: (Article) -> Unit = {},
    isNew: Boolean = false, followed: Boolean = false, onFollow: (Article) -> Unit = {},
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.width(280.dp).clip(RoundedCornerShape(20.dp)).combinedClickable(
            onClick = { onOpen(article) },
            onLongClick = {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                onLongPress(article)
            },
        ),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CalendarBlock(article, Modifier.padding(top = 6.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.weight(1f).padding(top = 6.dp)) { PillsLine(article, isNew) }
                    FollowButton(followed, { onFollow(article) }, Modifier.padding(start = 2.dp))
                }
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
