import { Group, Paper, Skeleton, Stack, Text, ThemeIcon, Title, Anchor } from '@mantine/core';
import { IconAlertTriangle, IconCalendarEvent, IconCheck, IconListCheck, IconPlayerPause } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { ActionButtons } from '../components/ActionButtons';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { type InboxItem, useInbox } from '../lib/attention';
import { relative } from '../lib/format';
import { useBandPath } from '../band/band';

const LOOK: Record<InboxItem['kind'], { color: string; icon: React.ReactNode }> = {
  failed: { color: 'red', icon: <IconAlertTriangle size={18} /> },
  held: { color: 'orange', icon: <IconPlayerPause size={18} /> },
  calendar: { color: 'brand', icon: <IconCalendarEvent size={18} /> },
  drift: { color: 'orange', icon: <IconListCheck size={18} /> },
};

/** Everything that needs the user, each with its fix: one place instead of four pages. */
export function InboxPage() {
  const path = useBandPath();
  const { t } = useTranslation();
  const inbox = useInbox();
  return (
    <Stack gap="md" maw={760}>
      <PageHeader title={t('inbox.title')} />
      {inbox.loading && <Skeleton h={140} radius="lg" />}
      {!inbox.loading && inbox.items.length > 0 && (
        <Text c="dimmed" fw={600} size="sm">
          {t('inbox.count', { count: inbox.items.length })}
        </Text>
      )}
      {inbox.items.map((item) => {
        const look = LOOK[item.kind];
        return (
          <Paper key={item.key} withBorder p="md">
            <Stack gap="xs">
              <Group gap="sm" wrap="nowrap">
                <ThemeIcon variant="light" color={look.color} radius="md" size={32}>
                  {look.icon}
                </ThemeIcon>
                <Text size="xs" fw={700} tt="uppercase" c={`${look.color}.8`} style={{ letterSpacing: '0.06em', flex: 1 }}>
                  {item.kicker}
                </Text>
                {item.when && (
                  <Text size="xs" c="dimmed">
                    {relative(item.when)}
                  </Text>
                )}
              </Group>
              <Title order={3} fz={17}>
                {item.gigId ? (
                  <Anchor component={Link} to={path(`/gig/${item.gigId}`)} c="var(--mantine-color-text)" inherit>
                    {item.title}
                  </Anchor>
                ) : item.title}
              </Title>
              {item.body && <Text c="dimmed">{item.body}</Text>}
              <ActionButtons actions={item.actions} />
            </Stack>
          </Paper>
        );
      })}
      {!inbox.loading && inbox.items.length === 0 && (
        <EmptyState icon={<IconCheck size={28} stroke={2.6} />} title={t('inbox.empty')} text={t('inbox.emptyText')} />
      )}
    </Stack>
  );
}
