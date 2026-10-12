import { Box, Button, Center, Group, Paper, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconWaveSine } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useCreateBand, useLogout, useMe, useMyBands } from '../api/hooks';
import { bandAddress } from '../band/band';
import { BandFields, useBandFields } from '../components/BandFields';
import { failed } from '../lib/notify';

/** "Create your band": a name and an address; the user becomes its owner and lands on its gigs. */
export function CreateBandPage() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const me = useMe();
  const bands = useMyBands();
  const create = useCreateBand();
  const logout = useLogout();
  const fields = useBandFields();
  const first = (bands.data ?? []).length === 0;
  return (
    <Center mih="100dvh" p="md" bg="var(--mantine-color-default-hover)">
      <Box w="100%" maw={440}>
        <Group gap="sm" mb="lg" justify="center">
          <ThemeIcon size={44} radius="md">
            <IconWaveSine size={26} />
          </ThemeIcon>
          <Title order={1} fz={24}>Gig sync hub</Title>
        </Group>
        <Paper withBorder p="xl" shadow="sm">
          <form onSubmit={(e) => {
            e.preventDefault();
            create.mutate(fields.value, {
              onSuccess: (band) => navigate(bandAddress(band.slug), { replace: true }),
              onError: failed,
            });
          }}>
            <Stack>
              <Title order={2} fz={20}>{first ? t('band.createFirst') : t('band.create')}</Title>
              <Text size="sm">{t('band.createIntro')}</Text>
              <BandFields fields={fields} />
              <Button type="submit" size="md" disabled={!fields.valid} loading={create.isPending}>{t('band.createButton')}</Button>
              {!first && <Button variant="subtle" onClick={() => navigate(-1)}>{t('common.cancel')}</Button>}
            </Stack>
          </form>
        </Paper>
        <Stack align="center" mt="lg" gap={4}>
          <Text size="xs" c="dimmed">{t('settings.signedInAs', { name: me.data?.username })}</Text>
          <Button size="xs" variant="subtle" loading={logout.isPending} onClick={() => logout.mutate()}>{t('settings.signOut')}</Button>
        </Stack>
      </Box>
    </Center>
  );
}
