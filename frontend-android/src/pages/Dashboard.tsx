import React, { useState, useEffect, useRef } from 'react';
import {
  Play,
  Square,
  Globe,
  Cpu,
  Shield,
  Bolt,
  ShieldAlert,
  Download,
  Lock
} from '../lib/icons';
import { alpha } from '@mui/material/styles';
import {
  GetProxyMode,
  IsProxyRunning,
  GetListenPort,
  GetStats,
  GetTUNStatus,
  StartProxy,
  StartTUN,
  StopProxy,
  StopTUN,
  GetCAInstallStatus,
  InstallCA,
  GetLastMigrationReport,
  EventsOn
} from '../api/bindings';
import Modal from '../components/Modal';
import { toast } from '../lib/toast';
import { extractErrorMessage } from '../lib/utils';
import { useTranslation } from '../i18n/I18nContext';
import { Box, Typography, Button, Grid } from '@mui/material';

const formatBytes = (bytes: number) => {
  if (!bytes || bytes <= 0) return '0 B';
  if (bytes >= 1024 ** 3) return `${(bytes / 1024 ** 3).toFixed(2)} GB`;
  if (bytes >= 1024 ** 2) return `${(bytes / 1024 ** 2).toFixed(1)} MB`;
  if (bytes >= 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${bytes} B`;
};

const Dashboard: React.FC = () => {
  const { t } = useTranslation();
  const [proxyRunning, setProxyRunning] = useState(false);
  const [proxyMode, setProxyMode] = useState('MITM');
  const [port, setPort] = useState(8080);
  const [isOperating, setIsOperating] = useState(false);
  const operatingRef = useRef(false);
  const [isActive, setIsActive] = useState(true);
  const [isPageVisible, setIsPageVisible] = useState(true);
  const inactivityTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [stats, setStats] = useState<{ down: number; up: number }>({ down: 0, up: 0 });
  const [tunStatus, setTunStatus] = useState<any>({
    supported: true, running: false, enabled: false, message: t('common.loading')
  });
  const [isTUNBusy, setIsTUNBusy] = useState(false);
  const [caStatus, setCaStatus] = useState<any>({ Installed: false, CertPath: '', Platform: 'android' });
  const [showCertModal, setShowCertModal] = useState(false);
  const [isInstallingCert, setIsInstallingCert] = useState(false);
  const [migration, setMigration] = useState<any>(null);

  const refresh = async () => {
    try {
      const [running, m, p, ca, tunState, traffic] = await Promise.all([
        IsProxyRunning(),
        GetProxyMode(),
        GetListenPort(),
        GetCAInstallStatus(),
        GetTUNStatus(),
        GetStats()
      ]);
      setProxyRunning(running);
      setProxyMode(m.toUpperCase());
      setPort(p);
      setCaStatus(ca || { Installed: false });
      if (traffic) {
        setStats({
          down: Number(traffic.down ?? traffic.Down ?? 0),
          up: Number(traffic.up ?? traffic.Up ?? 0),
        });
      }
      setTunStatus({
        supported: Boolean(tunState?.supported ?? tunState?.Supported),
        running: Boolean(tunState?.running ?? tunState?.Running),
        enabled: Boolean(tunState?.enabled ?? tunState?.Enabled),
        driver: String(tunState?.driver ?? tunState?.Driver ?? ''),
        message: String(tunState?.message ?? tunState?.Message ?? ''),
      });
      const statusPending = ca?.InstallHelp === '证书状态初始化中' || ca?.InstallHelp === '证书管理器未初始化';
      if (ca?.Installed) setShowCertModal(false);
      if (ca && !statusPending && !ca.Installed && !sessionStorage.getItem('ca_modal_shown')) {
        setShowCertModal(true);
        sessionStorage.setItem('ca_modal_shown', 'true');
      }
    } catch (e) {
      console.error("Dashboard refresh error:", e);
    }
  };

  useEffect(() => {
    GetLastMigrationReport()
      .then((report) => { if (report && (report.changes?.length || report.warnings?.length || report.errors?.length)) setMigration(report); })
      .catch(() => {});
  }, []);

  useEffect(() => {
    const resetInactivityTimer = () => {
      setIsActive(true);
      if (inactivityTimer.current) clearTimeout(inactivityTimer.current);
      inactivityTimer.current = setTimeout(() => setIsActive(false), 60000);
    };
    window.addEventListener('mousemove', resetInactivityTimer);
    window.addEventListener('keydown', resetInactivityTimer);
    window.addEventListener('click', resetInactivityTimer);
    const handleVisibilityChange = () => setIsPageVisible(!document.hidden);
    document.addEventListener('visibilitychange', handleVisibilityChange);
    resetInactivityTimer();
    return () => {
      window.removeEventListener('mousemove', resetInactivityTimer);
      window.removeEventListener('keydown', resetInactivityTimer);
      window.removeEventListener('click', resetInactivityTimer);
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (inactivityTimer.current) clearTimeout(inactivityTimer.current);
    };
  }, []);

  useEffect(() => {
    refresh();
    const getInterval = () => {
      if (!isPageVisible) return 60000;
      if (!isActive) return 30000;
      return 5000;
    };
    const timer = setInterval(refresh, getInterval());

    const unlisten = EventsOn("app:state_changed", (state: any) => {
      if (!state) return;
      if (typeof state.proxyRunning === 'boolean') setProxyRunning(state.proxyRunning);
      if (typeof state.proxyMode === 'string') setProxyMode(state.proxyMode.toUpperCase());
      if (typeof state.tunRunning === 'boolean') {
        setTunStatus((prev: any) => ({ ...prev, running: state.tunRunning, enabled: state.tunRunning }));
        if (state.tunRunning) setIsTUNBusy(false);
      }
      if (typeof state.tunMessage === 'string') {
        setTunStatus((prev: any) => ({ ...prev, message: state.tunMessage }));
        if (!state.tunRunning && state.tunMessage && state.tunMessage !== 'TUN is not running') {
          setIsTUNBusy(false);
          toast.error(t('dashboard.notifications.tun_failed'), state.tunMessage);
        }
      }
      setTimeout(refresh, 50);
    });

    return () => {
      clearInterval(timer);
      if (unlisten) unlisten();
    };
  }, [isActive, isPageVisible]);

  const handleToggleProxy = async () => {
    if (operatingRef.current) return;
    operatingRef.current = true;
    setIsOperating(true);
    try {
      if (proxyRunning) await StopProxy();
      else await StartProxy();
    } catch (err) {
      toast.error(t('dashboard.notifications.proxy_toggle_failed'), extractErrorMessage(err));
    } finally {
      refresh();
      operatingRef.current = false;
      setIsOperating(false);
    }
  };

  const handleInstallCA = async () => {
    setIsInstallingCert(true);
    try {
      await InstallCA();
      const ca = await GetCAInstallStatus();
      setCaStatus(ca || { Installed: false });
      if (ca?.Installed) setShowCertModal(false);
    } catch (err: any) {
      toast.error(t('common.failed'), extractErrorMessage(err));
    } finally {
      setIsInstallingCert(false);
    }
  };

  const handleToggleTUN = async () => {
    if (isTUNBusy) return;
    setIsTUNBusy(true);
    const nextEnabled = !tunStatus.running;
    try {
      if (nextEnabled) {
        await StartTUN();
        toast.success(t('dashboard.notifications.tun_updated'), t('dashboard.notifications.tun_starting'));
        const started = await waitForTUNState(true);
        if (!started) {
          toast.error(t('dashboard.notifications.tun_failed'), t('dashboard.notifications.tun_timeout'));
        }
      } else {
        await StopTUN();
        toast.success(t('dashboard.notifications.tun_updated'), t('dashboard.notifications.tun_stopped'));
        await waitForTUNState(false);
      }
    } catch (err) {
      toast.error(t('dashboard.notifications.tun_failed'), extractErrorMessage(err));
    } finally {
      await refresh();
      setIsTUNBusy(false);
    }
  };

  const waitForTUNState = async (wantRunning: boolean, timeoutMs = 60000) => {
    const deadline = Date.now() + timeoutMs;
    let delay = 300;
    while (Date.now() < deadline) {
      try {
        const state = await GetTUNStatus();
        if (Boolean(state?.running ?? state?.Running) === wantRunning) return true;
      } catch {
        return false;
      }
      await new Promise(r => setTimeout(r, delay));
      delay = Math.min(delay + 200, 1500);
    }
    return false;
  };

  return (
    <Box sx={{ flexGrow: 1, minHeight: 0, width: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ pt: 4, flexShrink: 0, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'flex-end' }, gap: 2, mb: 6 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{ p: 1.25, borderRadius: 1.5, border: 1, color: 'primary.main', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1), borderColor: (theme) => alpha(theme.palette.primary.main, 0.1), display: 'flex' }}>
            <Cpu size={20} />
          </Box>
          <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.02em', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%' }}>
            {t('dashboard.title')}
          </Typography>
        </Box>
        <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 1 }}>
          <Button
            data-tut="proxy-toggle"
            onClick={handleToggleProxy}
            loading={isOperating}
            color={proxyRunning ? 'error' : 'primary'}
            size="small"
            startIcon={!isOperating && (proxyRunning ? <Square size={14} /> : <Play size={14} />)}
          >
            {!isOperating && (proxyRunning ? t('dashboard.proxy_stop') : t('dashboard.proxy_start'))}
          </Button>
          <Button
            onClick={handleToggleTUN}
            loading={isTUNBusy}
            disabled={isTUNBusy || !tunStatus.supported}
            variant={tunStatus.running ? 'contained' : 'outlined'}
            color={tunStatus.running ? 'warning' : undefined}
            size="small"
            startIcon={!isTUNBusy && <Globe size={14} />}
          >
            {t('dashboard.tun_status')}: {tunStatus.running ? t('common.on') : t('common.off')}
          </Button>
        </Box>
      </Box>

      <Box sx={{ flexGrow: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', width: '100%', pb: 6 }}>
        <Grid container spacing={3}>
          <Grid size={{ xs: 12, md: 4 }}>
            <Box className="ss-card-hover" sx={{ p: 3, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 3, boxShadow: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
                <Box sx={{ color: 'primary.main' }}><Cpu size={20} /></Box>
                <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'text.secondary', letterSpacing: '-0.01em', textTransform: 'uppercase' }}>
                  {t('dashboard.core_status')}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minWidth: 0 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%', py: 0.25 }}>{t('dashboard.run_status')}</Typography>
                  <Box sx={{ px: 1, py: 0.25, borderRadius: 1, fontSize: '0.75rem', fontWeight: 'bold', textTransform: 'uppercase', bgcolor: proxyRunning ? 'success.main' : 'error.main', color: proxyRunning ? 'success.contrastText' : 'error.contrastText', flexShrink: 0, ...(proxyRunning ? { animation: 'ss-pulse-glow 2s ease-in-out infinite' } : {}) }}>
                    {proxyRunning ? t('common.running') : t('common.stopped')}
                  </Box>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minWidth: 0 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%', py: 0.25 }}>{t('dashboard.work_mode')}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'primary.main', flexShrink: 0 }}>
                    {proxyMode}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minWidth: 0 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%', py: 0.25 }}>{t('dashboard.listen_port')}</Typography>
                  <Typography variant="body2" sx={{ fontWeight: 'bold', flexShrink: 0 }}>
                    {port}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', minWidth: 0 }}>
                  <Typography variant="body2" color="text.secondary" sx={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%', py: 0.25 }}>{t('dashboard.tun_status')}</Typography>
                  <Box sx={{ px: 1, py: 0.25, borderRadius: 1, fontSize: '0.75rem', fontWeight: 'bold', textTransform: 'uppercase', bgcolor: tunStatus.running ? 'warning.main' : 'action.hover', color: tunStatus.running ? 'warning.contrastText' : 'text.secondary', flexShrink: 0 }}>
                    {tunStatus.running ? t('common.running') : t('common.off')}
                  </Box>
                </Box>
              </Box>
            </Box>
          </Grid>

          <Grid size={{ xs: 12, md: 4 }}>
            <Box className="ss-card-hover" sx={{ p: 3, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 3, boxShadow: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
                <Box sx={{ color: 'primary.main' }}><Shield size={20} /></Box>
                <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'text.secondary', letterSpacing: '-0.01em', textTransform: 'uppercase' }}>
                  {t('dashboard.cert_status')}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1.25, borderRadius: 2, border: 1, bgcolor: caStatus.Installed ? 'success.main' : 'error.main', color: caStatus.Installed ? 'success.contrastText' : 'error.contrastText', borderColor: caStatus.Installed ? 'success.light' : 'error.light', minWidth: 0 }}>
                  {caStatus.Installed ? <Shield size={18} /> : <ShieldAlert size={18} />}
                  <Typography variant="caption" sx={{ fontWeight: 'bold', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%', py: 0.25 }}>
                    {caStatus.Installed ? t('dashboard.cert_installed') : t('dashboard.cert_not_installed')}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', fontSize: '0.75rem', color: 'text.secondary', fontWeight: 'medium', px: 1, alignItems: 'center', minWidth: 0 }}>
                  <Typography noWrap sx={{ maxWidth: 140, opacity: 0.6, fontSize: '0.625rem', py: 0.25 }} title={caStatus.CertPath}>
                    {caStatus.CertPath || t('dashboard.path_pending')}
                  </Typography>
                </Box>
              </Box>
            </Box>
          </Grid>

          <Grid size={{ xs: 12, md: 4 }}>
            <Box className="ss-card-hover" sx={{ p: 3, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 3, boxShadow: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 2 }}>
                <Box sx={{ color: 'primary.main' }}><Bolt size={20} /></Box>
                <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'text.secondary', letterSpacing: '-0.01em', textTransform: 'uppercase' }}>
                  {t('dashboard.conn_info')}
                </Typography>
              </Box>
              <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, p: 1.25, bgcolor: 'primary.main', border: 1, borderColor: 'primary.main', borderRadius: 2, minWidth: 0 }}>
                  <Bolt size={18} color="primary.contrastText" aria-hidden />
                  <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'primary.contrastText', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', maxWidth: '100%', py: 0.25 }}>
                    127.0.0.1:{port}
                  </Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 1, gap: 1 }}>
                  <Typography variant="caption" color="text.secondary">{t('dashboard.download')}</Typography>
                  <Typography variant="caption" sx={{ fontWeight: 700 }}>{formatBytes(stats.down)}</Typography>
                </Box>
                <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', px: 1, gap: 1 }}>
                  <Typography variant="caption" color="text.secondary">{t('dashboard.upload')}</Typography>
                  <Typography variant="caption" sx={{ fontWeight: 700 }}>{formatBytes(stats.up)}</Typography>
                </Box>
              </Box>
            </Box>
          </Grid>
        </Grid>

        {migration && (
          <Grid size={12}>
            <Box sx={{ p: 2.5, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 3, boxShadow: 1 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, mb: 1.5 }}>
                <Box sx={{ color: 'warning.main' }}><ShieldAlert size={20} /></Box>
                <Typography variant="body2" sx={{ fontWeight: 'bold', color: 'text.secondary', letterSpacing: '-0.01em', textTransform: 'uppercase' }}>
                  {t('dashboard.migration_report.title')}
                </Typography>
              </Box>
              {([
                { key: 'changes', items: migration.changes, color: 'text.primary' },
                { key: 'warnings', items: migration.warnings, color: 'warning.main' },
                { key: 'errors', items: migration.errors, color: 'error.main' },
                { key: 'backups', items: migration.backup_paths, color: 'text.secondary' },
              ] as const).filter((section) => Array.isArray(section.items) && section.items.length > 0).map((section) => (
                <Box key={section.key} sx={{ mb: 1 }}>
                  <Typography variant="caption" sx={{ fontWeight: 800, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'text.secondary' }}>
                    {t(`dashboard.migration_report.${section.key}`)}
                  </Typography>
                  {section.items.slice(0, 6).map((line: string, index: number) => (
                    <Typography key={index} variant="caption" sx={{ display: 'block', color: section.color, lineHeight: 1.6, wordBreak: 'break-word' }}>
                      · {line}
                    </Typography>
                  ))}
                </Box>
              ))}
            </Box>
          </Grid>
        )}
      </Box>

      <Modal isOpen={showCertModal} onClose={() => setShowCertModal(false)} title={t('dashboard.install_cert.title')} maxWidth="md">
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3, py: 1 }}>
          <Box sx={{ display: 'flex', justifyContent: 'center' }}>
            <Box sx={{ width: 56, height: 56, borderRadius: '50%', bgcolor: 'primary.main', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Lock size={24} color="primary.contrastText" />
            </Box>
          </Box>
          <Box sx={{ textAlign: 'center' }}>
            <Typography variant="h5" sx={{ fontWeight: 'bold' }}>
              {t('dashboard.install_cert.subtitle')}
            </Typography>
          </Box>
          <Box sx={{ bgcolor: 'background.default', border: 1, borderColor: 'divider', borderRadius: 2, p: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1.5 }}>
              <Box sx={{ mt: 0.5, color: 'warning.main' }}>
                <ShieldAlert size={20} />
              </Box>
              <Box>
                <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                  {t('dashboard.install_cert.security_alert')}
                </Typography>
                <Typography variant="caption" color="text.secondary" sx={{ lineHeight: 1.5, display: 'block' }}>
                  {t('dashboard.install_cert.security_desc')}
                </Typography>
              </Box>
            </Box>
          </Box>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5, pt: 1 }}>
            <Button
              onClick={handleInstallCA}
              loading={isInstallingCert}
              variant="contained"
              fullWidth
              startIcon={!isInstallingCert && <Download size={16} />}
            >
              {isInstallingCert ? t('dashboard.install_cert.installing') : t('dashboard.install_cert.install_now')}
            </Button>
            <Button onClick={() => setShowCertModal(false)} variant="text" fullWidth>
              {t('dashboard.install_cert.remind_later')}
            </Button>
          </Box>
        </Box>
      </Modal>
    </Box>
  );
};

export default Dashboard;
