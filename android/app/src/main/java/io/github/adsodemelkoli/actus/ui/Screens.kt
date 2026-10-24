@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package io.github.adsodemelkoli.actus.ui

import android.os.Build
import androidx.compose.foundation.background
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
import java.time.LocalDate

enum class Tab(val label: String) { FEED("Fil"), AGENDA("Agenda"), SETTINGS("Réglages") }

data class UiState(
    val feed: Feed = Feed(),
    val loading: Boolean = false,
    val offline: Boolean = false,
    val error: String? = null,
    val filter: String? = null,
    val query: String = "",
    val searchOpen: Boolean = false,
)

data class SettingsState(
    val notifyNew: Boolean = true,
    val notifyToday: Boolean = true,
    val notifySchools: Set<String> = School.listed.map { it.key }.toSet(),
    val dynamicColor: Boolean = false,
    val canNotify: Boolean = true,
    val version: String = "",
    val builtOn: String = "",
    val update: UpdateState = UpdateState(),
)

/** Mises à jour : l'application est installée hors Play Store, elle vérifie elle-même la dernière version publiée. */
data class UpdateState(
    val checking: Boolean = false,
    val latest: String? = null,
    val downloadUrl: String? = null,
    val error: Boolean = false,
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
    val onCheckUpdate: () -> Unit = {},
    val onInstallUpdate: () -> Unit = {},
    val onNotifyNew: (Boolean) -> Unit = {},
    val onNotifyToday: (Boolean) -> Unit = {},
    val onNotifySchool: (String, Boolean) -> Unit = { _, _ -> },
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
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
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
                            Icon(
                                when (t) { Tab.FEED -> Icons.Filled.Home; Tab.AGENDA -> Icons.Filled.DateRange; Tab.SETTINGS -> Icons.Filled.Settings },
                                contentDescription = null,
                            )
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

/** « Actus | L'Institut Agro », avec « Actus » dans l'orange de la marque. */
@Composable
private fun BrandTitle(override: String?) {
    if (override != null) { Text(override); return }
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = Brand, fontWeight = FontWeight.Bold)) { append("Actus") }
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.outline, fontWeight = FontWeight.Light)) { append("  |  ") }
            append("L'Institut Agro")
        },
    )
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
private fun StatusBanner(state: UiState) {
    if (!state.offline || state.error == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Spacer(Modifier.size(12.dp))
            Text(
                "Hors ligne : affichage du dernier relevé enregistré. Tirez vers le bas pour réessayer.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun LazyListScope.header(state: UiState, actions: Actions) {
    item { SearchField(state, actions) }
    item { FilterRow(state, actions) }
    item { StatusBanner(state) }
}

@Composable
fun FeedScreen(state: UiState, today: LocalDate, actions: Actions) {
    val all = state.visible()
    val upcoming = all.filter { it.isUpcoming(today) }.sortedBy { it.day }
    val news = all.filter { !it.isEvent }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header(state, actions)
        if (all.isEmpty() && !state.loading) {
            item {
                if (state.feed.articles.isEmpty()) EmptyState("Aucune actu pour l'instant", "Tirez vers le bas pour charger le dernier relevé des sites.")
                else EmptyState("Rien ne correspond", "Essayez une autre école ou un autre mot-clé.")
            }
        }
        news.firstOrNull()?.let { first ->
            item(key = "une") { Box(Modifier.padding(horizontal = 16.dp)) { FeaturedCard(first, today, actions.onOpen, actions.onShare) } }
        }
        if (upcoming.isNotEmpty()) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("À venir", "${upcoming.size} événement${if (upcoming.size > 1) "s" else ""}") } }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(horizontal = 16.dp)) {
                    items(upcoming, key = { it.id }) { UpcomingCard(it, actions.onOpen, actions.longPress(today)) }
                }
            }
        }
        if (news.size > 1) {
            item { Box(Modifier.padding(horizontal = 16.dp)) { SectionTitle("Dernières actus") } }
            items(news.drop(1), key = { it.id }) { a ->
                Box(Modifier.padding(horizontal = 16.dp)) { ArticleRow(a, today, actions.onOpen, actions.longPress(today)) }
            }
        }
        item { Footer(state, today) }
    }
}

@Composable
fun AgendaScreen(state: UiState, today: LocalDate, actions: Actions) {
    val events = state.visible(kindFilter = "agenda")
    val upcoming = events.filter { it.isUpcoming(today) }.sortedBy { it.day }
    val past = events.filter { !it.isUpcoming(today) }.sortedByDescending { it.day }
    // un événement en cours depuis plusieurs jours est rangé à aujourd'hui
    val groups = upcoming.groupBy { maxOf(it.day ?: today, today) }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        header(state, actions)
        if (upcoming.isEmpty() && !state.loading) item { EmptyState("Aucun événement à venir", "Les prochains événements apparaîtront ici dès qu'ils seront annoncés.") }
        else item {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.DateRange, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(8.dp))
                Text("Appui long sur un événement pour l'ajouter à l'agenda du téléphone", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        groups.forEach { (day, list) ->
            item(key = "j$day") {
                Text(
                    dayHeader(day, today), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp),
                )
            }
            items(list, key = { "a" + it.id }) { a -> Box(Modifier.padding(horizontal = 16.dp)) { ArticleRow(a, today, actions.onOpen, actions.longPress(today)) } }
        }
        if (past.isNotEmpty()) {
            item { Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)) { SectionTitle("Passés", "${past.size}") } }
            items(past.take(20), key = { "p" + it.id }) { a -> Box(Modifier.padding(horizontal = 16.dp)) { ArticleRow(a, today, actions.onOpen, actions.longPress(today)) } }
        }
        item { Footer(state, today) }
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
                        Text("Les notifications sont désactivées pour cette application.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
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
                    Text(
                        when {
                            st == null -> "Pas encore relevée"
                            st.ok -> "${st.count} contenus"
                            else -> "Relevé incomplet · ${st.count} contenus"
                        },
                    )
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
                headlineContent = { Text("L'Institut Agro en actualités", fontWeight = FontWeight.SemiBold) },
                supportingContent = { Text("Version ${settings.version}" + if (settings.builtOn.isNotEmpty()) " · ${settings.builtOn}" else "") },
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
            )
        }
        item { UpdateRow(settings.update, actions) }
        item {
            Text(
                "Outil interne au service communication. Les contenus sont relevés chaque matin sur les sites de l'Institut Agro, de ses écoles et de sa Fondation.",
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

@Composable
private fun UpdateRow(u: UpdateState, actions: Actions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        when {
            u.checking -> {
                androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Spacer(Modifier.size(12.dp))
                Text("Recherche d'une mise à jour…", style = MaterialTheme.typography.bodyMedium)
            }
            u.available -> {
                Text("La version ${u.latest} est disponible", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Button(onClick = actions.onInstallUpdate) { Text("Installer") }
            }
            else -> {
                Text(
                    when { u.error -> "Mise à jour non vérifiée (hors ligne ?)"; u.latest != null -> "Application à jour"; else -> "" },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                )
                androidx.compose.material3.TextButton(onClick = actions.onCheckUpdate) { Text("Vérifier") }
            }
        }
    }
}
