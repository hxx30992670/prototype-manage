import { describe, it, expect } from 'vitest';
import { render } from '@testing-library/react';
import { SafeMarkdown } from './markdown';

describe('SafeMarkdown', () => {
  it.each([
    '<img src=x onerror=alert(1)>',
    '[x](javascript:alert(1))',
    '<iframe src="http://evil.test"></iframe>',
    '<script>alert(1)</script>',
    '<object data="http://evil.test"></object>',
    '<embed src="http://evil.test">',
    '[danger](vbscript:msgbox(1))',
  ])('does not render active content from %s', (payload) => {
    const { container } = render(<SafeMarkdown source={payload} />);
    expect(container.querySelector('script,iframe,object,embed')).toBeNull();
    expect(container.innerHTML).not.toMatch(/onerror|javascript:|vbscript:/i);
  });

  it('renders standard markdown elements safely', () => {
    const markdown = '# 标题\n\n这是**加粗**文本，包含[有效链接](https://example.com)。';
    const { container } = render(<SafeMarkdown source={markdown} />);
    const link = container.querySelector('a');
    expect(link).not.toBeNull();
    expect(link?.getAttribute('href')).toBe('https://example.com');
    expect(link?.getAttribute('target')).toBe('_blank');
    expect(link?.getAttribute('rel')).toBe('noopener noreferrer');
    expect(container.querySelector('h1')?.textContent).toBe('标题');
  });
});
