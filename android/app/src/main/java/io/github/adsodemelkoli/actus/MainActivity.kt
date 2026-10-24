package io.github.adsodemelkoli.actus

import android.Manifest
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import io.github.adsodemelkoli.actus.data.Article
import io.github.adsodemelkoli.actus.data.PARIS
import io.github.adsodemelkoli.actus.data.Prefs
import io.github.adsodemelkoli.actus.data.Repository
import io.github.adsodemelkoli.actus.notify.CheckWorker
import io.github.adsodemelkoli.actus.notify.Notifier
import io.github.adsodemelkoli.actus.ui.Actions
import io.github.adsodemelkoli.actus.ui.ActusApp
import io.github.adsodemelkoli.actus.ui.ActusTheme
import io.github.adsodemelkoli.actus.ui.SettingsState
import io.github.adsodemelkoli.actus.ui.Tab
import io.github.adsodemelkoli.actus.ui.UiState
import io.github.adsodemelkoli.actus.widget.AgendaWidget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

class ActusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.createChannels(this)
        CheckWorker.schedule(this)
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = Repository(app)
    private val prefs = Prefs(app)
    private val _state = MutableStateFlow(UiState())
    val state = _state.asStateFlow()
    private val _settings = MutableStateFlow(readSettings())
    val settings = _settings.asStateFlow()

    init {
        viewModelScope.launch {
            repo.cached()?.let { cached -> _state.update { it.copy(feed = cached) } }
            refresh()
        }
    }

    fun refresh() {
        if (_state.value.loading) return
        _state.update { it.copy(loading = true) }
        viewModelScope.launch {
            val r = repo.refresh()
            _state.update { it.copy(feed = r.feed, loading = false, offline = r.fromCache, error = r.error) }
            if (!r.fromCache) {
                CheckWorker.check(getApplication(), r.feed)
                AgendaWidget.refreshAll(getApplication())
            }
        }
    }

    fun setFilter(key: String?) = _state.update { it.copy(filter = key) }
    fun setQuery(q: String) = _state.update { it.copy(query = q) }
    fun setSearchOpen(open: Boolean) = _state.update { it.copy(searchOpen = open, query = if (open) it.query else "") }

    fun setNotifyNew(v: Boolean) { prefs.notifyNew = v; reloadSettings() }
    fun setNotifyToday(v: Boolean) { prefs.notifyToday = v; reloadSettings() }
    fun setNotifySchool(key: String, on: Boolean) { prefs.notifySchools = if (on) prefs.notifySchools + key else prefs.notifySchools - key; reloadSettings() }
    fun setDynamic(v: Boolean) { getApplication<Application>().getSharedPreferences("actus", 0).edit().putBoolean("dynamic", v).apply(); reloadSettings() }
    fun reloadSettings() { _settings.value = readSettings() }

    private fun readSettings(): SettingsState {
        val app = getApplication<Application>()
        return SettingsState(
            notifyNew = prefs.notifyNew,
            notifyToday = prefs.notifyToday,
            notifySchools = prefs.notifySchools,
            dynamicColor = app.getSharedPreferences("actus", 0).getBoolean("dynamic", false),
            canNotify = Notifier.canNotify(app),
            version = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "",
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
                        onNotifyNew = model::setNotifyNew,
                        onNotifyToday = model::setNotifyToday,
                        onNotifySchool = model::setNotifySchool,
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
        }.onFailure { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    private fun share(a: Article) {
        val text = "${a.title}\n${a.url}"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, a.title).putExtra(Intent.EXTRA_TEXT, text),
                "Partager",
            ),
        )
    }
}
