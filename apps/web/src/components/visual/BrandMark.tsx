import React from 'react';

interface BrandMarkProps {
  className?: string;
}

export const BrandMark: React.FC<BrandMarkProps> = ({ className = 'h-10 w-10' }) => (
  <div className={`relative grid place-items-center ${className}`}>
    <div className="absolute inset-0 animate-pulse-glow rounded-xl bg-signal/25 blur-md" />
    <svg viewBox="0 0 48 48" className="relative h-full w-full" aria-hidden="true">
      <path
        d="M24 6.5 40 16v16L24 41.5 8 32V16L24 6.5Z"
        fill="rgb(var(--signal-rgb) / 0.08)"
        stroke="var(--signal)"
        strokeWidth="1.8"
      />
      <circle cx="24" cy="24" r="3.4" fill="var(--signal)" />
      <path d="M24 20.2V13M27.2 25.8 33.4 29.6M20.8 25.8 14.6 29.6" stroke="var(--ember)" strokeWidth="1.6" />
    </svg>
  </div>
);
