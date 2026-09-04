import React, { useMemo, useState } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider, App, Empty } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import { getAntdTheme } from '@/theme/antdTheme';
import { ThemeProvider } from '@/theme/ThemeProvider';
import { useTheme } from '@/theme/themeContext';

dayjs.locale('zh-cn');

interface AppProvidersProps {
  children: React.ReactNode;
}

const ThemedApp: React.FC<AppProvidersProps> = ({ children }) => {
  const { mode } = useTheme();
  const antdTheme = useMemo(() => getAntdTheme(mode), [mode]);
  const [queryClient] = useState(
    () =>
      new QueryClient({
        defaultOptions: {
          queries: {
            refetchOnWindowFocus: false,
            retry: 1,
          },
        },
      })
  );

  return (
    <ConfigProvider
      locale={zhCN}
      theme={antdTheme}
      renderEmpty={() => <Empty description="暂无数据" image={Empty.PRESENTED_IMAGE_SIMPLE} />}
    >
      <App>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </App>
    </ConfigProvider>
  );
};

export const AppProviders: React.FC<AppProvidersProps> = ({ children }) => (
  <ThemeProvider>
    <ThemedApp>{children}</ThemedApp>
  </ThemeProvider>
);
