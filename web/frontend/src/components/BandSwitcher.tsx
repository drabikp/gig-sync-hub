import { Group, Menu, Stack, Text, ThemeIcon, UnstyledButton } from '@mantine/core';
import { IconCheck, IconPlus, IconSelector, IconWaveSine } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useMyBands } from '../api/hooks';
import { bandAddress, useBand } from '../band/band';

/** The band the pages show, and the way to another of the user's bands or to a new one. */
export function BandSwitcher() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const band = useBand();
  const bands = useMyBands();
  return (
    <Menu position="bottom-start" width={260} shadow="md">
      <Menu.Target>
        <UnstyledButton aria-label={t('band.switch')} p={6} style={{ borderRadius: 10, width: '100%' }}>
          <Group gap="sm" wrap="nowrap">
            <ThemeIcon size={36} radius="md">
              <IconWaveSine size={22} />
            </ThemeIcon>
            <Stack gap={0} style={{ flex: 1, minWidth: 0 }}>
              <Text fw={800} fz={16} truncate>{band.name}</Text>
              <Text size="xs" c="dimmed">{t(`band.role.${band.role}`)}</Text>
            </Stack>
            <IconSelector size={18} />
          </Group>
        </UnstyledButton>
      </Menu.Target>
      <Menu.Dropdown>
        <Menu.Label>{t('band.yourBands')}</Menu.Label>
        {(bands.data ?? []).map((b) => (
          <Menu.Item key={b.id} onClick={() => navigate(bandAddress(b.slug))}
            rightSection={b.id === band.id ? <IconCheck size={16} /> : undefined}>
            <Text size="sm" fw={600}>{b.name}</Text>
            <Text size="xs" c="dimmed">{t(`band.role.${b.role}`)}</Text>
          </Menu.Item>
        ))}
        <Menu.Divider />
        <Menu.Item leftSection={<IconPlus size={16} />} onClick={() => navigate('/new-band')}>{t('band.new')}</Menu.Item>
      </Menu.Dropdown>
    </Menu>
  );
}
