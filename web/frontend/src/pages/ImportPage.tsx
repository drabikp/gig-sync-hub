import { Alert, Badge, Button, Checkbox, Group, Loader, Paper, Radio, SegmentedControl, SimpleGrid, Skeleton, Stack, Switch, Text, Title } from '@mantine/core';
import { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router';
import { useApplyImport, useImport, useReadForImport } from '../api/hooks';
import type { ImportProposal, ImportVersion } from '../api/types';
import { useBandPath } from '../band/band';
import { EmptyState } from '../components/EmptyState';
import { PageHeader } from '../components/PageHeader';
import { IconCheck } from '@tabler/icons-react';
import { usePlatformName } from '../lib/attention';
import { shortDate, timeOfDay } from '../lib/format';
import { done, failed } from '../lib/notify';

interface Decision {
  include: boolean;
  sameGig: boolean;
  version: number;
}

function versionText(v: ImportVersion, tba: string): string {
  const [day, time] = v.start.split('T');
  return `${shortDate(day)} ${timeOfDay(time)} · ${v.title} · ${v.venue || tba}, ${v.city}`;
}

/**
 * Brings the band's gigs from the platforms into the catalog, linked to their events: read the
 * platforms (a while — it runs in the background), then decide per gig and import.
 */
export function ImportPage() {
  const path = useBandPath();
  const { t } = useTranslation();
  const navigate = useNavigate();
  const name = usePlatformName();
  const state = useImport();
  const read = useReadForImport();
  const apply = useApplyImport();
  const [from, setFrom] = useState<string[] | null>(null);
  const [show, setShow] = useState<'decide' | 'upcoming' | 'all'>('all');
  const [decisions, setDecisions] = useState<Record<number, Decision>>({});
  const plan = state.data?.plan ?? null;
  const reading = state.data?.reading || read.isPending;
  const sources = from ?? state.data?.platforms ?? [];

  useEffect(() => {
    if (!plan) return;
    const next: Record<number, Decision> = {};
    plan.proposals.forEach((p) => {
      next[p.index] = { include: true, sameGig: !p.suggested, version: 0 };
    });
    setDecisions(next);
  }, [plan]);

  const today = new Date().toISOString().substring(0, 10);
  const shown = useMemo(() => (plan?.proposals ?? []).filter((p) =>
    show === 'all' ? true : show === 'upcoming' ? p.date >= today : p.suggested || p.hasConflict), [plan, show, today]);

  const set = (p: ImportProposal, patch: Partial<Decision>) =>
    setDecisions({ ...decisions, [p.index]: { ...decisions[p.index], ...patch } });
  const included = Object.values(decisions).filter((d) => d.include).length;

  if (state.isLoading) return <Skeleton h={240} radius="lg" />;

  const stats = plan && [
    { n: plan.proposals.filter((p) => !p.inCatalog).length, label: t('import.fresh'), color: 'brand' },
    { n: plan.proposals.filter((p) => p.inCatalog).length, label: t('import.toLink'), color: 'teal' },
    { n: plan.proposals.filter((p) => p.suggested || p.hasConflict).length, label: t('import.toDecide'), color: 'orange' },
    { n: plan.alreadyLinked, label: t('import.alreadyLinked'), color: 'gray' },
  ];

  return (
    <Stack gap="md" maw={860}>
      <PageHeader title={t('import.title')} phoneBack="/more" />
      <Text c="dimmed">{t('import.intro')}</Text>

      <Paper withBorder p="lg">
        <Stack gap="sm">
          <Title order={2} fz={13} c="dimmed" tt="uppercase" style={{ letterSpacing: '0.06em' }}>{t('import.readFrom')}</Title>
          {(state.data?.platforms ?? []).map((p) => (
            <Switch key={p} size="md" label={name(p)} checked={sources.includes(p)} disabled={reading}
              onChange={() => setFrom(sources.includes(p) ? sources.filter((x) => x !== p) : [...sources, p])} />
          ))}
          {reading && (
            <Alert color="orange" variant="light" icon={<Loader size="sm" color="orange" />}>{t('import.reading')}</Alert>
          )}
          {state.data?.readError && <Alert color="red" variant="light">{state.data.readError}</Alert>}
          <Button size="md" disabled={!sources.length} loading={reading} onClick={() => read.mutate(sources, { onError: failed })}>
            {plan ? t('import.readAgain') : t('import.read')}
          </Button>
        </Stack>
      </Paper>

      {plan && (
        <>
          <SimpleGrid cols={{ base: 2, sm: 4 }}>
            {stats!.map((s) => (
              <Paper key={s.label} withBorder p="md">
                <Text fz={26} fw={800} c={`${s.color}.7`}>{s.n}</Text>
                <Text size="sm" c="dimmed" fw={600}>{s.label}</Text>
              </Paper>
            ))}
          </SimpleGrid>
          {Object.entries(plan.failures).map(([p, why]) => (
            <Alert key={p} color="red" variant="light">{t('import.failed', { platform: name(p), why })}</Alert>
          ))}
          {plan.skipped.length > 0 && (
            <Text size="sm" c="dimmed">{t('import.skipped', { count: plan.skipped.length })}: {plan.skipped.join('; ')}</Text>
          )}
          <SegmentedControl value={show} onChange={(v) => setShow(v as typeof show)}
            data={[{ value: 'all', label: t('import.showAll') }, { value: 'decide', label: t('import.showDecide') }, { value: 'upcoming', label: t('import.showUpcoming') }]} />
          {shown.map((p) => {
            const d = decisions[p.index] ?? { include: true, sameGig: !p.suggested, version: 0 };
            return (
              <Paper key={p.index} withBorder p="md" style={d.include ? undefined : { opacity: 0.6 }}>
                <Stack gap="xs">
                  <Group justify="space-between" wrap="nowrap" align="flex-start">
                    <div style={{ minWidth: 0 }}>
                      <Text fw={700}>{p.versions[d.version]?.title ?? p.versions[0].title}</Text>
                      <Text size="sm" c="dimmed">{versionText(p.versions[d.version] ?? p.versions[0], t('gig.tba'))}</Text>
                      <Group gap={6} mt={4}>
                        {p.foundOn.map((f) => <Badge key={f} variant="light" styles={{ label: { textTransform: 'none' } }}>{name(f)}</Badge>)}
                        <Badge variant="light" color={p.inCatalog ? 'teal' : 'brand'} styles={{ label: { textTransform: 'none' } }}>
                          {p.inCatalog ? t('import.linksToGig') : t('import.new')}
                        </Badge>
                      </Group>
                    </div>
                    <Switch size="md" checked={d.include} onChange={() => set(p, { include: !d.include })} aria-label={t('import.include')} />
                  </Group>
                  {p.suggested && (
                    <Checkbox checked={d.sameGig} onChange={() => set(p, { sameGig: !d.sameGig })} label={t('import.sameGig')} />
                  )}
                  {p.versions.length > 1 && (
                    <Radio.Group value={String(d.version)} onChange={(v) => set(p, { version: Number(v) })} label={t('import.keepFrom')}>
                      <Stack gap={6} mt={6}>
                        {p.versions.map((v, i) => (
                          <Radio key={i} value={String(i)} label={`${v.platform ? name(v.platform) : t('import.catalog')}: ${versionText(v, t('gig.tba'))}`} />
                        ))}
                      </Stack>
                    </Radio.Group>
                  )}
                </Stack>
              </Paper>
            );
          })}
          {plan.proposals.length === 0 && (
            <EmptyState icon={<IconCheck size={28} />} title={t('import.nothingNew')} text={t('import.nothingNewText')} />
          )}
          {plan.proposals.length > 0 && <Group justify="flex-end">
            <Button size="md" disabled={!included} loading={apply.isPending}
              onClick={() => apply.mutate(Object.entries(decisions).map(([index, d]) => ({ index: Number(index), ...d })), {
                onSuccess: (r) => {
                  done(t('import.done', { added: r.added, updated: r.updated, linked: r.linked }));
                  navigate(path('/'));
                },
                onError: failed,
              })}>
              {t('import.apply', { count: included })}
            </Button>
          </Group>}
        </>
      )}
    </Stack>
  );
}
