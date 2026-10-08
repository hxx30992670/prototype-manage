import React from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import remarkBreaks from 'remark-breaks';
import rehypeRaw from 'rehype-raw';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';

const SAFE_NAMED_COLORS = new Set([
  'black',
  'silver',
  'gray',
  'grey',
  'white',
  'maroon',
  'red',
  'purple',
  'fuchsia',
  'magenta',
  'green',
  'lime',
  'olive',
  'yellow',
  'navy',
  'blue',
  'teal',
  'aqua',
  'cyan',
  'orange',
  'pink',
  'brown',
  'gold',
  'coral',
  'tomato',
  'orangered',
  'darkred',
  'darkorange',
  'darkgreen',
  'darkblue',
  'darkgray',
  'darkgrey',
  'lightgray',
  'lightgrey',
  'lightblue',
  'lightgreen',
  'lightpink',
  'crimson',
  'indigo',
  'violet',
  'salmon',
  'khaki',
  'chocolate',
  'tan',
  'snow',
  'ivory',
  'azure',
  'beige',
  'wheat',
  'transparent',
]);

const HEX_COLOR = /^#(?:[0-9a-f]{3,4}|[0-9a-f]{6}|[0-9a-f]{8})$/i;
const RGB_COLOR = /^rgb\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*\)$/i;
const RGBA_COLOR = /^rgba\(\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*\d{1,3}\s*,\s*(?:0|1|0?\.\d+)\s*\)$/i;
const HSL_COLOR = /^hsl\(\s*\d{1,3}\s*,\s*\d{1,3}%\s*,\s*\d{1,3}%\s*\)$/i;
const HSLA_COLOR = /^hsla\(\s*\d{1,3}\s*,\s*\d{1,3}%\s*,\s*\d{1,3}%\s*,\s*(?:0|1|0?\.\d+)\s*\)$/i;

function isSafeCssColor(value: string): boolean {
  const color = value.trim();
  if (!color || color.length > 64) {
    return false;
  }
  if (HEX_COLOR.test(color) || RGB_COLOR.test(color) || RGBA_COLOR.test(color) || HSL_COLOR.test(color) || HSLA_COLOR.test(color)) {
    return true;
  }
  return SAFE_NAMED_COLORS.has(color.toLowerCase());
}

function extractColorFromStyle(style: unknown): string | undefined {
  if (typeof style === 'string') {
    const match = style.match(/(?:^|;)\s*color\s*:\s*([^;]+)/i);
    return match?.[1]?.trim();
  }
  if (style && typeof style === 'object' && 'color' in style) {
    const color = (style as { color?: unknown }).color;
    return typeof color === 'string' ? color.trim() : undefined;
  }
  return undefined;
}

interface HastNode {
  type?: string;
  tagName?: string;
  properties?: Record<string, unknown>;
  children?: HastNode[];
}

function restrictInlineColor(node: HastNode): void {
  if (node.type === 'element') {
    const props = node.properties ?? {};
    const candidates = [extractColorFromStyle(props.style), typeof props.color === 'string' ? props.color : undefined];
    const color = candidates.find((value): value is string => Boolean(value && isSafeCssColor(value)));
    delete props.style;
    delete props.color;
    if (color) {
      props.style = `color: ${color}`;
    }
    if (node.tagName === 'font') {
      node.tagName = 'span';
    }
    node.properties = props;
  }
  node.children?.forEach(restrictInlineColor);
}

function rehypeRestrictInlineColor() {
  return (tree: HastNode) => {
    restrictInlineColor(tree);
  };
}

export const markdownSchema = {
  ...defaultSchema,
  tagNames: [...(defaultSchema.tagNames || []), 'table', 'thead', 'tbody', 'tr', 'th', 'td', 'del', 'hr', 'input', 'section', 'span'],
  attributes: {
    ...defaultSchema.attributes,
    a: ['href', 'title', 'target', 'rel'],
    img: ['src', 'alt', 'title', 'width', 'height'],
    input: ['type', 'disabled', 'checked'],
    span: [...(defaultSchema.attributes?.span || []), 'style'],
  },
  protocols: {
    ...defaultSchema.protocols,
    href: ['http', 'https', 'mailto'],
    src: ['http', 'https'],
  },
};

export interface SafeMarkdownProps {
  source: string;
  className?: string;
}

export const SafeMarkdown: React.FC<SafeMarkdownProps> = ({ source, className }) => {
  return (
    <div className={`prose max-w-none ${className || 'text-gray-800'}`}>
      <ReactMarkdown
        remarkPlugins={[remarkGfm, remarkBreaks]}
        rehypePlugins={[rehypeRaw, rehypeRestrictInlineColor, [rehypeSanitize, markdownSchema]]}
        components={{
          a: ({ node: _node, ...props }) => (
            <a {...props} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline" />
          ),
          span: ({ node: _node, style, children, ...props }) => {
            void _node;
            const color = extractColorFromStyle(style);
            return (
              <span {...props} style={color && isSafeCssColor(color) ? { color } : undefined}>
                {children}
              </span>
            );
          },
        }}
      >
        {source}
      </ReactMarkdown>
    </div>
  );
};
