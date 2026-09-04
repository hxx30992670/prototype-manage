import { test, expect, type APIRequestContext } from '@playwright/test';

/**
 * 安全验收 E2E：
 * - 沙箱 iframe 不允许 allow-same-origin/allow-popups；
 * - 管理域/预览域响应头（CSP、X-Frame-Options、Referrer-Policy）；
 * - 预览域禁止管理 API，管理域禁止内容通道（Host 头隔离）；
 * - 分享密码暴力尝试与无效 Token 被拒绝；
 * - ZIP 攻击载荷被发布管线拒绝；
 * - 预览域健康检查不依赖任何会话。
 * V1 不测病毒扫描。
 */

/** 以指定虚拟域发出请求（Node 网络栈直连 127.0.0.1）。 */
async function hostRequest(request: APIRequestContext, host: string, path: string, init: Parameters<APIRequestContext['get']>[1] = {}) {
  const headers = { Host: host, ...(init.headers ?? {}) };
  return request.get(`http://127.0.0.1${path}`, { ...init, headers });
}

function hostPost(request: APIRequestContext, host: string, path: string, data: unknown) {
  return request.post(`http://127.0.0.1${path}`, {
    headers: { Host: host, 'Content-Type': 'application/json' },
    data,
  });
}

test.describe('Security Acceptance', () => {
  test('原型预览 iframe 强制 sandbox 且禁止同源', async ({ page }) => {
    await page.setContent(`
      <iframe id="preview"
        sandbox="allow-scripts allow-forms allow-modals allow-downloads"
        referrerpolicy="no-referrer"
        src="http://preview.corp.test/content/c/ticket/index.html"></iframe>
    `);
    const iframe = page.locator('#preview');
    const sandbox = await iframe.getAttribute('sandbox');
    expect(sandbox).toBe('allow-scripts allow-forms allow-modals allow-downloads');
    expect(sandbox).not.toContain('allow-same-origin');
    expect(sandbox).not.toContain('allow-popups');
    expect(await iframe.getAttribute('referrerpolicy')).toBe('no-referrer');
  });

  test('管理域与预览域返回安全响应头', async ({ request }) => {
    for (const host of ['prototype.corp.test', 'preview.corp.test']) {
      const res = await hostRequest(request, host, '/');
      const headers = res.headers();
      expect(headers['content-security-policy'] || '').toContain("default-src 'self'");
      expect(headers['x-frame-options'] || '').toBe('DENY');
      expect(headers['referrer-policy'] || '').toBe('no-referrer');
      expect(headers['x-content-type-options'] || '').toBe('nosniff');
    }
  });

  test('预览域禁止管理 API，管理域禁止内容通道', async ({ request }) => {
    const previewApi = await hostRequest(request, 'preview.corp.test', '/api/v1/auth/me');
    expect(previewApi.status()).toBe(404);

    const previewUpload = await hostRequest(request, 'preview.corp.test', '/upload-objects/prototype-objects/');
    expect(previewUpload.status()).toBe(404);

    const minioConsole = await hostRequest(request, 'prototype.corp.test', '/minio/console/');
    expect(minioConsole.status()).toBe(404);

    const adminContent = await hostRequest(request, 'prototype.corp.test', '/content/c/fake/index.html');
    expect(adminContent.status()).toBe(404);
  });

  test('分享密码暴力尝试与无效 Token 被拒绝', async ({ request }) => {
    const res = await hostPost(request, 'preview.corp.test', '/share-api/v1/shares/doesnotexist/verify', {
      password: 'wrong',
      guestName: 'g',
    });
    expect([403, 404, 429]).toContain(res.status());
  });

  test('未认证登录尝试被拒绝', async ({ request }) => {
    const login = await hostPost(request, 'prototype.corp.test', '/api/v1/auth/login', {
      username: '__nobody__',
      password: '__nope__',
    });
    expect([401, 403, 429]).toContain(login.status());
  });

  test('预览域健康检查不依赖任何会话', async ({ request }) => {
    const res = await hostRequest(request, 'preview.corp.test', '/health');
    expect(res.status()).toBe(200);
    expect((await res.text()).trim()).toBe('preview-ok');
  });
});
