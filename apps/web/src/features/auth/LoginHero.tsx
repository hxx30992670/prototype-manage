import React, { useEffect, useState } from 'react';
import { BrandMark } from '@/components/visual/BrandMark';

const CHANNELS = [
  { label: '沙箱环境', value: '在线运行' },
  { label: '评审链路', value: '协同就绪' },
  { label: '资产版本', value: '安全受控' },
];

function LiveClock() {
  const [now, setNow] = useState(() => new Date());

  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), 1000);
    return () => window.clearInterval(id);
  }, []);

  return (
    <time className="font-mono text-sm tracking-widest text-signal">
      {now.toLocaleTimeString('zh-CN', { hour12: false })}
    </time>
  );
}

function OrbitCore() {
  return (
    <div className="relative h-full w-full">
      <div className="absolute inset-0 rounded-full border border-signal/20" />
      <div className="absolute inset-0 animate-spin-slow">
        <span className="glow-signal absolute top-0 left-1/2 h-2.5 w-2.5 -translate-x-1/2 rounded-full bg-signal" />
      </div>
      <div className="absolute inset-8 rounded-full border border-dashed border-ember/35" />
      <div className="absolute inset-8 animate-spin-slower">
        <span className="glow-ember absolute top-0 left-1/2 h-2 w-2 -translate-x-1/2 rounded-full bg-ember" />
      </div>
      <div className="absolute inset-16 rounded-full border border-gold/25" />
      <div className="absolute inset-0 grid place-items-center">
        <div className="grid h-20 w-20 place-items-center rounded-full bg-signal/10 ring-1 ring-signal/40">
          <span className="font-display text-xs tracking-[0.18em] text-signal">核心</span>
        </div>
      </div>
    </div>
  );
}

export const LoginHero: React.FC = () => (
  <section className="relative flex flex-col justify-between overflow-hidden px-8 py-10 lg:px-16 lg:py-16">
    <div className="animate-rise flex items-center gap-3">
      <BrandMark className="h-11 w-11" />
      <div>
        <div className="font-display text-sm tracking-[0.18em] text-signal">贵州戴玛科技</div>
        <div className="text-xs text-mute">原型资产管理平台</div>
      </div>
      <div className="ml-auto hidden items-center gap-3 md:flex">
        <span className="glow-live h-1.5 w-1.5 animate-pulse rounded-full bg-signal" />
        <LiveClock />
      </div>
    </div>

    <div className="relative mt-12 lg:mt-0">
      <div className="pointer-events-none absolute top-2 right-[-1rem] hidden h-80 w-80 animate-float xl:block">
        <OrbitCore />
      </div>

      <div className="relative max-w-xl animate-rise [animation-delay:120ms]">
        <p className="font-mono text-xs tracking-[0.2em] text-ember">原型指挥中心</p>
        <h1 className="mt-4 font-display text-4xl leading-[1.15] font-semibold tracking-wide text-ink sm:text-5xl xl:text-[3.5rem]">
          <span className="block">把每一次原型</span>
          <span className="mt-1 block text-signal">送进可审计的轨道</span>
        </h1>
        <p className="mt-5 max-w-md text-sm leading-7 text-mute">
          版本沙箱、评审链路与分享通道在同一指挥面上汇合。登录后进入资产库，追踪发布、预览与协作。
        </p>
        <div className="mt-8 flex flex-wrap gap-3">
          {CHANNELS.map((item) => (
            <div
              key={item.label}
              className="min-w-[7.5rem] rounded-lg border border-signal/15 bg-panel/50 px-3 py-2 backdrop-blur-sm"
            >
              <div className="font-mono text-[10px] tracking-[0.16em] text-mute">{item.label}</div>
              <div className="font-display text-sm text-signal">{item.value}</div>
            </div>
          ))}
        </div>
      </div>

      <div className="relative mx-auto mt-10 h-64 w-64 animate-float xl:hidden">
        <OrbitCore />
      </div>
    </div>

    <div className="mt-10 overflow-hidden border-t border-signal/10 pt-4 font-mono text-[11px] tracking-[0.16em] text-mute">
      <div className="flex w-max animate-ticker gap-10 whitespace-nowrap">
        <span>安全通道 · 加密互联</span>
        <span>预览沙箱 · 稳定就绪</span>
        <span>版本流水线 · 实时处理</span>
        <span>全链路审计 · 安全守护</span>
        <span>贵州戴玛科技有限公司 · 技术驱动</span>
        <span>安全通道 · 加密互联</span>
        <span>预览沙箱 · 稳定就绪</span>
        <span>版本流水线 · 实时处理</span>
        <span>全链路审计 · 安全守护</span>
        <span>贵州戴玛科技有限公司 · 技术驱动</span>
      </div>
    </div>
  </section>
);
