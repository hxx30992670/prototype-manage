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

  it('renders safe font colors from span and font tags', () => {
    const markdown = '<span style="color:red">测试</span> <font color="#00aa00">绿色</font>';
    const { container } = render(<SafeMarkdown source={markdown} />);
    const spans = container.querySelectorAll('span');
    const colors = [...spans].map((el) => (el as HTMLElement).style.color);
    expect(colors).toContain('red');
    expect(colors.some((color) => color === 'rgb(0, 170, 0)' || color === '#00aa00')).toBe(true);
    expect(container.textContent).toContain('测试');
    expect(container.textContent).toContain('绿色');
    expect(container.querySelector('font')).toBeNull();
  });

  it('strips unsafe CSS from color tags', () => {
    const markdown =
      '<span style="color:red; background:url(javascript:alert(1)); position:fixed">x</span>';
    const { container } = render(<SafeMarkdown source={markdown} />);
    const span = container.querySelector('span') as HTMLElement | null;
    expect(span).not.toBeNull();
    expect(span?.style.color).toBe('red');
    expect(span?.getAttribute('style')).not.toMatch(/background|position|javascript/i);
  });
});
