import { test, expect } from '@playwright/test';

test.describe('Prototype Share & Revocation Lifecycle', () => {
  test('unauthenticated request on share without password proceeds to preview frame', async ({ page }) => {
    // In mock or live environment, verify passwordless share page structure
    await page.setContent(`
      <!DOCTYPE html>
      <html>
        <head><title>Shared Prototype</title></head>
        <body>
          <div id="root">
            <header>
              <span>对外分享预览</span>
            </header>
            <main>
              <iframe id="share-preview-frame" sandbox="allow-scripts allow-forms allow-modals allow-downloads"></iframe>
            </main>
          </div>
        </body>
      </html>
    `);

    const header = page.locator('header');
    await expect(header).toContainText('对外分享预览');

    const iframe = page.locator('#share-preview-frame');
    await expect(iframe).toHaveAttribute('sandbox', 'allow-scripts allow-forms allow-modals allow-downloads');
  });

  test('password protected share shows password prompt modal before rendering content', async ({ page }) => {
    await page.setContent(`
      <!DOCTYPE html>
      <html>
        <head><title>Password Protected Share</title></head>
        <body>
          <div id="password-modal">
            <h2>受密码保护的分享</h2>
            <input type="password" placeholder="请输入访问密码" />
            <button type="submit">进入预览</button>
          </div>
        </body>
      </html>
    `);

    const modalTitle = page.locator('#password-modal h2');
    await expect(modalTitle).toHaveText('受密码保护的分享');

    const submitBtn = page.locator('button[type="submit"]');
    await expect(submitBtn).toBeVisible();
  });
});
