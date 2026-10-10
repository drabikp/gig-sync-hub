import { Alert, Button, Group, Paper, Skeleton, Stack, Text, Title } from '@mantine/core';
import { IconListCheck } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useCheck, useCheckAction } from '../api/hooks';
import { useBandPath } from '../band/band';
import { ActionButtons } from '../components/ActionButtons';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { driftText, useInbox, usePlatformName } from '../lib/attention';
import { relative, when } from '../lib/format';
import { failed } from '../lib/notify';

/** Reads what the platforms really show and lists where they differ from the catalog. */
export function CheckPage() {
  const path = useBandPath();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const check = useCheck();
  const action = useCheckAction();
  const name = usePlatformName();
  const inbox = useInbox();
  const last = check.data?.last;
  const running = check.data?.running ?? false;
  const drifts = inbox.items.filter((i) => i.kind === 'drift');
  return (
    <Stack gap="md" maw={820}>
      <PageHeader title={t('check.title')} phoneBack="/more" />
      <Text c="dimmed">{t('check.intro')}</Text>
      <Paper withBorder p="md">
        <Group justify="space-between" wrap="wrap" gap="sm">
          <div>
            <Text fw={700}>
              {running ? t('check.running') : !last ? t('check.never')
                : last.drifts.length ? t('check.differences', { count: last.drifts.length }) : t('check.allMatch')}
            </Text>
            {last && <Text size="sm" c="dimmed">{t('check.lastRun', { when: when(last.checkedAt), ago: relative(last.checkedAt) })}</Text>}
          </div>
          <Button loading={running || action.isPending} onClick={() => action.mutate({ kind: 'run' }, { onError: failed })}>
            {t('check.now')}
          </Button>
        </Group>
      </Paper>
      {check.isLoading && <Skeleton h={120} radius="lg" />}
      {last && Object.entries(last.unreadable).map(([p, why]) => (
        <Alert key={p} color="orange" variant="light">{t('check.unreadable', { platform: name(p), why })}</Alert>
      ))}
      {drifts.map((item) => {
        const drift = last?.drifts.find((d) => `drift-${d.platform}-${d.gigId}` === item.key);
        return (
          <Paper key={item.key} withBorder p="md" style={{ borderColor: 'var(--mantine-color-orange-3)' }}>
            <Stack gap="xs">
              <Title order={3} fz={17}>{item.title}</Title>
              {drift && drift.kind === 'DIFFERENT' ? (
                <Stack gap={2}>
                  {drift.differences.map((d, i) => <Text key={i} c="dimmed">{driftText(t, d, name(drift.platform))}</Text>)}
                </Stack>
              ) : <Text c="dimmed">{item.body}</Text>}
              <ActionButtons actions={item.actions} />
            </Stack>
          </Paper>
        );
      })}
      {last && !running && last.drifts.length === 0 && (
        <EmptyState icon={<IconListCheck size={28} />} title={t('check.allMatch')} />
      )}
      {last && Object.entries(last.unlinked).filter(([, n]) => n > 0).map(([p, n]) => (
        <Paper key={p} withBorder p="md">
          <Group justify="space-between" wrap="wrap">
            <Text>{t('check.unlinked', { count: n, platform: name(p) })}</Text>
            <Button variant="light" onClick={() => navigate(path('/import'))}>{t('check.toImport')}</Button>
          </Group>
        </Paper>
      ))}
    </Stack>
  );
}
