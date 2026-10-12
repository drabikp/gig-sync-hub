import { ActionIcon, Box, Button, Group, Title } from '@mantine/core';
import { IconChevronLeft } from '@tabler/icons-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useIsMobile } from '../lib/useIsMobile';
import { useBandPath } from '../band/band';

/**
 * A page's title row. On a phone: a bar stuck to the top, with Back and up to one action; on a
 * desktop: a large title with the actions beside it and Back above it.
 */
export function PageHeader({ title, back: always, phoneBack, backLabel, actions, mobileAction }: {
  title: ReactNode;
  /** Where Back goes: an address within the band ('/', '/gig/x'), or what it does. */
  back?: string | (() => void);
  /** Back only on a phone (where the page is reached from More; the desktop menu lists it). */
  phoneBack?: string;
  backLabel?: string;
  actions?: ReactNode;
  mobileAction?: ReactNode;
}) {
  const mobile = useIsMobile();
  const navigate = useNavigate();
  const { t } = useTranslation();
  const back = always ?? (mobile ? phoneBack : undefined);
  const path = useBandPath();
  const goBack = () => (typeof back === 'function' ? back() : back ? navigate(path(back)) : undefined);
  if (mobile) {
    return (
      <Box
        pos="sticky"
        top={0}
        style={{ zIndex: 50, background: 'var(--mantine-color-body)', borderBottom: '1px solid var(--mantine-color-default-border)' }}
        mx="calc(var(--mantine-spacing-md) * -1)"
        mt="calc(var(--mantine-spacing-md) * -1)"
        mb="md"
        px={back ? 4 : 'md'}
        h={56}
      >
        <Group h="100%" gap={4} wrap="nowrap">
          {back && (
            <ActionIcon variant="subtle" color="gray" size={44} onClick={goBack} aria-label={t('common.back')}>
              <IconChevronLeft size={24} />
            </ActionIcon>
          )}
          <Title order={1} fz={19} fw={800} style={{ flex: 1, minWidth: 0 }} lineClamp={1}>
            {title}
          </Title>
          {mobileAction}
        </Group>
      </Box>
    );
  }
  return (
    <Box mb="lg">
      {back && (
        <Button variant="subtle" leftSection={<IconChevronLeft size={18} />} onClick={goBack} px={6} mb={4}>
          {backLabel ?? t('common.back')}
        </Button>
      )}
      <Group justify="space-between" align="center" gap="md">
        <Title order={1} fz={28} fw={800} style={{ letterSpacing: '-0.02em' }}>
          {title}
        </Title>
        {actions && <Group gap="sm">{actions}</Group>}
      </Group>
    </Box>
  );
}
