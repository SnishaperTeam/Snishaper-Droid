import { Call, Events, setTransport } from '@wailsio/runtime';

const installAndroidTransport = () => {
  const native = () => {
    const w = (globalThis as any).wails;
    return w && typeof w.invokeAsync === 'function' ? w : null;
  };

  const pending = new Map<string, { resolve: (v: any) => void; reject: (e: any) => void }>();

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

export const EventsOn = (eventName: string, callback: (data: any) => void) =>
  Events.On(eventName, (event) => callback(event.data));

export interface BuildInfo {
  Version: string;
  Channel: string;
  CoreVersion: string;
  GoVersion: string;
  Platform: string;
}

export const GetLanguage = () => appCall('GetLanguage');
export const SetLanguage = (lang: string) => appCall('SetLanguage', lang);
export const GetTheme = () => appCall('GetTheme');
export const SetTheme = (theme: string) => appCall('SetTheme', theme);
export const GetAppearanceMode = () => appCall('GetAppearanceMode');
export const SetAppearanceMode = (mode: string) => appCall('SetAppearanceMode', mode);
export const GetAutoUpdateRules = () => appCall('GetAutoUpdateRules');
export const SetAutoUpdateRules = (enabled: boolean) => appCall('SetAutoUpdateRules', enabled);
export const UpdateRules = () => appCall('UpdateRules');

export const GetAppVersion = () => appCall('GetAppVersion');
export const GetBuildInfo = () => appCall('GetBuildInfo');

export const GetTUNConfig = () => appCall('GetTUNConfig');
export const UpdateTUNConfig = (cfg: any) => appCall('UpdateTUNConfig', cfg);
export const GetTUNStatus = () => appCall('GetTUNStatus');
export const StartTUN = () => appCall('StartTUN');
export const StopTUN = () => appCall('StopTUN');

export const GetIPv6Available = () => appCall('GetIPv6Available');
export const RefreshIPv6Check = () => appCall('RefreshIPv6Check');

export const GetSiteGroups = () => appCall('GetSiteGroups');
export const AddSiteGroup = (sg: any) => appCall('AddSiteGroup', sg);
export const UpdateSiteGroup = (sg: any) => appCall('UpdateSiteGroup', sg);
export const DeleteSiteGroup = (id: string) => appCall('DeleteSiteGroup', id);
export const GetRulesLayerInfo = () => appCall('GetRulesLayerInfo');
export const RevertSiteGroupToUpstream = (id: string) => appCall('RevertSiteGroupToUpstream', id);
export const ExportConfig = () => appCall('ExportConfig');
export const ImportConfigWithSummary = (content: string) => appCall('ImportConfigWithSummary', content);

export const GetDNSNodes = () => appCall('GetDNSNodes');
export const AddDNSNode = (n: any) => appCall('AddDNSNode', n);
export const UpdateDNSNode = (n: any) => appCall('UpdateDNSNode', n);
export const DeleteDNSNode = (id: string) => appCall('DeleteDNSNode', id);
export const SetDNSNodePriority = (id: string, targetIndex: number) =>
  appCall('SetDNSNodePriority', id, targetIndex);
export const TestDNSNode = (nodeID: string) => appCall('TestDNSNode', nodeID);

export const GetECHProfiles = () => appCall('GetECHProfiles');
export const UpsertECHProfile = (p: any) => appCall('UpsertECHProfile', p);
export const DeleteECHProfile = (id: string) => appCall('DeleteECHProfile', id);
export const FetchECHConfig = (domain: string, dohURL: string) => appCall('FetchECHConfig', domain, dohURL);

export const GetNAT64Profiles = () => appCall('GetNAT64Profiles');
export const AddNAT64Profile = (p: any) => appCall('AddNAT64Profile', p);
export const UpdateNAT64Profile = (p: any) => appCall('UpdateNAT64Profile', p);
export const DeleteNAT64Profile = (id: string) => appCall('DeleteNAT64Profile', id);
export const TestNAT64Profile = (prefix: string) => appCall('TestNAT64Profile', prefix);

export const GetMigrationServer = () => appCall('GetMigrationServer');
export const SetMigrationServer = (server: string) => appCall('SetMigrationServer', server);
export const TestMigration = (server: string) => appCall('TestMigration', server);

export const GetCloudflareConfig = () => appCall('GetCloudflareConfig');
export const UpdateCloudflareConfig = (cfg: any) => appCall('UpdateCloudflareConfig', cfg);
export const GetCloudflareIPStats = () => appCall('GetCloudflareIPStats');
export const ForceFetchCloudflareIPs = () => appCall('ForceFetchCloudflareIPs');
export const TriggerCFHealthCheck = () => appCall('TriggerCFHealthCheck');
export const RemoveInvalidCFIPs = () => appCall('RemoveInvalidCFIPs');

export const GetListenPort = () => appCall('GetListenPort');
export const SetListenPort = (port: number) => appCall('SetListenPort', port);
export const GetProxyMode = () => appCall('GetProxyMode');
export const IsProxyRunning = () => appCall('IsProxyRunning');
export const StartProxy = () => appCall('StartProxy');
export const StopProxy = () => appCall('StopProxy');
export const GetStats = () => appCall('GetStats');

export const GetAutoRoutingStatus = () => appCall('GetAutoRoutingStatus');

export const GetRecentLogs = (limit: number) => appCall('GetRecentLogs', limit);
export const ClearLogs = () => appCall('ClearLogs');

export const StartEvolutionTest = (domains: string[], enableIPv6: boolean) =>
  appCall('StartEvolutionTest', domains, enableIPv6);
export const StopEvolutionTest = () => appCall('StopEvolutionTest');
export const ApplyEvolutionRule = (ruleId: string) => appCall('ApplyEvolutionRule', ruleId);
export const GetEvolutionTestStatus = () => appCall('GetEvolutionTestStatus');

export const InstallCA = () => appCall('InstallCA');
export const RegenerateCert = () => appCall('RegenerateCert');
export const UninstallCert = (thumbprint: string) => appCall('UninstallCert', thumbprint);
export const GetCAInstallStatus = () => appCall('GetCAInstallStatus');
export const GetInstalledCerts = () => appCall('GetInstalledCerts');
export const GetCACertPEM = () => appCall('GetCACertPEM');

const TUTORIAL_DONE_KEY = 'tutorial-done';

export const GetTutorialDone = () =>
  Promise.resolve(localStorage.getItem(TUTORIAL_DONE_KEY) === 'true');

export const SetTutorialDone = () => {
  try {
    localStorage.setItem(TUTORIAL_DONE_KEY, 'true');
  } catch {
    return Promise.resolve(null);
  }
  return Promise.resolve(null);
};

export const OpenURL = (url: string) => {
  try {
    const hook = (window as any).__snishaperOpenURL;
    if (typeof hook === 'function') {
      hook(url);
      return Promise.resolve(null);
    }
  } catch {
    return Promise.resolve(null);
  }
  try {
    window.open(url, '_blank');
  } catch {
    return Promise.resolve(null);
  }
  return Promise.resolve(null);
};
