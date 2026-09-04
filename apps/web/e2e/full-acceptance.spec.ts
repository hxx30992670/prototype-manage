import { test, expect, type APIRequestContext, type APIResponse, type Page } from '@playwright/test';

/**
 * 全业务验收 E2E（真实 API + UI 流程）。
 * 前置：Compose 已启动；两个域名已解析到同一测试服务器；管理员账号可登录。
 * 业务数据在用例内创建，避免依赖测试执行顺序；UI 用于验证关键页面结果，
 * API 用于准备大文件、轮询异步发布和覆盖当前页面尚未提供的细粒度操作。
 */

const ADMIN_USERNAME = process.env.E2E_ADMIN_USERNAME || 'admin';
const ADMIN_PASSWORD = process.env.E2E_ADMIN_PASSWORD || 'Admin@123456';
const ADMIN_PASSWORD_AFTER_CHANGE = process.env.E2E_ADMIN_PASSWORD_AFTER_CHANGE || 'E2e-Admin-New-123';
const MANAGEMENT_HOST = 'prototype.corp.test';
const PREVIEW_HOST = 'preview.corp.test';
const E2E_SERVER_IP = process.env.E2E_SERVER_IP;
const E2E_LOCAL_HOST = E2E_SERVER_IP?.replace(/^\[|\]$/g, '');

const GOOD_ZIP_BASE64 =
  'UEsDBBQAAAAIALybIV3LxBWiLQAAADkAAAAKAAAAaW5kZXguaHRtbLNRTMlPLqksSFXIKMnNsbOBkEn5KZVAtqFdVWaBQqpRqo0+kG2jDxHWB6sBAFBLAwQKAAAAAAC8myFdkF/UpwsAAAALAAAACQAAAGRhdGEuanNvbnsib2siOnRydWV9UEsBAh4DFAAAAAgAvJshXcvEFaItAAAAOQAAAAoAAAAAAAAAAQAAAKSBAAAAAGluZGV4Lmh0bWxQSwECHgMKAAAAAAC8myFdkF/UpwsAAAALAAAACQAAAAAAAAABAAAApIFVAAAAZGF0YS5qc29uUEsFBgAAAAACAAIAbwAAAIcAAAAAAA==';
const INVALID_ZIP_BASE64 =
  'UEsDBBQAAAAIAOubIV3qc795LQAAADYAAAALAAAAcmVhZG1lLmh0bWyzUUzJTy6pLEhVyCjJzbGzgZBJ+SmVdrmZxcWZeekKmXkpqRU2+mAxG32wAgBQSwECHgMUAAAACADrmyFd6nO/eS0AAAA2AAAACwAAAAAAAAABAAAApIEAAAAAcmVhZG1lLmh0bWxQSwUGAAAAAAEAAQA5AAAAVgAAAAAA';

interface Envelope<T> {
  data: T;
}

interface Category {
  code: string;
}

interface UploadResponse {
  uploadId: string;
  uploadUrl: string;
  expiresAt: string;
}

interface UploadInfo {
  uploadId: string;
  status: string;
  actualSize: number;
}

interface VersionResponse {
  versionId: string;
  versionNo: number;
  status: string;
  jobId: number;
}

interface VersionItem {
  versionId: string;
  versionNo: number;
  status: string;
  isCurrent: boolean;
  entryPath?: string;
}

interface PublishJob {
  status: 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED';
  errorDetail?: string;
}

interface Spec {
  rowVersion: number;
}

interface Comment {
  publicId: string;
  rowVersion: number;
  status: string;
}

interface ShareResponse {
  shareId: string;
  rawUrl: string;
  secretAvailable: boolean;
}

interface VerifyResponse {
  success: boolean;
  csrfToken: string;
}

interface ContentTicket {
  contentUrl: string;
}

async function readJson<T>(response: APIResponse, label: string): Promise<Envelope<T>> {
  const body = await response.text();
  expect(response.ok(), `${label} 应成功，实际 ${response.status()}：${body}`).toBeTruthy();
  return JSON.parse(body) as Envelope<T>;
}

function requestOf(page: Page): APIRequestContext {
  return page.context().request;
}

function localizeUrl(rawUrl: string): string {
  if (!E2E_SERVER_IP) return rawUrl;
  const parsed = new URL(rawUrl);
  return `http://${E2E_SERVER_IP}${parsed.pathname}${parsed.search}`;
}

function managementUrl(path: string): string {
  return E2E_SERVER_IP ? `http://${E2E_SERVER_IP}${path}` : path;
}

async function cookiesForHost(page: Page, host: string): Promise<string | undefined> {
  const cookies = await page.context().cookies();
  const values = cookies
    .filter((cookie) => cookie.domain === host || cookie.domain === `.${host}`)
    .map((cookie) => `${cookie.name}=${cookie.value}`);
  return values.length > 0 ? values.join('; ') : undefined;
}

async function managementHeaders(page: Page, extraHeaders: Record<string, string> = {}) {
  const headers = { ...extraHeaders };
  if (E2E_SERVER_IP) {
    headers.Host = MANAGEMENT_HOST;
    const cookie = await cookiesForHost(page, MANAGEMENT_HOST);
    if (cookie) headers.Cookie = cookie;
  }
  return headers;
}

function publicHeaders(extraHeaders: Record<string, string> = {}) {
  return E2E_SERVER_IP ? { Host: PREVIEW_HOST, ...extraHeaders } : extraHeaders;
}

function uploadHeaders(extraHeaders: Record<string, string> = {}) {
  return E2E_SERVER_IP ? { Host: MANAGEMENT_HOST, ...extraHeaders } : extraHeaders;
}

async function copyLocalCookiesToPreview(page: Page): Promise<void> {
  if (!E2E_LOCAL_HOST) return;
  const cookies = await page.context().cookies();
  const localCookies = cookies.filter((cookie) =>
    cookie.domain === E2E_LOCAL_HOST || cookie.domain === `.${E2E_LOCAL_HOST}`,
  );
  if (localCookies.length === 0) return;
  await page.context().addCookies(localCookies.map((cookie) => ({
    name: cookie.name,
    value: cookie.value,
    domain: PREVIEW_HOST,
    path: cookie.path,
    expires: cookie.expires,
    httpOnly: cookie.httpOnly,
    secure: false,
    sameSite: cookie.sameSite,
  })));
}

async function apiGet<T>(page: Page, path: string): Promise<Envelope<T>> {
  return readJson(
    await requestOf(page).get(managementUrl(path), { headers: await managementHeaders(page) }),
    `GET ${path}`,
  );
}

async function apiPost<T>(
  page: Page,
  path: string,
  data: unknown,
  csrfToken: string,
  extraHeaders: Record<string, string> = {},
): Promise<Envelope<T>> {
  return readJson(
    await requestOf(page).post(managementUrl(path), {
      headers: await managementHeaders(page, {
        'X-XSRF-TOKEN': csrfToken,
        'Content-Type': 'application/json',
        ...extraHeaders,
      }),
      data,
    }),
    `POST ${path}`,
  );
}

async function apiPut<T>(page: Page, path: string, data: unknown, csrfToken: string): Promise<Envelope<T>> {
  return readJson(
    await requestOf(page).put(managementUrl(path), {
      headers: await managementHeaders(page, {
        'X-XSRF-TOKEN': csrfToken,
        'Content-Type': 'application/json',
      }),
      data,
    }),
    `PUT ${path}`,
  );
}

async function apiDelete(page: Page, path: string, csrfToken: string): Promise<void> {
  const response = await requestOf(page).delete(managementUrl(path), {
    headers: await managementHeaders(page, { 'X-XSRF-TOKEN': csrfToken }),
  });
  const body = await response.text();
  expect(response.ok(), `DELETE ${path} 应成功，实际 ${response.status()}：${body}`).toBeTruthy();
}

async function loginAsAdmin(page: Page): Promise<void> {
  const submit = async (password: string) => {
    await page.goto('/login');
    await page.getByPlaceholder('用户名').fill(ADMIN_USERNAME);
    await page.getByPlaceholder('密码').fill(password);
    await page.getByRole('button', { name: /登\s*录/ }).click();
    await page.waitForTimeout(300);
  };

  await submit(ADMIN_PASSWORD);
  const initialPath = new URL(page.url()).pathname;
  if (initialPath === '/change-password') {
    await page.getByPlaceholder('原密码').fill(ADMIN_PASSWORD);
    await page.getByPlaceholder('新密码 (至少6位)').fill(ADMIN_PASSWORD_AFTER_CHANGE);
    await page.getByPlaceholder('再次输入新密码').fill(ADMIN_PASSWORD_AFTER_CHANGE);
    await page.getByRole('button', { name: /确\s*认\s*修\s*改/ }).click();
  } else if (initialPath === '/login') {
    await submit(ADMIN_PASSWORD_AFTER_CHANGE);
  }

  await expect(page).toHaveURL(/\/prototypes/, { timeout: 15000 });
}

async function getCsrfToken(page: Page): Promise<string> {
  const response = await apiGet<{ token: string }>(page, '/api/v1/auth/csrf');
  expect(response.data.token).toBeTruthy();
  return response.data.token;
}

async function uploadAndComplete(
  page: Page,
  csrfToken: string,
  filename: string,
  bytes: Buffer,
  fileType: 'HTML' | 'ZIP' | 'ATTACHMENT',
): Promise<UploadInfo> {
  const upload = await apiPost<UploadResponse>(page, '/api/v1/uploads', {
    filename,
    claimedSize: bytes.length,
    fileType,
  }, csrfToken);

  const putResponse = await requestOf(page).put(localizeUrl(upload.data.uploadUrl), {
    headers: uploadHeaders({
      'Content-Type': fileType === 'ZIP'
        ? 'application/zip'
        : fileType === 'ATTACHMENT'
          ? 'application/octet-stream'
          : 'text/html',
    }),
    data: bytes,
  });
  expect(putResponse.ok(), `直传 ${filename} 应成功，实际 ${putResponse.status()}`).toBeTruthy();

  const completed = await apiPost<UploadInfo>(
    page,
    `/api/v1/uploads/${upload.data.uploadId}/complete`,
    { checksum: '' },
    csrfToken,
  );
  expect(completed.data.status).toBe('COMPLETED');
  expect(completed.data.actualSize).toBe(bytes.length);
  return completed.data;
}

async function waitForPublish(
  page: Page,
  prototypeId: string,
  versionId: string,
  expected: 'SUCCEEDED' | 'FAILED',
): Promise<PublishJob> {
  for (let attempt = 0; attempt < 120; attempt += 1) {
    const job = await apiGet<PublishJob>(
      page,
      `/api/v1/prototypes/${prototypeId}/versions/${versionId}/publish-job`,
    );
    if (job.data.status === expected) {
      return job.data;
    }
    if (job.data.status === 'SUCCEEDED' || job.data.status === 'FAILED') {
      throw new Error(`版本 ${versionId} 发布状态为 ${job.data.status}，期望 ${expected}`);
    }
    await page.waitForTimeout(500);
  }
  throw new Error(`版本 ${versionId} 发布任务在 60 秒内未进入 ${expected}`);
}

test.describe('Full Acceptance', () => {
  test('管理员建用户 → HTML/ZIP 发布 → 失败保旧 → 回滚分享 → 删除恢复', async ({ page }, testInfo) => {
    test.skip(testInfo.project.name === 'preview-portal', '全量业务验收仅在管理域执行');
    test.setTimeout(240_000);

    await loginAsAdmin(page);
    let csrfToken = await getCsrfToken(page);
    await expect(page.getByText('原型库').first()).toBeVisible({ timeout: 10000 });

    // 1. 管理员创建创建者，并创建原型所需的分类。
    const categories = await apiGet<Category[]>(page, '/api/v1/categories');
    let categoryCode = categories.data[0]?.code;
    if (!categoryCode) {
      const category = await apiPost<Category>(page, '/api/v1/admin/categories', {
        code: `e2e-cat-${Date.now() % 100000}`,
        name: 'E2E 验收分类',
        sortNo: 99,
      }, csrfToken);
      categoryCode = category.data.code;
    }

    const creator = await apiPost<{ publicId: string }>(page, '/api/v1/admin/users', {
      username: `e2e_creator_${Date.now() % 100000}`,
      password: 'E2e-Password-123',
      displayName: 'E2E 创建者',
      department: '测试',
      roles: ['CREATOR'],
    }, csrfToken);

    const prototype = await apiPost<{ publicId: string }>(page, '/api/v1/prototypes', {
      name: 'E2E 验收原型',
      code: `E2E${Date.now() % 100000}`,
      description: '全量验收用原型',
      categoryId: categoryCode,
      ownerId: creator.data.publicId,
      visibility: 'ALL_INTERNAL',
      tagIds: [],
    }, csrfToken);
    const prototypeId = prototype.data.publicId;

    await page.goto('/prototypes');
    await expect(page.getByText('E2E 验收原型', { exact: true }).first()).toBeVisible({ timeout: 15000 });
    await page.getByRole('link', { name: 'E2E 验收原型' }).first().click();
    await expect(page).toHaveURL(new RegExp(`/prototypes/${prototypeId}`));
    await expect(page.getByText('版本')).toBeVisible();
    await expect(page.getByText('说明与约束')).toBeVisible();
    await expect(page.getByText('分享设置')).toBeVisible();
    await page.goto('/prototypes');

    // 2. 保存结构化说明，并验证 Markdown 内容经过可信页面展示链路。
    const currentSpec = await apiGet<Spec>(page, `/api/v1/prototypes/${prototypeId}/spec`);
    await apiPut(page, `/api/v1/prototypes/${prototypeId}/spec`, {
      goal: '验证 HTML 与 ZIP 版本生命周期',
      coreFlow: '上传、发布、分享、回滚',
      interactionRules: '所有版本操作可追溯',
      businessConstraints: '失败版本不能替换稳定版本',
      dataRequirements: '保留版本和评论关联',
      acceptanceNotes: 'E2E 自动验收',
      markdownExtra: '## 验收说明\n\n内容由安全 Markdown 组件展示。',
      rowVersion: currentSpec.data.rowVersion,
    }, csrfToken);

    // 3. HTML 首版发布，并通过内容票据访问稳定入口。
    const htmlBytes = Buffer.from(
      '<!doctype html><html><body><h1>html e2e</h1></body></html>',
      'utf8',
    );
    const htmlUpload = await uploadAndComplete(page, csrfToken, 'index.html', htmlBytes, 'HTML');
    const firstVersion = await apiPost<VersionResponse>(page, `/api/v1/prototypes/${prototypeId}/versions`, {
      uploadId: htmlUpload.uploadId,
      sourceType: 'HTML',
      changeLog: 'E2E HTML 首版',
    }, csrfToken);
    await waitForPublish(page, prototypeId, firstVersion.data.versionId, 'SUCCEEDED');

    const firstVersions = await apiGet<VersionItem[]>(page, `/api/v1/prototypes/${prototypeId}/versions`);
    expect(firstVersions.data).toContainEqual(expect.objectContaining({
      versionId: firstVersion.data.versionId,
      status: 'PUBLISHED',
      isCurrent: true,
    }));

    const internalTicket = await apiPost<ContentTicket>(
      page,
      `/api/v1/prototypes/${prototypeId}/preview-ticket`,
      {},
      csrfToken,
    );
    const contentResponse = await requestOf(page).get(localizeUrl(internalTicket.data.contentUrl), {
      headers: publicHeaders(),
    });
    expect(contentResponse.status()).toBe(200);
    const contentHeaders = contentResponse.headers();
    expect(contentHeaders['cache-control'] || '').toContain('private');
    expect(contentHeaders['cache-control'] || '').toContain('no-store');
    expect(contentHeaders['referrer-policy']).toBe('no-referrer');
    expect(contentHeaders['content-security-policy'] || '').toContain('sandbox');
    expect(await contentResponse.text()).toContain('html e2e');

    // 4. 缺少 index.html 的 ZIP 发布失败，稳定版本仍保持首版。
    const invalidZip = Buffer.from(INVALID_ZIP_BASE64, 'base64');
    const failedUpload = await uploadAndComplete(page, csrfToken, 'invalid.zip', invalidZip, 'ZIP');
    const failedVersion = await apiPost<VersionResponse>(page, `/api/v1/prototypes/${prototypeId}/versions`, {
      uploadId: failedUpload.uploadId,
      sourceType: 'ZIP',
      changeLog: 'E2E 无入口 ZIP，应失败',
    }, csrfToken);
    const failedJob = await waitForPublish(page, prototypeId, failedVersion.data.versionId, 'FAILED');
    expect(failedJob.errorDetail || '').toContain('index.html');

    const afterFailure = await apiGet<VersionItem[]>(page, `/api/v1/prototypes/${prototypeId}/versions`);
    expect(afterFailure.data).toContainEqual(expect.objectContaining({
      versionId: firstVersion.data.versionId,
      isCurrent: true,
    }));
    expect(afterFailure.data).toContainEqual(expect.objectContaining({
      versionId: failedVersion.data.versionId,
      status: 'FAILED',
    }));

    // 5. 合法 ZIP 成功切换当前版本，再回滚到 HTML 首版。
    const goodZip = Buffer.from(GOOD_ZIP_BASE64, 'base64');
    const goodUpload = await uploadAndComplete(page, csrfToken, 'prototype.zip', goodZip, 'ZIP');
    const secondVersion = await apiPost<VersionResponse>(page, `/api/v1/prototypes/${prototypeId}/versions`, {
      uploadId: goodUpload.uploadId,
      sourceType: 'ZIP',
      changeLog: 'E2E ZIP 第二版',
    }, csrfToken);
    await waitForPublish(page, prototypeId, secondVersion.data.versionId, 'SUCCEEDED');

    const switched = await apiGet<VersionItem[]>(page, `/api/v1/prototypes/${prototypeId}/versions`);
    const secondVersionItem = switched.data.find((item) => item.versionId === secondVersion.data.versionId);
    expect(secondVersionItem?.isCurrent).toBe(true);
    await apiPost(page, `/api/v1/prototypes/${prototypeId}/versions/${firstVersion.data.versionId}/switch`, {
      expectedCurrentVersionNo: secondVersion.data.versionNo,
      reason: 'E2E 验证回滚到稳定 HTML 版本',
    }, csrfToken);

    const afterRollback = await apiGet<VersionItem[]>(page, `/api/v1/prototypes/${prototypeId}/versions`);
    expect(afterRollback.data.find((item) => item.versionId === firstVersion.data.versionId)?.isCurrent).toBe(true);

    // 6. 添加附件和一条已处理的成员评论。
    const attachmentBytes = Buffer.from('E2E attachment', 'utf8');
    const attachmentUpload = await uploadAndComplete(
      page,
      csrfToken,
      'acceptance.txt',
      attachmentBytes,
      'ATTACHMENT',
    );
    const attachment = await apiPost<{ publicId: string }>(page, `/api/v1/prototypes/${prototypeId}/attachments`, {
      uploadId: attachmentUpload.uploadId,
      name: 'E2E 验收附件',
      type: 'DOCUMENT',
      purpose: '验收证据',
      accessScope: 'PUBLIC',
      versionPublicId: firstVersion.data.versionId,
    }, csrfToken);
    expect(attachment.data.publicId).toBeTruthy();

    const comment = await apiPost<Comment>(page, `/api/v1/prototypes/${prototypeId}/comments`, {
      versionPublicId: firstVersion.data.versionId,
      content: 'E2E 成员评审意见',
    }, csrfToken, { 'Idempotency-Key': `e2e-comment-${Date.now()}` });
    const resolvedComment = await apiPost<Comment>(
      page,
      `/api/v1/prototypes/${prototypeId}/comments/${comment.data.publicId}/resolve`,
      { status: 'RESOLVED', resolveNote: 'E2E 已处理', rowVersion: comment.data.rowVersion },
      csrfToken,
    );
    expect(resolvedComment.data.status).toBe('RESOLVED');

    await page.goto('/attachments');
    await expect(page.getByText('E2E 验收附件', { exact: true }).first()).toBeVisible({ timeout: 15000 });

    // 7. 密码分享：UI 展示密码页，真实分享会话通过 API 完成验证，再由 UI 加载内容。
    const share = await apiPost<ShareResponse>(page, `/api/v1/prototypes/${prototypeId}/shares`, {
      name: 'E2E 密码分享',
      password: 'Share-pass-123',
      expiresAt: null,
      specScope: 'ALL',
      allowComment: true,
      allowPublicAttachment: true,
    }, csrfToken, { 'Idempotency-Key': `e2e-share-${Date.now()}` });
    expect(share.data.secretAvailable).toBe(true);
    const shareUrl = share.data.rawUrl;
    const shareToken = new URL(shareUrl).pathname.split('/').filter(Boolean).pop();
    expect(shareToken).toBeTruthy();
    const shareOrigin = new URL(shareUrl).origin;
    const publicRequest = requestOf(page);
    const shareApiUrl = (path: string) => localizeUrl(`${shareOrigin}${path}`);

    await page.goto(shareUrl);
    await expect(page.getByRole('heading', { name: '受密码保护的分享' })).toBeVisible({ timeout: 15000 });

    const verifyResponse = await publicRequest.post(
      shareApiUrl(`/share-api/v1/shares/${shareToken}/verify-password`),
      {
        headers: publicHeaders(),
        data: { password: 'Share-pass-123', guestName: 'E2E 访客' },
      },
    );
    const verify = await readJson<VerifyResponse>(verifyResponse, '分享密码验证');
    expect(verify.data.success).toBe(true);
    await copyLocalCookiesToPreview(page);

    const shareTicketResponse = await publicRequest.post(
      shareApiUrl(`/share-api/v1/shares/${shareToken}/content-ticket`),
      { headers: publicHeaders({ 'X-CSRF-TOKEN': verify.data.csrfToken }) },
    );
    const shareTicket = await readJson<ContentTicket>(shareTicketResponse, '分享内容票据');
    const shareContentResponse = await publicRequest.get(localizeUrl(shareTicket.data.contentUrl), {
      headers: publicHeaders(),
    });
    expect(shareContentResponse.status()).toBe(200);
    expect(shareContentResponse.headers()['cache-control'] || '').toContain('no-store');
    expect(shareContentResponse.headers()['content-security-policy'] || '').toContain('sandbox');
    expect(await shareContentResponse.text()).toContain('html e2e');

    await page.reload();
    await expect(page.getByText('对外分享预览', { exact: true })).toBeVisible({ timeout: 15000 });
    await expect(page.locator('iframe').first()).toHaveAttribute('src', /\/content\/c\//, { timeout: 15000 });

    const guestCommentResponse = await publicRequest.post(
      shareApiUrl(`/share-api/v1/shares/${shareToken}/comments`),
      {
        headers: publicHeaders({
          'X-CSRF-TOKEN': verify.data.csrfToken,
          'Idempotency-Key': `e2e-guest-comment-${Date.now()}`,
        }),
        data: { versionPublicId: firstVersion.data.versionId, content: 'E2E 访客评论' },
      },
    );
    const guestComment = await readJson<Comment>(guestCommentResponse, '访客评论');
    expect(guestComment.data.status).toBe('OPEN');

    await apiPost(
      page,
      `/api/v1/prototypes/${prototypeId}/shares/${share.data.shareId}/status`,
      { status: 'DISABLED' },
      csrfToken,
    );
    const revokedShareContent = await publicRequest.get(localizeUrl(shareTicket.data.contentUrl), {
      headers: publicHeaders(),
    });
    expect(revokedShareContent.status()).toBe(403);

    // 8. 删除后通过 UI 回收站确认，再恢复并确认原型重新出现在原型库。
    await apiDelete(page, `/api/v1/prototypes/${prototypeId}`, csrfToken);
    await page.goto('/recycle-bin');
    await expect(page.getByText('E2E 验收原型', { exact: true }).first()).toBeVisible({ timeout: 15000 });

    csrfToken = await getCsrfToken(page);
    await apiPost(page, `/api/v1/admin/prototypes/${prototypeId}/restore`, {}, csrfToken);
    await page.goto('/prototypes');
    await expect(page.getByText('E2E 验收原型', { exact: true }).first()).toBeVisible({ timeout: 15000 });
  });
});
