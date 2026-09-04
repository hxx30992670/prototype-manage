import React from 'react';
import { Modal } from 'antd';
import { ThemeDock } from './ThemeDock';
import { useTheme } from '@/theme/themeContext';

interface ThemeSettingsDialogProps {
  open: boolean;
  onClose: () => void;
}

const HINT: Record<string, string> = {
  dark: '已固定为暗场主题，不受系统外观影响。',
  light: '已固定为日光主题，不受系统外观影响。',
  system: '跟随操作系统外观：系统浅色用日光，系统深色用暗场。',
};

export const ThemeSettingsDialog: React.FC<ThemeSettingsDialogProps> = ({ open, onClose }) => {
  const { preference } = useTheme();

  return (
    <Modal
      open={open}
      onCancel={onClose}
      footer={null}
      centered
      destroyOnHidden
      title={<span className="font-display tracking-[0.12em]">主题设置</span>}
    >
      <p className="mb-5 text-sm leading-6 text-mute">
        选择界面外观。这项偏好只保存在当前浏览器，不会同步到账号。
      </p>
      <div className="flex justify-center py-2">
        <ThemeDock />
      </div>
      <p className="mt-4 text-center font-mono text-[11px] tracking-wide text-signal">{HINT[preference]}</p>
    </Modal>
  );
};
