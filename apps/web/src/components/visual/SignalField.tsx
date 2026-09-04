import React, { useEffect, useRef } from 'react';
import { useTheme } from '@/theme/themeContext';

interface SignalFieldProps {
  density?: 'login' | 'app';
  className?: string;
}

interface Node {
  x: number;
  y: number;
  vx: number;
  vy: number;
}

function readGlowRgb() {
  const raw = getComputedStyle(document.documentElement).getPropertyValue('--glow-rgb').trim();
  return raw || '62 232 197';
}

export const SignalField: React.FC<SignalFieldProps> = ({ density = 'app', className }) => {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const { mode } = useTheme();

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    let ctx: CanvasRenderingContext2D | null = null;
    try {
      ctx = canvas.getContext('2d');
    } catch {
      return undefined;
    }
    if (!ctx) return;

    const mouse = { x: 0.5, y: 0.5 };
    const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    const count = density === 'login' ? 72 : 42;
    const nodes: Node[] = Array.from({ length: count }, () => ({
      x: Math.random(),
      y: Math.random(),
      vx: (Math.random() - 0.5) * 0.00035,
      vy: (Math.random() - 0.5) * 0.00035,
    }));
    const nodeAlpha = mode === 'light' ? 0.72 : 0.85;
    const linkAlpha = mode === 'light' ? 0.22 : 0.28;

    const resize = () => {
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      canvas.width = Math.max(1, Math.floor(width * dpr));
      canvas.height = Math.max(1, Math.floor(height * dpr));
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };

    const draw = (animate: boolean) => {
      const width = canvas.clientWidth;
      const height = canvas.clientHeight;
      const glow = readGlowRgb();
      ctx.clearRect(0, 0, width, height);

      for (const node of nodes) {
        if (animate) {
          const ax = (mouse.x - node.x) * 0.00004;
          const ay = (mouse.y - node.y) * 0.00004;
          node.vx += ax;
          node.vy += ay;
          node.x += node.vx;
          node.y += node.vy;
          if (node.x < 0 || node.x > 1) node.vx *= -1;
          if (node.y < 0 || node.y > 1) node.vy *= -1;
          node.x = Math.min(1, Math.max(0, node.x));
          node.y = Math.min(1, Math.max(0, node.y));
        }

        ctx.beginPath();
        ctx.fillStyle = `rgb(${glow} / ${nodeAlpha})`;
        ctx.arc(node.x * width, node.y * height, mode === 'light' ? 1.35 : 1.2, 0, Math.PI * 2);
        ctx.fill();
      }

      const linkDistance = density === 'login' ? 0.12 : 0.1;
      for (let i = 0; i < nodes.length; i += 1) {
        for (let j = i + 1; j < nodes.length; j += 1) {
          const dx = nodes[i].x - nodes[j].x;
          const dy = nodes[i].y - nodes[j].y;
          const dist = Math.hypot(dx, dy);
          if (dist > linkDistance) continue;
          const alpha = (1 - dist / linkDistance) * linkAlpha;
          ctx.strokeStyle = `rgb(${glow} / ${alpha})`;
          ctx.lineWidth = 1;
          ctx.beginPath();
          ctx.moveTo(nodes[i].x * width, nodes[i].y * height);
          ctx.lineTo(nodes[j].x * width, nodes[j].y * height);
          ctx.stroke();
        }
      }
    };

    resize();
    draw(!reduced);

    if (reduced) return undefined;

    let frame = 0;
    const tick = () => {
      draw(true);
      frame = window.requestAnimationFrame(tick);
    };
    frame = window.requestAnimationFrame(tick);

    const onMove = (event: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      mouse.x = (event.clientX - rect.left) / rect.width;
      mouse.y = (event.clientY - rect.top) / rect.height;
    };

    window.addEventListener('mousemove', onMove, { passive: true });
    window.addEventListener('resize', resize);

    return () => {
      window.cancelAnimationFrame(frame);
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('resize', resize);
    };
  }, [density, mode]);

  return <canvas ref={canvasRef} className={`absolute inset-0 h-full w-full ${className ?? ''}`} />;
};
