import React, { useState, useEffect, useCallback, useDeferredValue } from 'react';
import { Link, Search, X, Bolt, Shield } from '../lib/icons';
import { GetActiveConnections } from '../api/bindings';
import { useTranslation } from '../i18n/I18nContext';
import { Box, Typography, TextField, InputAdornment, IconButton, Chip, CircularProgress } from '@mui/material';
import { alpha } from '@mui/material/styles';

interface ConnInfo {
  id: number;
  protocol: string;
  host: string;
  dst_addr: string;
  rule: string;
  rule_domain: string;
  mode: string;
  source: string;
  state: string;
  started_at: string;
  bytes_up: number;
  bytes_down: number;
}

const formatBytes = (n: number): string => {
  if (!Number.isFinite(n) || n <= 0) return '0 B';
  const units = ['B', 'KiB', 'MiB', 'GiB'];
  let value = n;
  let index = 0;
  while (value >= 1024 && index < units.length - 1) {
    value /= 1024;
    index += 1;
  }
  return `${value >= 100 ? value.toFixed(0) : value.toFixed(1)} ${units[index]}`;
};

const Connections: React.FC = () => {
  const { t } = useTranslation();
  const [conns, setConns] = useState<ConnInfo[]>([]);
  const [loaded, setLoaded] = useState(false);
  const [search, setSearch] = useState('');
  const deferredSearch = useDeferredValue(search);
  const [now, setNow] = useState(Date.now());

  const fetchConns = useCallback(async () => {
    try {
      const list = await GetActiveConnections();
      setConns(Array.isArray(list) ? (list as ConnInfo[]) : []);
    } catch {
      setConns([]);
    }
    setNow(Date.now());
    setLoaded(true);
  }, []);

  useEffect(() => {
    void fetchConns();
    const timer = setInterval(() => void fetchConns(), 2500);
    return () => clearInterval(timer);
  }, [fetchConns]);

  const age = (startedAt: string) => {
    const start = Date.parse(startedAt);
    if (Number.isNaN(start)) return '—';
    const secs = Math.max(0, Math.floor((now - start) / 1000));
    if (secs < 60) return t('connections.age_seconds', { s: secs });
    if (secs < 3600) return t('connections.age_minutes', { m: Math.floor(secs / 60) });
    return t('connections.age_hours', { h: Math.floor(secs / 3600) });
  };

  const query = deferredSearch.trim().toLowerCase();
  const filtered = conns.filter((conn) => {
    if (!query) return true;
    return `${conn.host} ${conn.dst_addr} ${conn.rule} ${conn.mode} ${conn.protocol}`.toLowerCase().includes(query);
  });

  const totalUp = conns.reduce((sum, conn) => sum + (conn.bytes_up || 0), 0);
  const totalDown = conns.reduce((sum, conn) => sum + (conn.bytes_down || 0), 0);

  return (
    <Box sx={{ flexGrow: 1, minHeight: 0, width: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ pt: 4, flexShrink: 0, display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 1.5, mb: 3 }}>
        <Box sx={{ p: 1.25, borderRadius: 1.5, border: 1, color: 'primary.main', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1), borderColor: (theme) => alpha(theme.palette.primary.main, 0.1), display: 'flex' }}>
          <Link size={20} />
        </Box>
        <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>{t('connections.title')}</Typography>
        <Chip
          size="small"
          label={t('connections.count', { n: conns.length })}
          sx={{ bgcolor: (theme) => alpha(theme.palette.primary.main, 0.12), color: 'primary.main', fontWeight: 700 }}
        />
        {conns.length > 0 && (
          <Typography variant="caption" color="text.secondary" sx={{ fontVariantNumeric: 'tabular-nums', fontWeight: 700 }}>
            ↑ {formatBytes(totalUp)} · ↓ {formatBytes(totalDown)}
          </Typography>
        )}
      </Box>

      <Box sx={{ mb: 2, flexShrink: 0 }}>
        <TextField
          type="text"
          size="small"
          fullWidth
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder={t('connections.search_placeholder')}
          slotProps={{
            input: {
              startAdornment: (
                <InputAdornment position="start">
                  <Search size={16} aria-hidden />
                </InputAdornment>
              ),
              endAdornment: search ? (
                <InputAdornment position="end">
                  <IconButton size="small" edge="end" aria-label={t('common.clear')} onClick={() => setSearch('')}>
                    <X size={14} />
                  </IconButton>
                </InputAdornment>
              ) : null,
            },
          }}
          sx={{ '& .MuiOutlinedInput-root': { borderRadius: 2, bgcolor: 'background.paper' } }}
        />
      </Box>

      <Box sx={{ flexGrow: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', pb: 6 }}>
        {!loaded ? (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 8 }}>
            <CircularProgress size={24} />
          </Box>
        ) : filtered.length === 0 ? (
          <Box sx={{ py: 10, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 1, color: 'text.secondary', opacity: 0.7, bgcolor: 'background.paper', border: '1px dashed', borderColor: 'divider', borderRadius: 2 }}>
            <Link size={30} strokeWidth={1.5} />
            <Typography variant="body2">{conns.length === 0 ? t('connections.empty') : t('connections.no_match')}</Typography>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {filtered.map((conn) => (
              <Box key={conn.id} sx={{ p: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, display: 'flex', flexDirection: 'column', gap: 1, minWidth: 0 }}>
                <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
                  <Bolt size={14} color="primary.main" aria-hidden />
                  <Typography variant="body2" sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, minWidth: 0 }}>
                    {conn.host || conn.dst_addr || '—'}
                  </Typography>
                  <Chip
                    size="small"
                    label={conn.state === 'active' ? t('connections.state_active') : (conn.state || '—')}
                    color={conn.state === 'active' ? 'success' : 'default'}
                    variant={conn.state === 'active' ? 'filled' : 'outlined'}
                    sx={{ height: 20, fontSize: '0.6875rem', flexShrink: 0 }}
                  />
                </Box>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75, px: 0.25 }}>
                  {conn.dst_addr && conn.dst_addr !== conn.host && (
                    <Typography variant="caption" sx={{ fontSize: '0.6875rem', color: 'text.secondary', fontVariantNumeric: 'tabular-nums' }}>
                      {conn.dst_addr}
                    </Typography>
                  )}
                  <Chip size="small" variant="outlined" label={conn.mode || '—'} sx={{ height: 18, fontSize: '0.625rem' }} />
                  <Chip size="small" variant="outlined" label={conn.protocol || '—'} sx={{ height: 18, fontSize: '0.625rem' }} />
                  {conn.rule_domain && (
                    <Chip
                      size="small"
                      variant="outlined"
                      icon={<Shield size={11} />}
                      label={conn.rule_domain}
                      sx={{ height: 18, fontSize: '0.625rem', maxWidth: 180 }}
                    />
                  )}
                </Box>
                <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, px: 0.25 }}>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    ↑ {formatBytes(conn.bytes_up)}
                  </Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    ↓ {formatBytes(conn.bytes_down)}
                  </Typography>
                  <Typography variant="caption" sx={{ color: 'text.secondary' }}>
                    {age(conn.started_at)}
                  </Typography>
                </Box>
              </Box>
            ))}
          </Box>
        )}
      </Box>
    </Box>
  );
};

export default Connections;
