import React, { useEffect, useRef } from 'react';

export const CursorAura: React.FC = () => {
  const nodeRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const node = nodeRef.current;
    if (!node) return;

    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduced) return;

    const onMove = (event: MouseEvent) => {
      node.style.transform = `translate3d(${event.clientX - 160}px, ${event.clientY - 160}px, 0)`;
    };

    window.addEventListener('mousemove', onMove, { passive: true });
    return () => window.removeEventListener('mousemove', onMove);
  }, []);

  return (
    <div
      ref={nodeRef}
      className="cursor-aura pointer-events-none fixed left-0 top-0 z-0 h-80 w-80 rounded-full opacity-80 will-change-transform"
      style={{ transform: 'translate3d(-200px, -200px, 0)' }}
    />
  );
};
