package com.snishaper.android.core

import android.content.Context
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
import org.json.JSONObject

/**
 * Process-wide singleton around the gomobile AAR (`com.snishaper.core.mobile.App`).
 *
 * The Go engine is Ca-free: start it once (Application.onCreate) and call the
 * suspend helpers from ViewModels. All complex payloads are JSON strings; the
 * data classes in `Models.kt` decode them.
 */
object SnishaperEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var app: App? = null

    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _buildInfo = MutableStateFlow(BuildInfo())
    val buildInfo: StateFlow<BuildInfo> = _buildInfo.asStateFlow()

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

    private val logRing = ArrayDeque<String>()
    private val logLock = Any()

    /** Strong reference: the Go side only holds a proxy of this interface. */
    private val listener = object : Listener {
        override fun onLog(line: String) {
            appendLog(line)
            _logs.tryEmit(line)
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
                AssetSeeder.seed(context.applicationContext)
                val instance = Mobile.newApp(context.applicationContext.filesDir.absolutePath)
                instance.setListener(listener)
                instance.start()
                app = instance
                _buildInfo.value = BuildInfo.fromJson(instance.getBuildInfoJSON())
                _status.value = EngineStatus.fromJson(instance.getStatusJSON())
                _tunStatus.value = TunStatus.fromJson(instance.getTUNStatusJSON())
                _settings.value = AppSettings.fromJson(instance.getSettingsJSON())
                _ready.value = true
            }.onFailure { error ->
                _status.update { it.copy(lastError = error.message ?: "engine init failed") }
            }
        }
    }

    private fun requireApp(): App = app ?: throw IllegalStateException("engine is not started yet")

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    // ------------------------------------------------------------------
    // Status / logs
    // ------------------------------------------------------------------

    suspend fun refreshStatus() = io {
        val instance = requireApp()
        _status.value = EngineStatus.fromJson(instance.getStatusJSON())
        _tunStatus.value = TunStatus.fromJson(instance.getTUNStatusJSON())
    }

    private fun refreshTunStatusAsync() {
        scope.launch { runCatching { refreshStatus() } }
    }

    suspend fun getStats(): Stats = io { Stats.fromJson(requireApp().getStatsJSON()) }

    fun recentLogs(maxLines: Int = 500): String =
        runCatching { requireApp().getRecentLogs(maxLines) }.getOrDefault("")

    suspend fun clearLogs() = io {
        requireApp().clearLogs()
        synchronized(logLock) { logRing.clear() }
    }

    fun cachedLogs(): List<String> = synchronized(logLock) { logRing.toList() }

    private fun appendLog(line: String) {
        synchronized(logLock) {
            logRing.addLast(line)
            while (logRing.size > 5000) logRing.removeFirst()
        }
    }

    // ------------------------------------------------------------------
    // Proxy control
    // ------------------------------------------------------------------

    suspend fun startProxy() = io {
        requireApp().startProxy()
        refreshStatus()
    }

    suspend fun stopProxy() = io {
        requireApp().stopProxy()
        refreshStatus()
    }

    suspend fun setListenPort(port: Int) = io {
        requireApp().setListenPort(port)
        refreshStatus()
        loadSettings()
    }

    suspend fun setProxyMode(mode: String) = io {
        requireApp().setProxyMode(mode)
        refreshStatus()
    }

    suspend fun checkPortAvailable(port: Int): Boolean = io { requireApp().checkPortAvailable(port) }

    suspend fun refreshIpv6(): Boolean = io {
        val value = requireApp().refreshIPv6Check()
        _status.update { it.copy(ipv6Available = value) }
        value
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

    suspend fun getTunStatus(): TunStatus = io {
        TunStatus.fromJson(requireApp().getTUNStatusJSON()).also { _tunStatus.value = it }
    }

    suspend fun updateTunConfig(configJson: String) = io {
        requireApp().updateTUNConfigJSON(configJson)
        refreshStatus()
    }

    // ------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------

    suspend fun loadSettings() = io {
        _settings.value = AppSettings.fromJson(requireApp().getSettingsJSON())
    }

    /**
     * Patches the persisted settings JSON and sends the complete structure back.
     * (The Go side applies every section it receives, so partial JSON would reset
     * unrelated sections such as `tun` / `fragment` / `cloudflare_config`.)
     */
    suspend fun patchSettings(patch: (JSONObject) -> Unit) = io {
        val instance = requireApp()
        val current = parseObject(instance.getSettingsJSON())
        patch(current)
        instance.updateSettingsJSON(current.toString())
        _settings.value = AppSettings.fromJson(instance.getSettingsJSON())
        refreshStatus()
    }

    // ------------------------------------------------------------------
    // Rules / upstreams / DNS / ECH / NAT64 / fragment / Cloudflare
    // ------------------------------------------------------------------

    suspend fun siteGroups(): List<SiteGroup> = io { parseSiteGroups(requireApp().getSiteGroupsJSON()) }

    suspend fun addSiteGroup(group: SiteGroup): String = io {
        requireApp().addSiteGroupJSON(group.toJson().toString())
    }

    suspend fun updateSiteGroup(group: SiteGroup) = io {
        requireApp().updateSiteGroupJSON(group.toJson().toString())
    }

    suspend fun deleteSiteGroup(id: String) = io { requireApp().deleteSiteGroup(id) }

    suspend fun upstreams(): List<Upstream> = io { parseUpstreams(requireApp().getUpstreamsJSON()) }

    suspend fun addUpstream(upstream: Upstream): String = io {
        requireApp().addUpstreamJSON(upstream.toJson().toString())
    }

    suspend fun updateUpstream(upstream: Upstream) = io {
        requireApp().updateUpstreamJSON(upstream.toJson().toString())
    }

    suspend fun deleteUpstream(id: String) = io { requireApp().deleteUpstream(id) }

    suspend fun dnsNodes(): List<DnsNode> = io { parseDnsNodes(requireApp().getDNSNodesJSON()) }

    suspend fun addDnsNode(node: DnsNode): String = io {
        requireApp().addDNSNodeJSON(node.toJson().toString())
    }

    suspend fun updateDnsNode(node: DnsNode) = io {
        requireApp().updateDNSNodeJSON(node.toJson().toString())
    }

    suspend fun deleteDnsNode(id: String) = io { requireApp().deleteDNSNode(id) }

    suspend fun setDnsNodePriority(id: String, index: Int) = io {
        requireApp().setDNSNodePriority(id, index)
    }

    suspend fun testDnsNode(id: String): DnsTestResult = io {
        DnsTestResult.fromJson(requireApp().testDNSNode(id))
    }

    suspend fun echProfiles(): List<ECHProfile> = io { parseEchProfiles(requireApp().getECHProfilesJSON()) }

    suspend fun upsertEchProfile(profile: ECHProfile) = io {
        requireApp().upsertECHProfileJSON(profile.toJson().toString())
    }

    suspend fun deleteEchProfile(id: String) = io { requireApp().deleteECHProfile(id) }

    suspend fun fetchEchConfig(domain: String, dohUrl: String): String = io {
        // Returns {"config":"<base64>","length":N}
        requireApp().fetchECHConfigJSON(domain, dohUrl)
    }

    suspend fun nat64Profiles(): List<Nat64Profile> = io {
        parseNat64Profiles(requireApp().getNAT64ProfilesJSON())
    }

    suspend fun addNat64Profile(profile: Nat64Profile): String = io {
        requireApp().addNAT64ProfileJSON(profile.toJson().toString())
    }

    suspend fun updateNat64Profile(profile: Nat64Profile) = io {
        requireApp().updateNAT64ProfileJSON(profile.toJson().toString())
    }

    suspend fun deleteNat64Profile(id: String) = io { requireApp().deleteNAT64Profile(id) }

    suspend fun testNat64(prefix: String): Nat64TestResult = io {
        Nat64TestResult.fromJson(requireApp().testNAT64Profile(prefix))
    }

    suspend fun fragmentConfig(): FragmentConfig = io {
        FragmentConfig.fromJson(parseObject(requireApp().getFragmentConfigJSON()))
    }

    suspend fun updateFragmentConfig(config: FragmentConfig) = io {
        requireApp().setFragmentConfigJSON(config.toJson().toString())
        loadSettings()
    }

    suspend fun resetFragmentConfig() = io {
        requireApp().resetFragmentConfig()
        loadSettings()
    }

    suspend fun cloudflareConfig(): CloudflareConfig = io {
        CloudflareConfig.fromJson(parseObject(requireApp().getCloudflareConfigJSON()))
    }

    suspend fun updateCloudflareConfig(config: CloudflareConfig) = io {
        requireApp().updateCloudflareConfigJSON(config.toJson().toString())
        loadSettings()
    }

    suspend fun cloudflareIpStats(): List<CfIpStat> = io {
        parseCfIpStats(requireApp().getCloudflareIPStatsJSON())
    }

    suspend fun forceFetchCloudflareIps(): String = io { requireApp().forceFetchCloudflareIPs() }

    suspend fun triggerCloudflareHealthCheck() = io { requireApp().triggerCFHealthCheck() }

    suspend fun removeInvalidCloudflareIps(): Int = io { requireApp().removeInvalidCFIPs() }

    // ------------------------------------------------------------------
    // Auto routing (Android: always OFF, read-only GFWList status)
    // ------------------------------------------------------------------

    suspend fun autoRoutingStatus(): GfwListStatus = io {
        GfwListStatus.fromJson(requireApp().getAutoRoutingStatusJSON())
    }

    // ------------------------------------------------------------------
    // Import / export
    // ------------------------------------------------------------------

    suspend fun exportConfig(): String = io { requireApp().exportConfigJSON() }

    suspend fun importConfig(content: String): ImportSummary = io {
        ImportSummary.fromJson(requireApp().importConfigWithSummaryJSON(content))
    }

    // ------------------------------------------------------------------
    // Evolution (tests run CA-free; only TLS-fragment/direct rules applicable)
    // ------------------------------------------------------------------

    suspend fun startEvolutionTest(domains: List<String>, enableIpv6: Boolean) = io {
        requireApp().startEvolutionTest(jsonArrayOfStrings(domains).toString(), enableIpv6)
    }

    suspend fun stopEvolutionTest() = io { requireApp().stopEvolutionTest() }

    suspend fun evolutionStatus(): EvolutionTask? = io {
        EvolutionTask.fromJson(requireApp().getEvolutionStatusJSON()).also { _evolution.value = it }
    }

    suspend fun applyEvolutionRule(ruleId: String) = io { requireApp().applyEvolutionRule(ruleId) }
}

/** A routing decision reported by the Go engine. */
data class RouteEvent(val domain: String, val mode: String, val timestamp: Long = System.currentTimeMillis())
