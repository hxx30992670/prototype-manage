import React from 'react';
import { HudCorners } from './HudCorners';

interface GlassPanelProps {
  children: React.ReactNode;
  className?: string;
  shake?: boolean;
  onShakeEnd?: () => void;
}

export const GlassPanel: React.FC<GlassPanelProps> = ({ children, className = '', shake = false, onShakeEnd }) => (
  <div
    className={`relative overflow-hidden rounded-2xl border border-signal/20 bg-panel/75 p-8 shadow-glow backdrop-blur-xl ${shake ? 'animate-shake' : ''} ${className}`}
    onAnimationEnd={(event) => {
      if (event.animationName.includes('shake')) {
        onShakeEnd?.();
      }
    }}
  >
    <HudCorners />
    <div className="panel-sheen" />
    <div className="pointer-events-none absolute inset-x-10 top-0 h-px animate-scan bg-gradient-to-r from-transparent via-signal to-transparent" />
    {children}
  </div>
);
