import React, { useRef } from 'react';
import { Alert, Button, Form, Input, Space } from 'antd';
import { LockOutlined, UserOutlined } from '@ant-design/icons';
import type { CaptchaResponse, LoginPayload } from './api';
import { GlassPanel } from '@/components/visual/GlassPanel';

interface LoginFormPanelProps {
  loading: boolean;
  errorMessage: string | null;
  captcha: CaptchaResponse;
  returnUrl: string;
  onSubmit: (values: LoginPayload) => Promise<void>;
  onReloadCaptcha: () => void;
}

export const LoginFormPanel: React.FC<LoginFormPanelProps> = ({
  loading,
  errorMessage,
  captcha,
  returnUrl,
  onSubmit,
  onReloadCaptcha,
}) => {
  const wrapRef = useRef<HTMLDivElement>(null);

  const onMove = (event: React.MouseEvent<HTMLDivElement>) => {
    const node = wrapRef.current;
    if (!node) return;
    const rect = node.getBoundingClientRect();
    const x = event.clientX - (rect.left + rect.width / 2);
    const y = event.clientY - (rect.top + rect.height / 2);
    node.style.transform = `translate(${x * 0.08}px, ${y * 0.14}px)`;
  };

  const onLeave = () => {
    const node = wrapRef.current;
    if (node) node.style.transform = 'translate(0, 0)';
  };

  return (
    <GlassPanel className="w-full max-w-[420px] animate-rise [animation-delay:160ms]" shake={Boolean(errorMessage)}>
      <div className="mb-7 text-center">
        <p className="font-mono text-[11px] tracking-[0.2em] text-signal">安全鉴权认证</p>
        <h2 className="mt-2 font-display text-3xl font-semibold tracking-[0.28em] text-ink">登录</h2>
        <p className="mt-2 text-sm text-mute">贵州戴玛科技有限公司 · 原型资产管理平台</p>
      </div>

      <input type="hidden" data-testid="return-url" value={returnUrl} />

      {errorMessage ? (
        <Alert message={errorMessage} type="error" showIcon className="mb-4" />
      ) : null}

      <Form onFinish={onSubmit} layout="vertical" className="tech-form" requiredMark={false}>
        <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input prefix={<UserOutlined />} placeholder="用户名" size="large" autoComplete="username" />
        </Form.Item>

        <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" autoComplete="current-password" />
        </Form.Item>

        {captcha.enabled ? (
          <Form.Item name="captchaCode" rules={[{ required: true, message: '请输入验证码' }]}>
            <Space.Compact className="w-full">
              <Input placeholder="验证码" size="large" />
              {captcha.image ? (
                <img
                  src={captcha.image}
                  alt="验证码"
                  className="h-10 cursor-pointer border border-signal/20 bg-elevated"
                  onClick={onReloadCaptcha}
                />
              ) : null}
            </Space.Compact>
          </Form.Item>
        ) : null}

        <Form.Item className="!mb-2">
          <div
            ref={wrapRef}
            onMouseMove={onMove}
            onMouseLeave={onLeave}
            className="will-change-transform transition-transform duration-150 ease-out"
          >
            <Button type="primary" htmlType="submit" size="large" block loading={loading} className="login-submit-btn">
              登 录
            </Button>
          </div>
        </Form.Item>
      </Form>

      <div className="mt-6 text-center text-mute">
        <p className="text-[11px] tracking-[0.1em]">© 贵州戴玛科技有限公司 版权所有</p>
        <p className="mt-1 text-[11px] tracking-[0.08em] opacity-80">架构与设计：不忘初心 (toms he)</p>
      </div>
    </GlassPanel>
  );
};
