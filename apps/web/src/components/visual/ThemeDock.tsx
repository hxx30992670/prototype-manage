import React from 'react';
import { useTheme } from '@/theme/themeContext';
import type { ThemePreference } from '@/theme/theme';

const NightGlyph: React.FC = () => (
  <svg viewBox="0 0 12 12" className="h-3 w-3" aria-hidden="true">
    <path d="M7.6 1.4A4.6 4.6 0 1 0 10.6 8 3.6 3.6 0 0 1 7.6 1.4Z" fill="currentColor" />
  </svg>
);

const DayGlyph: React.FC = () => (
  <svg viewBox="0 0 12 12" className="h-3 w-3" aria-hidden="true">
    <circle cx="6" cy="6" r="2.1" fill="currentColor" />
    <path
      d="M6 1v1.4M6 9.6V11M1 6h1.4M9.6 6H11M2.4 2.4l1 1M8.6 8.6l1 1M2.4 9.6l1-1M8.6 3.4l1-1"
      stroke="currentColor"
      strokeWidth="0.9"
      strokeLinecap="square"
    />
  </svg>
);

const SystemGlyph: React.FC = () => (
  <svg viewBox="0 0 12 12" className="h-3 w-3" aria-hidden="true">
    <rect x="1.2" y="2.2" width="9.6" height="7.2" rx="1" fill="none" stroke="currentColor" strokeWidth="1.1" />
    <path d="M6 2.2v7.2" stroke="currentColor" strokeWidth="1.1" />
    <path d="M6 2.2a3.6 3.6 0 0 1 0 7.2" fill="currentColor" />
  </svg>
);

const OPTIONS: Array<{ value: ThemePreference; label: string; action: string; Glyph: React.FC }> = [
  { value: 'dark', label: '暗场', action: '切换为暗场主题', Glyph: NightGlyph },
  { value: 'system', label: '系统', action: '跟随系统主题', Glyph: SystemGlyph },
  { value: 'light', label: '日光', action: '切换为日光主题', Glyph: DayGlyph },
];

export const ThemeDock: React.FC<{ className?: string }> = ({ className = '' }) => {
  const { preference, setPreference } = useTheme();

  return (
    <div role="radiogroup" aria-label="主题" className={`theme-dock ${className}`}>
      <span className="theme-dock-frame">
        <span className={`theme-dock-thumb is-${preference}`} />
        {OPTIONS.map(({ value, label, action, Glyph }) => {
          const selected = preference === value;
          return (
            <button
              key={value}
              type="button"
              role="radio"
              aria-checked={selected}
              aria-label={action}
              className={`theme-dock-opt ${selected ? 'is-active' : ''}`}
              onClick={(event) => setPreference(value, { clientX: event.clientX, clientY: event.clientY })}
            >
              <Glyph />
              {label}
            </button>
          );
        })}
      </span>
    </div>
  );
};
