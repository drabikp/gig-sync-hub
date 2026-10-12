import {
  Alert,
  Box,
  Button,
  Collapse,
  Group,
  Paper,
  Select,
  SegmentedControl,
  Skeleton,
  Stack,
  Text,
  TextInput,
  Textarea,
  Title,
  UnstyledButton,
  Badge,
  CloseButton,
} from '@mantine/core';
import { IconChevronDown, IconClockHour4, IconLock } from '@tabler/icons-react';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { DatePickerInput, TimePicker } from '@mantine/dates';
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import {
  resolveTown,
  useAddFromCalendar,
  useAddGig,
  useCalendar,
  useCalendarAction,
  useEditGig,
  useGig,
  usePlatforms,
} from '../api/hooks';
import type { Country, EntryType, GigDraft } from '../api/types';
import { useBandPath } from '../band/band';
import { PageHeader } from '../components/PageHeader';
import { TownPicker } from '../components/TownPicker';
import { usePlatformName } from '../lib/attention';
import { hhmm, timeFormat, timeOfDay } from '../lib/format';
import { done, errorText, failed, queued } from '../lib/notify';
import { useIsMobile } from '../lib/useIsMobile';

const EMPTY: GigDraft = {
  title: '', date: null, time: '20:00', endDate: null, endTime: null, slotDate: null, slotTime: null,
  slotEndTime: null, venue: '', city: '', country: 'SLOVAKIA', street: null, postalCode: null, district: null,
  region: null, latitude: null, longitude: null, lineup: [], entry: 'FREE', price: null, description: null,
  facebookUrl: null, ticketUrl: null, posterUrl: null, cancelled: false,
};

/** Days between two "yyyy-mm-dd" days. */
function daysBetween(from: string, to: string): number {
  return Math.round((Date.parse(to + 'T00:00:00Z') - Date.parse(from + 'T00:00:00Z')) / 86_400_000);
}

function plusDays(day: string, days: number): string {
  const d = new Date(Date.parse(day + 'T00:00:00Z') + days * 86_400_000);
  return d.toISOString().substring(0, 10);
}

/**
 * The calendar's show — the band's day and time — on the form: as the band's slot when the gig
 * has one or runs over several days, otherwise as the start (moving the end along when the day moved).
 */
export function applyShow(draft: GigDraft, day: string, time: string | null, moveDay: boolean): GigDraft {
  const multiDay = !!draft.endDate && !!draft.date && daysBetween(draft.date, draft.endDate) > (draft.endTime === '00:00' ? 1 : 0);
  if (draft.slotDate || multiDay) {
    return { ...draft, slotDate: day, slotTime: time ?? draft.slotTime };
  }
  const next = { ...draft };
  if (moveDay && draft.date) {
    if (draft.endDate) next.endDate = plusDays(draft.endDate, daysBetween(draft.date, day));
    next.date = day;
  }
  if (time) next.time = time;
  return next;
}

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Paper withBorder p="lg">
      <Title order={2} fz={13} c="dimmed" tt="uppercase" mb="md" style={{ letterSpacing: '0.06em' }}>
        {title}
      </Title>
      <Stack gap="md">{children}</Stack>
    </Paper>
  );
}

/** A time of day in the user's format (Settings); the value stays "HH:mm". */
function TimeField({ value, onChange, ...field }: {
  label: string;
  value: string | null;
  onChange: (value: string | null) => void;
  error?: string;
  description?: string;
}) {
  return (
    <TimePicker {...field} value={value ? hhmm(value) : ''} onChange={(v) => onChange(v || null)}
      format={timeFormat()} minutesStep={5} withDropdown clearable />
  );
}

/** A day, shown in the page's language ("17. 10. 2026"); the value stays "YYYY-MM-DD". */
function DateField({ value, onChange, ...field }: {
  label: string;
  value: string | null;
  onChange: (value: string | null) => void;
  error?: string;
  description?: string;
  disabled?: boolean;
  clearable?: boolean;
}) {
  const { i18n } = useTranslation();
  return (
    <DatePickerInput {...field} value={value} onChange={(v) => onChange(v || null)}
      valueFormat={i18n.language === 'sk' ? 'D. M. YYYY' : 'D MMM YYYY'} />
  );
}

/** New gig, an edit, or a gig from a calendar event (mode). */
export function GigFormPage({ mode }: { mode: 'new' | 'edit' | 'calendar' }) {
  const path = useBandPath();
  const { id, eventId } = useParams();
  const [params] = useSearchParams();
  const { t } = useTranslation();
  const mobile = useIsMobile();
  const navigate = useNavigate();
  const origin = (useLocation().state as { back?: string } | null)?.back;
  const name = usePlatformName();
  const platforms = usePlatforms();
  const existing = useGig(mode === 'edit' ? id : undefined);
  const calendar = useCalendar();
  const add = useAddGig();
  const edit = useEditGig();
  const addFromCalendar = useAddFromCalendar();
  const calendarAction = useCalendarAction();
  const [draft, setDraft] = useState<GigDraft | null>(mode === 'new' ? EMPTY : null);
  const [slotOpen, setSlotOpen] = useState(false);
  const [moreOpen, setMoreOpen] = useState(false);
  const [band, setBand] = useState('');
  const [problems, setProblems] = useState<string[]>([]);
  const [startHint, setStartHint] = useState<string | null>(null);

  const row = mode === 'calendar' ? calendar.data?.rows.find((r) => r.eventId === eventId) : undefined;
  const fromEvent = params.get('fromEvent');

  // start from the gig (edit, with the calendar's show applied when asked) or from the calendar's event
  useEffect(() => {
    if (draft) return;
    if (mode === 'edit' && existing.data) {
      let d: GigDraft = { ...existing.data.gig.gig, venue: existing.data.gig.gig.venue ?? '' };
      d = { ...d, time: hhmm(d.time), endTime: d.endTime ? hhmm(d.endTime) : null, slotTime: d.slotTime ? hhmm(d.slotTime) : null,
        slotEndTime: d.slotEndTime ? hhmm(d.slotEndTime) : null };
      const day = params.get('day');
      if (day) d = applyShow(d, day, params.get('time'), params.get('moveDay') === '1');
      setDraft(d);
      setSlotOpen(!!d.slotDate);
      setMoreOpen(!!(d.facebookUrl || d.ticketUrl || d.posterUrl));
    }
    if (mode === 'calendar' && row) {
      const r = row.draft;
      const d: GigDraft = { ...EMPTY, title: r.title, date: r.date, time: r.showTime ? hhmm(r.showTime) : null,
        venue: r.venue ?? '', city: r.city ?? '', country: r.country ?? 'SLOVAKIA', street: r.street, postalCode: r.postalCode };
      if (!r.showTime && r.eventStart) setStartHint(t('form.eventStartHint', { time: timeOfDay(r.eventStart) }));
      setDraft(d);
      if (r.city) {
        resolveTown(r.city, r.country, r.postalCode).then((town) => {
          if (town) {
            setDraft((cur) => cur && { ...cur, city: town.name, district: town.district, region: town.region,
              latitude: town.latitude, longitude: town.longitude, country: town.country ?? cur.country,
              postalCode: cur.postalCode ?? town.postalCode });
          }
        }).catch(() => undefined);
      }
    }
  }, [mode, existing.data, row, draft, params, t]);

  const slotHelp = useMemo(() => {
    const all = platforms.data ?? [];
    const slot = all.filter((p) => p.listsBandSlot).map((p) => p.name);
    const whole = all.filter((p) => !p.listsBandSlot).map((p) => p.name);
    if (!slot.length) return t('form.slotHelpNone');
    return whole.length ? t('form.slotHelp', { list: slot.join(', '), others: whole.join(', ') }) : t('form.slotHelpAll', { list: slot.join(', ') });
  }, [platforms.data, t]);

  if (!draft) return <Skeleton h={400} radius="lg" />;

  const set = (patch: Partial<GigDraft>) => {
    setDraft({ ...draft, ...patch });
    setProblems([]);
  };
  const err = (field: string) => (problems.includes(field) ? t('form.required') : undefined);
  const addBand = () => {
    const b = band.trim();
    if (b && !draft.lineup.includes(b)) set({ lineup: [...draft.lineup, b] });
    setBand('');
  };

  const back = mode === 'edit' ? `/gig/${id}` : mode === 'calendar' ? '/calendar' : '/';   // within the band
  // opened from a view (the calendar's filter, the Inbox): back to it as it was
  const leave = () => (origin ? navigate(-1) : navigate(path(back), { replace: true }));
  const title = mode === 'edit' ? t('form.editTitle') : mode === 'calendar' ? t('form.fromCalendarTitle') : t('form.newTitle');
  const saving = add.isPending || edit.isPending || addFromCalendar.isPending;

  const send: GigDraft = {
    ...draft,
    venue: draft.venue?.trim() || null,
    slotDate: slotOpen ? draft.slotDate : null,
    slotTime: slotOpen ? draft.slotTime : null,
    slotEndTime: slotOpen ? draft.slotEndTime : null,
    endDate: draft.endTime ? draft.endDate : null,
    price: draft.entry === 'PAID' ? draft.price : null,
  };

  const onError = (e: unknown) => {
    if (e instanceof ApiError && e.code === 'invalidGig') {
      setProblems(e.args.fields.split(','));
    }
    failed(e);
  };

  const save = () => {
    const missing = [!draft.title.trim() && 'title', !draft.date && 'date', !draft.time && 'time', !draft.city?.trim() && 'city',
      draft.entry === 'PAID' && !draft.price?.trim() && 'price'].filter(Boolean) as string[];
    if (missing.length) {
      setProblems(missing);
      return;
    }
    if (mode === 'new') {
      add.mutate(send, {
        onSuccess: (gig) => {
          done(t('toast.saved'));
          navigate(path(`/gig/${gig.id}`), { replace: true, state: { justAdded: true } });
        },
        onError,
      });
    } else if (mode === 'edit' && existing.data) {
      edit.mutate({ id: existing.data.gig.id, rev: existing.data.gig.rev, gig: send }, {
        onSuccess: (r) => {
          queued(t, t('toast.saved'), r.queued, name);
          if (fromEvent) calendarAction.mutate({ kind: 'seen', event: fromEvent });
          navigate(path(`/gig/${r.gig.id}`), { replace: true });
        },
        onError,
      });
    } else if (mode === 'calendar' && eventId) {
      addFromCalendar.mutate({ eventId, gig: send }, {
        onSuccess: () => {
          done(t('toast.addedFromCalendar'));
          leave();
        },
        onError,
      });
    }
  };

  const countries: { value: Country; label: string }[] = [
    { value: 'SLOVAKIA', label: t('country.SLOVAKIA') },
    { value: 'CZECHIA', label: t('country.CZECHIA') },
  ];

  return (
    <Box maw={760}>
      <PageHeader title={title} back={origin ? leave : back} />
      {mode === 'calendar' && (
        <Alert color="brand" variant="light" mb="md">
          {t('form.fromCalendarHint')}
        </Alert>
      )}
      {mode === 'edit' && fromEvent && (
        <Alert color="orange" variant="light" mb="md">
          {t('form.fromCalendarChange')}
        </Alert>
      )}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          save();
        }}
        noValidate
      >
        <Stack gap="md">
          <Section title={t('form.when')}>
            <TextInput label={t('form.title')} placeholder={t('form.titlePlaceholder')} value={draft.title}
              onChange={(e) => set({ title: e.currentTarget.value })} error={err('title')} />
            <Group grow align="flex-start">
              <DateField label={t('form.date')} value={draft.date} error={err('date')} onChange={(date) => set({ date })} />
              <TimeField label={t('form.time')} value={draft.time} error={err('time')}
                description={startHint ?? undefined} onChange={(time) => set({ time })} />
            </Group>
            <Group grow align="flex-start">
              <TimeField label={t('form.endTime')} description={t('form.optional')} value={draft.endTime}
                onChange={(endTime) => set({ endTime })} />
              <DateField label={t('form.endDate')} description={t('form.endDateHelp')} value={draft.endDate} clearable
                disabled={!draft.endTime} onChange={(endDate) => set({ endDate })} />
            </Group>
            <UnstyledButton onClick={() => setSlotOpen(!slotOpen)} aria-expanded={slotOpen}
              style={{ border: '1px dashed var(--mantine-color-default-border)', borderRadius: 12, padding: '12px 14px' }}>
              <Group gap="sm" c="brand.7">
                <IconClockHour4 size={18} />
                <Text fw={600}>{slotOpen ? t('form.slotHide') : t('form.slotShow')}</Text>
              </Group>
            </UnstyledButton>
            <Collapse in={slotOpen}>
              <Stack gap="xs">
                <Group grow align="flex-start">
                  <DateField label={t('form.slotDate')} value={draft.slotDate} clearable
                    error={problems.includes('slot') ? t('form.slotBoth') : undefined} onChange={(slotDate) => set({ slotDate })} />
                  <TimeField label={t('form.slotTime')} value={draft.slotTime} onChange={(slotTime) => set({ slotTime })} />
                  <TimeField label={t('form.slotEndTime')} value={draft.slotEndTime}
                    onChange={(slotEndTime) => set({ slotEndTime })} />
                </Group>
                <Text size="sm" c="dimmed">{slotHelp}</Text>
              </Stack>
            </Collapse>
          </Section>

          <Section title={t('form.where')}>
            <TownPicker
              city={draft.city ?? ''}
              district={draft.district}
              country={draft.country}
              error={err('city')}
              onType={(text) => set({ city: text, district: null, region: null, latitude: null, longitude: null })}
              onPick={(town) => set({ city: town.name, district: town.district, region: town.region, latitude: town.latitude,
                longitude: town.longitude, country: town.country ?? draft.country, postalCode: draft.postalCode || town.postalCode })}
            />
            <TextInput label={t('form.venue')} placeholder={t('form.venuePlaceholder')} value={draft.venue ?? ''}
              onChange={(e) => set({ venue: e.currentTarget.value })} />
            <Select label={t('form.country')} data={countries} value={draft.country} allowDeselect={false}
              onChange={(v) => set({ country: (v as Country) ?? draft.country })} />
            <Group grow align="flex-start">
              <TextInput label={t('form.street')} description={t('form.optional')} value={draft.street ?? ''}
                onChange={(e) => set({ street: e.currentTarget.value || null })} />
              <TextInput label={t('form.postalCode')} description={t('form.optional')} value={draft.postalCode ?? ''}
                onChange={(e) => set({ postalCode: e.currentTarget.value || null })} />
            </Group>
          </Section>

          <Section title={t('form.about')}>
            <Stack gap={8}>
              <Text fw={500} size="sm">{t('form.lineup')}</Text>
              {draft.lineup.length > 0 && (
                <Group gap={6}>
                  {draft.lineup.map((b) => (
                    <Badge key={b} size="lg" variant="default" radius="xl" pr={4} styles={{ label: { textTransform: 'none' } }}
                      rightSection={<CloseButton size="sm" aria-label={t('form.removeBand', { name: b })}
                        onClick={() => set({ lineup: draft.lineup.filter((x) => x !== b) })} />}>
                      {b}
                    </Badge>
                  ))}
                </Group>
              )}
              <Group gap="xs" wrap="nowrap">
                <TextInput style={{ flex: 1 }} placeholder={t('form.addBand')} aria-label={t('form.addBand')} value={band}
                  onChange={(e) => setBand(e.currentTarget.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addBand();
                    }
                  }} />
                <Button variant="light" onClick={addBand}>{t('form.add')}</Button>
              </Group>
            </Stack>
            <Stack gap={6}>
              <Text fw={500} size="sm">{t('form.entry')}</Text>
              <SegmentedControl fullWidth value={draft.entry} onChange={(v) => set({ entry: v as EntryType })}
                data={(['FREE', 'VOLUNTARY', 'PAID'] as EntryType[]).map((e) => ({ value: e, label: t(`entry.${e}`) }))} />
              {draft.entry === 'PAID' && (
                <TextInput label={t('form.price')} placeholder={t('form.pricePlaceholder')} value={draft.price ?? ''}
                  error={err('price')} onChange={(e) => set({ price: e.currentTarget.value })} />
              )}
            </Stack>
            <Textarea label={t('form.description')} placeholder={t('form.descriptionPlaceholder')} autosize minRows={3}
              value={draft.description ?? ''} onChange={(e) => set({ description: e.currentTarget.value || null })} />
            <UnstyledButton onClick={() => setMoreOpen(!moreOpen)} aria-expanded={moreOpen}>
              <Group gap={6} c="brand.7">
                <IconChevronDown size={18} style={{ transform: moreOpen ? 'rotate(180deg)' : undefined, transition: 'transform 150ms' }} />
                <Text fw={600}>{t('form.links')}</Text>
              </Group>
            </UnstyledButton>
            <Collapse in={moreOpen}>
              <Stack gap="md">
                <TextInput label={t('form.ticketUrl')} type="url" value={draft.ticketUrl ?? ''} onChange={(e) => set({ ticketUrl: e.currentTarget.value || null })} />
                <TextInput label={t('form.facebookUrl')} type="url" value={draft.facebookUrl ?? ''} onChange={(e) => set({ facebookUrl: e.currentTarget.value || null })} />
                <TextInput label={t('form.posterUrl')} type="url" value={draft.posterUrl ?? ''} onChange={(e) => set({ posterUrl: e.currentTarget.value || null })} />
              </Stack>
            </Collapse>
          </Section>

          {row?.notes && (
            <Paper withBorder p="md" bg="var(--mantine-color-default-hover)">
              <Group gap={6} mb={6} c="dimmed">
                <IconLock size={16} />
                <Text size="sm" fw={700}>{t('form.privateNotes')}</Text>
              </Group>
              <Text size="sm" style={{ whiteSpace: 'pre-wrap' }}>{row.notes}</Text>
            </Paper>
          )}

          {problems.length > 0 && (
            <Alert color="red" variant="light">
              {errorText(new ApiError(400, 'invalidGig', { fields: problems.join(',') }, ''))}
            </Alert>
          )}

          <Group
            gap="sm"
            justify="flex-end"
            pos={mobile ? 'sticky' : undefined}
            bottom={mobile ? 'calc(68px + env(safe-area-inset-bottom))' : undefined}
            py={mobile ? 'sm' : 0}
            style={mobile ? { background: 'var(--mantine-color-body)', borderTop: '1px solid var(--mantine-color-default-border)', zIndex: 10 } : undefined}
          >
            <Button variant="default" size="md" onClick={() => (origin ? leave() : navigate(path(back)))}>
              {t('common.cancel')}
            </Button>
            <Button type="submit" size="md" loading={saving} style={mobile ? { flex: 1 } : undefined}>
              {mode === 'calendar' ? t('form.saveToCatalog') : t('form.save')}
            </Button>
          </Group>
        </Stack>
      </form>
    </Box>
  );
}
