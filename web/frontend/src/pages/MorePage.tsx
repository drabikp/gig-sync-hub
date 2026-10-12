import { Button, Group, NavLink, Paper, SegmentedControl, Stack, Text, ThemeIcon } from '@mantine/core';
import { IconActivity, IconAdjustmentsHorizontal, IconChevronRight, IconDeviceMobile, IconDownload, IconListCheck } from '@tabler/icons-react';
import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { BandSwitcher } from '../components/BandSwitcher';
import { PageHeader } from '../components/PageHeader';
import { LANGUAGES, setLanguage } from '../i18n';
import { done } from '../lib/notify';
import { useBandPath } from '../band/band';

interface InstallPrompt extends Event {
  prompt: () => Promise<void>;
}

/** The phone's "More": the band (and the way to another), the places used now and then, installing the app, the language. */
export function MorePage() {
  const path = useBandPath();
  const { t, i18n } = useTranslation();
  const [install, setInstall] = useState<InstallPrompt | null>(null);
  const standalone = typeof window !== 'undefined' && window.matchMedia?.('(display-mode: standalone)').matches;
  const [hidden, setHidden] = useState(() => {
    try {
      return localStorage.getItem('install-hidden') === '1';
    } catch {
      return false;
    }
  });
  useEffect(() => {
    const listen = (e: Event) => {
      e.preventDefault();
      setInstall(e as InstallPrompt);
    };
    window.addEventListener('beforeinstallprompt', listen);
    return () => window.removeEventListener('beforeinstallprompt', listen);
  }, []);
  const rows = [
    { to: '/activity', label: t('nav.activity'), icon: <IconActivity size={20} /> },
    { to: '/import', label: t('nav.import'), icon: <IconDownload size={20} /> },
    { to: '/check', label: t('nav.check'), icon: <IconListCheck size={20} /> },
    { to: '/settings', label: t('nav.settings'), icon: <IconAdjustmentsHorizontal size={20} /> },
  ];
  return (
    <Stack gap="md">
      <PageHeader title={t('nav.more')} />
      <Paper withBorder p={6}>
        <BandSwitcher />
      </Paper>
      {!standalone && !hidden && (
        <Paper p="md" radius="lg" bg="#1F2A44" c="white">
          <Group wrap="nowrap" align="flex-start" gap="md">
            <ThemeIcon size={44} radius="md">
              <IconDeviceMobile size={24} />
            </ThemeIcon>
            <Stack gap={6} style={{ flex: 1 }}>
              <Text fw={800}>{t('more.installTitle')}</Text>
              <Text size="sm" c="#D7DCEA">{t('more.installText')}</Text>
              <Group gap="xs" mt={4}>
                <Button size="xs" color="white" c="#1F2A44" onClick={() => (install ? install.prompt() : done(t('more.installHow')))}>
                  {install ? t('more.install') : t('more.how')}
                </Button>
                <Button size="xs" variant="subtle" color="gray.3" onClick={() => {
                  setHidden(true);
                  try {
                    localStorage.setItem('install-hidden', '1');
                  } catch {
                    // shown again next time; fine
                  }
                }}>
                  {t('more.notNow')}
                </Button>
              </Group>
            </Stack>
          </Group>
        </Paper>
      )}
      <Paper withBorder p={4}>
        {rows.map((row) => (
          <NavLink key={row.to} component={Link} to={path(row.to)} label={row.label} leftSection={<ThemeIcon variant="light" radius="md">{row.icon}</ThemeIcon>}
            rightSection={<IconChevronRight size={18} />}
            styles={{ root: { minHeight: 56, borderRadius: 12 }, label: { fontSize: 16, fontWeight: 600 } }} />
        ))}
      </Paper>
      <Paper withBorder p="md">
        <Text fw={700} mb="xs">{t('settings.language')}</Text>
        <SegmentedControl fullWidth value={i18n.language} onChange={setLanguage} data={LANGUAGES.map((l) => ({ value: l.code, label: l.name }))} />
      </Paper>
    </Stack>
  );
}
