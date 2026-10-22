package com.snishaper.android.core

import org.json.JSONArray
import org.json.JSONObject

/**
 * Immutable view models for the JSON payloads returned by the gomobile wrapper.
 * Field names mirror the Go JSON tags from
 * `Snishaper-core/proxy` and `Snishaper-core/evolution` (see
 * docs/android-bridge-api.md §6).
 */

data class EngineStatus(
    val proxyRunning: Boolean = false,
    val tunRunning: Boolean = false,
    val tunSupported: Boolean = true,
    val tunMessage: String = "",
    val listenAddr: String = "127.0.0.1:8080",
    val listenPort: Int = 8080,
    val proxyMode: String = "",
    val ipv6Available: Boolean = true,
    val lastError: String = "",
) {
    companion object {
        fun fromJson(json: String): EngineStatus {
            val o = parseObject(json)
            return EngineStatus(
                proxyRunning = o.bool("proxyRunning"),
                tunRunning = o.bool("tunRunning"),
                tunSupported = o.bool("tunSupported", true),
                tunMessage = o.str("tunMessage"),
                listenAddr = o.str("listenAddr", "127.0.0.1:8080"),
                listenPort = o.int("listenPort", 8080),
                proxyMode = o.str("proxyMode"),
                ipv6Available = o.bool("ipv6Available", true),
                lastError = o.str("lastError"),
            )
        }
    }
}

data class Stats(val down: Long = 0L, val up: Long = 0L, val etc: Long = 0L) {
    companion object {
        fun fromJson(json: String): Stats {
            val o = parseObject(json)
            return Stats(o.long("down"), o.long("up"), o.long("etc"))
        }
    }
}

data class TunStatus(
    val supported: Boolean = false,
    val running: Boolean = false,
    val enabled: Boolean = false,
    val message: String = "",
) {
    companion object {
        fun fromJson(json: String): TunStatus {
            val o = parseObject(json)
            return TunStatus(o.bool("supported"), o.bool("running"), o.bool("enabled"), o.str("message"))
        }
    }
}

data class BuildInfo(
    val version: String = "",
    val channel: String = "stable",
    val coreVersion: String = "",
    val goVersion: String = "",
    val platform: String = "android",
) {
    companion object {
        fun fromJson(json: String): BuildInfo {
            val o = parseObject(json)
            return BuildInfo(
                o.str("version"), o.str("channel", "stable"),
                o.str("coreVersion"), o.str("goVersion"), o.str("platform", "android"),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rules (site groups) / upstreams
// ---------------------------------------------------------------------------

data class SiteGroup(
    val id: String = "",
    val name: String = "",
    val mode: String = "tls-rf", // Android: tls-rf | transparent | direct (built-in, read-only)
    val domains: List<String> = emptyList(),
    val upstream: String = "",
    val upstreams: List<String> = emptyList(),
    val website: String = "",
    val enabled: Boolean = true,
    val dnsMode: String = "",
    val useCfPool: Boolean = false,
    val nat64Enabled: Boolean = false,
    val nat64ProfileId: String = "",
    val frag: FragmentConfig? = null,
) {
    companion object {
        fun fromJson(o: JSONObject) = SiteGroup(
            id = o.str("id"),
            name = o.str("name"),
            mode = o.str("mode", "tls-rf"),
            domains = o.strList("domains"),
            upstream = o.str("upstream"),
            upstreams = o.strList("upstreams"),
            website = o.str("website"),
            enabled = o.bool("enabled", true),
            dnsMode = o.str("dns_mode"),
            useCfPool = o.bool("use_cf_pool"),
            nat64Enabled = o.bool("nat64_enabled"),
            nat64ProfileId = o.str("nat64_profile_id"),
            frag = o.obj("frag")?.let { FragmentConfig.fromJson(it) },
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("mode", mode)
        put("domains", jsonArrayOfStrings(domains))
        put("enabled", enabled)
        if (upstream.isNotEmpty()) put("upstream", upstream)
        if (upstreams.isNotEmpty()) put("upstreams", jsonArrayOfStrings(upstreams))
        if (website.isNotEmpty()) put("website", website)
        if (dnsMode.isNotEmpty()) put("dns_mode", dnsMode)
        put("use_cf_pool", useCfPool)
        put("nat64_enabled", nat64Enabled)
        if (nat64ProfileId.isNotEmpty()) put("nat64_profile_id", nat64ProfileId)
        frag?.let { put("frag", it.toJson()) }
        // CA-only fields (sni_fake / cert_verify / ech_*) are never sent from Android.
    }
}

data class Upstream(
    val id: String = "",
    val name: String = "",
    val type: String = "cf_preferred",
    val addresses: List<String> = emptyList(),
    val enabled: Boolean = true,
    val address: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject) = Upstream(
            id = o.str("id"), name = o.str("name"), type = o.str("type", "cf_preferred"),
            addresses = o.strList("addresses"), enabled = o.bool("enabled", true),
            address = o.str("address"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("type", type)
        put("addresses", jsonArrayOfStrings(addresses)); put("enabled", enabled)
        if (address.isNotEmpty()) put("address", address)
    }
}

// ---------------------------------------------------------------------------
// DoH nodes / ECH / NAT64 / fragment / Cloudflare
// ---------------------------------------------------------------------------

data class CertVerifyConfig(
    val mode: String = "",
    val names: List<String> = emptyList(),
    val suffixes: List<String> = emptyList(),
    val spkiSha256: List<String> = emptyList(),
    val allowUnknownAuthority: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject?) = if (o == null) CertVerifyConfig() else CertVerifyConfig(
            mode = o.str("mode"),
            names = o.strList("names"),
            suffixes = o.strList("suffixes"),
            spkiSha256 = o.strList("spki_sha256"),
            allowUnknownAuthority = o.bool("allow_unknown_authority"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        if (mode.isNotEmpty()) put("mode", mode)
        if (names.isNotEmpty()) put("names", jsonArrayOfStrings(names))
        if (suffixes.isNotEmpty()) put("suffixes", jsonArrayOfStrings(suffixes))
        if (spkiSha256.isNotEmpty()) put("spki_sha256", jsonArrayOfStrings(spkiSha256))
        if (allowUnknownAuthority) put("allow_unknown_authority", true)
    }

    fun isEmpty(): Boolean = mode.isEmpty() && names.isEmpty() && suffixes.isEmpty() &&
        spkiSha256.isEmpty() && !allowUnknownAuthority
}

data class DnsNode(
    val id: String = "",
    val name: String = "",
    val url: String = "",
    val sni: String = "",
    val ips: List<String> = emptyList(),
    val echEnabled: Boolean = false,
    val echProfileId: String = "",
    val echAutoUpdate: Boolean = false,
    val quic: Boolean = false,
    val enabled: Boolean = true,
    val certVerify: CertVerifyConfig = CertVerifyConfig(),
) {
    companion object {
        fun fromJson(o: JSONObject) = DnsNode(
            id = o.str("id"), name = o.str("name"), url = o.str("url"),
            sni = o.str("sni"), ips = o.strList("ips"),
            echEnabled = o.bool("ech_enabled"), echProfileId = o.str("ech_profile_id"),
            echAutoUpdate = o.bool("ech_auto_update"), quic = o.bool("quic"),
            enabled = o.bool("enabled", true),
            certVerify = CertVerifyConfig.fromJson(o.obj("cert_verify")),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("url", url); put("enabled", enabled)
        if (sni.isNotEmpty()) put("sni", sni)
        if (ips.isNotEmpty()) put("ips", jsonArrayOfStrings(ips))
        put("ech_enabled", echEnabled)
        if (echProfileId.isNotEmpty()) put("ech_profile_id", echProfileId)
        put("ech_auto_update", echAutoUpdate)
        put("quic", quic)
        if (!certVerify.isEmpty()) put("cert_verify", certVerify.toJson())
    }
}

data class ECHProfile(
    val id: String = "",
    val name: String = "",
    val config: String = "",
    val discoveryDomain: String = "",
    val dohUpstream: String = "",
    val autoUpdate: Boolean = false,
) {
    companion object {
        fun fromJson(o: JSONObject) = ECHProfile(
            id = o.str("id"), name = o.str("name"), config = o.str("config"),
            discoveryDomain = o.str("discovery_domain"), dohUpstream = o.str("doh_upstream"),
            autoUpdate = o.bool("auto_update"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("config", config)
        if (discoveryDomain.isNotEmpty()) put("discovery_domain", discoveryDomain)
        if (dohUpstream.isNotEmpty()) put("doh_upstream", dohUpstream)
        put("auto_update", autoUpdate)
    }
}

data class Nat64Profile(val id: String = "", val name: String = "", val prefix: String = "") {
    companion object {
        fun fromJson(o: JSONObject) = Nat64Profile(o.str("id"), o.str("name"), o.str("prefix"))
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("prefix", prefix)
    }
}

data class FragmentConfig(
    val numRecords: Int = 4,
    val numSegments: Int = 1,
    val intervalMs: Int? = null,
    val modMinorVer: Boolean? = null,
    val oob: Boolean? = null,
    val oobEx: Boolean? = null,
) {
    companion object {
        fun fromJson(o: JSONObject) = FragmentConfig(
            numRecords = o.int("num_records", 4),
            numSegments = o.int("num_segments", 1),
            intervalMs = if (o.isNull("interval_ms")) null else o.optInt("interval_ms"),
            modMinorVer = if (o.isNull("mod_minor_ver")) null else o.optBoolean("mod_minor_ver"),
            oob = if (o.isNull("oob")) null else o.optBoolean("oob"),
            oobEx = if (o.isNull("oob_ex")) null else o.optBoolean("oob_ex"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("num_records", numRecords)
        put("num_segments", numSegments)
        intervalMs?.let { put("interval_ms", it) }
        modMinorVer?.let { put("mod_minor_ver", it) }
        oob?.let { put("oob", it) }
        oobEx?.let { put("oob_ex", it) }
    }
}

data class CloudflareConfig(
    val preferredIps: List<String> = emptyList(),
    val autoUpdate: Boolean = false,
    val apiKey: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject) = CloudflareConfig(
            preferredIps = o.strList("preferred_ips"),
            autoUpdate = o.bool("auto_update"),
            apiKey = o.str("api_key"),
        )
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("preferred_ips", jsonArrayOfStrings(preferredIps))
        put("auto_update", autoUpdate)
        put("api_key", apiKey)
    }
}

data class CfIpStat(
    val ip: String = "",
    val latency: String = "",
    val failures: Int = 0,
    val lastCheck: String = "",
) {
    companion object {
        fun fromJson(o: JSONObject) = CfIpStat(
            o.str("ip"), o.str("latency"), o.int("failures"), o.str("last_check"),
        )
    }
}

data class GfwListStatus(
    val enabled: Boolean = false,
    val mode: String = "",
    val domainCount: Int = 0,
) {
    companion object {
        fun fromJson(json: String): GfwListStatus {
            val o = parseObject(json)
            return GfwListStatus(o.bool("enabled"), o.str("mode"), o.int("domain_count"))
        }
    }
}

data class ImportSummary(
    val total: Int = 0,
    val added: Int = 0,
    val overwritten: Int = 0,
    val skipped: Int = 0,
    val rewritten: Int = 0,
    val format: String = "",
    val rawJson: String = "",
) {
    companion object {
        fun fromJson(json: String): ImportSummary {
            val o = parseObject(json)
            return ImportSummary(
                total = o.int("total"), added = o.int("added"),
                overwritten = o.int("overwritten"), skipped = o.int("skipped"),
                rewritten = o.int("rewritten"), format = o.str("format"), rawJson = json,
            )
        }
    }
}

data class DnsTestResult(
    val success: Boolean = false,
    val latencyMs: Long = 0L,
    val ips: List<String> = emptyList(),
    val error: String = "",
) {
    companion object {
        fun fromJson(json: String): DnsTestResult {
            val o = parseObject(json)
            return DnsTestResult(o.bool("success"), o.long("latencyMs"), o.strList("ips"), o.str("error"))
        }
    }
}

data class Nat64TestResult(
    val success: Boolean = false,
    val latencyMs: Long = 0L,
    val error: String = "",
) {
    companion object {
        fun fromJson(json: String): Nat64TestResult {
            val o = parseObject(json)
            return Nat64TestResult(o.bool("success"), o.long("latencyMs"), o.str("error"))
        }
    }
}

// ---------------------------------------------------------------------------
// Settings (only the Android-relevant subset)
// ---------------------------------------------------------------------------

data class AppSettings(
    val listenPort: String = "8080",
    val language: String = "zh",
    val theme: String = "dark",
    val appearanceMode: String = "system",
    val autoUpdateRules: Boolean = true,
    val tun: FragmentConfig? = null,
    val fragment: FragmentConfig = FragmentConfig(),
    val cloudflare: CloudflareConfig = CloudflareConfig(),
    val rawJson: String = "",
) {
    companion object {
        fun fromJson(json: String): AppSettings {
            val o = parseObject(json)
            return AppSettings(
                listenPort = o.str("listen_port", "8080"),
                language = o.str("language", "zh"),
                theme = o.str("theme", "dark"),
                appearanceMode = o.str("appearance_mode", "system"),
                autoUpdateRules = o.bool("auto_update_rules", true),
                tun = o.obj("tun")?.let { FragmentConfig.fromJson(it) },
                fragment = o.obj("fragment")?.let { FragmentConfig.fromJson(it) } ?: FragmentConfig(),
                cloudflare = o.obj("cloudflare_config")?.let { CloudflareConfig.fromJson(it) }
                    ?: CloudflareConfig(),
                rawJson = json,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Evolution (rule evolution) test task
// ---------------------------------------------------------------------------

data class EvolutionStep(
    val stepName: String = "",
    val success: Boolean = false,
    val delay: Long = 0L,
    val error: String = "",
)

data class EvolutionTempRule(
    val id: String = "",
    val name: String = "",
    val domain: String = "",
    val mode: String = "",
    val sniFake: String = "",
    val echEnabled: Boolean = false,
    val method: String = "",
    val isApplied: Boolean = false,
    val useCfPool: Boolean = false,
    val nat64Enabled: Boolean = false,
    val nat64ProfileId: String = "",
)

data class EvolutionResult(
    val domain: String = "",
    val reachable: Boolean = false,
    val method: String = "",
    val resolvedIps: List<String> = emptyList(),
    val bestIp: String = "",
    val isCloudflare: Boolean = false,
    val delay: Long = 0L,
    val error: String = "",
    val timestamp: String = "",
    val steps: List<EvolutionStep> = emptyList(),
    val generatedRule: EvolutionTempRule? = null,
)

data class EvolutionTask(
    val id: String = "",
    val domains: List<String> = emptyList(),
    val status: String = "pending",
    val progress: Int = 0,
    val total: Int = 0,
    val results: List<EvolutionResult> = emptyList(),
    val tempRules: List<EvolutionTempRule> = emptyList(),
    /** The engine's original payload, forwarded verbatim to the WebView. */
    val rawJson: String = "",
) {
    val running: Boolean get() = status == "running" || status == "pending"
    val appliedCount: Int get() = tempRules.count { it.isApplied }
    val successCount: Int get() = results.count { it.reachable }
    val failureCount: Int get() = results.count { !it.reachable }

    companion object {
        fun fromJson(json: String): EvolutionTask? {
            if (json.isBlank() || json == "{}") return null
            val o = parseObject(json)
            if (o.length() == 0) return null
            val results = o.objList("results").map { r ->
                EvolutionResult(
                    domain = r.str("domain"),
                    reachable = r.bool("reachable"),
                    method = r.str("method"),
                    resolvedIps = r.strList("resolved_ips"),
                    bestIp = r.str("best_ip"),
                    isCloudflare = r.bool("is_cloudflare"),
                    delay = r.long("delay"),
                    error = r.str("error"),
                    timestamp = r.str("timestamp"),
                    steps = r.objList("step_results").map { s ->
                        EvolutionStep(
                            s.str("step_name"), s.bool("success"), s.long("delay"), s.str("error"),
                        )
                    },
                    generatedRule = r.obj("generated_rule")?.let { g ->
                        EvolutionTempRule(
                            id = g.str("id"), name = g.str("name"), domain = g.str("domain"),
                            mode = g.str("mode"), sniFake = g.str("sni_fake"),
                            echEnabled = g.bool("ech_enabled"), method = g.str("method"),
                            isApplied = g.bool("is_applied"), useCfPool = g.bool("use_cf_pool"),
                            nat64Enabled = g.bool("nat64_enabled"),
                            nat64ProfileId = g.str("nat64_profile_id"),
                        )
                    },
                )
            }
            // The Go TestTask carries temp rules through the tester state; the bridge
            // exposes them inside the task JSON when available.
            val tempRules = o.objList("temp_rules").map { g ->
                EvolutionTempRule(
                    id = g.str("id"), name = g.str("name"), domain = g.str("domain"),
                    mode = g.str("mode"), sniFake = g.str("sni_fake"),
                    echEnabled = g.bool("ech_enabled"), method = g.str("method"),
                    isApplied = g.bool("is_applied"), useCfPool = g.bool("use_cf_pool"),
                    nat64Enabled = g.bool("nat64_enabled"),
                    nat64ProfileId = g.str("nat64_profile_id"),
                )
            }.ifEmpty { results.mapNotNull { it.generatedRule } }
            return EvolutionTask(
                id = o.str("id"),
                domains = o.strList("domains"),
                status = o.str("status", "pending"),
                progress = o.int("progress"),
                total = o.int("total"),
                results = results,
                tempRules = tempRules,
                rawJson = json,
            )
        }
    }
}

/** Convenience: parse a JSON array of site groups. */
fun parseSiteGroups(json: String): List<SiteGroup> = parseObjectArray(json).map { SiteGroup.fromJson(it) }

fun parseUpstreams(json: String): List<Upstream> = parseObjectArray(json).map { Upstream.fromJson(it) }

fun parseDnsNodes(json: String): List<DnsNode> = parseObjectArray(json).map { DnsNode.fromJson(it) }

fun parseEchProfiles(json: String): List<ECHProfile> = parseObjectArray(json).map { ECHProfile.fromJson(it) }

fun parseNat64Profiles(json: String): List<Nat64Profile> = parseObjectArray(json).map { Nat64Profile.fromJson(it) }

fun parseCfIpStats(json: String): List<CfIpStat> = parseObjectArray(json).map { CfIpStat.fromJson(it) }
