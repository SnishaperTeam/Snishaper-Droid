import React, { useState, useEffect, useCallback } from 'react';
import {
  Download, FolderOpen, RefreshCcw, Monitor, Anchor,
  Globe, Bolt, Delete,
  AlertCircle, ShieldAlert, Sun, Moon, Wifi, Settings as SettingsIcon
} from '../lib/icons';
import {
  SetListenPort,
  SetAutoUpdateRules,
  GetTUNConfig, UpdateTUNConfig, GetTUNStatus,
  RegenerateCert, GetCAInstallStatus, GetInstalledCerts, UninstallCert, GetCACertPEM,
  GetCloudflareConfig, GetCloudflareIPStats,
  ForceFetchCloudflareIPs, TriggerCFHealthCheck,
  SetLanguage, RefreshIPv6Check,
  SetAppearanceMode,
} from '../api/bindings';
import {
  Box, Button, TextField, Select, MenuItem, FormControl, InputLabel, Switch,
  FormControlLabel, Typography, Stack, Grid, CircularProgress,
} from '@mui/material';
import { alpha } from '@mui/material/styles';
import { toast } from '../lib/toast';
import { parseLatencyMs } from '../lib/utils';
import { useTranslation } from '../i18n/I18nContext';
import { useColorScheme } from '@mui/material';
import { availableThemes } from '../theme';

interface SettingsProps {
  cache: any;
  onCacheUpdate: (patch: any) => void;
  currentThemeId: string;
  onThemeChange: (id: string) => void;
}

const SectionHeader = ({ icon, label, action }: { icon: React.ReactNode; label: string; action?: React.ReactNode }) => (
  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', px: 1 }}>
    <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
      {icon}
      <Typography variant="body2" sx={{ fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </Typography>
    </Stack>
    {action}
  </Box>
);

const SettingRowInline = ({ icon, title, desc, children }: { icon: React.ReactNode; title: string; desc?: string; children: React.ReactNode }) => (
  <Box sx={{ p: 2.5, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: { xs: 1.5, sm: 3 }, flexWrap: { xs: 'wrap', sm: 'nowrap' } }}>
    <Stack direction="row" spacing={1.5} sx={{ flex: 1, minWidth: 0 }}>
      <Box sx={{ width: 40, height: 40, borderRadius: 1, bgcolor: 'primary.main', color: 'primary.contrastText', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {icon}
      </Box>
      <Box sx={{ minWidth: 0, py: 0.25 }}>
        <Typography variant="body2" sx={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>{title}</Typography>
        {desc && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{desc}</Typography>}
      </Box>
    </Stack>
    <Box sx={{ flexShrink: 0, width: { xs: '100%', sm: 'auto' } }}>
      {children}
    </Box>
  </Box>
);

const StackedRow = ({ icon, title, desc, children, ...rest }: { icon: React.ReactNode; title: string; desc?: string; children: React.ReactNode } & React.ComponentProps<typeof Box>) => (
  <Box {...rest} sx={{ p: 2.5, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2 }}>
    <Stack direction="row" spacing={1.5} sx={{ mb: desc ? 1.5 : 0, alignItems: 'center' }}>
      <Box sx={{ width: 40, height: 40, borderRadius: 1, bgcolor: 'primary.main', color: 'primary.contrastText', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {icon}
      </Box>
      <Box>
        <Typography variant="body2" sx={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>{title}</Typography>
        {desc && <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>{desc}</Typography>}
      </Box>
    </Stack>
    {children}
  </Box>
);

const Settings: React.FC<SettingsProps> = ({ cache, onCacheUpdate, currentThemeId, onThemeChange }) => {
  const { t, language, setLanguage: setI18nLanguage } = useTranslation();
  const { mode, setMode } = useColorScheme();
  const [port, setPort] = useState(String(cache.port ?? ''));
  const [autoUpdateRules, setAutoUpdateRules] = useState(cache.autoUpdateRules !== false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isCheckingHealth, setIsCheckingHealth] = useState(false);
  const tunStatus = cache.tunStatus;
  const caStatus = cache.caStatus;
  const installedCerts = cache.installedCerts || [];
  const [isCertBusy, setIsCertBusy] = useState(false);
  const [ipStats, setIpStats] = useState<any[]>(cache.ipStats || []);
  const [isIpv6Checking, setIsIpv6Checking] = useState(false);
  const ipv6Available = cache.ipv6Available !== false;
  const [mtu, setMtu] = useState(String(cache.tunConfig?.mtu ?? 1500));

  const bgColor = 'action.hover';

  const saveMtu = useCallback(async () => {
    const parsed = parseInt(mtu, 10);
    if (Number.isNaN(parsed) || parsed < 576 || parsed > 9000) {
      setMtu(String(cache.tunConfig?.mtu ?? 1500));
      toast.error(t('common.failed'), t('settings.mtu_invalid'));
      return;
    }
    try {
      const cfg = await GetTUNConfig();
      await UpdateTUNConfig({ ...(cfg || {}), mtu: parsed });
      onCacheUpdate({ tunConfig: { ...(cfg || {}), mtu: parsed } });
      toast.success(t('settings.notifications.updated'), `${t('settings.mtu_label')} ${parsed}`);
    } catch (err: any) {
      toast.error(t('common.failed'), String(err));
    }
  }, [mtu, cache.tunConfig, onCacheUpdate, t]);

  const reloadCriticalData = useCallback(async () => {
    try {
      const [tunCfg, tunState, cf, ca, certs, stats] = await Promise.all([
        GetTUNConfig(), GetTUNStatus(), GetCloudflareConfig(),
        GetCAInstallStatus(), GetInstalledCerts(), GetCloudflareIPStats()
      ]);
      if (stats) setIpStats(stats);
      if (tunCfg) setMtu(String(tunCfg.mtu ?? 1500));
      onCacheUpdate({
        tunConfig: tunCfg || cache.tunConfig, tunStatus: tunState || cache.tunStatus,
        cfConfig: cf || cache.cfConfig, caStatus: ca || cache.caStatus,
        installedCerts: certs || cache.installedCerts
      });
    } catch { /* ignore */ }
  }, [cache, onCacheUpdate]);

  useEffect(() => {
    void reloadCriticalData();
    TriggerCFHealthCheck().catch(() => {});
    const ipTimer = setInterval(async () => {
      const stats = await GetCloudflareIPStats();
      if (stats) setIpStats(stats);
    }, 5000);
    return () => clearInterval(ipTimer);
  }, [reloadCriticalData]);

  const handleRegenerateCert = async () => {
    setIsCertBusy(true);
    try {
      await RegenerateCert();
      await reloadCriticalData();
      toast.success(t('settings.ca_management.reset_success'));
    } catch (err: any) {
      toast.error(t('common.failed'), String(err));
    } finally { setIsCertBusy(false); }
  };

  const handleUninstallCert = async (token: string) => {
    if (!token) return;
    setIsCertBusy(true);
    try {
      await UninstallCert(token);
      await reloadCriticalData();
      toast.success(t('common.success'));
    } catch (err: any) {
      toast.error(t('common.failed'), String(err));
    } finally { setIsCertBusy(false); }
  };

  const handleExportCert = async () => {
    try {
      const pem = await GetCACertPEM();
      if (!pem) {
        toast.error(t('common.failed'), t('settings.ca_management.export_empty'));
        return;
      }
      const blob = new Blob([pem], { type: 'application/x-x509-ca-cert' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = 'snishaper-ca.crt';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(url);
      toast.success(t('common.success'), 'snishaper-ca.crt');
    } catch (err: any) {
      toast.error(t('common.failed'), String(err));
    }
  };

  const handleSavePort = async () => {
    const parsed = parseInt(port, 10);
    if (!port || Number.isNaN(parsed) || parsed < 1 || parsed > 65535) {
      setPort(String(cache.port ?? ''));
      toast.error(t('common.failed'), t('settings.notifications.port_invalid'));
      return;
    }
    await SetListenPort(parsed);
    onCacheUpdate({ port: parsed });
    toast.success(t('settings.notifications.updated'), `${t('settings.port_label')} ${parsed}`);
  };

  const handleToggleAutoUpdateRules = async (val: boolean) => {
    setAutoUpdateRules(val);
    try {
      await SetAutoUpdateRules(val);
      onCacheUpdate({ autoUpdateRules: val });
      toast.success(t('settings.notifications.updated'));
    } catch (err: any) {
      setAutoUpdateRules(!val);
      toast.error(t('common.failed'), String(err));
    }
  };

  const handleLanguageChange = async (lang: string) => {
    await SetLanguage(lang);
    setI18nLanguage(lang as any);
    onCacheUpdate({ language: lang });
    toast.success(t('common.success'));
  };

  const handleFetchIPs = async () => {
    setIsRefreshing(true);
    try {
      await ForceFetchCloudflareIPs();
      await reloadCriticalData();
      toast.success(t('settings.cf_pool.fetch_now'));
    } catch (err: any) {
      toast.error(t('common.failed'), String(err?.message || err));
    } finally { setIsRefreshing(false); }
  };

  const handleRefreshIPv6 = async () => {
    setIsIpv6Checking(true);
    try {
      const available = await RefreshIPv6Check();
      onCacheUpdate({ ipv6Available: available === true });
      toast[available ? 'success' : 'error'](available ? t('network.ipv6_ok') : t('network.ipv6_disabled_title'), available ? t('network.ipv6_ok_desc') : t('network.ipv6_disabled_desc'));
    } catch (err: any) {
      toast.error(t('common.failed'), String(err));
    } finally { setIsIpv6Checking(false); }
  };

  const handleHealthCheck = async () => {
    setIsCheckingHealth(true);
    try {
      await TriggerCFHealthCheck();
      await reloadCriticalData();
      window.setTimeout(() => { void reloadCriticalData(); }, 1200);
      window.setTimeout(() => { void reloadCriticalData(); }, 3000);
      toast.info(t('common.loading'));
    } finally { window.setTimeout(() => setIsCheckingHealth(false), 1200); }
  };

  return (
    <Box sx={{ flexGrow: 1, minHeight: 0, width: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ pt: 4, flexShrink: 0, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'flex-end' }, gap: 2, mb: 6 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{ p: 1.25, borderRadius: 1.5, border: 1, color: 'primary.main', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1), borderColor: (theme) => alpha(theme.palette.primary.main, 0.1), display: 'flex' }}>
            <SettingsIcon size={20} />
          </Box>
          <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>
            {t('settings.title')}
          </Typography>
        </Box>
      </Box>

      <Box sx={{ flexGrow: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', width: '100%', pb: 6 }}>
        <Grid container spacing={3}>
          <Grid size={12}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <SectionHeader icon={<Anchor size={18} />} label={t('settings.tabs.general')} />

              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <SettingRowInline icon={<Monitor size={18} />} title={t('settings.port_title')} desc={t('settings.port_desc')}>
                  <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                    <TextField
                      label={t('settings.port_label')}
                      type="text"
                      size="small"
                      value={port}
                      onChange={(e) => setPort(e.target.value.replace(/\D/g, '').slice(0, 5))}
                      sx={{ width: 120, '& input': { fontSize: '0.875rem', textAlign: 'center' } }}
                    />
                    <Button size="small" variant="contained" color="primary" onClick={handleSavePort}>
                      {t('common.apply')}
                    </Button>
                  </Stack>
                </SettingRowInline>

                <SettingRowInline title={t('settings.tutorial.title')} desc={t('settings.tutorial.desc')} icon={<RefreshCcw size={18} />}>
                  <Button size="small" variant="outlined" color="primary" onClick={() => window.dispatchEvent(new CustomEvent('app:restart-tutorial'))}>
                    {t('settings.tutorial.replay')}
                  </Button>
                </SettingRowInline>

                <SettingRowInline title={t('settings.language.title')} desc={t('settings.language.desc')} icon={<Globe size={18} />}>
                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', bgcolor: bgColor, border: 1, borderColor: 'divider', borderRadius: 2, p: 0.5 }} role="radiogroup" aria-label={t('settings.select_language')}>
                    {(['zh', 'en', 'ru'] as const).map((lang) => (
                      <Box
                        key={lang}
                        component="button"
                        type="button"
                        sx={{
                          px: 1.5, py: 0.5, fontSize: '0.75rem', fontWeight: 'bold', borderRadius: 1, border: 'none', cursor: 'pointer',
                          color: language === lang ? '#fff' : 'text.secondary',
                          bgcolor: language === lang ? 'primary.main' : 'transparent',
                          '&:hover': { color: 'text.primary' },
                        }}
                        onClick={() => handleLanguageChange(lang)}
                        role="radio"
                        aria-checked={language === lang}
                      >
                        {lang === 'zh' ? '中文' : lang === 'en' ? 'English' : 'Русский'}
                      </Box>
                    ))}
                  </Stack>
                </SettingRowInline>

                <SettingRowInline title={t('settings.appearance.title')} desc={t('settings.appearance.desc')} icon={mode === 'light' ? <Sun size={18} /> : <Moon size={18} />}>
                  <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', bgcolor: bgColor, border: 1, borderColor: 'divider', borderRadius: 2, p: 0.5 }} role="radiogroup" aria-label={t('settings.select_theme')}>
                    {([
                      { id: 'light' as const, icon: Sun, label: t('settings.appearance.light') },
                      { id: 'dark' as const, icon: Moon, label: t('settings.appearance.dark') },
                      { id: 'system' as const, icon: Monitor, label: t('settings.appearance.system') },
                    ]).map((opt) => (
                      <Box
                        key={opt.id}
                        component="button"
                        type="button"
                        sx={{
                          display: 'flex', alignItems: 'center', gap: 0.25, px: 1.5, py: 0.75,
                          fontSize: '0.75rem', fontWeight: 'bold', borderRadius: 1, border: 'none', cursor: 'pointer',
                          color: mode === opt.id ? '#fff' : 'text.secondary',
                          bgcolor: mode === opt.id ? 'primary.main' : 'transparent',
                          '&:hover': { color: 'text.primary' },
                        }}
                        onClick={() => { setMode(opt.id); SetAppearanceMode(opt.id).catch(() => {}); }}
                        role="radio"
                        aria-checked={mode === opt.id}
                      >
                        <opt.icon size={14} />
                        {opt.label}
                      </Box>
                    ))}
                  </Stack>
                </SettingRowInline>

                <SettingRowInline title={t('settings.theme.label')} desc={t('settings.theme.desc')} icon={<Monitor size={18} />}>
                  <Box sx={{ width: { xs: '100%', sm: 220 }, flexShrink: 0 }}>
                    <FormControl fullWidth size="small">
                      <InputLabel id="theme-select-label">{t('settings.theme.label')}</InputLabel>
                      <Select
                        labelId="theme-select-label"
                        id="theme-select"
                        value={currentThemeId}
                        label={t('settings.theme.label')}
                        onChange={(e) => onThemeChange(e.target.value)}
                      >
                        {availableThemes.map((th) => (
                          <MenuItem key={th.id} value={th.id}>
                            {t(th.nameKey)}
                          </MenuItem>
                        ))}
                      </Select>
                    </FormControl>
                  </Box>
                </SettingRowInline>

                <SettingRowInline title={t('settings.auto_update_rules.title')} desc={t('settings.auto_update_rules.desc')} icon={<RefreshCcw size={18} />}>
                  <FormControlLabel
                    control={<Switch checked={autoUpdateRules} onChange={(e) => handleToggleAutoUpdateRules(e.target.checked)} />}
                    label={t('settings.auto_update_rules.title')}
                    sx={{ gap: 1, marginLeft: 0, marginRight: 0 }}
                  />
                </SettingRowInline>
              </Box>
            </Box>
          </Grid>

          <Grid size={12}>
            <Box data-tut="tun-section" sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <SectionHeader
                icon={<Wifi size={18} />}
                label={t('settings.tabs.tun')}
                action={
                  <Typography variant="caption" color={tunStatus?.running ? 'success.main' : 'text.secondary'} sx={{ fontWeight: 700 }}>
                    {tunStatus?.running ? t('common.running') : t('common.off')}
                  </Typography>
                }
              />

              <SettingRowInline title={t('settings.mtu_title')} desc={t('settings.mtu_desc')} icon={<SettingsIcon size={18} />}>
                <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                  <TextField
                    label={t('settings.mtu_label')}
                    type="text"
                    size="small"
                    value={mtu}
                    onChange={(e) => setMtu(e.target.value.replace(/\D/g, '').slice(0, 4))}
                    sx={{ width: 110, '& input': { fontSize: '0.875rem', textAlign: 'center' } }}
                  />
                  <Button size="small" variant="contained" color="primary" onClick={saveMtu}>
                    {t('common.apply')}
                  </Button>
                </Stack>
              </SettingRowInline>

              <Box sx={{ p: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2 }}>
                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.6, display: 'block' }}>
                  {t('settings.tun_hint')}
                </Typography>
              </Box>
            </Box>
          </Grid>

          <Grid size={12}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <SectionHeader icon={<ShieldAlert size={18} />} label={t('settings.tabs.security')} />

              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <SettingRowInline title={t('settings.ca_management.reset')} desc={t('settings.ca_management.reset_hint')} icon={<RefreshCcw size={18} />}>
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={isCertBusy ? <CircularProgress size={20} /> : null}
                    onClick={handleRegenerateCert}
                  >
                    {isCertBusy ? t('common.loading') : t('common.apply')}
                  </Button>
                </SettingRowInline>
                <SettingRowInline title={t('settings.ca_management.export')} desc={caStatus?.CertPath || undefined} icon={<FolderOpen size={18} />}>
                  <Button
                    size="small"
                    variant="text"
                    startIcon={<Download size={16} />}
                    onClick={handleExportCert}
                  >
                    {t('common.export')}
                  </Button>
                </SettingRowInline>
              </Box>

              <StackedRow data-tut="cert-section" title={t('settings.ca_management.title')} desc={caStatus?.Installed ? t('dashboard.cert_installed') : t('dashboard.cert_not_installed')} icon={<ShieldAlert size={18} />}>
                <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                  <Typography variant="body2" sx={{ fontWeight: 'bold', color: caStatus?.Installed ? 'success.main' : 'text.secondary' }}>
                    {caStatus?.Installed ? t('settings.certs_count', { count: installedCerts.length }) : t('common.off')}
                  </Typography>
                  {installedCerts.length === 0 ? (
                    <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 2, color: 'text.secondary', opacity: 0.5 }}>
                      <ShieldAlert size={32} />
                      <Typography variant="caption" sx={{ mt: 0.5 }}>{t('settings.certs_empty')}</Typography>
                    </Box>
                  ) : (
                    <Box sx={{ mt: 1, maxHeight: 320, overflowY: 'auto' }}>
                      {installedCerts.map((cert: any) => (
                        <Box key={cert.token} sx={{ p: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5, border: 1, borderColor: 'divider', borderRadius: 1, bgcolor: 'background.default', mb: 0.5 }}>
                          <Box sx={{ minWidth: 0, flex: 1 }}>
                            <Typography variant="caption" sx={{ fontWeight: 'bold', display: 'block', wordBreak: 'break-all' }}>
                              {cert.subject}
                            </Typography>
                            <Typography variant="caption" sx={{ fontSize: '0.75rem', color: 'text.secondary', display: 'block', wordBreak: 'break-all' }}>
                              {[cert.storeLocation, cert.storeName, cert.thumbprint].filter(Boolean).join(' / ')}
                            </Typography>
                          </Box>
                          <Button
                            size="small"
                            variant="outlined"
                            color="error"
                            disabled={isCertBusy}
                            onClick={() => handleUninstallCert(cert.token)}
                          >
                            <Delete size={16} />
                            {t('common.delete')}
                          </Button>
                        </Box>
                      ))}
                    </Box>
                  )}
                </Box>
              </StackedRow>
            </Box>
          </Grid>

          <Grid size={12}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <SectionHeader icon={<Wifi size={18} />} label={t('network.ipv6_title')}
                action={<Button size="small" variant="text" disabled={isIpv6Checking} onClick={handleRefreshIPv6}>
                  {isIpv6Checking ? t('network.ipv6_checking') : t('network.ipv6_refresh')}
                </Button>}
              />

              {ipv6Available ? (
                <Box sx={{ p: 2, bgcolor: mode === 'light' ? 'rgba(34,197,94,0.1)' : 'rgba(34,197,94,0.15)', border: 1, borderColor: mode === 'light' ? 'rgba(34,197,94,0.3)' : 'rgba(34,197,94,0.4)', borderRadius: 2, display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                  <Box sx={{ width: 40, height: 40, borderRadius: 1, bgcolor: 'success.main', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <Wifi size={16} />
                  </Box>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'success.main' }}>
                      {t('network.ipv6_ok')}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.5, display: 'block' }}>
                      {t('network.ipv6_ok_desc')}
                    </Typography>
                  </Box>
                </Box>
              ) : (
                <Box sx={{ p: 2, bgcolor: mode === 'light' ? 'rgba(239,68,68,0.1)' : 'rgba(239,68,68,0.15)', border: 1, borderColor: mode === 'light' ? 'rgba(239,68,68,0.3)' : 'rgba(239,68,68,0.4)', borderRadius: 2, display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                  <Box sx={{ width: 40, height: 40, borderRadius: 1, bgcolor: 'error.main', color: '#fff', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                    <AlertCircle size={16} />
                  </Box>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'error.main' }}>
                      {t('network.ipv6_disabled_title')}
                    </Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.5, display: 'block' }}>
                      {t('network.ipv6_disabled_desc')}
                    </Typography>
                  </Box>
                </Box>
              )}
            </Box>
          </Grid>

          <Grid size={12}>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
              <SectionHeader icon={<Bolt size={18} />} label={t('rules.form.cf_pool')}
                action={<Button size="small" variant="text" disabled={isCheckingHealth} onClick={handleHealthCheck}>
                  {isCheckingHealth ? t('ech_form.probing') : t('dns.test')}
                </Button>}
              />

              <Box sx={{ bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, overflow: 'hidden' }}>
                <Grid container columns={12} spacing={1}>
                  <Grid size={{ xs: 12, md: 2 }}>
                    <Box sx={{ p: 2, borderRight: { md: 1 }, borderColor: 'divider', display: 'flex', flexDirection: 'column', justifyContent: 'center', alignItems: 'center', height: '100%' }}>
                      <Button
                        color="primary"
                        size="small"
                        startIcon={isRefreshing ? <CircularProgress size={20} /> : <Download size={16} />}
                        onClick={handleFetchIPs}
                      >
                        {isRefreshing ? undefined : t('settings.cf_pool.fetch_now')}
                      </Button>
                    </Box>
                  </Grid>
                  <Grid size={{ xs: 12, md: 10 }}>
                    <Box sx={{ p: 2 }}>
                      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 1, px: 1 }}>
                        <Typography variant="caption" sx={{ fontWeight: 'bold', textTransform: 'uppercase', color: 'text.secondary', letterSpacing: '0.05em' }}>
                          {t('settings.ip_pool', { count: ipStats.length })}
                        </Typography>
                        <Bolt size={16} color="warning.main" />
                      </Box>
                      <Grid container columns={{ xs: 1, sm: 2 }} spacing={1} sx={{ maxHeight: 400, overflowY: 'auto', px: 1, pb: 2 }}>
                        {ipStats.length === 0 ? (
                          <Grid size={12}>
                            <Box sx={{ display: 'flex', flexDirection: 'column', alignItems: 'center', py: 2, color: 'text.secondary', opacity: 0.5 }}>
                              <AlertCircle size={32} />
                              <Typography variant="caption" sx={{ mt: 0.5 }}>{t('settings.ip_pool_empty')}</Typography>
                            </Box>
                          </Grid>
                        ) : (
                          ipStats.map((ip: any, i: number) => {
                            const latency = parseLatencyMs(ip.latency);
                            const ok = latency > 0;
                            return (
                              <Grid key={i} size={{ xs: 12, sm: 6 }}>
                                <Box sx={{ p: 1, display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 1.5, border: 1, borderColor: 'divider', borderRadius: 1, bgcolor: 'background.default', '&:hover': { borderColor: 'primary.main' }, transition: 'all 0.15s' }}>
                                  <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', minWidth: 0 }}>
                                    <Box sx={{ width: 8, height: 8, borderRadius: '50%', flexShrink: 0, bgcolor: ok ? 'success.main' : 'error.main', boxShadow: ok ? '0 0 8px rgba(34,197,94,0.5)' : undefined }} />
                                    <Typography variant="caption" sx={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                                      {ip.ip}
                                    </Typography>
                                  </Box>
                                  <Typography variant="caption" sx={{ fontWeight: 'bold', color: ok && latency < 200 ? 'success.main' : 'warning.main' }}>
                                    {ip.latency ? `${Math.round(latency)}ms` : '---'}
                                  </Typography>
                                </Box>
                              </Grid>
                            );
                          })
                        )}
                      </Grid>
                    </Box>
                  </Grid>
                </Grid>
              </Box>
            </Box>
          </Grid>

        </Grid>
      </Box>
    </Box>
  );
};

export default Settings;
