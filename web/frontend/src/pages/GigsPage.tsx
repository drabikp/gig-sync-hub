import {
  Affix,
  Anchor,
  Badge,
  Button,
  Checkbox,
  Chip,
  Group,
  Menu,
  Paper,
  ScrollArea,
  SegmentedControl,
  Skeleton,
  Stack,
  Table,
  Text,
  TextInput,
  UnstyledButton,
} from '@mantine/core';
import { IconChevronRight, IconDownload, IconPlus, IconSearch, IconTicket } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate } from 'react-router';
import { useExportAddress, useGigs, usePlatforms, usePublish, useResync, useSyncStatus } from '../api/hooks';
import type { Gig } from '../api/types';
import { useBandPath } from '../band/band';
import { DateBlock } from '../components/DateBlock';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { StatusChip } from '../components/StatusChip';
import { usePlatformName } from '../lib/attention';
import { fold, shortDate, timeOfDay } from '../lib/format';
import { failed, queued } from '../lib/notify';
import { useIsMobile } from '../lib/useIsMobile';

type Filter = 'all' | 'needs' | 'unpublished';

function place(t: (k: string) => string, gig: Gig): string {
  return `${gig.gig.venue || t('gig.tba')} · ${gig.gig.city}`;
}

/** The band's gigs: upcoming first (or the past), searchable, each with its state on every platform. */
export function GigsPage() {
  const path = useBandPath();
  const exportAddress = useExportAddress();
  const { t } = useTranslation();
  const mobile = useIsMobile();
  const navigate = useNavigate();
  const gigs = useGigs();
  const platforms = usePlatforms();
  const status = useSyncStatus();
  const name = usePlatformName();
  const publish = usePublish();
  const resync = useResync();
  const [period, setPeriod] = useState<'upcoming' | 'past'>('upcoming');
  const [query, setQuery] = useState('');
  const [filter, setFilter] = useState<Filter>('all');
  const [selected, setSelected] = useState<string[]>([]);

  const list = useMemo(() => {
    const q = fold(query.trim());
    return (gigs.data ?? [])
      .filter((g) => (period === 'upcoming' ? !g.past : g.past))
      .filter((g) => !q || fold(`${g.gig.title} ${g.gig.venue ?? ''} ${g.gig.city}`).includes(q))
      .filter((g) =>
        filter === 'all' ? true
          : filter === 'needs' ? g.platforms.some((p) => p.state === 'failed')
            : !g.gig.cancelled && g.platforms.some((p) => p.state === 'none'))
      .sort((a, b) => {
        const order = `${a.gig.date}T${a.gig.time}`.localeCompare(`${b.gig.date}T${b.gig.time}`);
        return period === 'upcoming' ? order : -order;
      });
  }, [gigs.data, period, query, filter]);

  const chosen = selected.filter((id) => list.some((g) => g.id === id));
  const counts = status.data?.counts;
  const busy = counts ? counts.queued + counts.running + counts.retrying : 0;

  const bulkPublish = (targets: string[]) =>
    publish.mutate({ gigs: chosen, platforms: targets }, {
      onSuccess: (r) => {
        queued(t, '', r, name);
        setSelected([]);
      },
      onError: failed,
    });

  const exportable = (platforms.data ?? []).filter((p) => p.exportable);

  return (
    <Stack gap="md">
      <PageHeader
        title={t('gigs.title')}
        actions={
          <>
            {exportable.length > 0 && (
              <Menu position="bottom-end">
                <Menu.Target>
                  <Button variant="default" leftSection={<IconDownload size={18} />}>
                    {t('gigs.download')}
                  </Button>
                </Menu.Target>
                <Menu.Dropdown>
                  {exportable.map((p) => (
                    <Menu.Item key={p.id} component="a" href={exportAddress(p.id)}>
                      {t('gigs.downloadFor', { platform: p.name })}
                    </Menu.Item>
                  ))}
                </Menu.Dropdown>
              </Menu>
            )}
            <Button leftSection={<IconPlus size={18} />} onClick={() => navigate(path('/gig/new'))}>
              {t('gigs.new')}
            </Button>
          </>
        }
      />

      <Group gap="sm" wrap="wrap">
        <SegmentedControl
          value={period}
          onChange={(v) => setPeriod(v as 'upcoming' | 'past')}
          data={[
            { value: 'upcoming', label: t('gigs.upcoming') },
            { value: 'past', label: t('gigs.past') },
          ]}
        />
        <TextInput
          style={{ flex: '1 1 240px' }}
          leftSection={<IconSearch size={18} />}
          placeholder={t('gigs.search')}
          aria-label={t('gigs.search')}
          value={query}
          onChange={(e) => setQuery(e.currentTarget.value)}
          type="search"
        />
      </Group>
      <ScrollArea type="never">
        <Chip.Group value={filter} onChange={(v) => setFilter(v as Filter)}>
          <Group gap="xs" wrap="nowrap">
            <Chip value="all" radius="xl">{t('gigs.all')}</Chip>
            <Chip value="needs" radius="xl" color="red">{t('gigs.needsYou')}</Chip>
            <Chip value="unpublished" radius="xl">{t('gigs.unpublished')}</Chip>
          </Group>
        </Chip.Group>
      </ScrollArea>

      <Group justify="space-between">
        <Text size="sm" c="dimmed" fw={600}>
          {t('gigs.count', { count: list.length })}
        </Text>
        {counts && (busy > 0 || counts.failed > 0) && (
          <Anchor component={Link} to={path('/activity')} size="sm" fw={600} c={counts.failed ? 'red' : 'orange.8'}>
            {[busy && t('gigs.syncBusy', { count: busy }), counts.failed && t('gigs.syncFailed', { count: counts.failed })]
              .filter(Boolean).join(' · ')}
          </Anchor>
        )}
      </Group>

      {gigs.isLoading && <Skeleton h={90} radius="lg" />}
      {!gigs.isLoading && list.length === 0 && (
        <EmptyState icon={<IconTicket size={28} />} color="gray" title={t('gigs.empty')} text={t('gigs.emptyHint')} />
      )}

      {mobile ? (
        <Stack gap="sm">
          {list.map((g) => (
            <UnstyledButton key={g.id} onClick={() => navigate(path(`/gig/${g.id}`))}>
              <Paper withBorder p="md" radius="lg">
                <Group wrap="nowrap" gap="md" align="center">
                  <DateBlock date={g.gig.date!} muted={g.gig.cancelled} />
                  <Stack gap={3} style={{ flex: 1, minWidth: 0 }}>
                    <Group gap={8} wrap="nowrap">
                      <Text fw={700} fz={16} truncate td={g.gig.cancelled ? 'line-through' : undefined}
                        c={g.gig.cancelled ? 'dimmed' : undefined}>
                        {g.gig.title}
                      </Text>
                      {g.gig.cancelled && <Badge color="gray" variant="light" style={{ flexShrink: 0 }}>{t('gig.cancelled')}</Badge>}
                    </Group>
                    <Text size="sm" c="dimmed" truncate>
                      {timeOfDay(g.gig.time)} · {place(t, g)}
                    </Text>
                    <Group gap={6} mt={4}>
                      {g.platforms.map((p) => (
                        <StatusChip key={p.platform} state={p.state} name={name(p.platform)} compact />
                      ))}
                    </Group>
                  </Stack>
                  <IconChevronRight size={20} color="var(--mantine-color-dimmed)" />
                </Group>
              </Paper>
            </UnstyledButton>
          ))}
        </Stack>
      ) : (
        list.length > 0 && (
          <Paper withBorder radius="lg" style={{ overflow: 'hidden' }}>
            {chosen.length > 0 && (
              <Group gap="sm" px="md" py="sm" bg="var(--mantine-primary-color-light)">
                <Text fw={700} c="brand.8">
                  {t('gigs.selected', { count: chosen.length })}
                </Text>
                <Menu>
                  <Menu.Target>
                    <Button size="sm" loading={publish.isPending}>{t('gigs.publish')}</Button>
                  </Menu.Target>
                  <Menu.Dropdown>
                    <Menu.Item onClick={() => bulkPublish((platforms.data ?? []).map((p) => p.id))}>
                      {t('gigs.publishEverywhere')}
                    </Menu.Item>
                    {(platforms.data ?? []).map((p) => (
                      <Menu.Item key={p.id} onClick={() => bulkPublish([p.id])}>
                        {t('gigs.publishOn', { platform: p.name })}
                      </Menu.Item>
                    ))}
                  </Menu.Dropdown>
                </Menu>
                <Button size="sm" variant="default" loading={resync.isPending}
                  onClick={() => resync.mutate(chosen, { onSuccess: (r) => { queued(t, '', r, name); setSelected([]); }, onError: failed })}>
                  {t('gigs.updateEverywhere')}
                </Button>
                <Button size="sm" variant="subtle" onClick={() => setSelected([])}>
                  {t('gigs.clearSelection')}
                </Button>
              </Group>
            )}
            <Table.ScrollContainer minWidth={860}>
              <Table verticalSpacing="sm" highlightOnHover>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th w={44}>
                      <Checkbox
                        aria-label={t('gigs.selectAll')}
                        checked={chosen.length > 0 && chosen.length === list.length}
                        indeterminate={chosen.length > 0 && chosen.length < list.length}
                        onChange={() => setSelected(chosen.length === list.length ? [] : list.map((g) => g.id))}
                      />
                    </Table.Th>
                    <Table.Th>{t('gigs.date')}</Table.Th>
                    <Table.Th>{t('gigs.gig')}</Table.Th>
                    <Table.Th>{t('gigs.place')}</Table.Th>
                    {(platforms.data ?? []).map((p) => (
                      <Table.Th key={p.id}>{p.name}</Table.Th>
                    ))}
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {list.map((g) => (
                    <Table.Tr key={g.id} bg={chosen.includes(g.id) ? 'var(--mantine-primary-color-light)' : undefined}
                      style={{ cursor: 'pointer' }} onClick={() => navigate(path(`/gig/${g.id}`))}>
                      <Table.Td onClick={(e) => e.stopPropagation()}>
                        <Checkbox
                          aria-label={t('gigs.select', { title: g.gig.title })}
                          checked={chosen.includes(g.id)}
                          onChange={() => setSelected(chosen.includes(g.id) ? chosen.filter((x) => x !== g.id) : [...chosen, g.id])}
                        />
                      </Table.Td>
                      <Table.Td style={{ whiteSpace: 'nowrap' }}>
                        <Text fw={700}>{shortDate(g.gig.date!)}</Text>
                        <Text size="sm" c="dimmed">{timeOfDay(g.gig.time)}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Group gap={8} wrap="nowrap">
                          <Anchor component={Link} to={path(`/gig/${g.id}`)} fw={700} c="var(--mantine-color-text)"
                            td={g.gig.cancelled ? 'line-through' : undefined} onClick={(e) => e.stopPropagation()}>
                            {g.gig.title}
                          </Anchor>
                          {g.gig.cancelled && <Badge color="gray" variant="light">{t('gig.cancelled')}</Badge>}
                        </Group>
                      </Table.Td>
                      <Table.Td c="dimmed">{place(t, g)}</Table.Td>
                      {(platforms.data ?? []).map((p) => {
                        const s = g.platforms.find((x) => x.platform === p.id);
                        return (
                          <Table.Td key={p.id}>{s && <StatusChip state={s.state} />}</Table.Td>
                        );
                      })}
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Table.ScrollContainer>
          </Paper>
        )
      )}

      {mobile && (
        <Affix position={{ bottom: 'calc(84px + env(safe-area-inset-bottom))', right: 16 }}>
          <Button size="lg" radius="xl" leftSection={<IconPlus size={22} />} onClick={() => navigate(path('/gig/new'))}
            style={{ boxShadow: '0 10px 24px rgba(59, 79, 216, 0.38)' }}>
            {t('gigs.new')}
          </Button>
        </Affix>
      )}
    </Stack>
  );
}
