import React, { useCallback, useEffect, useMemo, useState } from 'react';
import { flushSync } from 'react-dom';
import {
  applyTheme,
  paintTheme,
  readSystemTheme,
  resolveInitialPreference,
  resolveThemeMode,
  type ThemeMode,
  type ThemePreference,
} from './theme';
import { ThemeContext, type ThemeContextValue } from './themeContext';

type DocumentWithViewTransition = {
  startViewTransition?: (update: () => void) => { ready: Promise<void> };
};

function prefersReducedMotion() {
  return window.matchMedia('(prefers-reduced-motion: reduce)').matches;
}

function revealFromPoint(x: number, y: number) {
  const radius = Math.hypot(Math.max(x, window.innerWidth - x), Math.max(y, window.innerHeight - y));
  document.documentElement.animate(
    {
      clipPath: [`circle(0px at ${x}px ${y}px)`, `circle(${radius}px at ${x}px ${y}px)`],
    },
    {
      duration: 620,
      easing: 'cubic-bezier(0.16, 1, 0.3, 1)',
      pseudoElement: '::view-transition-new(root)',
    }
  );
}

export const ThemeProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [preference, setPreferenceState] = useState<ThemePreference>(() => {
    const initial = resolveInitialPreference();
    applyTheme(initial);
    return initial;
  });
  const [mode, setMode] = useState<ThemeMode>(() => resolveThemeMode(preference));

  const commit = useCallback((nextPreference: ThemePreference) => {
    applyTheme(nextPreference);
    setPreferenceState(nextPreference);
    setMode(resolveThemeMode(nextPreference));
  }, []);

  const setPreference = useCallback(
    (nextPreference: ThemePreference, origin?: { clientX: number; clientY: number }) => {
      if (nextPreference === preference) return;
      const nextMode = resolveThemeMode(nextPreference);
      const update = () => {
        flushSync(() => commit(nextPreference));
      };

      if (nextMode === mode || prefersReducedMotion()) {
        update();
        return;
      }

      const doc = document as unknown as DocumentWithViewTransition;
      if (typeof doc.startViewTransition !== 'function') {
        update();
        return;
      }

      const x = origin?.clientX ?? window.innerWidth - 48;
      const y = origin?.clientY ?? 48;
      const transition = doc.startViewTransition(update);
      transition.ready.then(() => revealFromPoint(x, y)).catch(() => undefined);
    },
    [commit, mode, preference]
  );

  useEffect(() => {
    if (preference !== 'system') return undefined;

    const media = window.matchMedia('(prefers-color-scheme: light)');
    const syncFromSystem = () => {
      const nextMode = readSystemTheme();
      paintTheme(nextMode, 'system');
      setMode(nextMode);
    };

    media.addEventListener('change', syncFromSystem);
    return () => media.removeEventListener('change', syncFromSystem);
  }, [preference]);

  const value = useMemo<ThemeContextValue>(
    () => ({
      preference,
      mode,
      setPreference,
    }),
    [mode, preference, setPreference]
  );

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>;
};
