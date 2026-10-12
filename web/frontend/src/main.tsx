import '@fontsource-variable/figtree';
import '@mantine/core/styles.css';
import '@mantine/notifications/styles.css';
import '@mantine/dates/styles.css';
import './i18n';
import './styles.css';
import { localStorageColorSchemeManager, MantineProvider } from '@mantine/core';
import { DatesProvider } from '@mantine/dates';
import { Notifications } from '@mantine/notifications';
import 'dayjs/locale/sk';
import { useMediaQuery } from '@mantine/hooks';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import { theme } from './theme';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: true },
  },
});

/** Toasts: on a phone at the top (the bottom has the menu and the sheets), elsewhere at the bottom. */
function Toasts() {
  const mobile = useMediaQuery('(max-width: 48em)');
  return <Notifications position={mobile ? 'top-center' : 'bottom-center'} zIndex={1000} />;
}

/** Date pickers in the page's language, the week from Monday. */
function Dates({ children }: { children: ReactNode }) {
  const { i18n } = useTranslation();
  return <DatesProvider settings={{ locale: i18n.language, firstDayOfWeek: 1 }}>{children}</DatesProvider>;
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <MantineProvider theme={theme} defaultColorScheme="auto" colorSchemeManager={localStorageColorSchemeManager({ key: 'color-scheme' })}>
      <Toasts />
      <QueryClientProvider client={queryClient}>
        <Dates>
          <App />
        </Dates>
      </QueryClientProvider>
    </MantineProvider>
  </StrictMode>,
);
