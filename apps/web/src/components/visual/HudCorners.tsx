import React from 'react';

export const HudCorners: React.FC = () => (
  <>
    <span className="pointer-events-none absolute left-3 top-3 h-4 w-4 border-l-2 border-t-2 border-signal/80" />
    <span className="pointer-events-none absolute right-3 top-3 h-4 w-4 border-r-2 border-t-2 border-signal/80" />
    <span className="pointer-events-none absolute bottom-3 left-3 h-4 w-4 border-b-2 border-l-2 border-ember/70" />
    <span className="pointer-events-none absolute bottom-3 right-3 h-4 w-4 border-b-2 border-r-2 border-ember/70" />
  </>
);
