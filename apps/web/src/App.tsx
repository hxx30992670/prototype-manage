import { RouterProvider } from 'react-router-dom';
import type { createBrowserRouter } from 'react-router-dom';
import { AppProviders } from './app/AppProviders';
import { router } from './routes/router';

interface AppProps {
  router?: ReturnType<typeof createBrowserRouter>;
}

export function App({ router: customRouter }: AppProps) {
  return (
    <AppProviders>
      <RouterProvider router={customRouter || router} />
    </AppProviders>
  );
}

export default App;
