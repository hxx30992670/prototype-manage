import { createContext, useContext } from 'react';
import type { ThemeMode, ThemePreference } from './theme';

export interface ThemeContextValue {
  preference: ThemePreference;
  mode: ThemeMode;
  setPreference: (preference: ThemePreference, origin?: { clientX: number; clientY: number }) => void;
}

export const ThemeContext = createContext<ThemeContextValue | null>(null);

export function useTheme(): ThemeContextValue {
  const context = useContext(ThemeContext);
  if (!context) {
    return {
      preference: 'dark',
      mode: 'dark',
      setPreference: () => undefined,
    };
  }
  return context;
}
