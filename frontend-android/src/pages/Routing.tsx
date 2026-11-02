import React, { useState, useEffect } from 'react';
import {
  RefreshCcw,
  Shield,
  Activity,
  Share,
  Bolt
} from '../lib/icons';
import { GetAutoRoutingStatus, EventsOn } from '../api/bindings';
import { useTranslation } from '../i18n/I18nContext';
import { Box, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { keyframes } from '@emotion/react';

interface FlowEntry {
  id: string;
  domain: string;
  mode: string;
  modeClass: string;
  modeDisplay: string;
}

const pulse = keyframes`0%,100%{opacity:1}50%{opacity:0.4}`;

const Routing: React.FC = () => {
  const { t } = useTranslation();
  const [flows, setFlows] = useState<FlowEntry[]>([]);
  const [status, setStatus] = useState<any>({ enabled: false, domain_count: 0 });

  useEffect(() => {
    const loadStatus = async () => {
      try {
        setStatus(await GetAutoRoutingStatus());
      } catch { /* ignore */ }
    };
    void loadStatus();
    const timer = setInterval(() => void loadStatus(), 15000);

    const unoff = EventsOn("app:route", (data: any) => {
      if (!data) return;
      const { domain, mode } = data;
      if (!domain || !mode) return;

      let modeClass = 'success.main';
      let modeDisplay = mode.toUpperCase();
      const lowerMode = mode.toLowerCase();

      if (lowerMode.includes('direct') || lowerMode.includes('tcp')) {
        modeClass = 'rgba(107,114,128,0.9)'; modeDisplay = 'DIRECT';
      } else if (lowerMode.includes('transparent')) modeClass = 'error.main';
      else if (lowerMode.includes('mitm') || lowerMode.includes('proxy')) modeClass = 'warning.main';

      setFlows(prev => {
        const newFlow: FlowEntry = {
          id: crypto.randomUUID(),
          domain,
          mode,
          modeClass,
          modeDisplay
        };
        return [newFlow, ...prev].slice(0, 40);
      });
    });

    return () => {
      clearInterval(timer);
      unoff();
    };
  }, []);

  return (
    <Box sx={{ flexGrow: 1, minHeight: 0, width: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ pt: 4, mb: 4, flexShrink: 0, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'flex-end' }, gap: 2 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{ p: 1.25, borderRadius: 1.5, border: 1, color: 'primary.main', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1), borderColor: (theme) => alpha(theme.palette.primary.main, 0.1), display: 'flex' }}>
            <Activity size={20} />
          </Box>
          <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>{t('routing.title')}</Typography>
        </Box>
      </Box>

      <Box sx={{ flexGrow: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', width: '100%', pb: 6, display: 'flex', flexDirection: 'column', gap: 4 }}>
        <Box sx={{ bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, overflow: 'hidden', display: 'flex', flexDirection: 'column', height: 340 }}>
          <Box sx={{ px: 3, py: 2, borderBottom: 1, borderColor: 'divider', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, color: 'text.secondary', fontWeight: 700, fontSize: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.1em' }}>
              <Box sx={{ display: 'inline-flex', color: 'success.main', animation: `${pulse} 1.5s ease-in-out infinite` }}><Activity size={14} /></Box>
              {t('routing.traffic_flow')}
            </Box>
          </Box>
          <Box sx={{ flex: 1, overflowY: 'auto', p: 2, display: 'flex', flexWrap: 'wrap', alignContent: 'flex-start', gap: 1.5, position: 'relative' }}>
            {flows.length === 0 && (
              <Box sx={{ position: 'absolute', inset: 0, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', color: 'text.secondary', opacity: 0.4 }}>
                <Share size={40} strokeWidth={1} />
                <Typography variant="caption" sx={{ mt: 1.5, fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.1em' }}>{t('routing.waiting_traffic')}</Typography>
              </Box>
            )}
            {flows.map((flow) => (
              <Box
                key={flow.id}
                sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 1.5, py: 0.75, bgcolor: 'action.hover', border: 1, borderColor: 'divider', borderRadius: '999px', boxShadow: 1, minWidth: 0, flexGrow: 1, flexBasis: 200, maxWidth: '100%' }}
              >
                <Typography variant="caption" sx={{ fontWeight: 700, maxWidth: 240, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', py: 0.25 }} title={flow.domain}>
                  {flow.domain}
                </Typography>
                <Typography variant="caption" sx={{ color: 'text.secondary', fontSize: '0.625rem' }}>➔</Typography>
                <Typography variant="caption" sx={{ px: 1, py: 0.25, borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 900, textTransform: 'uppercase', color: 'common.white', bgcolor: flow.modeClass, boxShadow: 1 }}>
                  {flow.modeDisplay}
                </Typography>
              </Box>
            ))}
          </Box>
        </Box>

        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: '1fr 1fr', xl: 'repeat(3, 1fr)' }, gap: 4 }}>
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 0.5 }}>
              <Box sx={{ display: 'inline-flex', color: 'primary.main' }}><RefreshCcw size={18} /></Box>
              <Typography variant="body2" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'text.secondary' }}>{t('routing.strategy')}</Typography>
            </Box>
            <Box sx={{ bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, p: 2.5, display: 'flex', flexDirection: 'column', gap: 2 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 2 }}>
                <Box sx={{ minWidth: 0 }}>
                  <Typography variant="body2" sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{t('routing.gfwlist_check')}</Typography>
                  <Typography variant="caption" sx={{ fontSize: '0.6875rem', color: 'text.secondary', display: 'block', mt: 0.25, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {status.enabled ? t('routing.preloaded', { count: status.domain_count }) : t('routing.inactive')}
                  </Typography>
                </Box>
                <Box sx={{ px: 1, py: 0.25, borderRadius: 1, fontSize: '0.625rem', fontWeight: 900, textTransform: 'uppercase', bgcolor: status.enabled ? 'success.main' : 'action.hover', color: status.enabled ? 'success.contrastText' : 'text.secondary', flexShrink: 0 }}>
                  {status.enabled ? t('common.on') : t('common.off')}
                </Box>
              </Box>
              <Typography variant="caption" sx={{ fontSize: '0.6875rem', color: 'text.secondary', lineHeight: 1.6 }}>
                {t('routing.android_desc')}
              </Typography>
            </Box>
          </Box>

          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, px: 0.5 }}>
              <Box sx={{ display: 'inline-flex', color: 'success.main' }}><Shield size={18} /></Box>
              <Typography variant="body2" sx={{ fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'text.secondary' }}>{t('routing.features')}</Typography>
            </Box>
            <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
              {[
                { icon: Bolt, title: t('routing.feature_smart'), color: 'success.main', desc: t('routing.feature_smart_desc') },
                { icon: RefreshCcw, title: t('routing.feature_priority'), color: 'secondary.main', desc: t('routing.feature_priority_desc') }
              ].map((item, i) => (
                <Box key={i} sx={{ p: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider', borderRadius: 2, display: 'flex', gap: 2, transition: 'border-color 0.15s', '&:hover': { borderColor: (theme) => alpha(theme.palette.primary.main, 0.4) } }}>
                  <Box sx={{ flexShrink: 0, width: 40, height: 40, borderRadius: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', bgcolor: 'action.hover', color: item.color }}>
                    <item.icon size={20} />
                  </Box>
                  <Box>
                    <Typography variant="body2" sx={{ fontWeight: 700 }}>{item.title}</Typography>
                    <Typography variant="caption" sx={{ fontSize: '0.6875rem', color: 'text.secondary', display: 'block', mt: 0.5, lineHeight: 1.6, fontWeight: 500 }}>{item.desc}</Typography>
                  </Box>
                </Box>
              ))}
            </Box>
          </Box>
        </Box>
      </Box>
    </Box>
  );
};

export default Routing;
