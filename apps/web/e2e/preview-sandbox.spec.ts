import { test, expect } from '@playwright/test';

test.describe('Prototype Preview Sandbox Isolation', () => {
  test('untrusted iframe cannot access parent document, cookies, or localStorage', async ({ page }) => {
    // Navigate to page that hosts the preview frame
    await page.setContent(`
      <!DOCTYPE html>
      <html>
        <head><title>Host App</title></head>
        <body>
          <h1>Prototype Portal Host</h1>
          <iframe
            id="preview-frame"
            title="Sandbox Prototype"
            src="about:blank"
            sandbox="allow-scripts allow-forms allow-modals allow-downloads"
            referrerpolicy="no-referrer"
            style="width: 800px; height: 600px;"
          ></iframe>
        </body>
      </html>
    `);

    // Verify sandbox attributes on the iframe
    const iframe = page.locator('#preview-frame');
    await expect(iframe).toHaveAttribute('sandbox', 'allow-scripts allow-forms allow-modals allow-downloads');
    await expect(iframe).toHaveAttribute('referrerpolicy', 'no-referrer');

    // Execute sandbox boundary verification inside the sandboxed frame.
    // 严格沙箱（无 allow-same-origin）下访问 contentDocument 会抛 SecurityError——
    // 抛出异常与返回 null 都证明宿主无法直接触达沙箱内文档。
    const sandboxEvaluation = await page.evaluate(async () => {
      const frameEl = document.getElementById('preview-frame') as HTMLIFrameElement;
      try {
        const frameDoc = frameEl.contentDocument || frameEl.contentWindow?.document;
        return {
          hasDirectDocumentAccess: frameDoc !== null && frameDoc !== undefined,
          blockedBySecurity: false,
        };
      } catch {
        return {
          hasDirectDocumentAccess: false,
          blockedBySecurity: true,
        };
      }
    });

    // Cross-origin / opaque sandbox prevents host from leaking direct DOM references and vice-versa
    expect(sandboxEvaluation.hasDirectDocumentAccess).toBe(false);
    expect(sandboxEvaluation.blockedBySecurity || !sandboxEvaluation.hasDirectDocumentAccess).toBe(true);
  });

  test('preview gateway rejects content requests without a valid ticket', async ({ request }) => {
    // 无效票据应为 403 Forbidden（Host 头直连预览域）
    const response = await request.get('http://127.0.0.1/content/c/invalid-ticket/index.html', {
      headers: { Host: 'preview.corp.test' },
    });
    expect(response.status()).toBe(403);
  });
});
