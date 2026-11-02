import React, { useEffect, useState } from 'react';
import { Box, Button, Grid, Typography } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { useTranslation } from '../i18n/I18nContext';
import {
  Globe, Link as LinkIcon, Users, Shield, Heart, RefreshCw,
  Download, Sparkles, Bolt, Lock, Code2, RefreshCcw, Megaphone, Map, ExternalLink,
  Cpu
} from '../lib/icons';
import logoUrl from '../assets/logo.svg';
import { GetAppVersion, GetBuildInfo, OpenURL, type BuildInfo } from '../api/bindings';

const softBg = (token: string, opacity: number) => (theme: any) =>
  alpha(theme.palette[token.split('.')[0]][token.split('.')[1]], opacity);

const RELEASES_URL = 'https://github.com/SnishaperTeam/Snishaper-Droid/releases';

const About: React.FC = () => {
  const { t } = useTranslation();
  const [version, setVersion] = useState<string>('');
  const [build, setBuild] = useState<BuildInfo | null>(null);

  useEffect(() => {
    GetAppVersion().then((v) => { if (typeof v === 'string') setVersion(v); }).catch(() => {});
    GetBuildInfo().then((info) => { if (info) setBuild(info as BuildInfo); }).catch(() => {});
  }, []);

  const channelMap: Record<string, { key: string; color: string }> = {
    alpha: { key: 'about.channel_alpha', color: 'error.main' },
    beta: { key: 'about.channel_beta', color: 'warning.main' },
    rc: { key: 'about.channel_rc', color: 'primary.main' },
    stable: { key: 'about.channel_stable', color: 'success.main' },
  };
  const channelConf = channelMap[(build?.Channel || 'stable').toLowerCase()] || channelMap.stable;

  const handleOpenWebsite = () => OpenURL('https://jetcpp.ccwu.cc');
  const handleOpenGitHub = () => OpenURL('https://github.com/SnishaperTeam/Snishaper-Droid');
  const handleOpenReleases = () => OpenURL(RELEASES_URL);
  const handleOpenAdaptation = () => OpenURL('https://github.com/SnishaperTeam/SniShaper/issues/95');
  const handleOpenDevPlan = () => OpenURL('https://github.com/SnishaperTeam/SniShaper/issues/36');
  const handleOpenQQGroup = () => OpenURL('https://qm.qq.com/q/GtBOkAOiME');

  const features = [
    { icon: <Lock size={20} />, title: t('about.feature_ech'), desc: t('about.feature_ech_desc'), color: 'primary.main' },
    { icon: <Bolt size={20} />, title: t('about.feature_fast'), desc: t('about.feature_fast_desc'), color: 'success.main' },
    { icon: <Code2 size={20} />, title: t('about.feature_open'), desc: t('about.feature_open_desc'), color: 'warning.main' },
  ];

  const buildRows = [
    { label: t('about.app_version'), value: version || '—' },
    { label: t('about.core_version'), value: build?.CoreVersion || '—' },
    { label: t('about.channel'), value: t(channelConf.key) },
    { label: t('about.go_version'), value: build?.GoVersion || '—' },
    { label: t('about.platform'), value: build?.Platform || 'android' },
  ];

  const communityCards = [
    { onClick: handleOpenAdaptation, icon: <Megaphone size={24} />, title: t('about.site_adaptation'), desc: t('about.site_adaptation_desc'), action: t('about.participate_now'), color: 'primary.main', full: false },
    { onClick: handleOpenDevPlan, icon: <Map size={24} />, title: t('about.development_plan'), desc: t('about.development_plan_desc'), action: t('about.view_plan'), color: 'success.main', full: false },
    { onClick: handleOpenQQGroup, icon: <Users size={24} />, title: t('about.qq_group'), desc: t('about.qq_group_desc'), action: t('about.join_group'), color: 'warning.main', full: true },
  ];

  const infoCards = [
    { icon: <Heart size={22} />, title: t('about.contributors'), value: 'mechrevo, dongzheyu, JetCPP-dongle', color: 'success.main', valueColor: 'text.primary' },
    { icon: <Users size={22} />, title: t('about.maintainers'), value: 'JetCPP Team, SniShaper Team', color: 'warning.main', valueColor: 'text.primary' },
    { icon: <Globe size={22} />, title: t('about.website'), value: 'jetcpp.ccwu.cc', color: 'primary.main', valueColor: 'primary.main', onClick: handleOpenWebsite },
    { icon: <RefreshCcw size={22} />, title: 'GitHub', value: 'SnishaperTeam/Snishaper-Droid', color: 'text.primary', valueColor: 'text.primary', onClick: handleOpenGitHub },
    { icon: <Download size={22} />, title: t('about.releases'), value: t('about.releases_desc'), color: 'warning.main', valueColor: 'warning.main', onClick: handleOpenReleases },
  ];

  return (
    <Box sx={{ flexGrow: 1, minHeight: 0, width: '100%', display: 'flex', flexDirection: 'column', overflowY: 'auto', overflowX: 'hidden' }}>
      <Box sx={{ flex: 1, p: { xs: 2, md: 4 }, maxWidth: '64rem', mx: 'auto', width: '100%' }}>
        <Box sx={{ position: 'relative', mb: { xs: 4, md: 6 }, p: { xs: 2, md: 4 }, borderRadius: 3, border: 1, borderColor: 'divider', overflow: 'hidden', background: (theme: any) => `linear-gradient(135deg, ${alpha(theme.palette.primary.main, 0.05)}, ${theme.palette.background.paper}, ${alpha(theme.palette.primary.main, 0.05)})` }}>
          <Box sx={{ position: 'absolute', inset: 0, opacity: 0.5, background: (theme: any) => `radial-gradient(ellipse at top right, ${alpha(theme.palette.primary.main, 0.08)}, transparent 50%)` }} />
          <Box sx={{ position: 'relative', display: 'flex', flexDirection: 'column', alignItems: 'center', textAlign: 'center' }}>
            <Box sx={{ position: 'relative', mb: 3 }}>
              <Box sx={{ position: 'absolute', inset: 0, borderRadius: '50%', bgcolor: (theme: any) => alpha(theme.palette.primary.main, 0.2), filter: 'blur(24px)' }} />
              <Box component="img" src={logoUrl} alt="SniShaper logo" sx={{ position: 'relative', width: 112, height: 112, objectFit: 'contain', filter: 'drop-shadow(0 10px 30px rgba(33, 150, 243, 0.3))' }} />
            </Box>
            <Typography variant="h1" sx={{ fontSize: '2.25rem', fontWeight: 900, color: 'text.primary', mb: 0.5, letterSpacing: '-0.025em' }}>SniShaper</Typography>
            <Typography sx={{ fontSize: '1.125rem', fontWeight: 500, color: 'text.secondary', mb: 2 }}>{t('about.title')}</Typography>
            <Box sx={{ display: 'inline-flex', alignItems: 'center', gap: 1.5, px: 2.5, py: 1.25, borderRadius: '999px', bgcolor: (theme: any) => alpha(theme.palette.primary.main, 0.1), border: 1, borderColor: (theme: any) => alpha(theme.palette.primary.main, 0.2) }}>
              <Shield size={16} aria-hidden />
              <Typography variant="body2" sx={{ fontWeight: 700, color: 'primary.main' }}>{t('about.version')}: {version || '—'}</Typography>
              <Box component="span" sx={{ px: 1, py: 0.25, borderRadius: '999px', fontSize: '0.6875rem', fontWeight: 700, letterSpacing: '0.03em', color: channelConf.color, bgcolor: (theme: any) => {
                const parts = channelConf.color.split('.');
                return alpha((theme.palette as any)[parts[0]]?.[parts[1]] ?? theme.palette.primary.main, 0.12);
              } }}>
                {t(channelConf.key)}
              </Box>
            </Box>
          </Box>
        </Box>

        <Box sx={{ mb: 5, p: 3, borderRadius: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider' }}>
          <Typography sx={{ color: 'text.secondary', textAlign: 'center', lineHeight: 1.625, fontSize: '0.9375rem' }}>{t('about.description')}</Typography>
        </Box>

        <Box sx={{ mb: 5 }}>
          <Typography variant="h2" sx={{ fontSize: '1.125rem', fontWeight: 700, color: 'text.primary', mb: 2.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box component="span" sx={{ display: 'inline-flex', color: 'primary.main' }}><Sparkles size={20} aria-hidden /></Box>
            {t('about.features')}
          </Typography>
          <Grid container spacing={2}>
            {features.map((f, i) => (
              <Grid key={i} size={{ xs: 12, md: 4 }}>
                <Box sx={{ height: '100%', p: 3, borderRadius: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider' }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', width: 44, height: 44, borderRadius: 2, bgcolor: softBg(f.color, 0.1), color: f.color }}>{f.icon}</Box>
                  <Typography sx={{ mt: 2, fontWeight: 700, color: 'text.primary' }}>{f.title}</Typography>
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, lineHeight: 1.625 }}>{f.desc}</Typography>
                </Box>
              </Grid>
            ))}
          </Grid>
        </Box>

        <Box sx={{ mb: 5 }}>
          <Typography variant="h2" sx={{ fontSize: '1.125rem', fontWeight: 700, color: 'text.primary', mb: 2.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box component="span" sx={{ display: 'inline-flex', color: 'secondary.main' }}><Cpu size={20} aria-hidden /></Box>
            {t('about.build_info')}
          </Typography>
          <Box sx={{ p: 2.5, borderRadius: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider' }}>
            {buildRows.map((row) => (
              <Box key={row.label} sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 2, py: 1, borderBottom: 1, borderColor: 'divider', '&:last-of-type': { borderBottom: 0 } }}>
                <Typography variant="body2" color="text.secondary">{row.label}</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700, wordBreak: 'break-all', textAlign: 'right' }}>{row.value}</Typography>
              </Box>
            ))}
          </Box>
        </Box>

        <Box sx={{ mb: 5 }}>
          <Typography variant="h2" sx={{ fontSize: '1.125rem', fontWeight: 700, color: 'text.primary', mb: 2.5, display: 'flex', alignItems: 'center', gap: 1 }}>
            <Box component="span" sx={{ display: 'inline-flex', color: 'error.main' }}><Heart size={20} aria-hidden /></Box>
            {t('about.community')}
          </Typography>
          <Grid container spacing={2.5}>
            {communityCards.map((c, i) => (
              <Grid key={i} size={{ xs: 12, md: c.full ? 12 : 6 }}>
                <Box
                  role="button"
                  tabIndex={0}
                  onClick={c.onClick}
                  onKeyDown={(e) => e.key === 'Enter' && c.onClick()}
                  aria-label={c.title}
                  sx={{
                    p: 3, borderRadius: 2, cursor: 'pointer', border: 1, borderColor: 'divider', transition: 'all 0.3s',
                    background: (theme: any) => `linear-gradient(135deg, ${alpha(theme.palette[c.color.split('.')[0]][c.color.split('.')[1]], 0.05)}, ${theme.palette.background.paper})`,
                    '--reveal': 0,
                    '--chip-bg': softBg(c.color, 0.1),
                    '&:hover': {
                      borderColor: softBg(c.color, 0.3),
                      boxShadow: 8,
                      transform: 'translateY(-2px)',
                      '--reveal': 1,
                      '--chip-bg': softBg(c.color, 0.15),
                    },
                  }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                    <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', p: 1.5, borderRadius: 2, bgcolor: 'var(--chip-bg)', color: c.color, transition: 'background-color 0.3s' }}>{c.icon}</Box>
                    <Box sx={{ flex: 1, minWidth: 0 }}>
                      <Typography sx={{ fontSize: '1rem', fontWeight: 700, color: 'text.primary', mb: 0.75, display: 'flex', alignItems: 'center', gap: 1, overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '100%' }}>
                        {c.title}
                        <Box component="span" sx={{ display: 'inline-flex', color: 'text.secondary', opacity: 'var(--reveal, 0)', transition: 'opacity 0.3s' }}><ExternalLink size={14} /></Box>
                      </Typography>
                      <Typography sx={{ fontSize: '0.875rem', color: 'text.secondary', mb: 1.5, lineHeight: 1.625 }}>{c.desc}</Typography>
                      <Box component="span" sx={{ display: 'inline-flex', alignItems: 'center', gap: 0.75, fontSize: '0.875rem', fontWeight: 700, color: c.color }}>
                        {c.action}
                        <ExternalLink size={14} />
                      </Box>
                    </Box>
                  </Box>
                </Box>
              </Grid>
            ))}
          </Grid>
        </Box>

        <Grid container spacing={2.5} sx={{ mb: 5 }}>
          {infoCards.map((c, i) => (
            <Grid key={i} size={{ xs: 12, md: 6 }}>
              <Box
                {...(c.onClick ? { role: 'button', tabIndex: 0, onClick: c.onClick, onKeyDown: (e: React.KeyboardEvent) => e.key === 'Enter' && c.onClick() } : {})}
                sx={{
                  p: 2.5, borderRadius: 2, bgcolor: 'background.paper', border: 1, borderColor: 'divider', transition: 'all 0.3s',
                  ...(c.onClick ? { cursor: 'pointer' } : {}),
                  '--chip-bg': softBg(c.color, 0.1),
                  '&:hover': { borderColor: softBg(c.color, 0.3), boxShadow: 8, '--chip-bg': softBg(c.color, 0.15) },
                }}
              >
                <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                  <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'center', p: 1.5, borderRadius: 2, bgcolor: 'var(--chip-bg)', color: c.color, transition: 'background-color 0.3s' }}>{c.icon}</Box>
                  <Box sx={{ flex: 1, minWidth: 0 }}>
                    <Typography sx={{ fontSize: '0.75rem', fontWeight: 700, color: 'text.secondary', textTransform: 'uppercase', letterSpacing: '0.05em', mb: 0.75 }}>{c.title}</Typography>
                    <Typography sx={{ fontSize: '0.9375rem', fontWeight: 600, color: c.valueColor, lineHeight: 1.375 }}>{c.value}</Typography>
                  </Box>
                </Box>
              </Box>
            </Grid>
          ))}
        </Grid>

        <Box sx={{ display: 'flex', flexWrap: 'wrap', justifyContent: 'center', gap: 2, mb: 6 }}>
          <Button onClick={handleOpenReleases} variant="contained" size="large" startIcon={<RefreshCw size={18} />}>
            {t('about.open_releases')}
          </Button>
          <Button onClick={handleOpenWebsite} variant="outlined" size="large" startIcon={<Globe size={18} />}>{t('about.website')}</Button>
          <Button onClick={handleOpenGitHub} variant="outlined" size="large" startIcon={<LinkIcon size={18} />}>GitHub</Button>
          <Button onClick={handleOpenQQGroup} variant="outlined" size="large" startIcon={<Users size={18} />}>{t('about.qq_group')}</Button>
        </Box>

        <Box component="footer" sx={{ textAlign: 'center', pb: 2 }}>
          <Typography variant="caption" sx={{ display: 'block', fontSize: '0.75rem', color: 'text.secondary' }}>Copyleft 🄯 © 2025-2026 SniShaper Team and JetCPP Team. {t('about.rights_reserved')}</Typography>
          <Typography variant="caption" sx={{ display: 'block', fontSize: '0.6875rem', color: 'text.secondary', opacity: 0.6, mt: 1 }}>{t('about.made_with')} SniShaper Team</Typography>
        </Box>
      </Box>
    </Box>
  );
};

export default About;
