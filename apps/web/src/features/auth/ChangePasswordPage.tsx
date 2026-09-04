import React, { useState } from 'react';
import { Alert, Button, Form, Input, message } from 'antd';
import { LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { authApi, type ChangePasswordPayload } from './api';
import { normalizeApiError } from '@/lib/http';
import { StageBackdrop } from '@/components/visual/StageBackdrop';
import { GlassPanel } from '@/components/visual/GlassPanel';
import { BrandMark } from '@/components/visual/BrandMark';

export const ChangePasswordPage: React.FC = () => {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [shake, setShake] = useState(false);

  const handleSubmit = async (values: ChangePasswordPayload & { confirmPassword: string }) => {
    if (values.newPassword !== values.confirmPassword) {
      setErrorMessage('两次输入的新密码不一致');
      setShake(true);
      return;
    }
    setLoading(true);
    setErrorMessage(null);
    try {
      await authApi.changePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success('密码修改成功，请继续使用');
      queryClient.setQueryData(['auth', 'me'], (current: unknown) => {
        if (!current || typeof current !== 'object') return current;
        return { ...current, mustChangePassword: false };
      });
      await queryClient.invalidateQueries({ queryKey: ['auth', 'me'] });
      navigate('/prototypes');
    } catch (err) {
      const normalized = normalizeApiError(err);
      setErrorMessage(normalized.message || '密码修改失败');
      setShake(true);
    } finally {
      setLoading(false);
    }
  };

  return (
    <StageBackdrop density="login">
      <div className="flex min-h-screen items-center justify-center px-5 py-12">
        <GlassPanel className="w-full max-w-[440px] animate-rise" shake={shake} onShakeEnd={() => setShake(false)}>
          <div className="mb-7 flex flex-col items-center text-center">
            <BrandMark className="mb-4 h-12 w-12" />
            <h2 className="font-display text-3xl font-semibold tracking-[0.18em] text-ink">修改密码</h2>
            <p className="mt-2 text-sm text-mute">首次登录或管理员重置密码后必须修改密码</p>
          </div>

          {errorMessage ? (
            <Alert message={errorMessage} type="error" showIcon className="mb-4" />
          ) : null}

          <Form onFinish={handleSubmit} layout="vertical" className="tech-form" requiredMark={false}>
            <Form.Item
              name="oldPassword"
              label={<span className="text-mute">原密码</span>}
              rules={[{ required: true, message: '请输入原密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="原密码" size="large" />
            </Form.Item>

            <Form.Item
              name="newPassword"
              label={<span className="text-mute">新密码</span>}
              rules={[
                { required: true, message: '请输入新密码' },
                { min: 6, message: '密码长度至少6位' },
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="新密码 (至少6位)" size="large" />
            </Form.Item>

            <Form.Item
              name="confirmPassword"
              label={<span className="text-mute">确认新密码</span>}
              rules={[{ required: true, message: '请确认新密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="再次输入新密码" size="large" />
            </Form.Item>

            <Form.Item className="!mb-0">
              <Button type="primary" htmlType="submit" size="large" block loading={loading} className="login-submit-btn">
                确认修改
              </Button>
            </Form.Item>
          </Form>
        </GlassPanel>
      </div>
    </StageBackdrop>
  );
};
