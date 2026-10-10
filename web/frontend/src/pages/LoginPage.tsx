import { Alert, Box, Button, Center, Group, Paper, SegmentedControl, Stack, Text, ThemeIcon, Title } from '@mantine/core';
import { IconWaveSine } from '@tabler/icons-react';
import { useTranslation } from 'react-i18next';
import { LANGUAGES, setLanguage } from '../i18n';
import { signIn, signInFailed } from '../lib/signIn';

/** Not signed in: the way to Rauthy. */
export function LoginPage() {
  const { t, i18n } = useTranslation();
  return (
    <Center mih="100dvh" p="md" bg="var(--mantine-color-default-hover)">
      <Box w="100%" maw={400}>
        <Group gap="sm" mb="lg" justify="center">
          <ThemeIcon size={44} radius="md">
            <IconWaveSine size={26} />
          </ThemeIcon>
          <Title order={1} fz={24}>
            Gig sync hub
          </Title>
        </Group>
        <Paper withBorder p="xl" shadow="sm">
          <Stack>
            <Title order={2} fz={20}>
              {t('login.title')}
            </Title>
            {signInFailed() && (
              <Alert color="red" variant="light">
                {t('login.failed')}
              </Alert>
            )}
            <Text size="sm">{t('login.intro')}</Text>
            <Button size="md" onClick={signIn}>
              {t('login.signIn')}
            </Button>
          </Stack>
        </Paper>
        <Stack align="center" mt="lg" gap="xs">
          <SegmentedControl value={i18n.language} onChange={setLanguage}
            data={LANGUAGES.map((l) => ({ value: l.code, label: l.name }))} aria-label={t('settings.language')} />
          <Text size="xs" c="dimmed">
            {t('login.hint')}
          </Text>
        </Stack>
      </Box>
    </Center>
  );
}
