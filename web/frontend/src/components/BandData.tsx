import { Button, Collapse, Group, Stack, Table, Text, TextInput, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconDownload } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useAuditLog, useBandExportAddress, useDeleteBand } from '../api/hooks';
import { useBand } from '../band/band';
import { when } from '../lib/format';
import { done, failed } from '../lib/notify';
import { Sheet } from './Sheet';

/** "updateGig" → "Update gig": an operation as a person reads it, unless the page has its own words for it. */
function actionText(t: (key: string, options?: Record<string, unknown>) => string, action: string) {
  const words = action.replace(/([A-Z])/g, ' $1').toLowerCase();
  return t(`audit.action.${action}`, { defaultValue: words.charAt(0).toUpperCase() + words.slice(1) });
}

/** Who changed what in the band, newest first (owners). */
export function AuditLog() {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const log = useAuditLog(open);
  return (
    <Stack gap="xs">
      <UnstyledButton onClick={() => setOpen(!open)} aria-expanded={open}>
        <Group gap={6}>
          <Text fw={600}>{t('audit.title')}</Text>
          <IconChevronDown size={16} style={{ transform: open ? 'rotate(180deg)' : undefined }} />
        </Group>
      </UnstyledButton>
      <Collapse expanded={open}>
        {(log.data ?? []).length === 0 ? <Text size="sm" c="dimmed">{t('audit.none')}</Text> : (
          <Table verticalSpacing={4} fz="sm">
            <Table.Tbody>
              {(log.data ?? []).map((e, i) => (
                <Table.Tr key={i} c={e.status >= 400 ? 'dimmed' : undefined}>
                  <Table.Td style={{ whiteSpace: 'nowrap' }}>{when(e.at)}</Table.Td>
                  <Table.Td>{e.who ?? t('audit.someone')}</Table.Td>
                  <Table.Td>{actionText(t, e.action)}{e.target ? ` · ${e.target}` : ''}{e.status >= 400 ? ` (${t('audit.refused')})` : ''}</Table.Td>
                </Table.Tr>
              ))}
            </Table.Tbody>
          </Table>
        )}
      </Collapse>
    </Stack>
  );
}

/** The band's data as one file, and deleting the band (owners; its address typed in). */
export function BandDataActions() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const band = useBand();
  const exportAddress = useBandExportAddress();
  const remove = useDeleteBand();
  const [deleting, setDeleting] = useState(false);
  const [typed, setTyped] = useState('');
  return (
    <Stack gap="sm">
      <Group justify="space-between" wrap="wrap" gap="xs">
        <Text size="sm" c="dimmed" style={{ flex: '1 1 260px' }}>{t('bandData.exportHelp')}</Text>
        <Button component="a" href={exportAddress} download variant="default" leftSection={<IconDownload size={16} />}>
          {t('bandData.export')}
        </Button>
      </Group>
      <Group justify="space-between" wrap="wrap" gap="xs">
        <Text size="sm" c="dimmed" style={{ flex: '1 1 260px' }}>{t('bandData.deleteHelp')}</Text>
        <Button color="red" variant="light" onClick={() => setDeleting(true)}>{t('bandData.delete')}</Button>
      </Group>
      <Sheet opened={deleting} onClose={() => setDeleting(false)} title={t('bandData.deleteTitle', { band: band.name })}>
        <form onSubmit={(e) => {
          e.preventDefault();
          remove.mutate(typed.trim(), {
            onSuccess: () => { done(t('bandData.deleted', { band: band.name })); navigate('/', { replace: true }); },
            onError: failed,
          });
        }}>
          <Stack>
            <Text>{t('bandData.deleteText')}</Text>
            <TextInput label={t('bandData.typeAddress', { slug: band.slug })} value={typed} autoComplete="off"
              onChange={(e) => setTyped(e.currentTarget.value)} />
            <Group grow>
              <Button variant="default" onClick={() => setDeleting(false)}>{t('common.cancel')}</Button>
              <Button type="submit" color="red" disabled={typed.trim() !== band.slug} loading={remove.isPending}>
                {t('bandData.delete')}
              </Button>
            </Group>
          </Stack>
        </form>
      </Sheet>
    </Stack>
  );
}
