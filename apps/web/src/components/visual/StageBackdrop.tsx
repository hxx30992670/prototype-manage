import React from 'react';
import { CursorAura } from './CursorAura';
import { SignalField } from './SignalField';

interface StageBackdropProps {
  children: React.ReactNode;
  density?: 'login' | 'app';
}

export const StageBackdrop: React.FC<StageBackdropProps> = ({ children, density = 'app' }) => (
  <div className="relative isolate min-h-screen overflow-hidden bg-void text-ink">
    <div className="pointer-events-none absolute inset-0 bg-grid opacity-70" />
    <div className="stage-prism pointer-events-none absolute inset-0" />
    <div className="light-caustic pointer-events-none absolute inset-0" />
    <SignalField density={density} />
    <div className="orb-signal pointer-events-none absolute -left-24 top-[-8rem] h-[28rem] w-[28rem] animate-float rounded-full blur-3xl" />
    <div className="orb-ember pointer-events-none absolute -right-16 bottom-[-6rem] h-[24rem] w-[24rem] animate-float rounded-full blur-3xl [animation-delay:1.4s]" />
    <div className="stage-vignette pointer-events-none absolute inset-0" />
    <div className="scanlines pointer-events-none absolute inset-0" />
    <div className="bg-noise pointer-events-none absolute inset-0 opacity-40" />
    <CursorAura />
    <div className="relative z-10">{children}</div>
  </div>
);
