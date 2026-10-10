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
    const glow = readGlowRgb();
    const linkDistance = density === 'login' ? 0.12 : 0.1;
    const linkDistanceSquared = linkDistance * linkDistance;
    const frameInterval = 1000 / 30;
    const simulationStep = 1000 / 60;
    let simulationTime = 0;
    let width = 0;
    let height = 0;

    const draw = (elapsed = 0) => {
      // 保留原来的 60 Hz 运动步长，只减少绘制次数，避免降帧后运动变慢。
      simulationTime += elapsed;
      const steps = Math.floor((simulationTime + 0.1) / simulationStep);
      simulationTime = Math.max(0, simulationTime - steps * simulationStep);
      ctx.clearRect(0, 0, width, height);
      ctx.fillStyle = `rgb(${glow} / ${nodeAlpha})`;

      for (const node of nodes) {
        for (let step = 0; step < steps; step += 1) {
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
        ctx.arc(node.x * width, node.y * height, mode === 'light' ? 1.35 : 1.2, 0, Math.PI * 2);
        ctx.fill();
      }

      for (let i = 0; i < nodes.length; i += 1) {
        for (let j = i + 1; j < nodes.length; j += 1) {
          const dx = nodes[i].x - nodes[j].x;
          const dy = nodes[i].y - nodes[j].y;
          const distanceSquared = dx * dx + dy * dy;
          if (distanceSquared > linkDistanceSquared) continue;
          const dist = Math.sqrt(distanceSquared);
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

    const resize = () => {
      // 背景粒子无需使用文字级别的 Retina 分辨率。
      const dpr = Math.min(window.devicePixelRatio || 1, 1.25);
      const nextWidth = canvas.clientWidth;
      const nextHeight = canvas.clientHeight;
      const pixelWidth = Math.max(1, Math.floor(nextWidth * dpr));
      const pixelHeight = Math.max(1, Math.floor(nextHeight * dpr));
      if (width === nextWidth && height === nextHeight && canvas.width === pixelWidth && canvas.height === pixelHeight) return;
      width = nextWidth;
      height = nextHeight;
      canvas.width = pixelWidth;
      canvas.height = pixelHeight;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      draw();
    };

    let frame = 0;
    let lastDraw = performance.now();
    const tick = (timestamp: number) => {
      frame = 0;
      if (document.hidden) return;
      const elapsed = timestamp - lastDraw;
      if (elapsed >= frameInterval - 0.5) {
        // 长时间卡顿后不追赶所有遗漏帧，避免恢复时产生计算尖峰。
        draw(Math.min(elapsed, 100));
        lastDraw = timestamp;
      }
      frame = window.requestAnimationFrame(tick);
    };
    const syncVisibility = () => {
      window.cancelAnimationFrame(frame);
      frame = 0;
      simulationTime = 0;
      lastDraw = performance.now();
      if (!reduced && !document.hidden) frame = window.requestAnimationFrame(tick);
    };

    const onMove = (event: MouseEvent) => {
      const rect = canvas.getBoundingClientRect();
      if (rect.width === 0 || rect.height === 0) return;
      mouse.x = (event.clientX - rect.left) / rect.width;
      mouse.y = (event.clientY - rect.top) / rect.height;
    };

    resize();
    syncVisibility();
    const observer = typeof ResizeObserver === 'undefined' ? null : new ResizeObserver(resize);
    observer?.observe(canvas);
    if (!reduced) window.addEventListener('mousemove', onMove, { passive: true });
    window.addEventListener('resize', resize);
    document.addEventListener('visibilitychange', syncVisibility);

    return () => {
      window.cancelAnimationFrame(frame);
      observer?.disconnect();
      window.removeEventListener('mousemove', onMove);
      window.removeEventListener('resize', resize);
      document.removeEventListener('visibilitychange', syncVisibility);
    };
  }, [density, mode]);

  return <canvas ref={canvasRef} className={`absolute inset-0 h-full w-full ${className ?? ''}`} />;
};
