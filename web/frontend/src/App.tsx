import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { BrowserRouter, Navigate, Route, Routes } from 'react-router';
import { whenUnauthorized } from './api/client';
import { useMe } from './api/hooks';
import { BandPages } from './band/BandPages';
import { Loading } from './components/Loading';
import { StartPage } from './band/StartPage';
import { ActivityPage } from './pages/ActivityPage';
import { CalendarPage } from './pages/CalendarPage';
import { CheckPage } from './pages/CheckPage';
import { CreateBandPage } from './pages/CreateBandPage';
import { GigFormPage } from './pages/GigFormPage';
import { GigPage } from './pages/GigPage';
import { GigsPage } from './pages/GigsPage';
import { ImportPage } from './pages/ImportPage';
import { InboxPage } from './pages/InboxPage';
import { LoginPage } from './pages/LoginPage';
import { takeReturnPath } from './lib/signIn';
import { MorePage } from './pages/MorePage';
import { SettingsPage } from './pages/SettingsPage';

/**
 * Signed in → the app (back where the user was before signing in); otherwise the sign-in page (the
 * API answers 401 when the session ends). Each band's pages live under its address, /b/<slug>/…;
 * the start goes to the band used last, or to making one.
 */
export function App() {
  const me = useMe();
  const client = useQueryClient();
  const returned = useRef(false);
  useEffect(() => whenUnauthorized(() => client.setQueryData(['me'], null)), [client]);
  if (me.isLoading) return <Loading />;
  if (!me.data) return <LoginPage />;
  if (!returned.current) {
    returned.current = true;
    const back = takeReturnPath();
    if (back) window.history.replaceState(null, '', back);   // before the router reads the address
  }
  return (
    <BrowserRouter>
      <Routes>
        <Route index element={<StartPage />} />
        <Route path="new-band" element={<CreateBandPage />} />
        <Route path="b/:slug" element={<BandPages />}>
          <Route index element={<GigsPage />} />
          <Route path="gig/new" element={<GigFormPage mode="new" />} />
          <Route path="gig/:id" element={<GigPage />} />
          <Route path="gig/:id/edit" element={<GigFormPage mode="edit" />} />
          <Route path="inbox" element={<InboxPage />} />
          <Route path="calendar" element={<CalendarPage />} />
          <Route path="calendar/:eventId/add" element={<GigFormPage mode="calendar" />} />
          <Route path="more" element={<MorePage />} />
          <Route path="activity" element={<ActivityPage />} />
          <Route path="import" element={<ImportPage />} />
          <Route path="check" element={<CheckPage />} />
          <Route path="settings" element={<SettingsPage />} />
          <Route path="*" element={<Navigate to="." replace />} />
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}
