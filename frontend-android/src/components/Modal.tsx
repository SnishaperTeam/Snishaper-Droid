import React from 'react';
import { Modal as MuiModal, Box, Typography, IconButton } from '@mui/material';
import { X } from '../lib/icons';
import { useTranslation } from '../i18n/I18nContext';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  footer?: React.ReactNode;
  maxWidth?: string;
}

const Modal: React.FC<ModalProps> = ({
  isOpen,
  onClose,
  title,
  subtitle,
  children,
  footer,
  maxWidth = '42rem'
}) => {
  const { t } = useTranslation();
  return (
    <MuiModal open={isOpen} onClose={onClose}>
      <Box
        sx={{
          position: 'absolute',
          top: '50%',
          left: '50%',
          transform: 'translate(-50%, -50%)',
          // On phones the dialog must stay inside the viewport: the outer
          // margin keeps it clear of the screen edges and the padding shrinks.
          width: '100%',
          maxWidth,
          maxHeight: { xs: '90vh', sm: '85vh' },
          display: 'flex',
          flexDirection: 'column',
          bgcolor: 'background.paper',
          border: 1,
          borderColor: 'divider',
          borderRadius: 2,
          boxShadow: 24,
          backdropFilter: 'blur(24px) saturate(180%)',
          WebkitBackdropFilter: 'blur(24px) saturate(180%)',
          m: { xs: 1, sm: 2 },
          p: { xs: 2, sm: 3 },
        }}
      >
        {title && (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 0.5, pr: 4 }}>
            <Typography variant="h5" sx={{ fontWeight: 'bold', letterSpacing: '-0.02em' }}>
              {title}
            </Typography>
            {subtitle && (
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, fontWeight: 'medium' }}>
                {subtitle}
              </Typography>
            )}
          </Box>
        )}
        <Box sx={{ flexGrow: 1, py: 2, overflowY: 'auto', overflowX: 'hidden', minWidth: 0 }}>
          {children}
        </Box>
        {footer && (
          <Box sx={{ pt: 2, borderTop: 1, borderColor: 'divider', display: 'flex', gap: 1, justifyContent: 'flex-end' }}>
            {footer}
          </Box>
        )}
        <IconButton
          aria-label={t('modal.close_dialog')}
          size="small"
          onClick={onClose}
          sx={{ position: 'absolute', top: 8, right: 8 }}
        >
          <X size={16} />
        </IconButton>
      </Box>
    </MuiModal>
  );
};

export default Modal;
