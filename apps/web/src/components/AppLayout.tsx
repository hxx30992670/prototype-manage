import React, { useState } from 'react';
import { Menu, Button, Space, Tag, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import {
  AppstoreOutlined,
  UserOutlined,
  LogoutOutlined,
  DeleteOutlined,
  FolderOpenOutlined,
  CheckSquareOutlined,
  PaperClipOutlined,
  SettingOutlined,
  SkinOutlined,
} from '@ant-design/icons';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { authApi } from '@/features/auth/api';
import { BrandMark } from './visual/BrandMark';
import { StageBackdrop } from './visual/StageBackdrop';
import { ThemeSettingsDialog } from './visual/ThemeSettingsDialog';

export const AppLayout: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const queryClient = useQueryClient();
  const [themeOpen, setThemeOpen] = useState(false);

  const { data: user } = useQuery({
    queryKey: ['auth', 'me'],
    queryFn: () => authApi.getCurrentUser(),
    staleTime: 5 * 60 * 1000,
  });

  const isAdmin = user?.roles.some((r) => r === 'ADMIN' || r === 'ROLE_ADMIN');
  const isCreator = user?.roles.some(
    (r) => r === 'CREATOR' || r === 'ROLE_CREATOR' || r === 'ADMIN' || r === 'ROLE_ADMIN'
  );

  const handleLogout = async () => {
    try {
      await authApi.logout();
    } finally {
      queryClient.removeQueries({ queryKey: ['auth', 'me'] });
      navigate('/login');
    }
  };

  const menuItems: MenuProps['items'] = [
    {
      key: '/prototypes',
      icon: <AppstoreOutlined />,
      label: '原型库',
      onClick: () => navigate('/prototypes'),
    },
    ...(isCreator
      ? [
          {
            key: '/my-prototypes',
            icon: <FolderOpenOutlined />,
            label: '我的原型',
            onClick: () => navigate('/my-prototypes'),
          },
        ]
      : []),
    {
      key: '/reviews',
      icon: <CheckSquareOutlined />,
      label: '评审',
      onClick: () => navigate('/reviews'),
    },
    {
      key: '/attachments',
      icon: <PaperClipOutlined />,
      label: '附件',
      onClick: () => navigate('/attachments'),
    },
    ...(isCreator
      ? [
          {
            key: '/recycle-bin',
            icon: <DeleteOutlined />,
            label: '回收站',
            onClick: () => navigate('/recycle-bin'),
          },
        ]
      : []),
    ...(isAdmin
      ? [
          {
            key: 'admin-submenu',
            icon: <SettingOutlined />,
            label: '系统管理',
            children: [
              {
                key: '/admin/users',
                label: '用户管理',
                onClick: () => navigate('/admin/users'),
              },
              {
                key: '/admin/catalog',
                label: '分类与标签',
                onClick: () => navigate('/admin/catalog'),
              },
              {
                key: '/admin/config',
                label: '系统配置',
                onClick: () => navigate('/admin/config'),
              },
              {
                key: '/admin/audit',
                label: '审计日志',
                onClick: () => navigate('/admin/audit'),
              },
            ],
          },
        ]
      : []),
  ];

  const userMenuItems: MenuProps['items'] = [
    {
      key: 'change-password',
      label: '修改密码',
      onClick: () => navigate('/change-password'),
    },
    {
      key: 'theme',
      icon: <SkinOutlined />,
      label: '主题设置',
      onClick: () => setThemeOpen(true),
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
      danger: true,
      onClick: handleLogout,
    },
  ];

  const selectedKey = location.pathname.startsWith('/admin')
    ? location.pathname
    : location.pathname.startsWith('/prototypes')
      ? '/prototypes'
      : location.pathname;

  return (
    <StageBackdrop density="app">
      <div className="flex min-h-screen flex-col">
        <header className="sticky top-0 z-20 border-b border-signal/10 bg-void/70 backdrop-blur-xl">
          <div className="flex h-16 items-center gap-4 px-4 md:px-6">
            <div className="flex min-w-0 items-center gap-3">
              <BrandMark className="h-8 w-8 shrink-0" />
              <div className="min-w-0">
                <div className="font-display text-base tracking-[0.16em] text-signal font-semibold">戴玛科技</div>
                <div className="truncate text-[11px] text-mute">原型资产管理平台</div>
              </div>
            </div>

            <Menu
              mode="horizontal"
              selectedKeys={[selectedKey]}
              items={menuItems}
              className="app-shell-menu min-w-0 flex-1 !bg-transparent"
            />

            <Space size="middle" className="shrink-0">
              {user ? (
                <Dropdown
                  trigger={['hover']}
                  menu={{ items: userMenuItems }}
                  placement="bottomRight"
                >
                  <Button type="text" icon={<UserOutlined />} className="!text-ink hover:!text-signal">
                    {user.displayName || user.username}
                    <span className="ml-1.5 font-mono text-xs text-mute">@{user.username}</span>
                    {user.roles.map((role) => (
                      <Tag key={role} color="cyan" className="ml-1.5">
                        {role.replace('ROLE_', '')}
                      </Tag>
                    ))}
                  </Button>
                </Dropdown>
              ) : null}
            </Space>
          </div>
          <div
            key={location.pathname}
            className="h-px origin-left animate-route-scan bg-gradient-to-r from-transparent via-signal to-transparent"
          />
        </header>

        <main className="flex-1 px-4 py-6 md:px-6">
          <div className="animate-rise">
            <Outlet />
          </div>
        </main>
      </div>
      <ThemeSettingsDialog open={themeOpen} onClose={() => setThemeOpen(false)} />
    </StageBackdrop>
  );
};
