import { Alert, Anchor, Badge, Button, Collapse, Group, Paper, SegmentedControl, Skeleton, Stack, Switch, Text, ThemeIcon, UnstyledButton } from '@mantine/core';
import { IconAlertTriangle, IconCheck, IconClockHour4, IconMinus, IconRefresh } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router';
import { useSyncAction, useSyncStatus, useSyncTasks, useTaskLog } from '../api/hooks';
import type { Task } from '../api/types';
import { useBandPath } from '../band/band';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { usePlatformName } from '../lib/attention';
import { clock, dayOf, longDate, when } from '../lib/format';
import { done, failed } from '../lib/notify';

function look(task: Task): { color: string; icon: React.ReactNode } {
  switch (task.status) {
    case 'DONE':
      return { color: 'teal', icon: <IconCheck size={16} stroke={3} /> };
    case 'FAILED':
      return { color: 'red', icon: <IconAlertTriangle size={16} /> };
    case 'DISCARDED':
      return { color: 'gray', icon: <IconMinus size={16} /> };
    case 'RUNNING':
      return { color: 'orange', icon: <IconRefresh size={16} /> };
    default:
      return { color: 'orange', icon: <IconClockHour4 size={16} /> };
  }
}

function TaskRow({ task }: { task: Task }) {
  const path = useBandPath();
  const { t } = useTranslation();
  const name = usePlatformName();
  const sync = useSyncAction();
  const [open, setOpen] = useState(false);
  const log = useTaskLog(open ? task.id : null);
  const l = look(task);
  const state = task.status === 'PENDING' && task.retrying ? t('activity.retryAt', { time: clock(task.nextAttemptAt) })
    : t(`taskStatus.${task.status}`);
  return (
    <Paper withBorder p="sm" radius="md">
      <UnstyledButton w="100%" onClick={() => setOpen(!open)} aria-expanded={open}>
        <Group wrap="nowrap" gap="sm" align="flex-start">
          <ThemeIcon variant="light" color={l.color} radius="xl" size={30}>{l.icon}</ThemeIcon>
          <Stack gap={0} style={{ flex: 1, minWidth: 0 }}>
            <Text fw={600}>
              {t(`history.${task.status === 'DONE' ? 'DONE' : task.status === 'FAILED' ? 'FAILED' : 'PENDING'}`, { action: t(`action.${task.action}`), platform: name(task.platform) })}
            </Text>
            <Text size="sm" c="dimmed" truncate>{task.gigLabel}</Text>
            {task.message && <Text size="sm" c={task.status === 'FAILED' ? 'red.8' : 'dimmed'} lineClamp={open ? undefined : 1}>{task.message}</Text>}
          </Stack>
          <Stack gap={2} align="flex-end">
            <Text size="xs" c="dimmed">{clock(task.updatedAt)}</Text>
            <Badge size="sm" variant="light" color={l.color} styles={{ label: { textTransform: 'none' } }}>{state}</Badge>
          </Stack>
        </Group>
      </UnstyledButton>
      <Collapse in={open}>
        <Stack gap={4} mt="sm" pl={42}>
          {(log.data ?? []).map((e, i) => (
            <Text key={i} size="sm"><Text span c="dimmed">{when(e.at)}</Text> {e.message}</Text>
          ))}
          <Group gap="xs" mt="xs">
            <Anchor component={Link} to={path(`/gig/${task.gigId}`)} size="sm">{t('activity.openGig')}</Anchor>
            {(task.status === 'FAILED' || task.status === 'DISCARDED') && (
              <Button size="xs" onClick={() => sync.mutate({ kind: 'retry', task: task.id }, { onSuccess: () => done(t('toast.retrying', { platform: name(task.platform) })), onError: failed })}>
                {t('activity.retry')}
              </Button>
            )}
            {(task.status === 'FAILED' || task.status === 'PENDING') && (
              <Button size="xs" variant="default" onClick={() => sync.mutate({ kind: 'discard', task: task.id }, { onSuccess: () => done(t('toast.done')), onError: failed })}>
                {t('activity.discard')}
              </Button>
            )}
          </Group>
        </Stack>
      </Collapse>
    </Paper>
  );
}

/** Every platform task, newest first, by day; failed ones wait for Retry or Discard. Pause / Resume. */
export function ActivityPage() {
  const { t } = useTranslation();
  const [show, setShow] = useState<'all' | 'open' | 'failed'>('all');
  const tasks = useSyncTasks(show);
  const status = useSyncStatus();
  const sync = useSyncAction();
  const name = usePlatformName();
  const days = useMemo(() => {
    const byDay = new Map<string, Task[]>();
    for (const task of tasks.data ?? []) {
      const day = dayOf(task.updatedAt);
      byDay.set(day, [...(byDay.get(day) ?? []), task]);
    }
    return [...byDay.entries()];
  }, [tasks.data]);
  const paused = status.data?.paused ?? false;
  return (
    <Stack gap="md" maw={820}>
      <PageHeader title={t('activity.title')} phoneBack="/more" />
      <Paper withBorder p="md">
        <Switch size="md" checked={paused} label={t('activity.pause')} description={paused ? t('activity.pausedHelp') : t('activity.pauseHelp')}
          onChange={() => sync.mutate({ kind: paused ? 'resume' : 'pause' }, { onError: failed })} />
      </Paper>
      {(status.data?.breakers ?? []).filter((b) => b.heldUntil).map((b) => (
        <Alert key={b.platform} color="orange" variant="light" title={t('inbox.heldTitle', { platform: name(b.platform) })}>
          <Stack gap="xs">
            <Text size="sm">{b.held ? t('inbox.heldBody', { failures: b.failures, time: clock(b.heldUntil), last: b.lastFailure ?? '' }) : t('activity.trial')}</Text>
            <Button size="xs" w="fit-content" onClick={() => sync.mutate({ kind: 'resumePlatform', platform: b.platform }, { onError: failed })}>{t('inbox.resume')}</Button>
          </Stack>
        </Alert>
      ))}
      <SegmentedControl value={show} onChange={(v) => setShow(v as typeof show)}
        data={[{ value: 'all', label: t('activity.all') }, { value: 'open', label: t('activity.open') }, { value: 'failed', label: t('activity.failed') }]} />
      {tasks.isLoading && <Skeleton h={200} radius="lg" />}
      {!tasks.isLoading && days.length === 0 && (
        <EmptyState icon={<IconCheck size={28} />} title={t('activity.empty')} />
      )}
      {days.map(([day, list]) => (
        <Stack key={day} gap="xs">
          <Text size="sm" fw={700} c="dimmed" tt="uppercase" style={{ letterSpacing: '0.06em' }}>{longDate(day)}</Text>
          {list.map((task) => <TaskRow key={task.id} task={task} />)}
        </Stack>
      ))}
    </Stack>
  );
}
