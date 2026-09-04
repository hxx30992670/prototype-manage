import '@testing-library/jest-dom';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';

dayjs.locale('zh-cn');

// Fix Node 24 + JSDOM AbortSignal mismatch in React Router 7
const OriginalRequest = globalThis.Request;
globalThis.Request = class extends OriginalRequest {
  constructor(input: RequestInfo | URL, init?: RequestInit) {
    if (init && init.signal) {
      // In JSDOM, signal may fail undici instanceof check
      const { signal: _signal, ...rest } = init;
      void _signal;
      super(input, rest);
    } else {
      super(input, init);
    }
  }
} as typeof Request;

HTMLCanvasElement.prototype.getContext = (() => null) as typeof HTMLCanvasElement.prototype.getContext;

Object.defineProperty(window, 'matchMedia', {
  writable: true,
  value: (query: string) => ({
    matches: false,
    media: query,
    onchange: null,
    addListener: () => {},
    removeListener: () => {},
    addEventListener: () => {},
    removeEventListener: () => {},
    dispatchEvent: () => false,
  }),
});
