import { theme, type ThemeConfig } from 'antd';
import type { ThemeMode } from './theme';

const fontFamily = "'Noto Sans SC', 'Oxanium', system-ui, sans-serif";

const darkTheme: ThemeConfig = {
  algorithm: theme.darkAlgorithm,
  token: {
    colorPrimary: '#3ee8c5',
    colorInfo: '#3ee8c5',
    colorSuccess: '#7cffb2',
    colorWarning: '#ffb703',
    colorError: '#ff5d73',
    colorBgBase: '#05070b',
    colorBgContainer: '#10151e',
    colorBgElevated: '#161d28',
    colorBgLayout: '#05070b',
    colorBorder: 'rgba(62, 232, 197, 0.16)',
    colorBorderSecondary: 'rgba(62, 232, 197, 0.08)',
    colorText: '#e7eef8',
    colorTextSecondary: '#8b97ab',
    colorLink: '#3ee8c5',
    borderRadius: 10,
    fontFamily,
    fontSize: 14,
    controlHeight: 40,
    boxShadow: '0 18px 40px rgba(0, 0, 0, 0.35)',
  },
  components: {
    Layout: {
      headerBg: 'transparent',
      bodyBg: 'transparent',
      siderBg: 'transparent',
    },
    Menu: {
      itemBg: 'transparent',
      horizontalItemSelectedColor: '#3ee8c5',
      horizontalItemHoverColor: '#3ee8c5',
      itemColor: '#c5cedb',
      itemHoverColor: '#3ee8c5',
      popupBg: '#10151e',
    },
    Card: {
      colorBgContainer: 'rgba(16, 21, 30, 0.82)',
      headerBg: 'transparent',
    },
    Table: {
      headerBg: 'rgba(62, 232, 197, 0.06)',
      rowHoverBg: 'rgba(62, 232, 197, 0.06)',
    },
    Input: {
      activeBorderColor: '#3ee8c5',
      hoverBorderColor: 'rgba(62, 232, 197, 0.55)',
      colorBgContainer: 'rgba(8, 12, 18, 0.72)',
    },
    Button: {
      primaryShadow: '0 0 18px rgba(62, 232, 197, 0.28)',
    },
    Modal: {
      contentBg: '#10151e',
      headerBg: '#10151e',
    },
    Dropdown: {
      colorBgElevated: '#10151e',
    },
  },
};

const lightTheme: ThemeConfig = {
  algorithm: theme.defaultAlgorithm,
  token: {
    colorPrimary: '#0c8f82',
    colorInfo: '#0c8f82',
    colorSuccess: '#1a9d6c',
    colorWarning: '#c98a12',
    colorError: '#d6455d',
    colorBgBase: '#e8eef5',
    colorBgContainer: '#f7fafc',
    colorBgElevated: '#ffffff',
    colorBgLayout: '#e8eef5',
    colorBorder: 'rgba(12, 143, 130, 0.22)',
    colorBorderSecondary: 'rgba(12, 143, 130, 0.12)',
    colorText: '#0d1b2e',
    colorTextSecondary: '#4e6078',
    colorLink: '#0c8f82',
    borderRadius: 10,
    fontFamily,
    fontSize: 14,
    controlHeight: 40,
    boxShadow: '0 16px 40px rgba(16, 48, 72, 0.1)',
  },
  components: {
    Layout: {
      headerBg: 'transparent',
      bodyBg: 'transparent',
      siderBg: 'transparent',
    },
    Menu: {
      itemBg: 'transparent',
      horizontalItemSelectedColor: '#0c8f82',
      horizontalItemHoverColor: '#0c8f82',
      itemColor: '#2a3d55',
      itemHoverColor: '#0c8f82',
      popupBg: '#ffffff',
    },
    Card: {
      colorBgContainer: 'rgba(255, 255, 255, 0.78)',
      headerBg: 'transparent',
    },
    Table: {
      headerBg: 'rgba(12, 143, 130, 0.08)',
      rowHoverBg: 'rgba(26, 184, 166, 0.08)',
    },
    Input: {
      activeBorderColor: '#0c8f82',
      hoverBorderColor: 'rgba(12, 143, 130, 0.55)',
      colorBgContainer: 'rgba(255, 255, 255, 0.88)',
    },
    Button: {
      primaryShadow: '0 0 18px rgba(26, 184, 166, 0.28)',
    },
    Modal: {
      contentBg: '#ffffff',
      headerBg: '#ffffff',
    },
    Dropdown: {
      colorBgElevated: '#ffffff',
    },
  },
};

export const antdTheme = darkTheme;

export function getAntdTheme(mode: ThemeMode): ThemeConfig {
  return mode === 'light' ? lightTheme : darkTheme;
}
