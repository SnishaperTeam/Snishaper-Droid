import { Call, Events, setTransport } from '@wailsio/runtime';

/**
 * SniShaper frontend <-> backend bindings (Android / WebView build).
 *
 * Transport
 * ---------
 * `@wailsio/runtime` can talk to an Android host natively: when the Kotlin
 * side injects `window.wails.invokeAsync`, the runtime switches to its Android
 * transport (see `@wailsio/runtime/dist/runtime.js`, `androidBridge`).
 *
 * That detection runs while the runtime module is *evaluated*, and the WebView
 * only attaches the native object to `window` once the page exists — so the
 * check can lose the race and the runtime silently falls back to HTTP, which
 * fails with *"Method not found"* because there is no Wails server behind the
 * synthetic `https://appassets.androidplatform.net` origin.
 *
 * Rather than depend on that timing, we install the transport explicitly with
 * `setTransport` as soon as this module is evaluated. The transport looks the
 * native bridge up lazily on every call, so it works no matter when the WebView
 * attaches it.
 *
 * The wire format is the Wails Android one:
 *   request  : `window.wails.invokeAsync(id, JSON.stringify({object, method, windowName, args, clientId}))`
 *   response : `window._wailsAndroidCallback(id, envelopeJson, null)`
 *              with `envelopeJson` = `{"ok":true,"data":…}` or `{"ok":false,"error":"…"}`
 *
 * The `snishaper/app.App.` prefix stays as-is: it is the Wails binding path
 * and the Android host resolves the bare method name from it.
 *
 * CA removal (Android is CA-free by design)
 * ------------------------------------------
 * The following desktop exports were DELETED and have no Android counterpart:
 *   ExportCert, GetCACertPEM, GetCACertPath, GetCAInstallStatus,
 *   GetInstalledCerts, InstallCA, OpenCAFile, OpenCertDir,
 *   RegenerateCert, UninstallCert
 * Android never installs a root CA, so the whole certificate surface is gone.
 *
 * Unsupported / desktop-only API mapping
 * --------------------------------------
 * The exports below keep their desktop NAME and SIGNATURE (pages import them),
 * but resolve with a benign default WITHOUT calling the backend, because the
 * Kotlin bridge does not implement them. They never reject, so the UI renders.
 *
 *   StartTUN / StopTUN                     -> real bridge call (VpnService)
 *       TUN is driven by the Android VpnService (TunVpnService).
 *   EnableSystemProxy / DisableSystemProxy -> null
 *       Android has no system-wide HTTP proxy setting.
 *   GetSystemProxyStatus                  -> { Enabled:false, Server:'', Override:'' }
 *   GetNetworkInterfaces                  -> []
 *   SetTUNInterface / SetTUNInterfaceExclude-> null
 *       Android routes via the VPN service; there is no outbound NIC picker.
 *   GetAutoStart / SetAutoStart           -> false / null
 *   GetCloseToTray / SetCloseToTray       -> false / null
 *   GetShowMainWindowOnAutoStart / Set…    -> false / null
 *   GetAutoEnableProxyOnAutoStart / Set…   -> false / null
 *   GetAutoEnableSysProxyOnAutoStart / Set-> false / null
 *   UpdateTrayMenu                        -> null
 *   QuitApp                               -> null
 *   WindowMinimise / WindowToggleMaximise -> null
 *   WindowClose / HandleWindowClose        -> null
 *   OpenLogFile                           -> null
 *   GetLogFiles                           -> []
 *   CleanOldLogs                          -> null
 *   StartLogCapture / StopLogCapture      -> null
 *   IsLogCaptureEnabled                   -> true
 *       Android shows the log ring buffer in the Logs page instead of files.
 *   GetPortOccupant / KillPortOccupant    -> null
 *   ProxySelfCheck / GetProxyDiagnostics  -> null
 *   CheckUpdate                           -> null
 *   GetPendingUpdate                      -> null
 *   DownloadUpdateAsset / InstallUpdateAsset-> null
 *   MeasureDownloadSources                -> []
 *   GetReleaseChannel / GetUpdateChannel  -> null
 *   SetUpdateChannel                      -> null
 *   GetDownloadSource / SetDownloadSource  -> null
 *   GetCustomDownloadSource / Set…         -> null
 *   GetMigrationEnabled / SetMigrationEnabled -> false / null
 *   GetMigrationServer / SetMigrationServer   -> '' / null
 *   TestMigration                         -> ''
 *   GetTutorialDone                       -> localStorage-backed boolean
 *   SetTutorialDone                       -> localStorage-backed no-op
 *   OpenURL                               -> window.open + optional host hook
 *
 * Everything else (proxy, rules, DNS, ECH, NAT64, evolution, Cloudflare,
 * settings, TUN config, logs, stats, IPv6, auto-routing, …) is passed straight
 * through to the Kotlin host using the desktop method name.
 */

/**
 * Installs the Android transport.
 *
 * The native bridge is resolved on every call instead of being captured once,
 * because the WebView attaches `window.wails` after this module is evaluated.
 */
const installAndroidTransport = () => {
  const native = () => {
    const w = (globalThis as any).wails;
    return w && typeof w.invokeAsync === 'function' ? w : null;
  };

  const pending = new Map<string, { resolve: (v: any) => void; reject: (e: any) => void }>();

  // The host calls this back with the response envelope.
  (globalThis as any)._wailsAndroidCallback = (id: string, response: string | null, error: string | null) => {
    const entry = pending.get(id);
    if (!entry) return;
    pending.delete(id);
    if (error) {
      entry.reject(new Error(error));
      return;
    }
    try {
      const envelope = JSON.parse(response ?? '{}');
      if (!envelope.ok) {
        entry.reject(new Error(envelope.error ?? 'unknown runtime call error'));
        return;
      }
      entry.resolve('text' in envelope ? envelope.text : envelope.data);
    } catch (e) {
      entry.reject(e);
    }
  };

  let counter = 0;
  const nextId = () => `snishaper-${Date.now().toString(36)}-${(counter++).toString(36)}`;

  setTransport({
    call(objectID: number, method: number, windowName: string, args: any) {
      return new Promise((resolve, reject) => {
        const bridge = native();
        if (!bridge) {
          reject(new Error('Snishaper native bridge is unavailable'));
          return;
        }
        const id = nextId();
        pending.set(id, { resolve, reject });
        try {
          bridge.invokeAsync(
            id,
            JSON.stringify({
              object: objectID,
              method,
              windowName,
              args: args ?? null,
              clientId: 'snishaper-android',
            }),
          );
        } catch (e) {
          pending.delete(id);
          reject(e);
        }
      });
    },
  });
};

installAndroidTransport();

const appCall = (name: string, ...args: any[]) => Call.ByName(`snishaper/app.App.${name}`, ...args);

/** Resolve with a fixed value; used for the unsupported desktop-only APIs. */
const unsupported = <T>(value: T): Promise<T> => Promise.resolve(value);

export const EventsOn = (eventName: string, callback: (data: any) => void) =>
  Events.On(eventName, (event) => callback(event.data));

// ---------------------------------------------------------------------------
// Startup / window / tray (desktop-only on Android)
// ---------------------------------------------------------------------------
export const GetAutoStart = () => unsupported(false);
export const SetAutoStart = (enabled: boolean) => unsupported<any>(null);
export const GetShowMainWindowOnAutoStart = () => unsupported(false);
export const SetShowMainWindowOnAutoStart = (enabled: boolean) => unsupported<any>(null);
export const GetAutoEnableProxyOnAutoStart = () => unsupported(false);
export const SetAutoEnableProxyOnAutoStart = (enabled: boolean) => unsupported<any>(null);
export const GetAutoEnableSysProxyOnAutoStart = () => unsupported(false);
export const SetAutoEnableSysProxyOnAutoStart = (enabled: boolean) => unsupported<any>(null);
export const GetAutoUpdateRules = () => appCall('GetAutoUpdateRules');
export const SetAutoUpdateRules = (enabled: boolean) => appCall('SetAutoUpdateRules', enabled);
export const UpdateRules = () => appCall('UpdateRules');
export const GetLanguage = () => appCall('GetLanguage');
export const SetLanguage = (lang: string) => appCall('SetLanguage', lang);
export const GetTheme = () => appCall('GetTheme');
export const SetTheme = (theme: string) => appCall('SetTheme', theme);
export const GetAppearanceMode = () => appCall('GetAppearanceMode');
export const SetAppearanceMode = (mode: string) => appCall('SetAppearanceMode', mode);

// ---------------------------------------------------------------------------
// TUN
// ---------------------------------------------------------------------------
export const GetTUNConfig = () => appCall('GetTUNConfig');
export const UpdateTUNConfig = (cfg: any) => appCall('UpdateTUNConfig', cfg);
export const GetTUNStatus = () => appCall('GetTUNStatus');
/**
 * TUN is the Android VpnService (system VPN consent). StartTUN replies
 * "started" when the service launches directly, or "consent" when the system
 * VPN permission dialog was opened — the caller then polls GetTUNStatus until
 * the tunnel is actually up.
 */
export const StartTUN = () => appCall('StartTUN');
export const StopTUN = () => appCall('StopTUN');
export const GetNetworkInterfaces = () => unsupported<any[]>([]);
export const SetTUNInterface = (name: string) => unsupported<any>(null);
export const SetTUNInterfaceExclude = (exclude: string) => unsupported<any>(null);

// ---------------------------------------------------------------------------
// Fragment
// ---------------------------------------------------------------------------
export const GetFragmentConfig = () => appCall('GetFragmentConfig');
export const SetFragmentConfig = (cfg: any) => appCall('SetFragmentConfig', cfg);
export const ResetFragmentConfig = () => appCall('ResetFragmentConfig');

// ---------------------------------------------------------------------------
// IPv6
// ---------------------------------------------------------------------------
export const GetIPv6Available = () => appCall('GetIPv6Available');
export const RefreshIPv6Check = () => appCall('RefreshIPv6Check');

// ---------------------------------------------------------------------------
// Rules / site groups / upstreams
// ---------------------------------------------------------------------------
export const AddSiteGroup = (sg: any) => appCall('AddSiteGroup', sg);
export const AddUpstream = (u: any) => appCall('AddUpstream', u);
export const DeleteECHProfile = (id: string) => appCall('DeleteECHProfile', id);
export const DeleteSiteGroup = (id: string) => appCall('DeleteSiteGroup', id);
export const DeleteUpstream = (id: string) => appCall('DeleteUpstream', id);
export const GetSiteGroups = () => appCall('GetSiteGroups');
export const UpdateSiteGroup = (sg: any) => appCall('UpdateSiteGroup', sg);
export const UpdateUpstream = (u: any) => appCall('UpdateUpstream', u);
export const GetRulesLayerInfo = () => appCall('GetRulesLayerInfo');
export const RevertSiteGroupToUpstream = (id: string) => appCall('RevertSiteGroupToUpstream', id);
export const RevertUpstreamConfigToUpstream = (id: string) => appCall('RevertUpstreamConfigToUpstream', id);

// ---------------------------------------------------------------------------
// Logs
// ---------------------------------------------------------------------------
export const ClearLogs = () => appCall('ClearLogs');
export const GetRecentLogs = (limit: number) => appCall('GetRecentLogs', limit);
/** Android keeps logs in an in-memory ring buffer, shown on the Logs page. */
export const GetLogFiles = () => unsupported<any[]>([]);
export const OpenLogFile = (name: string) => unsupported<any>(null);
export const CleanOldLogs = () => unsupported<any>(null);
export const StartLogCapture = () => unsupported<any>(null);
export const StopLogCapture = () => unsupported<any>(null);
export const IsLogCaptureEnabled = () => unsupported(true);

// ---------------------------------------------------------------------------
// System proxy (not available on Android)
// ---------------------------------------------------------------------------
export const EnableSystemProxy = () => unsupported<any>(null);
export const DisableSystemProxy = () => unsupported<any>(null);
export const GetSystemProxyStatus = () =>
  unsupported<any>({ Enabled: false, Server: '', Override: '' });

// ---------------------------------------------------------------------------
// Config import / export
// ---------------------------------------------------------------------------
export const ExportConfig = () => appCall('ExportConfig');
export const ImportConfig = (content: string) => appCall('ImportConfig', content);
export const ImportConfigWithSummary = (content: string) => appCall('ImportConfigWithSummary', content);

// ---------------------------------------------------------------------------
// ECH
// ---------------------------------------------------------------------------
export const FetchECHConfig = (domain: string, dohURL: string) => appCall('FetchECHConfig', domain, dohURL);
export const GetECHProfiles = () => appCall('GetECHProfiles');
export const UpsertECHProfile = (p: any) => appCall('UpsertECHProfile', p);

// ---------------------------------------------------------------------------
// Cloudflare IP pool
// ---------------------------------------------------------------------------
export const ForceFetchCloudflareIPs = () => appCall('ForceFetchCloudflareIPs');
export const GetCloudflareConfig = () => appCall('GetCloudflareConfig');
export const GetCloudflareIPStats = () => appCall('GetCloudflareIPStats');
export const RefreshCloudflareIPPool = () => appCall('RefreshCloudflareIPPool');
export const RemoveInvalidCFIPs = () => appCall('RemoveInvalidCFIPs');
export const TriggerCFHealthCheck = () => appCall('TriggerCFHealthCheck');
export const UpdateCloudflareConfig = (cfg: any) => appCall('UpdateCloudflareConfig', cfg);

// ---------------------------------------------------------------------------
// Tray / window / quit
// ---------------------------------------------------------------------------
export const UpdateTrayMenu = () => unsupported<any>(null);
export const QuitApp = () => unsupported<any>(null);
export const WindowClose = () => unsupported<any>(null);
export const WindowMinimise = () => unsupported<any>(null);
export const WindowToggleMaximise = () => unsupported<any>(null);
export const HandleWindowClose = () => unsupported<any>(null);
export const GetCloseToTray = () => unsupported(false);
export const SetCloseToTray = (enabled: boolean) => unsupported<any>(null);

// ---------------------------------------------------------------------------
// Proxy core
// ---------------------------------------------------------------------------
export const GetListenPort = () => appCall('GetListenPort');
export const SetListenPort = (port: number) => appCall('SetListenPort', port);
export const GetProxyMode = () => appCall('GetProxyMode');
export const SetProxyMode = (mode: string) => appCall('SetProxyMode', mode);
export const IsProxyRunning = () => appCall('IsProxyRunning');
export const StartProxy = () => appCall('StartProxy');
export const StopProxy = () => appCall('StopProxy');
export const GetStats = () => appCall('GetStats');
export const GetUpstreams = () => appCall('GetUpstreams');
export const ProxySelfCheck = () => unsupported<any>(null);
export const GetProxyDiagnostics = () => unsupported<any>(null);
export const GetPortOccupant = (port: number) => unsupported<any>(null);
export const KillPortOccupant = (pid: number) => unsupported<any>(null);

// ---------------------------------------------------------------------------
// Auto routing (GFW list)
// ---------------------------------------------------------------------------
export const GetAutoRoutingConfig = () => appCall('GetAutoRoutingConfig');
export const UpdateAutoRoutingConfig = (cfg: any) => appCall('UpdateAutoRoutingConfig', cfg);
export const GetAutoRoutingStatus = () => appCall('GetAutoRoutingStatus');

// ---------------------------------------------------------------------------
// Tutorial (persisted locally on Android)
// ---------------------------------------------------------------------------
const TUTORIAL_DONE_KEY = 'tutorial-done';
export const GetTutorialDone = () =>
  unsupported<boolean>(localStorage.getItem(TUTORIAL_DONE_KEY) === 'true');
export const SetTutorialDone = () => {
  try {
    localStorage.setItem(TUTORIAL_DONE_KEY, 'true');
  } catch {
    /* storage may be unavailable */
  }
  return unsupported<any>(null);
};

// ---------------------------------------------------------------------------
// About / update
// ---------------------------------------------------------------------------
export const GetAppVersion = () => appCall('GetAppVersion');
export const GetReleaseChannel = () => unsupported<any>(null);
export const CheckUpdate = () => unsupported<any>(null);
export const GetUpdateChannel = () => unsupported<any>(null);
export const SetUpdateChannel = (channel: string) => unsupported<any>(null);
export const GetDownloadSource = () => unsupported<any>(null);
export const SetDownloadSource = (src: string) => unsupported<any>(null);
export const GetCustomDownloadSource = () => unsupported<any>(null);
export const SetCustomDownloadSource = (prefix: string) => unsupported<any>(null);
export const MeasureDownloadSources = () => unsupported<any[]>([]);
export const GetPendingUpdate = () => unsupported<any>(null);
export const DownloadUpdateAsset = (url: string) => unsupported<any>(null);
export const InstallUpdateAsset = (localPath: string) => unsupported<any>(null);

/**
 * Open an external URL.
 *
 * Uses `window.open` (the Android WebView handles http/https links) and also
 * notifies the host through the optional `window.__snishaperOpenURL` hook so a
 * native intent can be used when the host provides one.
 */
export const OpenURL = (url: string) => {
  try {
    const hook = (window as any).__snishaperOpenURL;
    if (typeof hook === 'function') {
      hook(url);
      return unsupported<any>(null);
    }
  } catch {
    /* fall through to window.open */
  }
  try {
    window.open(url, '_blank');
  } catch {
    /* ignore */
  }
  return unsupported<any>(null);
};

// ---------------------------------------------------------------------------
// Evolution mode
// ---------------------------------------------------------------------------
export const StartEvolutionTest = (domains: string[], enableIPv6: boolean) =>
  appCall('StartEvolutionTest', domains, enableIPv6);
export const StopEvolutionTest = () => appCall('StopEvolutionTest');
export const ApplyEvolutionRule = (ruleId: string) => appCall('ApplyEvolutionRule', ruleId);
export const GetEvolutionTestStatus = () => appCall('GetEvolutionTestStatus');

// ---------------------------------------------------------------------------
// DNS nodes
// ---------------------------------------------------------------------------
export const GetDNSNodes = () => appCall('GetDNSNodes');
export const AddDNSNode = (n: any) => appCall('AddDNSNode', n);
export const UpdateDNSNode = (n: any) => appCall('UpdateDNSNode', n);
export const DeleteDNSNode = (id: string) => appCall('DeleteDNSNode', id);
export const SetDNSNodePriority = (id: string, targetIndex: number) =>
  appCall('SetDNSNodePriority', id, targetIndex);
export const TestDNSNode = (nodeID: string) => appCall('TestDNSNode', nodeID);

// ---------------------------------------------------------------------------
// NAT64 profiles
// ---------------------------------------------------------------------------
export const GetNAT64Profiles = () => appCall('GetNAT64Profiles');
export const AddNAT64Profile = (p: any) => appCall('AddNAT64Profile', p);
export const UpdateNAT64Profile = (p: any) => appCall('UpdateNAT64Profile', p);
export const DeleteNAT64Profile = (id: string) => appCall('DeleteNAT64Profile', id);
export const TestNAT64Profile = (prefix: string) => appCall('TestNAT64Profile', prefix);

// ---------------------------------------------------------------------------
// Migration service (desktop only)
// ---------------------------------------------------------------------------
export const GetMigrationEnabled = () => unsupported(false);
export const SetMigrationEnabled = (enabled: boolean) => unsupported<any>(null);
export const GetMigrationServer = () => unsupported<string>('');
export const SetMigrationServer = (server: string) => unsupported<any>(null);
export const TestMigration = (server: string) => unsupported<string>('');
