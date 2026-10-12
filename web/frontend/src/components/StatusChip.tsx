import { Badge, Loader } from '@mantine/core';
import { IconAlertTriangle, IconCheck, IconClockHour4, IconMinus } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import type { PlatformStateName } from '../api/types';

const LOOK: Record<PlatformStateName, { color: string; variant: 'light' | 'filled' }> = {
  live: { color: 'teal', variant: 'light' },
  none: { color: 'gray', variant: 'light' },
  queued: { color: 'orange', variant: 'light' },
  running: { color: 'orange', variant: 'light' },
  retrying: { color: 'orange', variant: 'light' },
  failed: { color: 'red', variant: 'light' },
};

function Icon({ state }: { state: PlatformStateName }) {
  switch (state) {
    case 'live':
      return <IconCheck size={13} stroke={3} />;
    case 'failed':
      return <IconAlertTriangle size={13} stroke={2.4} />;
    case 'none':
      return <IconMinus size={13} stroke={2.6} />;
    case 'running':
      return <Loader size={10} color="orange" />;
    default:
      return <IconClockHour4 size={13} stroke={2.4} />;
  }
}

/** A gig's state on one platform: by colour AND icon AND (unless {@code compact}) words. */
export function StatusChip({ state, name, compact }: { state: PlatformStateName; name?: string; compact?: boolean }) {
  const { t } = useTranslation();
  const label = t(`status.${state}`);
  const look = LOOK[state];
  return (
    <Badge
      color={look.color}
      variant={look.variant}
      radius="xl"
      size={compact ? 'md' : 'lg'}
      leftSection={<Icon state={state} />}
      styles={{ label: { textTransform: 'none', fontWeight: 700 } }}
      title={name ? `${name}: ${label}` : label}
      aria-label={name ? `${name}: ${label}` : label}
    >
      {name ? (compact ? name : `${name} · ${label}`) : label}
    </Badge>
  );
}
