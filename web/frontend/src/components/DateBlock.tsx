import { Box, Text } from '@mantine/core';
import { dayParts } from '../lib/format';

/** A day at a glance: weekday, day, month, as on a ticket. */
export function DateBlock({ date, muted, size = 'md' }: { date: string; muted?: boolean; size?: 'md' | 'lg' }) {
  const p = dayParts(date);
  const w = size === 'lg' ? 66 : 54;
  return (
    <Box
      w={w}
      miw={w}
      py={size === 'lg' ? 8 : 6}
      ta="center"
      style={{
        borderRadius: 14,
        background: muted ? 'var(--mantine-color-default-hover)' : 'var(--mantine-primary-color-light)',
      }}
      aria-hidden
    >
      <Text size="xs" fw={700} tt="uppercase" c="dimmed" lh={1.2}>
        {p.wd}
      </Text>
      <Text fw={800} fz={size === 'lg' ? 28 : 23} lh={1.1}>
        {p.day}
      </Text>
      <Text size="xs" fw={700} tt="uppercase" c={muted ? 'dimmed' : 'brand'} lh={1.2}>
        {p.mon}
      </Text>
    </Box>
  );
}
