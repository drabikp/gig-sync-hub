import {
  Alert,
  Badge,
  Button,
  Collapse,
  Group,
  Paper,
  Select,
  Skeleton,
  Stack,
  Text,
  TextInput,
  UnstyledButton,
} from '@mantine/core';
import { IconCalendarOff, IconChevronDown, IconRefresh, IconSearch } from '@tabler/icons-react';
import { useMemo, useState } from 'react';
import type { TFunction } from 'i18next';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router';
import { useCalendar, useCalendarAction, useCalendarRules, useReadCalendar } from '../api/hooks';
import type { CalendarFilter, CalendarRow } from '../api/types';
import { useBandPath } from '../band/band';
import { ActionButtons } from '../components/ActionButtons';
import { CalendarRules } from '../components/CalendarSetup';
import { DateBlock } from '../components/DateBlock';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { Sheet } from '../components/Sheet';
import { calendarRowBody, changeText, useCalendarRowActions } from '../lib/attention';
import { fold, localDate, relative, shortDate, timeOfDay } from '../lib/format';
import { done, failed } from '../lib/notify';

const FILTERS: CalendarFilter[] = ['NEEDS_A_LOOK', 'MISSING', 'NOT_SURE', 'GIGS', 'NOT_GIGS', 'DECIDED', 'ALL'];

function whenText(t: TFunction, row: CalendarRow): string {
  if (!row.allDay) return `${shortDate(row.start.split('T')[0])} · ${timeOfDay(row.start.split('T')[1])}`;
  const last = localDate(row.end);
  last.setDate(last.getDate() - 1);
  const lastDay = `${last.getFullYear()}-${String(last.getMonth() + 1).padStart(2, '0')}-${String(last.getDate()).padStart(2, '0')}`;
  const first = row.start.split('T')[0];
  return lastDay > first ? `${shortDate(first)} – ${shortDate(lastDay)} · ${t('calendar.allDay')}` : `${shortDate(first)} · ${t('calendar.allDay')}`;
}

function Row({ row }: { row: CalendarRow }) {
  const { t } = useTranslation();
  const actions = useCalendarRowActions();
  const [why, setWhy] = useState(false);
  const kindColor = row.kind === 'GIG' ? 'teal' : row.kind === 'UNSURE' ? 'orange' : 'gray';
  const body = calendarRowBody(t, row);
  return (
    <Paper withBorder p="md" style={row.removed ? { opacity: 0.75 } : undefined}>
      <Stack gap="sm">
        <Group wrap="nowrap" gap="md" align="flex-start">
          <DateBlock date={row.draft.date} muted={row.kind === 'NOT_GIG' || row.removed} />
          <Stack gap={4} style={{ flex: 1, minWidth: 0 }}>
            <Text fw={700} fz={16}>{row.title}</Text>
            <Text size="sm" c="dimmed">
              {whenText(t, row)}
              {row.location ? ` · ${row.location}` : ''}
            </Text>
            <Group gap={6}>
              <Badge color={kindColor} variant="light" styles={{ label: { textTransform: 'none' } }}>
                {t(`kind.${row.kind}`)}
                {row.decidedByUser ? ` · ${t('calendar.byYou')}` : ''}
              </Badge>
              {row.status !== 'CONFIRMED' && row.kind !== 'NOT_GIG' && (
                <Badge color={row.status === 'CANCELLED' ? 'red' : 'yellow'} variant="light" styles={{ label: { textTransform: 'none' } }}>
                  {t(`eventStatus.${row.status}`)}
                </Badge>
              )}
              {row.match.state === 'LINKED' && row.match.gig && (
                <Badge color="teal" variant="dot" styles={{ label: { textTransform: 'none' } }}>
                  {t('calendar.linked', { title: row.match.gig.title })}
                </Badge>
              )}
            </Group>
          </Stack>
        </Group>
        {body && <Text size="sm" fw={600} c={row.match.differences.length ? 'orange.9' : 'brand.8'}>{body}</Text>}
        {row.change && row.match.differences.length > 0 && (
          <Text size="sm" c="brand.8">{changeText(t, row.change)} · {relative(row.change.at)}</Text>
        )}
        {row.reasons.length > 0 && (
          <div>
            <UnstyledButton onClick={() => setWhy(!why)} aria-expanded={why}>
              <Group gap={4} c="dimmed">
                <Text size="sm" fw={600}>{t('calendar.why', { score: row.score })}</Text>
                <IconChevronDown size={14} style={{ transform: why ? 'rotate(180deg)' : undefined }} />
              </Group>
            </UnstyledButton>
            <Collapse expanded={why}>
              <Group gap={6} mt={6}>
                {row.reasons.map((r, i) => (
                  <Badge key={i} variant="default" radius="xl" styles={{ label: { textTransform: 'none', fontWeight: 600 } }}>
                    {t(`why.${r.why}`, { value: r.value ?? '' })}
                    {r.weight !== 0 ? ` ${r.weight > 0 ? '+' : '−'}${Math.abs(r.weight)}` : ''}
                  </Badge>
                ))}
              </Group>
            </Collapse>
          </div>
        )}
        <ActionButtons actions={actions(row, true)} />
      </Stack>
    </Paper>
  );
}

/** The band's calendar, sorted into gigs and the rest and checked against the catalog. */
export function CalendarPage() {
  const { t } = useTranslation();
  const path = useBandPath();
  const calendar = useCalendar();
  const read = useReadCalendar();
  const action = useCalendarAction();
  // in the address, so the view is the same after adding a gig, going back or reloading
  const [params, setParams] = useSearchParams();
  const shown = params.get('show') as CalendarFilter | null;
  const filter: CalendarFilter = shown && FILTERS.includes(shown) ? shown : 'NEEDS_A_LOOK';
  const query = params.get('q') ?? '';
  const setParam = (name: string, value: string, fallback: string) =>
    setParams((p) => {
      if (value === fallback) p.delete(name); else p.set(name, value);
      return p;
    }, { replace: true });
  const setFilter = (f: CalendarFilter) => setParam('show', f, 'NEEDS_A_LOOK');
  const setQuery = (q: string) => setParam('q', q, '');
  const [rulesOpen, setRulesOpen] = useState(false);
  const [linking, setLinking] = useState(false);
  const rules = useCalendarRules(rulesOpen);
  const data = calendar.data;

  const rows = useMemo(() => {
    const q = fold(query.trim());
    return (data?.rows ?? []).filter((r) => r.filters.includes(filter))
      .filter((r) => !q || fold(`${r.title} ${r.location}`).includes(q));
  }, [data, filter, query]);

  if (calendar.isLoading) return <Skeleton h={300} radius="lg" />;
  if (data && !data.configured) {
    return (
      <Stack>
        <PageHeader title={t('calendar.title')} />
        <EmptyState icon={<IconCalendarOff size={28} />} color="gray" title={t('calendar.notConfigured')} text={t('calendar.notConfiguredText')}>
          <Button component={Link} to={path('/settings')} mt="xs">{t('calendarSetup.setUp')}</Button>
        </EmptyState>
      </Stack>
    );
  }
  const counts = data?.counts;
  const readNow = () => read.mutate(undefined, { onSuccess: () => done(t('toast.calendarRead')), onError: failed });

  return (
    <Stack gap="md" maw={900}>
      <PageHeader
        title={t('calendar.title')}
        actions={<Button leftSection={<IconRefresh size={18} />} loading={read.isPending} onClick={readNow}>{t('calendar.read')}</Button>}
        mobileAction={<Button variant="subtle" loading={read.isPending} onClick={readNow} leftSection={<IconRefresh size={18} />}>{t('calendar.readShort')}</Button>}
      />
      <Text c="dimmed" size="sm">
        {data?.lastRead ? t('calendar.lastRead', { when: relative(data.lastRead) }) : t('calendar.neverRead')}
      </Text>
      {read.isError && <Alert color="red" variant="light">{t('calendar.readFailed')}</Alert>}

      <Group gap="sm" wrap="wrap">
        <Select
          w={240}
          value={filter}
          onChange={(v) => v && setFilter(v as CalendarFilter)}
          allowDeselect={false}
          aria-label={t('calendar.show')}
          data={FILTERS.map((f) => ({
            value: f,
            label: `${t(`calendarFilter.${f}`)} (${(data?.rows ?? []).filter((r) => r.filters.includes(f)).length})`,
          }))}
        />
        <TextInput style={{ flex: '1 1 200px' }} leftSection={<IconSearch size={18} />} placeholder={t('calendar.search')}
          aria-label={t('calendar.search')} value={query} onChange={(e) => setQuery(e.currentTarget.value)} type="search" />
      </Group>
      {counts && (counts.linkable > 0 || counts.changes > 0) && (
        <Group gap="xs">
          {counts.linkable > 0 && (
            <Button variant="light" size="sm" onClick={() => setLinking(true)}>
              {t('calendar.linkSameDay', { count: counts.linkable })}
            </Button>
          )}
          {counts.changes > 0 && (
            <Button variant="default" size="sm" onClick={() => action.mutate({ kind: 'seenAll' }, { onError: failed })}>
              {t('calendar.allSeen')}
            </Button>
          )}
        </Group>
      )}

      <Sheet opened={linking} onClose={() => setLinking(false)} title={t('calendar.linkSameDayTitle')}>
        <Stack gap="md">
          <Text size="sm">{t('calendar.linkSameDayText')}</Text>
          <Stack gap="xs">
            {(data?.rows ?? []).filter((r) => r.linkable).map((r) => (
              <Paper key={r.eventId} withBorder p="sm">
                <Text fw={700} size="sm">{shortDate(r.draft.date)} · {r.title}</Text>
                {r.match.gig && (
                  <Text size="sm" c="dimmed">→ {shortDate(r.match.gig.start.split('T')[0])} · {r.match.gig.title} · {r.match.gig.city}</Text>
                )}
              </Paper>
            ))}
          </Stack>
          <Group justify="flex-end">
            <Button variant="default" onClick={() => setLinking(false)}>{t('common.cancel')}</Button>
            <Button loading={action.isPending}
              onClick={() => action.mutate({ kind: 'linkSameDay' }, {
                onSuccess: (r) => { setLinking(false); done(t('toast.linkedSameDay', { count: r?.linked ?? 0 })); },
                onError: failed,
              })}>
              {t('calendar.linkThem')}
            </Button>
          </Group>
        </Stack>
      </Sheet>

      {rows.length === 0 ? (
        <EmptyState icon={<IconRefresh size={28} />} title={filter === 'NEEDS_A_LOOK' ? t('calendar.allGood') : t('calendar.none')}
          text={counts ? t('calendar.summary', { events: counts.events, gigs: counts.gigs }) : undefined} />
      ) : (
        rows.map((row) => <Row key={row.eventId} row={row} />)
      )}

      <Paper withBorder p="md">
        <UnstyledButton onClick={() => setRulesOpen(!rulesOpen)} aria-expanded={rulesOpen} w="100%">
          <Group justify="space-between">
            <Text fw={700}>{t('calendar.rules')}</Text>
            <IconChevronDown size={18} style={{ transform: rulesOpen ? 'rotate(180deg)' : undefined }} />
          </Group>
        </UnstyledButton>
        <Collapse expanded={rulesOpen}>
          <Text size="sm" c="dimmed" mt="sm" mb="sm">{t('calendar.rulesHelp')}</Text>
          {rules.data && <CalendarRules rules={rules.data} />}
        </Collapse>
      </Paper>
    </Stack>
  );
}
