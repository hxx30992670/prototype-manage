/* eslint-disable react-refresh/only-export-components */
import React from 'react';
import { createBrowserRouter, Navigate } from 'react-router-dom';
import type { RouteObject } from 'react-router-dom';
import { Result, Button } from 'antd';
import { RequireAuth } from './RequireAuth';
import { AppLayout } from '@/components/AppLayout';
import { LoginPage } from '@/features/auth/LoginPage';
import { ChangePasswordPage } from '@/features/auth/ChangePasswordPage';
import { PrototypeListPage } from '@/features/prototypes/PrototypeListPage';
import { PrototypeDetailPage } from '@/features/prototypes/PrototypeDetailPage';
import { MyPrototypesPage } from '@/features/prototypes/MyPrototypesPage';
import { UserAdminPage } from '@/features/admin/users/UserAdminPage';
import { CatalogPage } from '@/features/admin/catalog/CatalogPage';
import { SystemConfigPage } from '@/features/admin/config/SystemConfigPage';
import { AuditLogPage } from '@/features/admin/audit/AuditLogPage';
import { RecycleBinPage } from '@/features/admin/recycle/RecycleBinPage';
import { AttachmentLibraryPage } from '@/features/attachments/AttachmentLibraryPage';
import { ReviewActivityPage } from '@/features/comments/ReviewActivityPage';
import { SharedPrototypePage } from '@/features/shares/SharedPrototypePage';

const isPreviewDomain = typeof window !== 'undefined' && window.location.host.startsWith('preview.');

const PreviewDomainNotAvailable: React.FC = () => (
  <div className="grid min-h-screen place-items-center bg-void px-4">
    <Result
      status="404"
      title="预览域"
      subTitle="此域名仅用于原型安全隔离预览。访问管理功能请前往管理域。"
      extra={
        <Button type="primary" onClick={() => { window.location.href = 'http://prototype.corp.test/prototypes'; }}>
          前往管理平台
        </Button>
      }
    />
  </div>
);

export const adminRoutes: RouteObject[] = [
  {
    path: '/login',
    element: <LoginPage />,
  },
  {
    element: <RequireAuth />,
    children: [
      {
        path: '/change-password',
        element: <ChangePasswordPage />,
      },
      {
        element: <AppLayout />,
        children: [
          {
            path: '/',
            element: <Navigate to="/prototypes" replace />,
          },
          {
            path: '/prototypes',
            element: <PrototypeListPage />,
          },
          {
            path: '/prototypes/:id',
            element: <PrototypeDetailPage />,
          },
          {
            path: '/my-prototypes',
            element: <MyPrototypesPage />,
          },
          {
            path: '/reviews',
            element: <ReviewActivityPage />,
          },
          {
            path: '/attachments',
            element: <AttachmentLibraryPage />,
          },
          {
            path: '/recycle-bin',
            element: <RecycleBinPage />,
          },
        ],
      },
      {
        path: '/admin',
        element: <RequireAuth roles={['ADMIN']} />,
        children: [
          {
            element: <AppLayout />,
            children: [
              {
                path: 'users',
                element: <UserAdminPage />,
              },
              {
                path: 'catalog',
                element: <CatalogPage />,
              },
              {
                path: 'config',
                element: <SystemConfigPage />,
              },
              {
                path: 'audit',
                element: <AuditLogPage />,
              },
            ],
          },
        ],
      },
    ],
  },
  {
    path: '*',
    element: (
      <div className="grid min-h-screen place-items-center bg-void px-4">
        <Result
          status="404"
          title="404"
          subTitle="页面未找到"
          extra={<Button type="primary" onClick={() => window.location.assign('/prototypes')}>返回原型库</Button>}
        />
      </div>
    ),
  },
];

export const previewRoutes: RouteObject[] = [
  {
    path: '/s/:token',
    element: <SharedPrototypePage />,
  },
  {
    path: '*',
    element: <PreviewDomainNotAvailable />,
  },
];

export const adminRouter = createBrowserRouter(adminRoutes);
export const previewRouter = createBrowserRouter(previewRoutes);
export const router = isPreviewDomain ? previewRouter : adminRouter;
