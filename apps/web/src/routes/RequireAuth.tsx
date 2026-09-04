import React from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Spin, Result, Button } from 'antd';
import { authApi } from '@/features/auth/api';
import { StageBackdrop } from '@/components/visual/StageBackdrop';

interface RequireAuthProps {
  roles?: string[];
}

export const RequireAuth: React.FC<RequireAuthProps> = ({ roles }) => {
  const location = useLocation();

  const { data: user, isPending, isError, isFetching } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.getCurrentUser(),
    retry: false,
    staleTime: 5 * 60 * 1000,
  });

  if (isPending || (isError && isFetching)) {
    return (
      <StageBackdrop density="app">
        <div className="flex min-h-screen flex-col items-center justify-center gap-4">
          <Spin size="large" />
          <p className="font-mono text-xs tracking-[0.32em] text-mute">正在校验会话通道</p>
        </div>
      </StageBackdrop>
    );
  }

  if (isError || !user) {
    const returnUrl = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?returnUrl=${returnUrl}`} replace />;
  }

  if (user.mustChangePassword && location.pathname !== '/change-password') {
    return <Navigate to="/change-password" replace />;
  }

  if (roles && roles.length > 0) {
    const hasRequiredRole = roles.some((role) => user.roles.includes(role) || user.roles.includes(`ROLE_${role}`));
    if (!hasRequiredRole) {
      return (
        <StageBackdrop density="app">
          <div className="grid min-h-screen place-items-center px-4">
            <Result
              status="403"
              title="403"
              subTitle="抱歉，您无权访问此页面。"
              extra={
                <Button type="primary" onClick={() => window.location.assign('/prototypes')}>
                  返回原型库
                </Button>
              }
            />
          </div>
        </StageBackdrop>
      );
    }
  }

  return <Outlet />;
};
