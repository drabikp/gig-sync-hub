import { Button, Group, Stack, Text } from '@mantine/core';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { Action } from '../lib/attention';
import { Sheet } from './Sheet';

/** A row of actions (the first primary filled); one with {@code confirm} asks first. */
export function ActionButtons({ actions, size = 'sm' }: { actions: Action[]; size?: 'xs' | 'sm' | 'md' }) {
  const { t } = useTranslation();
  const [asking, setAsking] = useState<Action | null>(null);
  if (!actions.length) return null;
  return (
    <>
      <Group gap="xs" wrap="wrap">
        {actions.map((a) => (
          <Button
            key={a.label}
            size={size}
            mih={40}
            variant={a.primary ? 'filled' : a.danger ? 'light' : 'default'}
            color={a.danger ? 'red' : undefined}
            onClick={() => (a.confirm ? setAsking(a) : a.run())}
          >
            {a.label}
          </Button>
        ))}
      </Group>
      <Sheet opened={!!asking} onClose={() => setAsking(null)} title={asking?.confirm?.title}>
        <Stack>
          <Text>{asking?.confirm?.text}</Text>
          <Group grow>
            <Button variant="default" size="md" onClick={() => setAsking(null)}>
              {t('common.cancel')}
            </Button>
            <Button
              color="red"
              size="md"
              onClick={() => {
                asking?.run();
                setAsking(null);
              }}
            >
              {asking?.confirm?.yes}
            </Button>
          </Group>
        </Stack>
      </Sheet>
    </>
  );
}
