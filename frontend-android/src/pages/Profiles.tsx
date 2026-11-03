import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Box, Button, TextField, Typography, Chip, CircularProgress } from '@mui/material';
import { alpha } from '@mui/material/styles';
import { Plus, Edit, CheckCircle, Map, RefreshCw, Download } from '../lib/icons';
import { toast } from '../lib/toast';
import { useTranslation } from '../i18n/I18nContext';
import {
  ListProfiles, GetActiveProfile, CreateProfile, RenameProfile, DuplicateProfile,
  SwitchProfile, ExportProfile, ImportProfile, SaveFile,
} from '../api/bindings';
import Modal from '../components/Modal';

type NameMode = 'create' | 'rename' | 'duplicate';

const Profiles: React.FC = () => {
  const { t } = useTranslation();
  const [profiles, setProfiles] = useState<any[]>([]);
  const [active, setActive] = useState('');
  const [busy, setBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [switchTarget, setSwitchTarget] = useState<string | null>(null);
  const [nameDialog, setNameDialog] = useState<{ mode: NameMode; source?: string } | null>(null);
  const [nameInput, setNameInput] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);

  const reload = useCallback(async () => {
    try {
      const [list, current] = await Promise.all([ListProfiles(), GetActiveProfile()]);
      setProfiles(Array.isArray(list) ? list : []);
      setActive(typeof current === 'string' ? current : '');
    } catch { /* keep last state */ }
  }, []);

  useEffect(() => {
    reload().finally(() => setLoading(false));
  }, [reload]);

  const runAction = async (action: () => Promise<void>, successKey?: string, successDetail?: string) => {
    setBusy(true);
    try {
      await action();
      if (successKey) toast.success(t(successKey), successDetail);
      await reload();
    } catch (error: any) {
      toast.error(t('profiles.action_failed'), String(error?.message || error || ''));
    } finally {
      setBusy(false);
    }
  };

  const openNameDialog = (mode: NameMode, source?: string) => {
    setNameDialog({ mode, source });
    setNameInput(mode === 'create' ? '' : (source || ''));
  };

  const submitName = () => {
    if (!nameDialog) return;
    const name = nameInput.trim();
    if (!name) return;
    const dialog = nameDialog;
    setNameDialog(null);
    if (dialog.mode === 'create') {
      void runAction(async () => { await CreateProfile(name); }, 'profiles.created', name);
    } else if (dialog.mode === 'rename' && dialog.source) {
      void runAction(async () => { await RenameProfile(dialog.source as string, name); }, 'profiles.renamed', name);
    } else if (dialog.mode === 'duplicate' && dialog.source) {
      void runAction(async () => { await DuplicateProfile(dialog.source as string, name); }, 'profiles.duplicated', name);
    }
  };

  const confirmSwitch = async () => {
    const target = switchTarget;
    setSwitchTarget(null);
    if (!target) return;
    setBusy(true);
    try {
      await SwitchProfile(target);
      toast.success(t('profiles.switched'), target);
      window.location.reload();
    } catch (error: any) {
      toast.error(t('profiles.action_failed'), String(error?.message || error || ''));
      setBusy(false);
    }
  };

  const handleExport = async (name: string) => {
    await runAction(async () => {
      const bundle = await ExportProfile(name);
      await SaveFile(`snishaper-profile-${name}.json`, String(bundle || ''));
    }, 'profiles.exported', name);
  };

  const handleImportFile = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    try {
      const content = await file.text();
      const result: any = await ImportProfile(content);
      if (result?.warnings?.length) {
        toast.info(t('profiles.imported_with_warnings'), result.warnings.join('; '));
      } else {
        toast.success(t('profiles.imported'), result?.name || '');
      }
      await reload();
    } catch (error: any) {
      toast.error(t('profiles.import_invalid'), String(error?.message || error || ''));
    } finally {
      setBusy(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  const labels: Record<NameMode, { title: string; action: string }> = {
    create: { title: 'profiles.create_title', action: 'profiles.create' },
    rename: { title: 'profiles.rename_title', action: 'profiles.rename' },
    duplicate: { title: 'profiles.duplicate_title', action: 'profiles.duplicate' },
  };

  return (
    <Box sx={{ flexGrow: 1, minHeight: 0, width: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <Box sx={{ pt: 4, flexShrink: 0, display: 'flex', flexWrap: 'wrap', justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'flex-end' }, gap: 2, mb: 3 }}>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
          <Box sx={{ p: 1.25, borderRadius: 1.5, border: 1, color: 'primary.main', bgcolor: (theme) => alpha(theme.palette.primary.main, 0.1), borderColor: (theme) => alpha(theme.palette.primary.main, 0.1), display: 'flex' }}>
            <Map size={20} />
          </Box>
          <Typography variant="h5" sx={{ fontWeight: 700, letterSpacing: '-0.02em' }}>{t('profiles.title')}</Typography>
        </Box>
        <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5, flexWrap: 'wrap' }}>
          <Chip
            size="small"
            color={active ? 'success' : 'default'}
            variant={active ? 'filled' : 'outlined'}
            label={active ? `${t('profiles.active')}: ${active}` : t('profiles.none_active')}
          />
          <Button size="small" variant="outlined" disabled={busy} onClick={() => fileInputRef.current?.click()}>
            {t('profiles.import')}
          </Button>
          <Button size="small" variant="outlined" disabled={busy} startIcon={<Plus size={14} />} onClick={() => openNameDialog('create')}>
            {t('profiles.create')}
          </Button>
          <input
            ref={fileInputRef}
            type="file"
            accept="application/json,.json"
            style={{ display: 'none' }}
            onChange={(e) => void handleImportFile(e.target.files?.[0])}
          />
        </Box>
      </Box>

      <Box sx={{ flexGrow: 1, minHeight: 0, overflowY: 'auto', overflowX: 'hidden', pb: 6, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography variant="body2" color="text.secondary">{t('profiles.desc')}</Typography>

        {loading ? (
          <Box sx={{ py: 8, display: 'flex', justifyContent: 'center' }}>
            <CircularProgress size={22} />
          </Box>
        ) : profiles.length === 0 ? (
          <Box sx={{ py: 10, display: 'flex', flexDirection: 'column', alignItems: 'center', color: 'text.secondary', opacity: 0.7, bgcolor: 'background.paper', border: '1px dashed', borderColor: 'divider', borderRadius: 2 }}>
            <Map size={30} strokeWidth={1.5} />
            <Typography variant="body2" sx={{ mt: 1.5 }}>{t('profiles.empty')}</Typography>
          </Box>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1.5 }}>
            {profiles.map((profile) => {
              const isActive = profile.name === active;
              return (
                <Box
                  key={profile.name}
                  sx={{
                    p: 2, bgcolor: 'background.paper', border: 1,
                    borderColor: isActive ? 'primary.main' : 'divider',
                    borderRadius: 2, display: 'flex', flexDirection: 'column', gap: 1.5, minWidth: 0,
                  }}
                >
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 0 }}>
                    <Typography variant="body2" sx={{ fontWeight: 700, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1, minWidth: 0 }}>
                      {profile.name}
                    </Typography>
                    <Box component="span" sx={{ display: 'inline-flex', color: 'primary.main' }}><CheckCircle size={15} aria-hidden /></Box>
                  </Box>
                  {profile.updated_at && (
                    <Typography variant="caption" sx={{ fontSize: '0.6875rem', color: 'text.secondary', fontWeight: 700 }}>
                      {t('profiles.updated')}: {new Date(profile.updated_at).toLocaleString()}
                    </Typography>
                  )}
                  <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.75 }}>
                    <Button
                      size="small"
                      variant="outlined"
                      disabled={busy || isActive}
                      onClick={() => setSwitchTarget(profile.name)}
                      startIcon={<RefreshCw size={14} />}
                    >
                      {t('profiles.switch')}
                    </Button>
                    <Button size="small" disabled={busy} onClick={() => openNameDialog('rename', profile.name)} startIcon={<Edit size={14} />}>
                      {t('profiles.rename')}
                    </Button>
                    <Button size="small" disabled={busy} onClick={() => openNameDialog('duplicate', profile.name)} startIcon={<Plus size={14} />}>
                      {t('profiles.duplicate')}
                    </Button>
                    <Button size="small" disabled={busy} onClick={() => void handleExport(profile.name)} startIcon={<Download size={14} />}>
                      {t('profiles.export')}
                    </Button>
                  </Box>
                </Box>
              );
            })}
          </Box>
        )}
      </Box>

      <Modal
        isOpen={!!switchTarget}
        onClose={() => setSwitchTarget(null)}
        title={t('profiles.switch_confirm_title')}
        footer={(
          <>
            <Button onClick={() => setSwitchTarget(null)} variant="outlined" size="small">{t('common.cancel')}</Button>
            <Button onClick={() => void confirmSwitch()} variant="contained" size="small">{t('profiles.switch')}</Button>
          </>
        )}
      >
        <Typography variant="body2" color="text.secondary" sx={{ lineHeight: 1.6 }}>
          {t('profiles.switch_confirm_desc', { name: switchTarget || '' })}
        </Typography>
      </Modal>

      <Modal
        isOpen={!!nameDialog}
        onClose={() => setNameDialog(null)}
        title={nameDialog ? t(labels[nameDialog.mode].title) : ''}
        footer={(
          <>
            <Button onClick={() => setNameDialog(null)} variant="outlined" size="small">{t('common.cancel')}</Button>
            <Button onClick={submitName} variant="contained" size="small" disabled={!nameInput.trim()}>
              {nameDialog ? t(labels[nameDialog.mode].action) : ''}
            </Button>
          </>
        )}
      >
        <TextField
          autoFocus
          fullWidth
          size="small"
          label={t('profiles.name_label')}
          value={nameInput}
          onChange={(e) => setNameInput(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') submitName(); }}
        />
      </Modal>
    </Box>
  );
};

export default Profiles;
