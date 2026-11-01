package com.snishaper.android.core

import android.content.Context
import android.util.Log
import com.snishaper.core.mobile.App
import com.snishaper.core.mobile.Listener
import com.snishaper.core.mobile.Mobile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Process-wide singleton around the gomobile AAR (`com.snishaper.core.mobile.App`).
 *
 * The Go engine is Ca-free: start it once (Application.onCreate) and call the
 * suspend helpers from ViewModels. All complex payloads are JSON strings; the
 * data classes in `Models.kt` decode them.
 */
object SnishaperEngine {

    /** Logcat tag used by smoke tests: `adb logcat -s Snishaper:*`. */
    const val LOG_TAG = "Snishaper"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var app: App? = null

    private val _ready = MutableStateFlow(value = false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _buildInfo = MutableStateFlow(BuildInfo())

    private val _settings = MutableStateFlow(AppSettings())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    private val _tunStatus = MutableStateFlow(TunStatus())
    val tunStatus: StateFlow<TunStatus> = _tunStatus.asStateFlow()

    private val _evolution = MutableStateFlow<EvolutionTask?>(null)
    val evolution: StateFlow<EvolutionTask?> = _evolution.asStateFlow()

    private val _logs = MutableSharedFlow<String>(
        replay = 0,
        extraBufferCapacity = 2048,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private val _routes = MutableSharedFlow<RouteEvent>(
        replay = 0,
        extraBufferCapacity = 512,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
    )
    val routes: SharedFlow<RouteEvent> = _routes.asSharedFlow()

    /** Strong reference: the Go side only holds a proxy of this interface. */
    private val listener = object : Listener {
        override fun onLog(line: String) {
            Log.i(LOG_TAG, line)
        }

        override fun onRouteEvent(domain: String, mode: String) {
            _routes.tryEmit(RouteEvent(domain, mode, System.currentTimeMillis()))
        }

        override fun onStateChanged(stateJSON: String) {
            _status.value = EngineStatus.fromJson(stateJSON)
            if (_tunStatus.value.running != _status.value.tunRunning) {
                refreshTunStatusAsync()
            }
        }

        override fun onEvolutionProgress(taskJSON: String) {
            _evolution.value = EvolutionTask.fromJson(taskJSON)
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    fun initialize(context: Context) {
        if (app != null) return
        scope.launch {
            runCatching {
                Log.i(LOG_TAG, "engine: initializing (dataDir=${context.applicationContext.filesDir})")
                AssetSeeder.seed(context.applicationContext)
                val instance = Mobile.newApp(context.applicationContext.filesDir.absolutePath)
                instance.setListener(listener)
                instance.start()
                app = instance
                _buildInfo.value = BuildInfo.fromJson(instance.buildInfoJSON)
                _status.value = EngineStatus.fromJson(instance.let(App::getStatusJSON))
                _tunStatus.value = TunStatus.fromJson(instance.let(App::getTUNStatusJSON))
                _settings.value = AppSettings.fromJson(instance.let(App::getSettingsJSON))
                _ready.value = true
                Log.i(
                    LOG_TAG,
                    "engine: ready (core=${_buildInfo.value.coreVersion}, port=${_status.value.listenPort})",
                )
            }.onFailure { error ->
                Log.e(LOG_TAG, "engine: init failed: ${error.message}", error)
                _status.update { it.copy(lastError = error.message ?: "engine init failed") }
            }
        }
    }

    private fun requireApp(): App = app ?: throw IllegalStateException("engine is not started yet")

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    /**
     * Raw access to the gomobile `App` for the JSON passthrough bridge.
     *
     * The WebView bridge mirrors the desktop API, whose payloads are the very
     * same Go structs (see `proxy.SiteGroup` etc.). Using the raw `*JSON`
     * methods keeps the shapes byte-identical to the desktop app instead of
     * round-tripping through the Kotlin data classes in `Models.kt`, which only
     * model the subset the old Compose UI needed.
     */
    suspend fun <T> withApp(block: (App) -> T): T = io { block(requireApp()) }

    // ------------------------------------------------------------------
    // Status / logs
    // ------------------------------------------------------------------

    suspend fun refreshStatus() = io {
        val instance = requireApp()
        _status.value = EngineStatus.fromJson(instance.let(App::getStatusJSON))
        _tunStatus.value = TunStatus.fromJson(instance.let(App::getTUNStatusJSON))
    }

    private fun refreshTunStatusAsync() {
        scope.launch { runCatching { refreshStatus() } }
    }



    // ------------------------------------------------------------------
    // TUN (VpnService)
    // ------------------------------------------------------------------

    suspend fun startTun(fd: Int, mtu: Int = 1500) = io {
        requireApp().startTUN(fd, mtu)
        refreshStatus()
    }

    suspend fun stopTun() = io {
        requireApp().stopTUN()
        refreshStatus()
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    suspend fun loadSettings() = io {
        _settings.value = AppSettings.fromJson(requireApp().let(App::getSettingsJSON))
    }
}

/** A routing decision reported by the Go engine. */
data class RouteEvent(val domain: String, val mode: String, val timestamp: Long = System.currentTimeMillis())
