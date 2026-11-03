import React, { useCallback, useEffect, useState } from 'react';
import { Box, Button, TextField, Typography, Stack, IconButton, CircularProgress } from '@mui/material';
import { Bolt, Delete, Download, RefreshCcw, ShieldAlert } from '../lib/icons';
import { GetIPPools, UpdateIPPools, ForceFetchIPPoolIPs, TriggerIPPoolHealthCheck, RemoveInvalidIPPoolIPs } from '../api/bindings';
import { splitListInput } from '../lib/utils';
import { toast } from '../lib/toast';
import { useTranslation } from '../i18n/I18nContext';

const RESERVED = 'cloudflare';

const IPPoolsSection: React.FC = () => {
  const { t } = useTranslation();
  const [pools, setPools] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState<string | null>(null);
  const [name, setName] = useState('');
  const [ips, setIps] = useState('');

  const reload = useCallback(async () => {
    try {
      const list = await GetIPPools();
      setPools(Array.isArray(list) ? list : []);
    } catch {
      setPools([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { void reload(); }, [reload]);

  const persist = async (next: any[]) => {
    await UpdateIPPools(next);
    setPools(next);
  };

  const addPool = async () => {
    const trimmed = name.trim().toLowerCase();
    const list = splitListInput(ips);
    if (!trimmed || trimmed === RESERVED) {
      toast.error(t('common.failed'), t('settings.ip_pools.invalid_name'));
      return;
    }
    if (list.length === 0) {
      toast.error(t('common.failed'), t('settings.ip_pools.no_ips'));
      return;
    }
    if (pools.some((pool) => pool.name === trimmed)) {
      toast.error(t('common.failed'), t('settings.ip_pools.duplicate_name'));
      return;
    }
    setBusy('add');
    try {
      await persist([...pools, { name: trimmed, preferred_ips: list, auto_update: true }]);
      setName('');
      setIps('');
      toast.success(t('settings.ip_pools.added', { name: trimmed }));
    } catch (error: any) {
      toast.error(t('common.failed'), String(error?.message || error || ''));
    } finally {
      setBusy(null);
    }
  };

  const removePool = async (poolName: string) => {
    setBusy(poolName);
    try {
      await persist(pools.filter((pool) => pool.name !== poolName));
      toast.success(t('settings.ip_pools.removed', { name: poolName }));
    } catch (error: any) {
      toast.error(t('common.failed'), String(error?.message || error || ''));
    } finally {
      setBusy(null);
    }
  };

  const runPoolAction = async (poolName: string, action: 'fetch' | 'health' | 'clean') => {
    setBusy(poolName);
    try {
      if (action === 'fetch') {
        const result: any = await ForceFetchIPPoolIPs(poolName);
        await reload();
        toast.success(t('settings.ip_pools.added', { name: poolName }), result?.count ? `${result.count} IP` : undefined);
      } else if (action === 'health') {
        await TriggerIPPoolHealthCheck(poolName);
        toast.info(t('common.loading'), poolName);
      } else {
        const removed = await RemoveInvalidIPPoolIPs(poolName);
        await reload();
        toast.success(t('settings.ip_pools.removed', { name: poolName }), `${removed}`);
      }
    } catch (error: any) {
      toast.error(t('common.failed'), String(error?.message || error || ''));
    } finally {
      setBusy(null);
    }
  };

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1 }}>
        <Bolt size={18} />
        <Typography variant="body2" sx={{ fontWeight: 'bold', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          {t('settings.ip_pools.title')}
        </Typography>
      </Box>
      <Typography variant="caption" color="text.secondary" sx={{ px: 1, lineHeight: 1.6 }}>
        {t('settings.ip_pools.subtitle')}
      </Typography>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', py: 3 }}>
          <CircularProgress size={22} />
        </Box>
      ) : pools.length === 0 ? (
        <Box sx={{ p: 3, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, bgcolor: 'background.paper', border: '1px dashed', borderColor: 'divider', borderRadius: 2, color: 'text.secondary', opacity: 0.7 }}>
          <ShieldAlert size={26} strokeWidth={1.5} />
          <Typography variant="caption">{t('settings.ip_pools.empty')}</Typography>
        </Box>
      ) : (
        <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
          {pools.map((pool) => (
            <Box key={pool.name} sx={{ p: 1.5, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, display: 'flex', alignItems: 'center', gap: 1, flexWrap: 'wrap' }}>
              <Box sx={{ minWidth: 0, flex: 1 }}>
                <Typography variant="body2" sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {pool.name}
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  {t('settings.ip_pools.ip_count', { count: (pool.preferred_ips || []).length })}
                </Typography>
              </Box>
              <Stack direction="row" spacing={0.25} sx={{ alignItems: 'center' }}>
                <IconButton size="small" disabled={busy === pool.name} aria-label={t('common.apply')} onClick={() => void runPoolAction(pool.name, 'fetch')}>
                  <Download size={16} />
                </IconButton>
                <IconButton size="small" disabled={busy === pool.name} aria-label={t('settings.cf_pool.fetch_now')} onClick={() => void runPoolAction(pool.name, 'health')}>
                  <RefreshCcw size={16} />
                </IconButton>
                <IconButton size="small" disabled={busy === pool.name} aria-label={t('common.delete')} onClick={() => void runPoolAction(pool.name, 'clean')}>
                  <Bolt size={16} />
                </IconButton>
                <IconButton size="small" color="error" disabled={busy === pool.name} aria-label={t('common.delete')} onClick={() => void removePool(pool.name)}>
                  <Delete size={16} />
                </IconButton>
              </Stack>
            </Box>
          ))}
        </Box>
      )}

      <Box sx={{ p: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, display: 'flex', flexDirection: 'column', gap: 1.5 }}>
        <TextField
          size="small"
          fullWidth
          value={name}
          onChange={(e) => setName(e.target.value)}
          label={t('settings.ip_pools.name_label')}
          slotProps={{ htmlInput: { maxLength: 64 } }}
        />
        <TextField
          size="small"
          fullWidth
          multiline
          minRows={2}
          maxRows={5}
          value={ips}
          onChange={(e) => setIps(e.target.value)}
          label={t('settings.ip_pools.ips_label')}
        />
        <Button variant="outlined" size="small" disabled={busy === 'add'} onClick={() => void addPool()} sx={{ alignSelf: 'flex-end' }}>
          {t('settings.ip_pools.add')}
        </Button>
      </Box>
    </Box>
  );
};

export default IPPoolsSection;
