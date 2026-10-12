import { AppShell, Avatar, Badge, Box, Group, NavLink, SegmentedControl, Stack, Text, UnstyledButton } from '@mantine/core';
import {
  IconActivity,
  IconAdjustmentsHorizontal,
  IconCalendarEvent,
  IconDots,
  IconDownload,
  IconInbox,
  IconListCheck,
  IconTicket,
} from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Outlet, useLocation, useNavigate } from 'react-router';
import { useMe } from '../api/hooks';
import { useLiveUpdates } from '../api/live';
import { useBand, useBandPath } from '../band/band';
import { BandSwitcher } from '../components/BandSwitcher';
import { LANGUAGES, setLanguage } from '../i18n';
import { useInbox } from '../lib/attention';
import { useIsMobile } from '../lib/useIsMobile';

interface Item {
  to: string;
  label: string;
  icon: ReactNode;
  badge?: number;
}

/** Which menu place an address within the band is ('/gig/x' → '/'). */
function section(within: string): string {
  if (within === '/' || within === '' || within.startsWith('/gig')) return '/';
  if (within.startsWith('/calendar')) return '/calendar';
  return '/' + within.split('/')[1];
}

/**
 * Around every band page. Desktop: the band (and the way to another) and the menu on the left.
 * Phone: a bottom bar with the four places used day to day (Gigs, Inbox, Calendar, More), the rest
 * — the band switcher too — under More.
 */
export function AppLayout() {
  const { t, i18n } = useTranslation();
  const mobile = useIsMobile();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const me = useMe();
  const band = useBand();
  const path = useBandPath();
  useLiveUpdates(band.id);
  const inbox = useInbox();
  const count = inbox.items.length;
  const current = section(pathname.substring(path('/').length));

  const main: Item[] = [
    { to: '/', label: t('nav.gigs'), icon: <IconTicket size={22} stroke={1.8} /> },
    { to: '/inbox', label: t('nav.inbox'), icon: <IconInbox size={22} stroke={1.8} />, badge: count },
    { to: '/calendar', label: t('nav.calendar'), icon: <IconCalendarEvent size={22} stroke={1.8} /> },
  ];
  const more: Item[] = [
    { to: '/activity', label: t('nav.activity'), icon: <IconActivity size={22} stroke={1.8} /> },
    { to: '/import', label: t('nav.import'), icon: <IconDownload size={22} stroke={1.8} /> },
    { to: '/check', label: t('nav.check'), icon: <IconListCheck size={22} stroke={1.8} /> },
    { to: '/settings', label: t('nav.settings'), icon: <IconAdjustmentsHorizontal size={22} stroke={1.8} /> },
  ];
  const inMore = ['/more', ...more.map((m) => m.to)].includes(current);

  if (mobile) {
    const tabs: Item[] = [...main, { to: '/more', label: t('nav.more'), icon: <IconDots size={22} stroke={1.8} /> }];
    return (
      <AppShell footer={{ height: 'calc(68px + env(safe-area-inset-bottom))' }} padding="md">
        <AppShell.Main pb="calc(84px + env(safe-area-inset-bottom))">
          <Outlet />
        </AppShell.Main>
        <AppShell.Footer component="nav" aria-label={t('nav.label')} pb="env(safe-area-inset-bottom)">
          <Group h={68} gap={0} grow px={4}>
            {tabs.map((tab) => {
              const active = tab.to === '/more' ? inMore : current === tab.to;
              return (
                <UnstyledButton
                  key={tab.to}
                  onClick={() => navigate(path(tab.to))}
                  aria-current={active ? 'page' : undefined}
                  h={64}
                  c={active ? 'brand.8' : 'dimmed'}
                  style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 3 }}
                >
                  <Box
                    pos="relative"
                    px={18}
                    py={4}
                    style={{ borderRadius: 999, background: active ? 'var(--mantine-primary-color-light)' : 'transparent' }}
                  >
                    {tab.icon}
                    {!!tab.badge && (
                      <Badge size="xs" circle color="red" pos="absolute" top={-2} right={8} style={{ border: '2px solid var(--mantine-color-body)' }}>
                        {tab.badge}
                      </Badge>
                    )}
                  </Box>
                  <Text size="xs" fw={700}>
                    {tab.label}
                  </Text>
                </UnstyledButton>
              );
            })}
          </Group>
        </AppShell.Footer>
      </AppShell>
    );
  }

  const link = (item: Item) => (
    <NavLink
      key={item.to}
      component={Link}
      to={path(item.to)}
      label={item.label}
      leftSection={item.icon}
      active={current === item.to}
      rightSection={item.badge ? <Badge color="red" size="sm" circle>{item.badge}</Badge> : undefined}
      styles={{ root: { borderRadius: 10, minHeight: 44 }, label: { fontWeight: 600, fontSize: 15 } }}
      aria-current={current === item.to ? 'page' : undefined}
    />
  );

  return (
    <AppShell navbar={{ width: 264, breakpoint: 'sm' }} padding="xl">
      <AppShell.Navbar p="md" component="nav" aria-label={t('nav.label')}>
        <Box pb="md">
          <BandSwitcher />
        </Box>
        <Stack gap={2}>{main.map(link)}</Stack>
        <Box my="sm" style={{ borderTop: '1px solid var(--mantine-color-default-border)' }} />
        <Stack gap={2}>{more.map(link)}</Stack>
        <Box style={{ flex: 1 }} />
        <Stack gap="xs" pt="md" style={{ borderTop: '1px solid var(--mantine-color-default-border)' }}>
          <SegmentedControl
            fullWidth
            value={i18n.language}
            onChange={setLanguage}
            data={LANGUAGES.map((l) => ({ value: l.code, label: l.name }))}
            aria-label={t('settings.language')}
          />
          <Group gap="sm" px={4}>
            <Avatar color="brand" radius="xl" size={32}>
              {(me.data?.name ?? '?').substring(0, 2).toUpperCase()}
            </Avatar>
            <Text size="sm" fw={600} truncate style={{ flex: 1, minWidth: 0 }}>
              {me.data?.name}
            </Text>
          </Group>
        </Stack>
      </AppShell.Navbar>
      <AppShell.Main>
        <Box maw={1200}>
          <Outlet />
        </Box>
      </AppShell.Main>
    </AppShell>
  );
}
