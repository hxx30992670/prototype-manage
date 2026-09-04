import React from 'react';
import ReactMarkdown from 'react-markdown';
import rehypeSanitize, { defaultSchema } from 'rehype-sanitize';

export const markdownSchema = {
  ...defaultSchema,
  tagNames: [
    ...(defaultSchema.tagNames || []),
    'table',
    'thead',
    'tbody',
    'tr',
    'th',
    'td',
    'del',
    'hr',
  ],
  attributes: {
    ...defaultSchema.attributes,
    a: ['href', 'title', 'target', 'rel'],
    img: ['src', 'alt', 'title', 'width', 'height'],
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
    <div className={`prose max-w-none text-gray-800 ${className || ''}`}>
      <ReactMarkdown
        skipHtml
        rehypePlugins={[[rehypeSanitize, markdownSchema]]}
        components={{
          a: ({ node: _node, ...props }) => (
            <a {...props} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline" />
          ),
        }}
      >
        {source}
      </ReactMarkdown>
    </div>
  );
};
