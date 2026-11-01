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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-wide singleton around the gomobile AAR (`com.snishaper.core.mobile.App`).
 *
 * The Go engine is CA-free: start it once (Application.onCreate). All complex
 * payloads are JSON strings passed through to the WebView bridge untouched.
 */
object SnishaperEngine {

    /** Logcat tag used by smoke tests: `adb logcat -s Snishaper:*`. */
    const val LOG_TAG = "Snishaper"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Guards against double init; reset on failure so a retry is possible. */
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var app: App? = null

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    /** Latest evolution task JSON ("" = none); the event pump forwards it raw. */
    private val _evolution = MutableStateFlow("")
    val evolution: StateFlow<String> = _evolution.asStateFlow()

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
        }

        override fun onEvolutionProgress(taskJSON: String) {
            _evolution.value = taskJSON
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle
    // ------------------------------------------------------------------

    /**
     * Starts the engine at most once per successful run. The guard is checked
     * synchronously so Application.onCreate and MainActivity.onCreate racing
     * cannot both spin up an engine (the old async check could).
     */
    fun initialize(context: Context) {
        if (!initialized.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            runCatching {
                Log.i(LOG_TAG, "engine: initializing (dataDir=${appContext.filesDir})")
                AssetSeeder.seed(appContext)
                val instance = Mobile.newApp(appContext.filesDir.absolutePath)
                instance.setListener(listener)
                instance.start()
                app = instance
                _status.value = EngineStatus.fromJson(instance.getStatusJSON())
                val coreVersion = BuildInfo.fromJson(instance.buildInfoJSON).coreVersion
                Log.i(LOG_TAG, "engine: ready (core=$coreVersion, port=${_status.value.listenPort})")
            }.onFailure { error ->
                initialized.set(false)
                Log.e(LOG_TAG, "engine: init failed: ${error.message}", error)
                _status.value = _status.value.copy(lastError = error.message ?: "engine init failed")
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
     * methods keeps the shapes byte-identical to the desktop app.
     */
    suspend fun <T> withApp(block: (App) -> T): T = io { block(requireApp()) }

    private suspend fun refreshStatus() = io {
        _status.value = EngineStatus.fromJson(requireApp().getStatusJSON())
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
}

/** A routing decision reported by the Go engine. */
data class RouteEvent(val domain: String, val mode: String, val timestamp: Long = System.currentTimeMillis())
