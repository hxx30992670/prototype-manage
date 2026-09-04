import { defineConfig, devices } from '@playwright/test';

const managementBaseURL = process.env.E2E_MANAGEMENT_BASE_URL || 'http://prototype.corp.test';
const previewBaseURL = process.env.E2E_PREVIEW_BASE_URL || 'http://preview.corp.test';
const e2eServerIP = process.env.E2E_SERVER_IP;
const hostResolverRules = e2eServerIP
  ? `MAP prototype.corp.test ${e2eServerIP},MAP preview.corp.test ${e2eServerIP}`
  : undefined;
const browserArgs = hostResolverRules
  ? [
      '--proxy-server=direct://',
      '--proxy-bypass-list=*',
      `--host-resolver-rules=${hostResolverRules}`,
    ]
  : undefined;

// 浏览器页面使用真实的两个主机名，才能覆盖 React 按 Host 分流和 Host-only Cookie。
// 测试服务器没有 DNS 时，可设置 E2E_SERVER_IP 让 Chromium 将两个域名映射到同一 IP；
// 也可通过 E2E_*_BASE_URL 指向已配置的测试域名。脚本级 Host 隔离由 verify-http-isolation.sh 覆盖。
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: process.env.CI ? 1 : undefined,
  reporter: 'html',
  use: {
    baseURL: managementBaseURL,
    trace: 'on-first-retry',
    ...(browserArgs ? { launchOptions: { args: browserArgs } } : {}),
  },
  projects: [
    {
      name: 'admin-portal',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: managementBaseURL,
        ...(browserArgs ? { launchOptions: { args: browserArgs } } : {}),
      },
    },
    {
      name: 'preview-portal',
      use: {
        ...devices['Desktop Chrome'],
        baseURL: previewBaseURL,
        ...(browserArgs ? { launchOptions: { args: browserArgs } } : {}),
      },
    },
  ],
});
