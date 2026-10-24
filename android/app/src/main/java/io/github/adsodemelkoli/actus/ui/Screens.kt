@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.adsodemelkoli.actus.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Feed
import io.github.adsodemelkoli.actus.data.School
import io.github.adsodemelkoli.actus.data.sorted
import io.github.adsodemelkoli.actus.data.downSources
import io.github.adsodemelkoli.actus.data.eventsOn
import io.github.adsodemelkoli.actus.data.monthCells
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

enum class Tab(val label: String) { FEED("Fil"), AGENDA("Agenda"), SETTINGS("Réglages") }

enum class AgendaView { LIST, MONTH }

data class UiState(
    val feed: Feed = Feed(),
    val loading: Boolean = false,
    val offline: Boolean = false,
    /** Message déjà traduit en français courant (jamais un code ou une adresse). */
    val error: String? = null,
    val filter: String? = null,
    val query: String = "",
    val searchOpen: Boolean = false,
    /** Contenus apparus depuis la dernière visite. */
    val newIds: Set<String> = emptySet(),
    /** Événements suivis (rappel la veille et une semaine avant). */
    val followed: Set<String> = emptySet(),
    val agendaView: AgendaView = AgendaView.LIST,
    val month: YearMonth? = null,
    val selectedDay: LocalDate? = null,
    /** Message bref affiché en bas de l'écran (suivi d'un événement…). */
    val message: String? = null,
)

data class SettingsState(
    val notifyNew: Boolean = true,
    val notifyToday: Boolean = true,
    val notifySchools: Set<String> = School.listed.map { it.key }.toSet(),
    val remindDayBefore: Boolean = true,
    val remindWeekBefore: Boolean = true,
    val dynamicColor: Boolean = false,
    val canNotify: Boolean = true,
    val version: String = "",
    val builtOn: String = "",
    val update: UpdateState = UpdateState(),
)

/** Mises à jour : l'application est installée hors Play Store, elle vérifie et installe elle-même la dernière version. */
data class UpdateState(
    val checking: Boolean = false,
    val latest: String? = null,
    val latestCode: Int = 0,
    val downloadUrl: String? = null,
    val error: Boolean = false,
    val downloading: Boolean = false,
    /** Avancement du téléchargement entre 0 et 1, ou -1 tant qu'il est inconnu. */
    val progress: Float = -1f,
    /** Android attend l'autorisation « Installer des applis inconnues » pour cette application. */
    val needsPermission: Boolean = false,
    /** Échec du téléchargement, en français courant. */
    val problem: String? = null,
) {
    val available: Boolean get() = downloadUrl != null
}

class Actions(
    val onTab: (Tab) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onFilter: (String?) -> Unit = {},
    val onQuery: (String) -> Unit = {},
    val onSearchOpen: (Boolean) -> Unit = {},
    val onOpen: (Article) -> Unit = {},
    val onShare: (Article) -> Unit = {},
    val onAddToCalendar: (Article) -> Unit = {},
    val onFollow: (Article) -> Unit = {},
    val onAgendaView: (AgendaView) -> Unit = {},
    val onMonth: (YearMonth) -> Unit = {},
    val onDay: (LocalDate) -> Unit = {},
    val onMessageShown: () -> Unit = {},
    val onCheckUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    val onAllowInstall: () -> Unit = {},
    val onNotifyNew: (Boolean) -> Unit = {},
    val onNotifyToday: (Boolean) -> Unit = {},
    val onNotifySchool: (String, Boolean) -> Unit = { _, _ -> },
    val onRemindDayBefore: (Boolean) -> Unit = {},
    val onRemindWeekBefore: (Boolean) -> Unit = {},
    val onDynamicColor: (Boolean) -> Unit = {},
    val onAskPermission: () -> Unit = {},
    val onOpenSite: () -> Unit = {},
)

/** Appui long : un événement à venir s'ajoute à l'agenda du téléphone, le reste se partage. */
fun Actions.longPress(today: LocalDate): (Article) -> Unit = { a ->
    if (a.isEvent && a.isUpcoming(today)) onAddToCalendar(a) else onShare(a)
}

/** L'application entière, sans dépendance à Android : utilisée telle quelle par les captures automatiques. */
@Composable
fun ActusApp(state: UiState, settings: SettingsState, tab: Tab, today: LocalDate, actions: Actions) {
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        val m = state.message ?: return@LaunchedEffect
        snackbar.showSnackbar(m)
        actions.onMessageShown()
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            LargeTopAppBar(
                title = { BrandTitle(if (tab == Tab.SETTINGS) "Réglages" else null) },
                actions = {
                    if (tab != Tab.SETTINGS) {
                        IconButton(onClick = { actions.onSearchOpen(!state.searchOpen) }) {
                            Icon(if (state.searchOpen) Icons.Filled.Close else Icons.Filled.Search, contentDescription = "Rechercher")
                        }
                    }
                },
                scrollBehavior = scroll,
            )
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = t == tab,
                        onClick = { actions.onTab(t) },
                        icon = {
                            val icon = when (t) { Tab.FEED -> Icons.Filled.Home; Tab.AGENDA -> Icons.Filled.DateRange; Tab.SETTINGS -> Icons.Filled.Settings }
                            val n = if (t == Tab.FEED) state.newIds.size else 0
                            if (n > 0) BadgedBox(badge = { Badge { Text(if (n > 99) "99+" else "$n") } }) { Icon(icon, null) }
                            else Icon(icon, contentDescription = null)
                        },
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.SETTINGS -> SettingsScreen(state, settings, today, actions, padding)
            else -> PullToRefreshBox(
                isRefreshing = state.loading,
                onRefresh = actions.onRefresh,
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                if (tab == Tab.FEED) FeedScreen(state, today, actions) else AgendaScreen(state, today, actions)
            }
        }
    }
}

/** Pilule « Bêta », puis « Actus | L'Institut Agro » avec « Actus » dans l'orange de la marque. */
@Composable
private fun BrandTitle(override: String?) {
    Column {
        BetaPill()
        Spacer(Modifier.height(2.dp))
        if (override != null) Text(override)
        else Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Brand, fontWeight = FontWeight.Bold)) { append("Actus") }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Light)) { append("  |  ") }
                append("L'Institut Agro")
            },
        )
    }
}

private fun UiState.visible(kindFilter: String? = null): List<Article> {
    val q = query.trim().lowercase()
    return feed.sorted()
        .filter { filter == null || it.source == filter }
        .filter { kindFilter == null || it.kind == kindFilter }
        .filter { q.isEmpty() || (it.title + " " + (it.summary ?: "") + " " + (it.place ?: "")).lowercase().contains(q) }
}

@Composable
private fun FilterRow(state: UiState, actions: Actions) {
    val counts = state.feed.articles.groupingBy { it.source }.eachCount()
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        item {
            FilterChip(
                selected = state.filter == null, onClick = { actions.onFilter(null) },
                label = { Text("Toutes") },
            )
        }
        items(School.listed) { s ->
            FilterChip(
                selected = state.filter == s.key,
                onClick = { actions.onFilter(if (state.filter == s.key) null else s.key) },
                label = { Text("${s.shortLabel}  ${counts[s.key] ?: 0}") },
                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(s.color)) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = s.color.copy(alpha = 0.18f)),
            )
        }
    }
}

@Composable
private fun SearchField(state: UiState, actions: Actions) {
    if (!state.searchOpen) return
    OutlinedTextField(
        value = state.query,
        onValueChange = actions.onQuery,
        placeholder = { Text("Rechercher une actu, un lieu…") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: androidx.compose.ui.text.AnnotatedString, warn: Boolean = false) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (warn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,
        ),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        val fg = if (warn) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = fg)
            Spacer(Modifier.size(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = fg)
        }
    }
}

/** Pas de réseau ou serveur indisponible : on le dit simplement, et on montre le dernier relevé enregistré. */
@Composable
private fun StatusBanner(state: UiState, today: LocalDate) {
    if (!state.offline || state.error == null || state.feed.articles.isEmpty()) return
    InfoCard(
        Icons.Filled.CloudOff,
        buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(state.error) }
            append(" Voici le dernier relevé enregistré sur le téléphone (${updatedLabel(state.feed.updatedAt, today).replaceFirstChar { it.lowercase() }}). Tirez vers le bas pour réessayer.")
        },
    )
}

/** Le site d'une école n'a pas pu être lu au dernier relevé : on le dit en clair. */
@Composable
private fun SourceBanner(state: UiState) {
    val down = state.feed.downSources()
    if (down.isEmpty()) return
    InfoCard(
        Icons.Filled.Warning,
        buildAnnotatedString {
            if (down.size == 1) {
                val (school, st) = down.first()
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(school.label) }
                append(" — lors du dernier relevé, ${st.plainProblem}. Ses contenus déjà connus restent affichés ; nouvel essai au prochain relevé.")
            } else {
                append("Certains sites n'ont pas pu être lus lors du dernier relevé : ")
                down.forEachIndexed { i, (school, st) ->
                    if (i > 0) append(", ")
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(school.label) }
                    append(" (${st.plainProblem})")
                }
                append(". Leurs contenus déjà connus restent affichés ; nouvel essai au prochain relevé.")
            }
        },
        warn = true,
    )
}

@Composable
private fun EmptyState(title: String, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAction) { Text(action) }
        }
    }
}

/** Aucun contenu à montrer : soit le premier chargement a échoué, soit rien n'a encore été relevé. */
@Composable
private fun NothingYet(state: UiState, actions: Actions) {
    if (state.error != null) EmptyState("Impossible de charger les actus", "${state.error} Vérifiez la connexion du téléphone, puis réessayez.", "Réessayer", actions.onRefresh)
    else EmptyState("Aucune actu pour l'instant", "Tirez vers le bas pour charger le dernier relevé des sites.")
}

private fun LazyListScope.header(state: UiState, today: LocalDate, actions: Actions) {
    item { SearchField(state, actions) }
    item { FilterRow(state, actions) }
    item { StatusBanner(state, today) }
    item { SourceBanner(state) }
}

@Composable
fun FeedScreen(state: UiState, today: LocalDate, actions: Actions) {
    val all = state.visible()
    val upcoming = all.filter { it.isUpcoming(today) }.sortedBy { it.day }
    val news = all.filter { !it.isEvent }
    val fresh = all.count { it.id in state.newIds }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header(state, today, actions)
        if (all.isEmpty() && !state.loading) {
            item {
                if (state.feed.articles.isEmpty()) NothingYet(state, actions)
                else EmptyState("Rien ne correspond", "Essayez une autre école ou un autre mot-clé.")
            }
        }
        if (fresh > 0) {
            item(key = "nouveautes") {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    NewPill()
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "$fresh nouveauté${if (fresh > 1) "s" else ""} depuis votre dernière visite",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        news.firstOrNull()?.let { first ->
            item(key = "une") { Box(Modifier.padding(horizontal = 16.dp)) { FeaturedCard(first, today, actions.onOpen, actions.onShare, isNew = first.id in state.newIds) } }
        }
        if (upcoming.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("À venir", "${upcoming.size} événement${if (upcoming.size > 1) "s" else ""}") } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(upcoming, key = { it.id }) {
                        UpcomingCard(it, actions.onOpen, actions.longPress(today), isNew = it.id in state.newIds, followed = it.id in state.followed, onFollow = actions.onFollow)
                    }
                }
            }
        }
        if (news.size > 1) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("Dernières actus") } }
            items(news.drop(1), key = { it.id }) { a ->
                Box(Modifier.padding(horizontal = 16.dp)) { ArticleRow(a, today, actions.onOpen, actions.longPress(today), isNew = a.id in state.newIds) }
            }
        }
        item { Footer(state, today) }
    }
}

/** Une ligne d'événement dans l'agenda : étoile pour les événements à venir. */
@Composable
private fun EventRow(a: Article, state: UiState, today: LocalDate, actions: Actions) {
    Box(Modifier.padding(horizontal = 16.dp)) {
        ArticleRow(
            a, today, actions.onOpen, actions.longPress(today),
            isNew = a.id in state.newIds,
            followed = if (a.isUpcoming(today)) a.id in state.followed else null,
            onFollow = actions.onFollow,
        )
    }
}

@Composable
private fun AgendaViewSwitch(state: UiState, actions: Actions) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        AgendaView.entries.forEachIndexed { i, v ->
            SegmentedButton(
                selected = state.agendaView == v,
                onClick = { actions.onAgendaView(v) },
                shape = SegmentedButtonDefaults.itemShape(i, AgendaView.entries.size),
                icon = {
                    SegmentedButtonDefaults.Icon(active = state.agendaView == v) {
                        Icon(if (v == AgendaView.LIST) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.CalendarMonth, null, Modifier.size(SegmentedButtonDefaults.IconSize))
                    }
                },
            ) { Text(if (v == AgendaView.LIST) "Liste" else "Mois") }
        }
    }
}

@Composable
fun AgendaScreen(state: UiState, today: LocalDate, actions: Actions) {
    val events = state.visible(kindFilter = "agenda")
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header(state, today, actions)
        if (state.feed.articles.isEmpty() && !state.loading) {
            item { NothingYet(state, actions) }
        } else {
            item { AgendaViewSwitch(state, actions) }
            if (state.agendaView == AgendaView.MONTH) monthView(events, state, today, actions)
            else listView(events, state, today, actions)
        }
        item { Footer(state, today) }
    }
}

private fun LazyListScope.listView(events: List<Article>, state: UiState, today: LocalDate, actions: Actions) {
    val upcoming = events.filter { it.isUpcoming(today) }.sortedBy { it.day }
    val past = events.filter { !it.isUpcoming(today) }.sortedByDescending { it.day }
    // un événement en cours depuis plusieurs jours est rangé à aujourd'hui
    val groups = upcoming.groupBy { maxOf(it.day ?: today, today) }
    val followed = upcoming.filter { it.id in state.followed }
    if (upcoming.isEmpty() && !state.loading) item { EmptyState("Aucun événement à venir", "Les prochains événements apparaîtront ici dès qu'ils seront annoncés.") }
    else item { AgendaHint() }
    if (followed.isNotEmpty()) {
        item(key = "suivis") { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) { SectionTitle("Suivis", "${followed.size} · rappel avant chaque date") } }
        items(followed, key = { "s" + it.id }) { a -> EventRow(a, state, today, actions) }
        item(key = "tous") { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)) { SectionTitle("Tous les événements") } }
    }
    groups.forEach { (day, list) ->
        item(key = "j$day") {
            Text(
                dayHeader(day, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
            )
        }
        items(list, key = { "a" + it.id }) { a -> EventRow(a, state, today, actions) }
    }
    if (past.isNotEmpty()) {
        item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { SectionTitle("Passés", "${past.size}") } }
        items(past.take(20), key = { "p" + it.id }) { a -> EventRow(a, state, today, actions) }
    }
}

@Composable
private fun AgendaHint() {
    Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.Star, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(8.dp))
        Text(
            "Étoile : suivre un événement et recevoir un rappel. Appui long : l'ajouter à l'agenda du téléphone.",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val MONTH_TITLE = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

private fun LazyListScope.monthView(events: List<Article>, state: UiState, today: LocalDate, actions: Actions) {
    val selected = state.selectedDay ?: today
    val month = state.month ?: YearMonth.from(selected)
    item(key = "mois") { MonthCalendar(month, selected, today, events, state.followed, actions) }
    val dayEvents = eventsOn(events, selected).sortedBy { it.title }
    item(key = "jour") {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp)) {
            SectionTitle(dayHeader(selected, today), if (dayEvents.isEmpty()) null else "${dayEvents.size} événement${if (dayEvents.size > 1) "s" else ""}")
        }
    }
    if (dayEvents.isEmpty()) {
        item(key = "rien") {
            val next = events.mapNotNull { it.day }.filter { it.isAfter(selected) }.minOrNull()
            Column(Modifier.padding(horizontal = 20.dp)) {
                Text("Aucun événement ce jour-là.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (next != null) {
                    TextButton(onClick = { actions.onDay(next) }, contentPadding = PaddingValues(0.dp)) {
                        Text("Prochain : ${dayHeader(next, today).replaceFirstChar { it.lowercase() }}")
                    }
                }
            }
        }
    }
    items(dayEvents, key = { "m" + it.id }) { a -> EventRow(a, state, today, actions) }
}

/** Calendrier du mois : points aux couleurs des écoles les jours d'événement, étoile les jours d'un événement suivi. */
@Composable
private fun MonthCalendar(month: YearMonth, selected: LocalDate, today: LocalDate, events: List<Article>, followed: Set<String>, actions: Actions) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { actions.onMonth(month.minusMonths(1)) }) { Icon(Icons.Filled.ChevronLeft, "Mois précédent") }
                Text(
                    month.format(MONTH_TITLE).replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { actions.onMonth(month.plusMonths(1)) }) { Icon(Icons.Filled.ChevronRight, "Mois suivant") }
            }
            Row(Modifier.padding(top = 4.dp, bottom = 2.dp)) {
                listOf("L", "M", "M", "J", "V", "S", "D").forEach { d ->
                    Text(d, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            monthCells(month).chunked(7).forEach { week ->
                Row {
                    week.forEach { d ->
                        Box(Modifier.weight(1f).height(52.dp), contentAlignment = Alignment.TopCenter) {
                            if (d != null) DayCell(d, d == selected, d == today, eventsOn(events, d), followed, actions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(d: LocalDate, isSelected: Boolean, isToday: Boolean, dayEvents: List<Article>, followed: Set<String>, actions: Actions) {
    val colors = dayEvents.map { it.school }.distinct().take(3)
    val star = dayEvents.any { it.id in followed }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { actions.onDay(d) }.padding(top = 4.dp, bottom = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(
                when {
                    isSelected -> MaterialTheme.colorScheme.primary
                    isToday -> MaterialTheme.colorScheme.primaryContainer
                    else -> androidx.compose.ui.graphics.Color.Transparent
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "${d.dayOfMonth}", style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday || dayEvents.isNotEmpty()) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isSelected -> MaterialTheme.colorScheme.onPrimary
                    isToday -> MaterialTheme.colorScheme.onPrimaryContainer
                    dayEvents.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
            )
        }
        Spacer(Modifier.height(3.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(8.dp)) {
            if (star) Icon(Icons.Filled.Star, null, Modifier.size(9.dp), tint = Brand)
            colors.forEach { s -> Box(Modifier.size(6.dp).clip(CircleShape).background(s.color)) }
        }
    }
}

@Composable
private fun Footer(state: UiState, today: LocalDate) {
    Text(
        updatedLabel(state.feed.updatedAt, today) + " · Outil interne au service communication",
        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

@Composable
fun SettingsScreen(state: UiState, settings: SettingsState, today: LocalDate, actions: Actions, padding: PaddingValues) {
    LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { SettingsHeader("Notifications") }
        if (!settings.canNotify) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Les notifications sont désactivées pour cette application : ni nouveautés ni rappels ne peuvent s'afficher.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = actions.onAskPermission) { Text("Autoriser les notifications") }
                    }
                }
            }
        }
        item {
            SwitchRow("Nouvelles publications", "Dès qu'une école ou la Fondation publie une actu ou un événement", settings.notifyNew, actions.onNotifyNew)
        }
        if (settings.notifyNew) {
            item {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)) {
                    Text("Écoles suivies", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        School.listed.forEach { s ->
                            val on = s.key in settings.notifySchools
                            FilterChip(
                                selected = on, onClick = { actions.onNotifySchool(s.key, !on) }, label = { Text(s.shortLabel) },
                                leadingIcon = { Box(Modifier.size(8.dp).clip(CircleShape).background(s.color)) },
                            )
                        }
                    }
                }
            }
        }
        item {
            SwitchRow("Aujourd'hui sur les campus", "Vers 7 h 15 en semaine, les événements du jour sur les campus", settings.notifyToday, actions.onNotifyToday)
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SettingsHeader("Rappels des événements suivis") }
        item {
            val n = state.followed.size
            Text(
                if (n == 0) "Touchez l'étoile d'un événement, dans le Fil ou l'Agenda, pour le suivre."
                else "$n événement${if (n > 1) "s" else ""} suivi${if (n > 1) "s" else ""}. Retrouvez-les en tête de l'Agenda.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        item { SwitchRow("La veille", "Un rappel la veille de chaque événement suivi", settings.remindDayBefore, actions.onRemindDayBefore) }
        item { SwitchRow("Une semaine avant", "Un rappel sept jours avant, pour s'organiser", settings.remindWeekBefore, actions.onRemindWeekBefore) }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || settings.dynamicColor) {
            item { SettingsHeader("Apparence") }
            item { SwitchRow("Couleurs du téléphone", "Reprendre les couleurs du fond d'écran (Material You) au lieu de celles de l'Institut Agro", settings.dynamicColor, actions.onDynamicColor) }
            item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        }
        item { SettingsHeader("Sources suivies") }
        items(School.listed) { s ->
            val st = state.feed.sources[s.key]
            ListItem(
                headlineContent = { Text(s.label) },
                supportingContent = {
                    when {
                        st == null -> Text("Pas encore relevée")
                        st.ok -> Text("${st.count} contenus")
                        else -> Column {
                            Text("À vérifier · ${st.count} contenus conservés", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                            Text("Dernier relevé : ${st.plainProblem}.")
                        }
                    }
                },
                leadingContent = { Box(Modifier.size(12.dp).clip(CircleShape).background(s.color)) },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(updatedLabel(state.feed.updatedAt, today), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = actions.onOpenSite) { Text("Ouvrir le site") }
            }
        }
        item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
        item { SettingsHeader("À propos") }
        item {
            ListItem(
                leadingContent = { androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(io.github.adsodemelkoli.actus.R.drawable.logo_institut_agro), null, Modifier.size(44.dp)) },
                headlineContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("L'Institut Agro en actualités", fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.size(8.dp))
                        BetaPill()
                    }
                },
                supportingContent = { Text("Version ${settings.version}" + if (settings.builtOn.isNotEmpty()) " · ${settings.builtOn}" else "") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
        item { UpdateRow(settings.update, actions) }
        item {
            Text(
                "Outil interne au service communication, en version bêta. Les contenus sont relevés chaque matin sur les sites de l'Institut Agro, de ses écoles et de sa Fondation.",
                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun SettingsHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(text) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

/** Vérification, téléchargement et installation de la nouvelle version, sans passer par le navigateur. */
@Composable
private fun UpdateRow(u: UpdateState, actions: Actions) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        when {
            u.downloading -> {
                val pct = if (u.progress >= 0f) " · ${(u.progress * 100).toInt()} %" else ""
                Text("Téléchargement de la version ${u.latest}$pct", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                if (u.progress >= 0f) LinearProgressIndicator(progress = { u.progress }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            u.needsPermission -> {
                Text(
                    "Dernière étape : Android demande d'autoriser cette application à installer sa mise à jour. Activez l'autorisation, puis revenez ici : l'installation se lancera.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Button(onClick = actions.onAllowInstall) { Text("Autoriser l'installation") }
            }
            u.checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Recherche d'une mise à jour…", style = MaterialTheme.typography.bodyMedium)
            }
            u.available -> Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("La version ${u.latest} est disponible", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                    if (u.problem != null) {
                        Text("Le téléchargement n'a pas abouti : ${u.problem.replaceFirstChar { it.lowercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.size(8.dp))
                Button(onClick = actions.onInstallUpdate) { Text(if (u.problem != null) "Réessayer" else "Installer") }
            }
            else -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when { u.error -> "Vérification impossible pour le moment (pas de connexion ?)"; u.latest != null -> "Application à jour"; else -> "" },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                TextButton(onClick = actions.onCheckUpdate) { Text("Vérifier") }
            }
        }
    }
}
