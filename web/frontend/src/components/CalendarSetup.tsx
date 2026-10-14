import { ActionIcon, Badge, Button, Code, Collapse, Group, NumberInput, Paper, PasswordInput, Select, Stack, Switch, Table, Text, TextInput, UnstyledButton } from '@mantine/core';
import { IconChevronDown, IconPlus, IconTrash } from '@tabler/icons-react';
import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useCalendarConnection, useConnectCalendar, useDisconnectCalendar, useRuleKinds, useSaveCalendarRules } from '../api/hooks';
import type { CalendarRule, CalendarRuleDraft, RuleKind } from '../api/types';
import { done, failed } from '../lib/notify';
import { useIsMobile } from '../lib/useIsMobile';
import { ActionButtons } from './ActionButtons';

/** The zones a band's calendar is likely in; any other can be typed. */
const ZONES = ['Europe/Prague', 'Europe/Bratislava', 'Europe/Vienna', 'Europe/Berlin', 'Europe/Warsaw', 'Europe/Budapest', 'Europe/London', 'UTC'];

/**
 * Which calendar the band reads: its private address (a secret — kept encrypted, never shown again; only
 * whose calendar it is) and the zone of its times (Settings → This band).
 */
export function CalendarConnection() {
  const { t } = useTranslation();
  const connection = useCalendarConnection();
  const connect = useConnectCalendar();
  const disconnect = useDisconnectCalendar();
  const [address, setAddress] = useState('');
  const [zone, setZone] = useState<string | null>(null);
  const current = connection.data;
  const chosenZone = zone ?? current?.zone ?? 'Europe/Prague';
  const changed = address.trim() !== '' || (current?.connected && zone !== null && zone !== current.zone);
  return (
    <form onSubmit={(e) => {
      e.preventDefault();
      if (!address.trim() && !current?.connected) return;
      connect.mutate({ address: address.trim(), zone: chosenZone }, {
        onSuccess: () => { done(t('calendarSetup.saved')); setAddress(''); setZone(null); },
        onError: failed,
      });
    }}>
      <Stack gap="sm">
        <Group justify="space-between" wrap="nowrap">
          <Text size="sm">{current?.connected ? t('calendarSetup.connected', { host: current.host }) : t('calendarSetup.notConnected')}</Text>
          <Badge color={current?.connected ? 'teal' : 'gray'} variant="light" style={{ flexShrink: 0 }}
            styles={{ label: { textTransform: 'none' } }}>
            {current?.connected ? t('account.connected') : t('account.notConnected')}
          </Badge>
        </Group>
        <PasswordInput label={t('calendarSetup.address')} description={t('calendarSetup.addressHelp')}
          autoComplete="off" placeholder={current?.connected ? t('calendarSetup.addressKept') : 'https://…/basic.ics'}
          maxLength={2000} value={address} onChange={(e) => setAddress(e.currentTarget.value)} />
        <Select label={t('calendarSetup.zone')} description={t('calendarSetup.zoneHelp')} searchable allowDeselect={false}
          data={ZONES.includes(chosenZone) ? ZONES : [chosenZone, ...ZONES]} value={chosenZone} onChange={(v) => v && setZone(v)} />
        <Group justify="flex-end" gap="xs">
          {current?.connected && (
            <ActionButtons size="sm" actions={[{
              label: t('calendarSetup.remove'), danger: true,
              run: () => disconnect.mutate(undefined, { onSuccess: () => done(t('calendarSetup.removed')), onError: failed }),
              confirm: { title: t('calendarSetup.removeTitle'), text: t('calendarSetup.removeText'), yes: t('calendarSetup.remove') },
            }]} />
          )}
          <Button type="submit" disabled={!changed || (!current?.connected && !address.trim())} loading={connect.isPending}>
            {t('calendarSetup.save')}
          </Button>
        </Group>
      </Stack>
    </form>
  );
}

/** A rule's weight as the classifier adds it: "+4", "−6", "—" for one that only marks something. */
function weightText(rule: { weight: number }, weighted: boolean) {
  if (!weighted) return '—';
  return rule.weight > 0 ? `+${rule.weight}` : rule.weight < 0 ? `−${Math.abs(rule.weight)}` : '0';
}

function ownDrafts(rules: CalendarRule[]): CalendarRuleDraft[] {
  return rules.filter((r) => r.origin === 'USER')
    .map((r) => ({ kind: r.kind, value: r.value ?? null, weight: r.weight, enabled: r.enabled }));
}

/** One of the band's own rules on one line (wrapping on a phone): its kind, its words (or number, or label=prefix), its weight, on or off. */
function RuleRow({ rule, kinds, onChange, onRemove }: {
  rule: CalendarRuleDraft;
  kinds: RuleKind[];
  onChange: (rule: CalendarRuleDraft) => void;
  onRemove: () => void;
}) {
  const { t } = useTranslation();
  const mobile = useIsMobile();
  const kind = kinds.find((k) => k.kind === rule.kind);
  const kindSelect = (
    <Select style={{ flex: mobile ? 1 : '0 0 210px' }} aria-label={t('calendar.rule')} searchable allowDeselect={false}
      data={kinds.map((k) => k.kind)} value={rule.kind} onChange={(v) => v && onChange({ ...rule, kind: v })} />
  );
  const remove = (
    <ActionIcon variant="subtle" color="red" size="lg" aria-label={t('rules.remove')} onClick={onRemove}>
      <IconTrash size={18} />
    </ActionIcon>
  );
  const settings = (
    <>
      <TextInput style={{ flex: 1, minWidth: 0 }} aria-label={t('calendar.value')} disabled={kind?.value === 'NONE'}
        placeholder={kind && kind.value !== 'NONE' ? t(`rules.value.${kind.value}`) : undefined}
        maxLength={1000} value={kind?.value === 'NONE' ? '' : rule.value ?? ''} onChange={(e) => onChange({ ...rule, value: e.currentTarget.value })} />
      <NumberInput w={84} aria-label={t('calendar.weight')} allowDecimal={false} disabled={kind?.weighted === false}
        value={kind?.weighted === false ? '' : rule.weight}
        onChange={(v) => onChange({ ...rule, weight: typeof v === 'number' ? v : Number(v) || 0 })} />
      <Switch aria-label={t('rules.enabled')} checked={rule.enabled}
        onChange={(e) => onChange({ ...rule, enabled: e.currentTarget.checked })} />
    </>
  );
  const style = { opacity: rule.enabled ? 1 : 0.6 };
  // a phone: the kind and its removal on one line, its words, weight and switch on the next
  return mobile ? (
    <Paper withBorder p="xs" style={style}>
      <Stack gap="xs">
        <Group gap="xs" wrap="nowrap">{kindSelect}{remove}</Group>
        <Group gap="xs" wrap="nowrap">{settings}</Group>
      </Stack>
    </Paper>
  ) : (
    <Group gap="xs" wrap="nowrap" style={style}>{kindSelect}{settings}{remove}</Group>
  );
}

/**
 * The band profile's rules: the shipped ones (everyone's, read-only) and the band's own — added, changed,
 * switched off or removed here, saved as a whole.
 */
export function CalendarRules({ rules }: { rules: CalendarRule[] }) {
  const { t } = useTranslation();
  const kinds = useRuleKinds(true);
  const save = useSaveCalendarRules();
  const [own, setOwn] = useState<CalendarRuleDraft[] | null>(null);
  const [shippedOpen, setShippedOpen] = useState(false);
  const saved = ownDrafts(rules);
  const editing = own ?? saved;
  const dirty = own !== null && JSON.stringify(own) !== JSON.stringify(saved);
  const shipped = rules.filter((r) => r.origin !== 'USER');
  const kindOf = (name: string) => kinds.data?.find((k) => k.kind === name);
  const update = (i: number, rule: CalendarRuleDraft) => setOwn(editing.map((r, j) => (j === i ? rule : r)));
  return (
    <Stack gap="sm">
      <Text fw={700}>{t('rules.own')}</Text>
      {editing.length === 0 ? <Text size="sm" c="dimmed">{t('rules.none')}</Text>
        : <Text size="xs" c="dimmed">{t('rules.columns')}</Text>}
      {editing.map((rule, i) => (
        <RuleRow key={i} rule={rule} kinds={kinds.data ?? []} onChange={(r) => update(i, r)}
          onRemove={() => setOwn(editing.filter((_, j) => j !== i))} />
      ))}
      <Group justify="space-between" wrap="wrap" gap="xs">
        <Button variant="default" leftSection={<IconPlus size={16} />}
          onClick={() => setOwn([...editing, { kind: 'TITLE_CONTAINS', value: '', weight: -2, enabled: true }])}>
          {t('rules.add')}
        </Button>
        <Group gap="xs">
          {dirty && <Button variant="subtle" onClick={() => setOwn(null)}>{t('rules.undo')}</Button>}
          <Button disabled={!dirty} loading={save.isPending} onClick={() => save.mutate(editing, {
            onSuccess: () => { done(t('rules.saved')); setOwn(null); },
            onError: failed,
          })}>{t('rules.save')}</Button>
        </Group>
      </Group>

      <UnstyledButton onClick={() => setShippedOpen(!shippedOpen)} aria-expanded={shippedOpen} mt="sm">
        <Group gap={6}>
          <Text fw={700}>{t('rules.shipped', { count: shipped.length })}</Text>
          <IconChevronDown size={16} style={{ transform: shippedOpen ? 'rotate(180deg)' : undefined }} />
        </Group>
      </UnstyledButton>
      <Collapse expanded={shippedOpen}>
        <Table verticalSpacing={6} fz="sm">
          <Table.Thead>
            <Table.Tr>
              <Table.Th>{t('calendar.rule')}</Table.Th>
              <Table.Th>{t('calendar.value')}</Table.Th>
              <Table.Th>{t('calendar.weight')}</Table.Th>
            </Table.Tr>
          </Table.Thead>
          <Table.Tbody>
            {shipped.map((r, i) => (
              <Table.Tr key={i} c={r.enabled ? undefined : 'dimmed'}>
                <Table.Td><Code>{r.kind}</Code></Table.Td>
                <Table.Td style={{ wordBreak: 'break-word' }}>{r.value?.replaceAll('|', ' | ')}</Table.Td>
                <Table.Td>{weightText(r, kindOf(r.kind)?.weighted ?? r.weighted)}</Table.Td>
              </Table.Tr>
            ))}
          </Table.Tbody>
        </Table>
      </Collapse>
    </Stack>
  );
}
