package io.github.adsodemelkoli.actus

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.Installer
import io.github.adsodemelkoli.actus.data.PARIS
import io.github.adsodemelkoli.actus.data.Prefs
import io.github.adsodemelkoli.actus.data.Problems
import io.github.adsodemelkoli.actus.data.Repository
import io.github.adsodemelkoli.actus.data.Updates
import io.github.adsodemelkoli.actus.data.newSince
import io.github.adsodemelkoli.actus.notify.CheckWorker
import io.github.adsodemelkoli.actus.notify.Notifier
import io.github.adsodemelkoli.actus.ui.Actions
import io.github.adsodemelkoli.actus.ui.ActusApp
import io.github.adsodemelkoli.actus.ui.ActusTheme
import io.github.adsodemelkoli.actus.ui.AgendaView
import io.github.adsodemelkoli.actus.ui.SettingsState
import io.github.adsodemelkoli.actus.ui.Tab
import io.github.adsodemelkoli.actus.ui.UiState
import io.github.adsodemelkoli.actus.ui.UpdateState
import io.github.adsodemelkoli.actus.ui.dayHeader
import io.github.adsodemelkoli.actus.widget.Widgets
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class ActusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        CheckWorker.schedule(this)
        // l'APK de la mise à jour, une fois installée, ne sert plus
        runCatching { Installer.cleanup(this, BuildConfig.VERSION_CODE) }
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)
    /** Contenus connus à la fin de la visite précédente : tout le reste est « Nouveau » pendant cette visite. */
    private var visit: Set<String>? = prefs.visitIds
    private val _state = MutableStateFlow(UiState(followed = prefs.follows.keys))
    val state = _state.asStateFlow()
    private val _settings: MutableStateFlow<SettingsState> = MutableStateFlow(SettingsState())
    val settings = _settings.asStateFlow()
    /** APK téléchargé et vérifié, à remettre à l'installateur d'Android par l'activité. */
    val installs = Channel<File>(Channel.CONFLATED)
    private var download: Job? = null

    init {
        _settings.value = readSettings()
        checkUpdate()
        viewModelScope.launch {
            repo.cached()?.let { cached -> _state.update { it.copy(feed = cached, newIds = newSince(cached, visit)) } }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val r = repo.refresh()
            _state.update { it.copy(feed = r.feed, loading = false, offline = r.fromCache, error = r.error, newIds = newSince(r.feed, visit)) }
            if (!r.fromCache) {
                CheckWorker.check(getApplication(), r.feed)
                Widgets.refreshAll(getApplication())
            }
        }
    }

    /** En quittant l'application : ce qui a été vu cesse d'être « Nouveau » à la prochaine visite. */
    fun rememberVisit() {
        val ids = _state.value.feed.articles.map { it.id }.toSet()
        if (ids.isEmpty()) return
        prefs.visitIds = ids
        viewModelScope.launch { Widgets.refreshAll(getApplication()) }
    }

    /**
     * Retour dans l'application après plus d'une demi-heure ailleurs : c'est une nouvelle visite.
     * (Lire une actu dans le navigateur puis revenir ne remet pas les compteurs à zéro.)
     */
    fun startVisit(awayMillis: Long) {
        if (awayMillis < 30 * 60_000L) return
        visit = prefs.visitIds
        _state.update { it.copy(newIds = newSince(it.feed, visit)) }
        refresh()
    }

    fun toggleFollow(a: Article) {
        val follows = prefs.follows
        val today = LocalDate.now(PARIS)
        val nowFollowed = a.id !in follows
        prefs.follows = if (nowFollowed) follows + (a.id to today) else follows - a.id
        if (!nowFollowed) prefs.remindersSent = prefs.remindersSent.filterNot { it.startsWith("${a.id}:") }.toSet()
        val message = if (!nowFollowed) "Vous ne suivez plus cet événement"
        else followMessage(a, today)
        _state.update { it.copy(followed = prefs.follows.keys, message = message) }
    }

    private fun followMessage(a: Article, today: LocalDate): String {
        val app = getApplication<Application>()
        if (!Notifier.canNotify(app)) return "Événement suivi. Autorisez les notifications (Réglages) pour recevoir le rappel."
        val day = a.day ?: return "Événement suivi"
        val left = java.time.temporal.ChronoUnit.DAYS.between(today, day)
        val parts = buildList {
            if (prefs.remindWeekBefore && left > 7) add("une semaine avant")
            if (prefs.remindDayBefore && left > 1) add("la veille")
        }
        return if (parts.isEmpty()) "Événement suivi : il a lieu ${dayHeader(day, today).lowercase()}"
        else "Événement suivi : rappel ${parts.joinToString(" et ")}"
    }

    fun messageShown() = _state.update { it.copy(message = null) }

    fun setAgendaView(v: AgendaView) = _state.update { it.copy(agendaView = v) }
    fun setMonth(m: YearMonth) {
        val today = LocalDate.now(PARIS)
        _state.update { it.copy(month = m, selectedDay = if (YearMonth.from(today) == m) today else m.atDay(1)) }
    }
    fun setDay(d: LocalDate) = _state.update { it.copy(selectedDay = d, month = YearMonth.from(d)) }

    /** Dernière version publiée sur GitHub (release « android ») comparée à celle installée. */
    fun checkUpdate() {
        val u0 = _settings.value.update
        if (u0.checking || u0.downloading) return
        _settings.update { it.copy(update = it.update.copy(checking = true, error = false)) }
        viewModelScope.launch {
            val u = runCatching { Updates.latest() }.getOrNull()
            _settings.update {
                it.copy(update = when {
                    u == null -> UpdateState(error = true)
                    u.versionCode > BuildConfig.VERSION_CODE -> UpdateState(latest = u.versionName, latestCode = u.versionCode, downloadUrl = u.url)
                    else -> UpdateState(latest = u.versionName)
                })
            }
        }
    }

    /** Télécharge la nouvelle version dans l'application, puis la confie à l'installateur d'Android. */
    fun installUpdate() {
        val u = _settings.value.update
        val url = u.downloadUrl ?: return
        if (download?.isActive == true) return
        setUpdate { it.copy(downloading = true, progress = -1f, problem = null, needsPermission = false) }
        download = viewModelScope.launch {
            try {
                val file = Installer.download(getApplication(), url, u.latestCode) { p -> setUpdate { it.copy(progress = p) } }
                setUpdate { it.copy(downloading = false) }
                installs.send(file)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                setUpdate { it.copy(downloading = false, problem = Problems.plain(e)) }
            }
        }
    }

    fun setNeedsPermission(v: Boolean) = setUpdate { it.copy(needsPermission = v) }

    private fun setUpdate(f: (UpdateState) -> UpdateState) = _settings.update { it.copy(update = f(it.update)) }

    fun setFilter(key: String?) = _state.update { it.copy(filter = key) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setSearchOpen(open: Boolean) = _state.update { it.copy(searchOpen = open, query = if (open) it.query else "") }

    fun setNotifyNew(v: Boolean) { prefs.notifyNew = v; reloadSettings() }
    fun setNotifyToday(v: Boolean) { prefs.notifyToday = v; reloadSettings() }
    fun setNotifySchool(key: String, on: Boolean) { prefs.notifySchools = if (on) prefs.notifySchools + key else prefs.notifySchools - key; reloadSettings() }
    fun setRemindDayBefore(v: Boolean) { prefs.remindDayBefore = v; reloadSettings() }
    fun setRemindWeekBefore(v: Boolean) { prefs.remindWeekBefore = v; reloadSettings() }
    fun setDynamic(v: Boolean) { getApplication<Application>().getSharedPreferences("actus", 0).edit().putBoolean("dynamic", v).apply(); reloadSettings() }
    fun reloadSettings() { _settings.value = readSettings() }

    private fun readSettings(): SettingsState {
        val app = getApplication<Application>()
        return SettingsState(
            notifyNew = prefs.notifyNew,
            notifyToday = prefs.notifyToday,
            notifySchools = prefs.notifySchools,
            remindDayBefore = prefs.remindDayBefore,
            remindWeekBefore = prefs.remindWeekBefore,
            dynamicColor = app.getSharedPreferences("actus", 0).getBoolean("dynamic", false),
            canNotify = Notifier.canNotify(app),
            version = BuildConfig.VERSION_NAME,
            builtOn = java.time.Instant.ofEpochMilli(BuildConfig.BUILD_TIME).atZone(PARIS).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.FRENCH)),
            update = _settings.value.update,
        )
    }
}

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    private val askNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        vm.reloadSettings()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeAskNotifications()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.installs.receiveAsFlow().collect { install(it) }
            }
        }
        setContent {
            val model = vm
            val state by model.state.collectAsStateWithLifecycle()
            val settings by model.settings.collectAsStateWithLifecycle()
            var tab by rememberSaveable { mutableStateOf(Tab.FEED) }
            ActusTheme(dynamic = settings.dynamicColor) {
                ActusApp(
                    state = state,
                    settings = settings,
                    tab = tab,
                    today = LocalDate.now(PARIS),
                    actions = Actions(
                        onTab = { tab = it },
                        onRefresh = model::refresh,
                        onFilter = model::setFilter,
                        onQuery = model::setQuery,
                        onSearchOpen = model::setSearchOpen,
                        onOpen = ::open,
                        onShare = ::share,
                        onAddToCalendar = ::addToCalendar,
                        onFollow = model::toggleFollow,
                        onAgendaView = model::setAgendaView,
                        onMonth = model::setMonth,
                        onDay = model::setDay,
                        onMessageShown = model::messageShown,
                        onCheckUpdate = model::checkUpdate,
                        onInstallUpdate = model::installUpdate,
                        onAllowInstall = { runCatching { startActivity(Installer.permissionIntent(this)) } },
                        onNotifyNew = model::setNotifyNew,
                        onNotifyToday = model::setNotifyToday,
                        onNotifySchool = model::setNotifySchool,
                        onRemindDayBefore = model::setRemindDayBefore,
                        onRemindWeekBefore = model::setRemindWeekBefore,
                        onDynamicColor = model::setDynamic,
                        onAskPermission = ::requestNotifications,
                        onOpenSite = { openUrl(Repository.SITE) },
                    ),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.reloadSettings()
        // retour des réglages d'Android après avoir autorisé l'installation : on reprend la mise à jour
        val prefs = Prefs(this)
        val pending = prefs.pendingApk?.let(::File)
        if (pending != null) {
            if (!pending.exists()) { prefs.pendingApk = null; vm.setNeedsPermission(false) }
            else if (Installer.canInstall(this)) install(pending)
        }
    }

    private var stoppedAt = 0L

    override fun onStart() {
        super.onStart()
        if (stoppedAt > 0) vm.startVisit(System.currentTimeMillis() - stoppedAt)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) { vm.rememberVisit(); stoppedAt = System.currentTimeMillis() }
    }

    /** Remet l'APK vérifié à l'installateur d'Android, après l'autorisation si elle manque encore. */
    private fun install(file: File) {
        val prefs = Prefs(this)
        if (!Installer.canInstall(this)) {
            prefs.pendingApk = file.path
            vm.setNeedsPermission(true)
            runCatching { startActivity(Installer.permissionIntent(this)) }
            return
        }
        prefs.pendingApk = null
        vm.setNeedsPermission(false)
        runCatching { startActivity(Installer.installIntent(this, file)) }
            .onFailure { Toast.makeText(this, "L'installation n'a pas pu démarrer. Réessayez depuis les Réglages.", Toast.LENGTH_LONG).show() }
    }

    /** Au premier lancement, sur Android 13 et plus, demande l'autorisation d'envoyer des notifications. */
    private fun maybeAskNotifications() {
        val prefs = Prefs(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !prefs.askedPermission) {
            prefs.askedPermission = true
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestNotifications() {
        // après un premier refus, Android n'affiche plus la demande : on ouvre les réglages de l'application
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(intent)
        }
    }

    private fun open(a: Article) = openUrl(a.url)

    private fun openUrl(url: String) {
        val params = CustomTabColorSchemeParams.Builder().setToolbarColor(0xFFF8AC00.toInt()).build()
        runCatching {
            CustomTabsIntent.Builder().setDefaultColorSchemeParams(params).setShowTitle(true).build().launchUrl(this, Uri.parse(url))
        }.recoverCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { Toast.makeText(this, "Aucun navigateur n'est disponible pour ouvrir cette page", Toast.LENGTH_LONG).show() }
    }

    /**
     * Ouvre l'écran « Nouvel événement » de l'agenda du téléphone, prérempli : l'utilisateur choisit
     * l'agenda et confirme. Aucune autorisation n'est nécessaire.
     */
    private fun addToCalendar(a: Article) {
        val start = a.day ?: return
        val end = (a.endDay ?: start).plusDays(1) // journée entière : fin exclusive, à minuit UTC
        val description = listOfNotNull(a.summary?.takeIf { it.isNotBlank() }, a.school.label, a.url).joinToString("\n\n")
        val intent = Intent(Intent.ACTION_INSERT)
            .setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, a.title)
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
            .putExtra(CalendarContract.Events.EVENT_LOCATION, if (a.remote) "À distance" else a.place ?: "")
            .putExtra(CalendarContract.Events.DESCRIPTION, description)
            .putExtra(CalendarContract.Events.AVAILABILITY, CalendarContract.Events.AVAILABILITY_FREE)
        runCatching { startActivity(intent) }
            .onFailure { Toast.makeText(this, "Aucune application d'agenda n'est installée", Toast.LENGTH_LONG).show() }
    }

    private fun share(a: Article) {
        val text = "${a.title}\n${a.url}"
        runCatching {
            startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, a.title).putExtra(Intent.EXTRA_TEXT, text),
                    "Partager",
                ),
            )
        }
    }
}
