import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { ThemeProvider } from './ThemeProvider';
import { ThemeDock } from '@/components/visual/ThemeDock';
import { THEME_STORAGE_KEY, resolveThemeMode } from './theme';

type Scheme = 'light' | 'dark';

function mockColorScheme(initial: Scheme) {
  const state = { scheme: initial };
  const listeners = new Set<(event: { matches: boolean }) => void>();

  window.matchMedia = ((query: string) => {
    const isLightQuery = query.includes('prefers-color-scheme: light');
    return {
      get matches() {
        if (isLightQuery) return state.scheme === 'light';
        return false;
      },
      media: query,
      onchange: null,
      addListener: (cb: (event: { matches: boolean }) => void) => listeners.add(cb),
      removeListener: (cb: (event: { matches: boolean }) => void) => listeners.delete(cb),
      addEventListener: (_event: string, cb: (event: { matches: boolean }) => void) => listeners.add(cb),
      removeEventListener: (_event: string, cb: (event: { matches: boolean }) => void) => listeners.delete(cb),
      dispatchEvent: () => false,
    };
  }) as unknown as typeof window.matchMedia;

  return {
    setScheme(scheme: Scheme) {
      state.scheme = scheme;
      listeners.forEach((cb) => cb({ matches: scheme === 'light' }));
    },
  };
}

describe('ThemeProvider', () => {
  const originalMatchMedia = window.matchMedia;

  beforeEach(() => {
    window.localStorage.clear();
    document.documentElement.removeAttribute('data-theme');
    document.documentElement.removeAttribute('data-theme-pref');
    document.documentElement.style.colorScheme = '';
    mockColorScheme('dark');
  });

  afterEach(() => {
    window.matchMedia = originalMatchMedia;
  });

  it('defaults to dark theme and can switch to the light HUD theme', () => {
    render(
      <ThemeProvider>
        <ThemeDock />
      </ThemeProvider>
    );

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(document.documentElement.getAttribute('data-theme-pref')).toBe('dark');
    fireEvent.click(screen.getByRole('radio', { name: '切换为日光主题' }));

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.style.colorScheme).toBe('light');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light');
    expect(screen.getByRole('radio', { name: '切换为日光主题' })).toHaveAttribute('aria-checked', 'true');
  });

  it('restores the stored light theme on mount', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'light');

    render(
      <ThemeProvider>
        <ThemeDock />
      </ThemeProvider>
    );

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.getAttribute('data-theme-pref')).toBe('light');
    expect(screen.getByRole('radio', { name: '切换为日光主题' })).toHaveAttribute('aria-checked', 'true');
  });

  it('stores system preference on the frontend and follows the OS color scheme', () => {
    mockColorScheme('light');

    render(
      <ThemeProvider>
        <ThemeDock />
      </ThemeProvider>
    );

    fireEvent.click(screen.getByRole('radio', { name: '跟随系统主题' }));

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');
    expect(document.documentElement.getAttribute('data-theme-pref')).toBe('system');
    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(screen.getByRole('radio', { name: '跟随系统主题' })).toHaveAttribute('aria-checked', 'true');
  });

  it('updates the applied theme when the system color scheme changes', () => {
    const media = mockColorScheme('dark');
    window.localStorage.setItem(THEME_STORAGE_KEY, 'system');

    render(
      <ThemeProvider>
        <ThemeDock />
      </ThemeProvider>
    );

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');

    act(() => {
      media.setScheme('light');
    });

    expect(document.documentElement.getAttribute('data-theme')).toBe('light');
    expect(document.documentElement.getAttribute('data-theme-pref')).toBe('system');
    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('system');
  });

  it('resolves system preference to the current OS theme', () => {
    mockColorScheme('light');
    expect(resolveThemeMode('system')).toBe('light');
    mockColorScheme('dark');
    expect(resolveThemeMode('system')).toBe('dark');
    expect(resolveThemeMode('light')).toBe('light');
  });
});
