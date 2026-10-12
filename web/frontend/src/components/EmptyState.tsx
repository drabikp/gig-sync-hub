import { Paper, Stack, Text, ThemeIcon } from '@mantine/core';
import type { ReactNode } from 'react';

export function EmptyState({ icon, title, text, color = 'teal', children }: {
  icon: ReactNode;
  title: string;
  text?: string;
  color?: string;
  children?: ReactNode;
}) {
  return (
    <Paper withBorder radius="lg" p="xl">
      <Stack align="center" gap="xs" ta="center">
        <ThemeIcon size={56} radius="lg" variant="light" color={color}>
          {icon}
        </ThemeIcon>
        <Text fw={800} fz={19}>
          {title}
        </Text>
        {text && (
          <Text c="dimmed" maw={460}>
            {text}
          </Text>
        )}
        {children}
      </Stack>
    </Paper>
  );
}
