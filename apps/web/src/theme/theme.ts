export type ThemeMode = 'dark' | 'light';
export type ThemePreference = ThemeMode | 'system';

export const THEME_STORAGE_KEY = 'prototype-theme';

export function isThemeMode(value: unknown): value is ThemeMode {
  return value === 'dark' || value === 'light';
}

export function isThemePreference(value: unknown): value is ThemePreference {
  return value === 'dark' || value === 'light' || value === 'system';
}

export function readStoredTheme(): ThemePreference | null {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY);
    return isThemePreference(stored) ? stored : null;
  } catch {
    return null;
  }
}

export function persistThemePreference(preference: ThemePreference) {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, preference);
  } catch {
    // Ignore quota / private-mode failures.
  }
}

export function readSystemTheme(): ThemeMode {
  if (typeof window === 'undefined') return 'dark';
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
}

export function resolveThemeMode(preference: ThemePreference): ThemeMode {
  return preference === 'system' ? readSystemTheme() : preference;
}

export function paintTheme(mode: ThemeMode, preference: ThemePreference) {
  const root = document.documentElement;
  root.setAttribute('data-theme', mode);
  root.setAttribute('data-theme-pref', preference);
  root.style.colorScheme = mode;
}

export function applyTheme(preference: ThemePreference) {
  paintTheme(resolveThemeMode(preference), preference);
  persistThemePreference(preference);
}

export function resolveInitialPreference(): ThemePreference {
  if (typeof document === 'undefined') return 'dark';
  const fromDom = document.documentElement.getAttribute('data-theme-pref');
  if (isThemePreference(fromDom)) return fromDom;
  return readStoredTheme() ?? 'dark';
}
